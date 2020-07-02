package com.danawa.search.util;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.delete.DeleteRequest;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortBuilder;

public class SearchUtil {

	private static final TimeValue DEFAULT_SCROLL_KEEP_ALIVE = TimeValue.timeValueMinutes(1L);
	private static final TimeValue DEFAULT_SEARCH_TIME_OUT = new TimeValue(60, TimeUnit.SECONDS);
	private static final int DEFAULT_SCROLL_SIZE = 10000;

	private static Logger logger = Loggers.getLogger(SearchUtil.class, "");

	public static void deleteAllData(NodeClient client, String index) {
		BulkRequestBuilder builder = null;
		builder = client.prepareBulk();
		SearchHit[] hits = null;
		ClearScrollRequest clearScroll = null;
		Scroll scroll = null;
		String scrollId = null;

		try {
			QueryBuilder query = null;
			query = QueryBuilders.matchAllQuery();
			SearchSourceBuilder source = new SearchSourceBuilder();
			source.query(query);
			SearchRequest search = new SearchRequest(index.split("[,]"));
			clearScroll = new ClearScrollRequest();
			scroll = new Scroll(DEFAULT_SCROLL_KEEP_ALIVE);
			source.from(0);
			source.size(DEFAULT_SCROLL_SIZE);
			source.timeout(DEFAULT_SEARCH_TIME_OUT);
			search.source(source);
			search.scroll(scroll);
			SearchResponse response = client.search(search).get();
			hits = response.getHits().getHits();
			scrollId = response.getScrollId();
			clearScroll.addScrollId(scrollId);

			int totInx = 0;
			for (; hits != null && hits.length > 0;) {
				for (int inx = 0; inx < hits.length; inx++, totInx++) {
					DeleteRequest request = new DeleteRequest(index, hits[inx].getId());
					builder.add(request);
					if (totInx > 0 && totInx % 5000 == 0) {
						builder.execute().actionGet();
						builder = client.prepareBulk();
					}
				}
				SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
				scrollRequest.scroll(scroll);
				response = client.searchScroll(scrollRequest).get();
				hits = response.getHits().getHits();
				scrollId = response.getScrollId();
				clearScroll.addScrollId(scrollId);
			}
			if (totInx > 0) {
				builder.execute().actionGet();
			}
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	public static long count(NodeClient client, String index, QueryBuilder query) {
		long ret = 0;
		try {
			SearchRequest countRequest = new SearchRequest(index.split("[,]"));
			SearchSourceBuilder countSource = new SearchSourceBuilder().query(query).size(0).trackTotalHits(true);
			countRequest.source(countSource);
			SearchResponse countResponse = client.search(countRequest).get();
			ret = countResponse.getHits().getTotalHits().value;
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	public static Iterator<Map<String, Object>> search(NodeClient client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, int from, int size, boolean doScroll) {
		Iterator<Map<String, Object>> ret = null;
		if (doScroll) {
			ret = new ScrollSearchResultIterator().doSearch(client, index, query, sortSet, from, size);
		} else {
			ret = new SearchResultIterator().doSearch(client, index, query, sortSet, from, size);
		}
		return ret;
	}

	static abstract class AbstractSearchResultIterator implements Iterator<Map<String, Object>> {
		public static final String FIELD_ROWNUM = "_ROWNUM";
		public static final String FIELD_SORT = "_SORT";
		abstract Iterator<Map<String, Object>> doSearch(NodeClient client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, int from, int size);
		static Map<String, Object> processHit(SearchHit hit, int rowNum) {
			Map<String, Object> rowData;
			rowData = hit.getSourceAsMap();
			rowData.put(FIELD_ROWNUM, rowNum);
			rowData.put(FIELD_SORT, hit.getSortValues());
			return rowData;
		}
	}

	static class SearchResultIterator extends AbstractSearchResultIterator {
		private SearchHit[] hits;
		private int rowNum;
		private int hitsInx;
		private Map<String, Object> rowData;
		private TimeValue timeOut;

		@Override 
		public Iterator<Map<String, Object>> doSearch(NodeClient client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, int from, int size) {
			/**
			 * 단순 검색. 빠르지만 1만건 이상 검색결과 검색 불가능
			 **/
			try {
				SearchSourceBuilder source = new SearchSourceBuilder();
				if (sortSet != null) {
					for (SortBuilder<?> sort : sortSet) {
						source.sort(sort);
					}
				}
				source.query(query);
				SearchRequest search = new SearchRequest(index.split("[,]"));
				source.from(from);
				source.size(size);
				source.timeout(timeOut);
				search.source(source);
				SearchResponse response = client.search(search).get();
				hits = response.getHits().getHits();
				rowNum = from;
				rowData = null;
				return this;
			} catch (Exception e) {
				logger.debug("SEARCH ERROR : {} ( It may be over 10,000 records ) ", e.getMessage());
			}
			return null;
		}

		@Override public boolean hasNext() {
			boolean ret = false;
			if (rowData != null) { return true; }
			try {
				for (; hits != null && hits.length > 0;) {
					for (; hitsInx < hits.length;) {
						rowData = processHit(hits[hitsInx], rowNum);
						hitsInx++;
						rowNum++;
						break;
					}

					if (rowData != null) {
						ret = true;
						break;
					} else {
						ret = false;
						break;
					}
				}
			} catch (Exception e) {
				logger.error("", e);
			}
			return ret;
		}

		@Override public Map<String, Object> next() {
			Map<String, Object> ret = rowData;
			rowData = null;
			return ret;
		}

		public void close() { }
	}

	static class ScrollSearchResultIterator extends AbstractSearchResultIterator {

		private NodeClient client;
		private SearchHit[] hits;
		private ClearScrollRequest clearScroll;
		private Scroll scroll;
		private String scrollId;
		private int rowNum;
		private int hitsInx;
		private Map<String, Object> rowData;
		private TimeValue scrollKeepAlive;
		private TimeValue timeOut;
		private int scrollSize;
		private int size;

		public ScrollSearchResultIterator() { 
			this(DEFAULT_SEARCH_TIME_OUT, DEFAULT_SCROLL_KEEP_ALIVE, DEFAULT_SCROLL_SIZE);
		}

		public ScrollSearchResultIterator(TimeValue timeOut, TimeValue scrollKeepAlive, int scrollSize) {
			this.scrollKeepAlive = scrollKeepAlive;
			this.timeOut = timeOut;
			this.scrollSize = scrollSize;
		}

		@Override
		public Iterator<Map<String, Object>> doSearch(NodeClient client, String index, QueryBuilder query, List<SortBuilder<?>> sortSet, int from, int size) {
			/**
			 * 스크롤 스트리밍 검색. 느리지만 1만건 이상 검색결과를 추출가능함.
			 **/
			try {
				this.client = client;
				SearchSourceBuilder source = new SearchSourceBuilder();
				if (sortSet != null) {
					for (SortBuilder<?> sort : sortSet) {
						source.sort(sort);
					}
				}
				source.query(query);
				SearchRequest search = new SearchRequest(index.split("[,]"));
				clearScroll = new ClearScrollRequest();
				scroll = new Scroll(scrollKeepAlive);
				source.from(0);
				source.size(scrollSize);
				source.timeout(timeOut);
				search.source(source);
				search.scroll(scroll);
				SearchResponse response = client.search(search).get();
				hits = response.getHits().getHits();
				scrollId = response.getScrollId();
				clearScroll.addScrollId(scrollId);
				for (rowNum = 0; hits != null && hits.length > 0 && rowNum < from;) {
					if (rowNum + hits.length <= from) { 
						rowNum += hits.length;
					} else {
						hitsInx = from - rowNum;
						rowNum = from;
						break;
					}
					SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
					scrollRequest.scroll(scroll);
					response = client.searchScroll(scrollRequest).get();
					hits = response.getHits().getHits();
					scrollId = response.getScrollId();
					clearScroll.addScrollId(scrollId);
				}
				this.size = size;
				rowData = null;
				return this;
			} catch (Exception e) {
				logger.error("", e);
			}
			return null;
		}

		@Override public boolean hasNext() {
			boolean ret = false;
			if (rowData != null) { return true; }
			try {
				for (; hits != null && hits.length > 0 && (size == -1 || size > 0);) {
					for (; hitsInx < hits.length && (size == -1 || size > 0);) {
						rowData = processHit(hits[hitsInx], rowNum);
						hitsInx++;
						rowNum++;
						if (size != -1) {
							size--;
						}
						break;
					}

					if (rowData != null) {
						ret = true;
						break;
					}

					SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
					scrollRequest.scroll(scroll);

					SearchResponse response = client.searchScroll(scrollRequest).get();
					hits = response.getHits().getHits();
					scrollId = response.getScrollId();
					clearScroll.addScrollId(scrollId);
					hitsInx = 0;
				}
				if (size == 0 || (hits == null && rowData == null)) {
					close(); 
					ret = false;
				}
			} catch (Exception e) {
				logger.error("", e);
			}
			return ret;
		}

		@Override public Map<String, Object> next() {
			Map<String, Object> ret = rowData;
			rowData = null;
			return ret;
		}

		public void close() {
			try {
				client.clearScroll(clearScroll).get();
			} catch (Exception ignore) { }
		}
	}
}
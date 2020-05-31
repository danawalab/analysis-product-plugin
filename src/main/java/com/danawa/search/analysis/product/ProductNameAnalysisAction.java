package com.danawa.search.analysis.product;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.util.ContextStore;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.ClearScrollRequest;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.action.search.SearchScrollRequest;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.Scroll;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

public class ProductNameAnalysisAction extends BaseRestHandler {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisAction.class, "");

	private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
	private static final String BASE_URI = "/_product-name-analysis";
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);
	private static final String ACTION_RELOAD_DICT = "reload-dict";
	private static final String ACTION_ADD_DOCUMENT = "add-document";
	private static final String ACTION_FULL_INDEX = "full-index";
	private static final String ACTION_SEARCH = "search";

	private IndexingThread indexingThread;

	@Inject
	ProductNameAnalysisAction(Settings settings, RestController controller) {
		controller.registerHandler(Method.GET, BASE_URI + "/{action}", this);
		controller.registerHandler(Method.POST, BASE_URI + "/{action}", this);
		controller.registerHandler(Method.GET, BASE_URI, this);
		controller.registerHandler(Method.POST, BASE_URI, this);
	}

	@Override
	public String getName() {
		return "rest_handler_product_name_analysis";
	}

	@Override
	protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
		final String action = request.param("action");
		final StringWriter buffer = new StringWriter();
		JSONWriter builder = new JSONWriter(buffer);

		if (ACTION_RELOAD_DICT.equals(action)) {
			reloadDictionary();
			builder.object()
				.key("action").value(action)
				.endObject();
		} else if (ACTION_ADD_DOCUMENT.equals(action)) {
			addDocument(request, client);
			builder.object()
				.key("action").value(action)
				.endObject();
		} else if (ACTION_FULL_INDEX.equals(action)) {
			SpecialPermission.check();
			int count = AccessController.doPrivileged((PrivilegedAction<Integer>) () -> {
				return bulkIndex(request, client);
			});
			builder.object()
				.key("action").value(action);
			if (count > 0) {
				builder.key("working").value("true")
					.key("count").value(count);
			} else {
				builder.key("working").value("false");
			}
			builder.endObject();
		} else if (ACTION_SEARCH.equals(action)) {
			search(request, client, builder);
		}
		return channel -> {
			channel.sendResponse(new BytesRestResponse(RestStatus.OK, CONTENT_TYPE_JSON, buffer.toString()));
		};
	}

	public void reloadDictionary() {
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			ProductNameTokenizerFactory.reloadDictionary();
		}
	}

	public void addDocument(final RestRequest request, final NodeClient client) {
		try {
			logger.debug("PARSING REST-BODY...");
			JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
			for (String key : jobj.keySet()) {
				String value = jobj.optString(key, "");
				logger.debug("PARSE VALUE {} = {}", key, value);
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		try {
			logger.debug("TESTING SEARCH...");
			// 문서 검색 테스트
			ActionListener<SearchResponse> listener = new ActionListener<SearchResponse>() {
				@Override public void onResponse(SearchResponse response) {
					SearchHits hits = response.getHits();
					for (SearchHit hit : hits.getHits()) {
						Map<String, Object> map = hit.getSourceAsMap();
						logger.debug("RESULT:{}", map);
					}
				}
				@Override public void onFailure(Exception e) { }
			};
			SearchSourceBuilder sourceBuilder = new SearchSourceBuilder();
			sourceBuilder.query(QueryBuilders.queryStringQuery("PRODUCTNAME:상품명테스트"));
			sourceBuilder.from(0);
			sourceBuilder.size(5);
			sourceBuilder.timeout(new TimeValue(60, TimeUnit.SECONDS));
			SearchRequest searchRequest = new SearchRequest("sample_index");
			searchRequest.source(sourceBuilder);
			client.search(searchRequest, listener);
		} catch (Exception e) {
			logger.error("", e);
		}
		try {
			logger.debug("CREATE DOCUMENT...");
			// 샘플문서 생성 테스트
			IndexRequestBuilder builder = client.prepareIndex("sample_index", "_doc");
			Map<String, Object> source = new HashMap<>();
			source.put("PRODUCTNAME", "상품명테스트");
			builder.setSource(source);
			builder.get();
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	private int bulkIndex(final RestRequest request, final NodeClient client) {
		int ret = 0;
		String path = null;
		String enc = null;
		String indexName = null;
		int flush = 5000;
		try {
			JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
			path = jobj.optString("path", "");
			enc = jobj.optString("enc", "euc-kr");
			indexName = jobj.optString("index", "");
			flush = jobj.optInt("flush", 50000);
		} catch (Exception e) {
			logger.error("", e);
		}
		synchronized (this) {
			if (indexingThread == null || !indexingThread.running()) {
				indexingThread = new IndexingThread(indexName, path, enc, flush, client);
				indexingThread.start();
			} else {
				ret = indexingThread.count();
			}
		}
		return ret;
	}

	private void search(final RestRequest request, final NodeClient client, final JSONWriter builder) {
		TokenStream stream = null;
		try {
			logger.debug("TESTING SEARCH...");
			JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
			String index = jobj.optString("index", "");
			String[] fields = jobj.optString("fields", "").split("[,]");
			String text = jobj.optString("text", "");
			int from = jobj.optInt("from", 0);
			int size = jobj.optInt("size", 20);
			boolean trackTotal = jobj.optBoolean("total", false);

			logger.debug("ANALYZE TEXT : {}", text);
			stream = getAnalyzer(text);

			JSONArray analysis = new JSONArray();

			QueryBuilder query = buildQuery(stream, fields, analysis);

			logger.debug("Q:{}", query.toString());

			SearchSourceBuilder source = new SearchSourceBuilder();
			source.query(query);

			long total = -1;

			if (trackTotal) {
				// NOTE: 부하가 얼마나 걸릴지 체크해 봐야 할듯.
				SearchRequest countRequest = new SearchRequest(index);
				SearchSourceBuilder countSource = new SearchSourceBuilder().query(query).size(0).trackTotalHits(true);
				countRequest.source(countSource);
				SearchResponse countResponse = client.search(countRequest).get();
				total = countResponse.getHits().getTotalHits().value;
				logger.debug("TOTAL:{}", total);
			}

			boolean doScroll = from + size > 10000;
			SearchRequest search = new SearchRequest(index);
			Scroll scroll = null;

			if (doScroll) {
				logger.debug("SCROLL SEARCH");
				scroll = new Scroll(TimeValue.timeValueMinutes(10L));
				source.from(0);
				source.size(100);
				source.timeout(new TimeValue(60, TimeUnit.SECONDS));
				search.source(source);
				search.scroll(scroll);
			} else {
				logger.debug("LIMIT SEARCH {}~{}", from, size);
				source.from(from);
				source.size(size);
				search.source(source);
			}

			builder.object().key("analysis").value(analysis);
			if (doScroll) {
				doSearchScroll(search, scroll, from, size, total, client, builder);
			} else {
				doSearch(search, total, client, builder);
			}
			builder.endObject();
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	public QueryBuilder buildQuery(TokenStream stream, String[] fields, JSONArray analysis) {
		QueryBuilder ret = null;

		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);

		try {
			stream.reset();
			BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
			List<CharSequence> synonyms = null;
			while (stream.incrementToken()) {
				String termStr = String.valueOf(termAttr);
				logger.debug("TOKEN:{} / {}", termStr, typeAttr.type());
				JSONObject termDetail = new JSONObject();
				termDetail.put("term", termStr);
				QueryBuilder termQuery = QueryBuilders.multiMatchQuery(termStr, fields);

				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					JSONArray synonymList = new JSONArray();
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					query.should().add(termQuery);
					for (int sinx = 0; sinx < synonyms.size(); sinx++) {
						String synonym = String.valueOf(synonyms.get(sinx));
						synonymList.put(synonym);
						if (synonym.indexOf(" ") == -1) {
							query.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
						} else {
							BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
							for (String field : fields) {
								inQuery.should().add(QueryBuilders.matchPhraseQuery(field, synonym).slop(3));
							}
							query.should().add(inQuery);
						}
						logger.debug(" |_synonym : {}", synonym);
					}
					termDetail.put("synonym", synonymList);
					termQuery = query;
				}
				if (extAttr != null && extAttr.size() > 0) {
					JSONArray extraList = new JSONArray();
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					Iterator<String> termIter = extAttr.iterator();
					for (; termIter.hasNext();) {
						String term = termIter.next();
						String type = typeAttr.type();
						extraList.put(term);
						query.must().add(QueryBuilders.multiMatchQuery(String.valueOf(term), fields));
						logger.debug("a-term:{} / type:{}", term, type);
					}
					BoolQueryBuilder parent = QueryBuilders.boolQuery();
					parent.should().add(termQuery);
					parent.should().add(query);
					termDetail.put("extra", extraList);
					termQuery = parent;
				}
				if (analysis != null) {
					analysis.put(termDetail);
				}
				mainQuery.must().add(termQuery);
			}
			ret = mainQuery;
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}

		return ret;
	}


	public void doSearchScroll(SearchRequest search, Scroll scroll, int from, int size, long total, NodeClient client, JSONWriter builder) throws Exception {
		SearchHit[] hits = null;
		SearchResponse response = null;
		ClearScrollRequest clearScroll = new ClearScrollRequest();
		response = client.search(search).get();
		String scrollId = response.getScrollId();
		clearScroll.addScrollId(scrollId);
		hits = response.getHits().getHits();
		if (hits != null) {
			if (total != -1) {
				builder.key("total").value(total);
			}
			builder.key("result").array();
			for (int rownum = 0; hits != null && hits.length > 0;) {
				logger.trace("FROM:{} / {}", from, rownum);
				if (rownum + hits.length <= from) { 
					rownum += hits.length;
				} else {
					for (SearchHit hit : hits) {
						if (rownum < from) { 
						} else {
							Map<String, Object> map = hit.getSourceAsMap();
							map.put("ROWNUM", rownum);
							logger.trace("RESULT:{}", map);
							builder.object();
							for (String key : map.keySet()) {
								builder.key(key).value(map.get(key));
							}
							builder.endObject();
							if (--size <= 0) { break; }
						}
						rownum++;
					}
				}
				if (size <= 0) { break; }
				SearchScrollRequest scrollRequest = new SearchScrollRequest(scrollId);
				scrollRequest.scroll(scroll);
				response = client.searchScroll(scrollRequest).get();
				hits = response.getHits().getHits();
				scrollId = response.getScrollId();
				clearScroll.addScrollId(scrollId);
			}
			builder.endArray();
		}
		client.clearScroll(clearScroll).get();
	}

	public void doSearch(SearchRequest search, long total, NodeClient client, JSONWriter builder) throws Exception {
		SearchHit[] hits = null;
		SearchResponse response = null;
		response = client.search(search).get();
		hits = response.getHits().getHits();
		if (hits != null) {
			if (total != -1) {
				builder.key("total").value(total);
			}
			builder.key("result").array();
			int rownum = 0;
			for (SearchHit hit : hits) {
				Map<String, Object> map = hit.getSourceAsMap();
				map.put("ROWNUM", rownum);
				logger.trace("RESULT:{}", map);
				builder.object();
				for (String key : map.keySet()) {
					builder.key(key).value(map.get(key));
				}
				builder.endObject();
				rownum++;
			}
			builder.endArray();
		}
	}

	public static TokenStream getAnalyzer(String str) {
		TokenStream tstream = null;
		Reader reader = null;
		Tokenizer tokenizer = null;
		ProductNameDictionary dictionary = null;
		KoreanWordExtractor extractor = null;
		AnalyzerOption option = null;
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
			extractor = new KoreanWordExtractor(dictionary);
		}
		option = new AnalyzerOption();
		option.useForQuery(true);
		option.useSynonym(true);
		option.useStopword(true);
		reader = new StringReader(str);
		tokenizer = new ProductNameTokenizer(dictionary, false);
		extractor = new KoreanWordExtractor(dictionary);
		tokenizer.setReader(reader);
		tstream = new ProductNameAnalysisFilter(tokenizer, extractor, dictionary, option);
		return tstream;
	}

	static class IndexingThread extends Thread implements FileFilter {
		private String indexName;
		private String path;
		private String enc;
		private int flush;
		private NodeClient client;
		private boolean running; 
		private int count;
		List<File> files;
		private static final Pattern ptnHead = Pattern.compile("\\x5b[%]([a-zA-Z0-9_-]+)[%]\\x5d");

		public IndexingThread(String indexName, String path, String enc, int flush, NodeClient client) {
			this.indexName = indexName;
			this.path = path;
			this.enc = enc;
			this.flush = flush;
			this.client = client;
		}

		public boolean running() {
			return running;
		}

		public int count() {
			return count;
		}

		@Override public void run() {
			running = true;
			count = 0;
			files = new ArrayList<>();
			String[] paths = path.split(",");
			for (String path : paths) {
				path = path.trim();
				File base = new File(path);
				if (!base.exists()) { 
					logger.debug("BASE FILE NOT FOUND : {}", base);
				} else {
					if (base.isDirectory()) {
						base.listFiles(this);
					} else {
						files.add(base);
					}
				}
			}

			if (files.size() > 0) {
				BufferedReader reader = null;
				InputStream istream = null;
				long time = System.currentTimeMillis();
				boolean isSourceFile = false;
				try {
					BulkRequestBuilder builder = null;
					Map<String, Object> source;
					
					for (File file : files) {
						if (!file.exists()) {
							logger.debug("FILE NOT FOUND : {}", file);
							continue;
						}
						isSourceFile = false;
						istream =  new FileInputStream(file);
						reader = new BufferedReader(new InputStreamReader(istream, String.valueOf(enc)));
						logger.debug("PARSING FILE..{}", file);
						builder = client.prepareBulk();
						for (String line; (line = reader.readLine()) != null; count++) {
							Matcher mat = ptnHead.matcher(line);
							String key = null;
							int offset = 0;
							source = new HashMap<>();
							while (mat.find()) {
								isSourceFile = true;
								if (key != null) {
									fieldValue(source, key, line.substring(offset, mat.start()));
								}
								key = mat.group(1);
								offset = mat.end();
							}
							if (isSourceFile) {
								fieldValue(source, key, line.substring(offset));

								builder.add(client.prepareIndex(String.valueOf(indexName), "_doc").setSource(source));
								if (count > 0 && count % flush == 0) {
									builder.execute().actionGet();
									builder = client.prepareBulk();
								}
								if (count > 0 && count % 100000 == 0) {
									logger.debug("{} ROWS FLUSHED! in {}ms", count, System.currentTimeMillis() - time);
								}
							} else {
								logger.debug("{} IS NOT SOURCEFILE", file);
								// 소스파일이 아니므로 바로 다음파일로.
								break;
							}
						}
						builder.execute().actionGet();
						try { reader.close(); } catch (Exception ignore) { }
					}
					logger.debug("TOTAL {} ROWS in {}ms", count, System.currentTimeMillis() - time);
				} catch (Exception e) {
					logger.error("", e);
				} finally {
					try { reader.close(); } catch (Exception ignore) { }
				}
			} else {
				logger.debug("THERE'S NO SOURCE FILE(S) FOUND");
			}
			running = false;
		}

		private void fieldValue(Map<String, Object> source, String key , String value) throws Exception {
			if ("".equals(key)) {
			} else if ("REGISTERDATE".equals(key)) {
				SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
				source.put(key, sdf.parse(value));
			} else {
				source.put(key, value);
			}
			logger.trace("ROW:{} / {}", key, value);
		}

		@Override public boolean accept(File file) {
			if (!file.exists()) { return false; }
			if (file.isDirectory()) {
				file.listFiles(this);
			} else if (file.isFile()) {
				files.add(file);
			}
			return false;
		}
	}
}
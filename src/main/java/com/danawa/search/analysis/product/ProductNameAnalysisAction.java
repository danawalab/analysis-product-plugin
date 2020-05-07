package com.danawa.search.analysis.product;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.danawa.util.ContextStore;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.XContentParser.Token;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;

public class ProductNameAnalysisAction extends BaseRestHandler {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisAction.class, "");

	private static final String BASE_URI = "/_product-name-analysis";
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);
	private static final String RELOAD_DICT = "reload-dict";
	private static final Object ADD_DOCUMENT = "add-document";

	@Inject
	ProductNameAnalysisAction(Settings settings, RestController controller) {
		controller.registerHandler(Method.GET, BASE_URI + "/{action}", this);
		controller.registerHandler(Method.POST, BASE_URI + "/{action}", this);
		controller.registerHandler(Method.GET, BASE_URI, this);
		controller.registerHandler(Method.POST, BASE_URI, this);
	}

	@Override
	public String getName() {
		return "rest_handler_product-name-analysis";
	}

	@Override
	protected RestChannelConsumer prepareRequest(RestRequest request, NodeClient client) throws IOException {
		final String action = request.param("action");

		if (RELOAD_DICT.equals(action)) {
			reloadDictionary();
		} else if (ADD_DOCUMENT.equals(action)) {
			addDocument(request, client);
		}

		return channel -> {
			XContentBuilder builder = channel.newBuilder();
			builder.startObject()
				.field("message", "OK")
				.field("action", action)
			.endObject();
			channel.sendResponse(new BytesRestResponse(RestStatus.OK, builder));
		};
	}

	public void reloadDictionary() {
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			ProductNameTokenizerFactory.reloadDictionary();
		}
	}

	public void addDocument(RestRequest request, NodeClient client) {

		try {
			logger.debug("PARSING REST-BODY...");
			XContentParser parser = null;
			Token token = null;
			parser = request.contentParser();
			// REST body (JSON) parse
			while ((token = parser.nextToken()) != Token.END_OBJECT) {
				if (token == XContentParser.Token.FIELD_NAME) {
					String fieldName = parser.currentName();
					token = parser.nextToken();
					if (token == Token.VALUE_STRING) {
						String text = parser.text();
						logger.debug("PARSE VALUE {} = {}", fieldName, text);
					}
				}
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
			sourceBuilder.query(QueryBuilders.matchAllQuery());
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
}
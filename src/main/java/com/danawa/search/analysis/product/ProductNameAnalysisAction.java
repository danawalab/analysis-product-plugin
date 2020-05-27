package com.danawa.search.analysis.product;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.text.SimpleDateFormat;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.HashMap;
import java.util.Map;

import com.danawa.util.ContextStore;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.ActionListener;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.unit.TimeValue;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.rest.BaseRestHandler;
import org.elasticsearch.rest.BytesRestResponse;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestRequest.Method;
import org.elasticsearch.rest.RestRequest;
import org.elasticsearch.rest.RestStatus;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.SearchHits;
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

		if (ACTION_RELOAD_DICT.equals(action)) {
			reloadDictionary();
		} else if (ACTION_ADD_DOCUMENT.equals(action)) {
			addDocument(request, client);
		} else if (ACTION_FULL_INDEX.equals(action)) {
			SpecialPermission.check();
			AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
				bulkIndex(request, client);
				return null;
			});
		}

		return channel -> {
			StringWriter buffer = new StringWriter();
			JSONWriter builder = new JSONWriter(buffer);
			builder.object()
				.key("message").value("OK")
				.key("action").value(action)
				.endObject();
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

	private void bulkIndex(final RestRequest request, final NodeClient client) {
		final StringBuilder path = new StringBuilder();
		final StringBuilder enc = new StringBuilder();
		final StringBuilder indexName = new StringBuilder();
		try {
			JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
			path.append(jobj.optString("path", ""));
			enc.append(jobj.optString("enc", "euc-kr"));
			indexName.append(jobj.optString("index", ""));
		} catch (Exception e) {
			logger.error("", e);
		}

		new Thread() {
			@Override public void run() {
				final File file = new File(String.valueOf(path));
				if (!file.exists()) { 
					logger.debug("FILE NOT FOUND : {}", path);
					return; 
				}
				BufferedReader reader = null;
				InputStream istream = null;
				Pattern ptn = Pattern.compile("\\x5b[%]([a-zA-Z0-9]+)[%]\\x5d");
				long time = System.currentTimeMillis();
				try {
					BulkRequestBuilder builder = client.prepareBulk();
					Map<String, Object> source;
					istream =  new FileInputStream(file);
					reader = new BufferedReader(new InputStreamReader(istream, String.valueOf(enc)));
					int count = 0;
					logger.debug("PARSING FILE..");
					for (String line; (line = reader.readLine()) != null; count++) {
						Matcher mat = ptn.matcher(line);
						String key = null;
						int offset = 0;
						source = new HashMap<>();
						while (mat.find()) {
							if (key != null) {
								columnValue(source, key, line.substring(offset, mat.start()));
							}
							key = mat.group(1);
							offset = mat.end();
						}
						columnValue(source, key, line.substring(offset));

						builder.add(client.prepareIndex(String.valueOf(indexName), "_doc").setSource(source));
						if (count > 0 && count % 50000 == 0) {
							builder.execute().actionGet();
							builder = client.prepareBulk();
						}
						if (count > 0 && count % 100000 == 0) {
							logger.debug("{} ROWS FLUSHED! in {}ms", count, System.currentTimeMillis() - time);
						}
					}
					builder.execute().actionGet();
				} catch (Exception e) {
					logger.error("", e);
				} finally {
					try { reader.close(); } catch (Exception ignore) { }
				}
			}
		}.start();
	}

	private void columnValue(Map<String, Object> source, String key , String value) throws Exception {
		if ("".equals(key)) {
		} else if ("REGISTERDATE".equals(key)) {
			SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
			source.put(key, sdf.parse(value));
		} else {
			source.put(key, value);
		}
		logger.trace("ROW:{} / {}", key, value);
	}
}
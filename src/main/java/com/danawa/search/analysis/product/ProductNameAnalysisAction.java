package com.danawa.search.analysis.product;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SourceDictionary;
import com.danawa.search.analysis.dict.TagProbDictionary;
import com.danawa.search.analysis.korean.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.product.ProductNameTokenizerFactory.DictionaryRepository;
import com.danawa.search.index.DanawaBulkTextIndexer;
import com.danawa.search.index.DanawaSearchQueryBuilder;
import com.danawa.search.util.SearchUtil;
import com.danawa.util.CharVector;
import com.danawa.util.ContextStore;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
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
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.JSONWriter;

public class ProductNameAnalysisAction extends BaseRestHandler {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisAction.class, "");

	private static final String CONTENT_TYPE_JSON = "application/json;charset=UTF-8";
	private static final String BASE_URI = "/_product-name-analysis";
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);
	private static final String ACTION_INFO_DICT = "info-dict";
	private static final String ACTION_FIND_DICT = "find-dict";
	private static final String ACTION_RELOAD_DICT = "reload-dict";
	private static final String ACTION_RESTORE_DICT = "restore-dict";
	private static final String ACTION_COMPILE_DICT = "compile-dict";
	private static final String ACTION_FULL_INDEX = "full-index";
	private static final String ACTION_TEST = "test";
	private static final String ACTION_SEARCH = "search";
	private static final String ES_DICTIONARY_INDEX = ".fastcatx_dict";
	private static final String ES_DICT_FIELD_ID = "id";
	private static final String ES_DICT_FIELD_TYPE = "type";
	private static final String ES_DICT_FIELD_KEYWORD = "keyword";
	private static final String ES_DICT_FIELD_VALUE = "value";

	public static final String TAB = "\t";

	private static ProductNameDictionary dictionary;

	private DanawaBulkTextIndexer indexingThread;

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
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_INFO_DICT.equals(action)) {
			builder.object().key("action").value(action);
			infoDictionary(request, client, builder);
			builder.endObject();
		} else if (ACTION_FIND_DICT.equals(action)) {
			builder.object().key("action").value(action);
			findDictionary(request, client, builder);
			builder.endObject();
		} else if (ACTION_RESTORE_DICT.equals(action)) {
			restoreDictionary(request, client);
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_COMPILE_DICT.equals(action)) {
			compileDictionary(request, client);
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_FULL_INDEX.equals(action)) {
			SpecialPermission.check();
			int count = AccessController.doPrivileged((PrivilegedAction<Integer>) () -> {
				return bulkIndex(request, client);
			});
			builder.object().key("action").value(action);
			if (count > 0) {
				builder.key("working").value("true").key("count").value(count);
			} else {
				builder.key("working").value("false");
			}
			builder.endObject();
		} else if (ACTION_TEST.equals(action)) {
			testAction(request, client);
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_SEARCH.equals(action)) {
			search(request, client, builder);
		}
		return channel -> {
			channel.sendResponse(new BytesRestResponse(RestStatus.OK, CONTENT_TYPE_JSON, buffer.toString()));
		};
	}

	private static final String FULL_STRING = ProductNameTokenizer.FULL_STRING;
	private static final String MODEL_NAME = ProductNameTokenizer.MODEL_NAME;
	private static final String UNIT = ProductNameTokenizer.UNIT;
	private static final String RESTRICTED = "RESTRICTED";
	private static final String NORMAL_SET = "NORMAL_SET";
	private static final String FINAL_SET = "FINAL_SET";

	private void testAction(RestRequest request, NodeClient client) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String text = jobj.optString("text", "");
		boolean useForQuery = jobj.optBoolean("useForQuery", true);
		boolean useSynonym = jobj.optBoolean("useSynonym", true);
		boolean useStopword = jobj.optBoolean("useStopword", true);
		TokenStream stream = getAnalyzer(text, useForQuery, useSynonym, useStopword);
		analyzeTextDetail(stream);
	}

	public static void analyzeTextDetail(TokenStream stream) {
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		SynonymAttribute synAtt = stream.addAttribute(SynonymAttribute.class);
		OffsetAttribute offAttr = stream.addAttribute(OffsetAttribute.class);
		Map<String, List<List<String>>> result = new HashMap<>();
		List<List<String>> wordList = null;
		List<String> words = null;
		result.put(FULL_STRING, new ArrayList<>());
		result.put(MODEL_NAME, new ArrayList<>());
		result.put(UNIT, new ArrayList<>());
		result.put(RESTRICTED, new  ArrayList<>());
		result.put(NORMAL_SET, new ArrayList<>());
		result.put(FINAL_SET, new ArrayList<>());

		// QUERYING..
		// TOKEN:Sandisk Extream Z80 USB 16gb / 0~28 / <FULL_STRING> / [null|[]]
		// TOKEN:Sandisk / 0~7 / <ALPHA> / [[샌디스크, 산디스크, 센디스크, 샌디스크 코리아, 산디스크 코리아]|[]]
		// |_synonym : 샌디스크
		// |_synonym : 산디스크
		// |_synonym : 센디스크
		// |_synonym : 샌디스크 코리아
		// |_synonym : 산디스크 코리아
		// TOKEN:Extream / 8~15 / <ALPHA> / [null|[]]
		// TOKEN:Z80 / 16~19 / <MODEL_NAME> / [null|[Z, 80]]
		// a-term:Z / type:<ALPHA>
		// a-term:80 / type:<NUMBER>
		// TOKEN:USB / 20~23 / <ALPHA> / [[유에스비, usb용, usb형, 유에스비용, 유에스비형]|[]]
		// |_synonym : 유에스비
		// |_synonym : usb용
		// |_synonym : usb형
		// |_synonym : 유에스비용
		// |_synonym : 유에스비형
		// TOKEN:16gb / 24~28 / <UNIT> / [[16g, 16기가]|[]]
		// |_synonym : 16g
		// |_synonym : 16기가

		// INDEXING..
		// TOKEN:Sandisk / 0~7 / <HANGUL> / [null|[]]
		// TOKEN:Extream / 8~15 / <ALPHA> / [null|[]]
		// TOKEN:Z80 / 16~19 / <MODEL_NAME> / [null|[]]
		// TOKEN:Z / 16~17 / <ALPHA> / [null|[]]
		// TOKEN:80 / 17~19 / <NUMBER> / [null|[]]
		// TOKEN:USB / 20~23 / <ALPHA> / [null|[]]
		// TOKEN:16gb / 24~28 / <UNIT> / [null|[]]
		// TOKEN:16 / 24~26 / <NUMBER> / [null|[]]

		String term = null;
		String type = null;
		String typePrev = null;
		int[] offset = { 0, 0 };
		int[] offsetPrev = { 0, 0 };
		try {
			stream.reset();
			while (stream.incrementToken()) {
				typePrev = type;
				offsetPrev = new int[] { offset[0], offset[1] };
				term = String.valueOf(termAttr);
				type = typeAttr.type();
				offset = new int[] { offAttr.startOffset(), offAttr.endOffset() };
				if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					// 전체 단어
					setAnalyzedResult(result, term, FULL_STRING);
				} else if (ProductNameTokenizer.MODEL_NAME.equals(type)) {
					// 모델명의 경우 색인시와 질의시 추출방법이 서로 다르다.
					setAnalyzedResult(result, term, MODEL_NAME, FINAL_SET);
				} else if (ProductNameTokenizer.UNIT.equals(type)) {
					setAnalyzedResult(result, term, UNIT, NORMAL_SET, FINAL_SET);
				} else {
					setAnalyzedResult(result, term, NORMAL_SET, FINAL_SET);
					if (ProductNameTokenizer.MODEL_NAME.equals(typePrev) && offset[0] < offsetPrev[1]) {
						wordList = result.get(MODEL_NAME);
						words = wordList.get(wordList.size() - 1);
						words.add(term);
						type = typePrev;
						offset = new int[] { offsetPrev[0], offsetPrev[1] };
					} else if (ProductNameTokenizer.UNIT.equals(typePrev) && offset[0] < offsetPrev[1]) {
						wordList = result.get(UNIT);
						words = wordList.get(wordList.size() - 1);
						words.add(term);
						type = typePrev;
						offset = new int[] { offsetPrev[0], offsetPrev[1] };
					}
				}
			}
			for (String key : result.keySet()) {
				logger.debug("TYPE:{}", key);
				wordList = result.get(key);
				logger.debug("  {}", wordList);
			}
		} catch (Exception ignore) {
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
	}

	private static void setAnalyzedResult(Map<String, List<List<String>>> result, String term, String... types) {
		List<List<String>> wordList = null;
		List<String> words = null;
		for (String type : types) {
			wordList = result.get(type);
			wordList.add(words = new ArrayList<>());
			words.add(term);
		}
	}

	private void reloadDictionary() {
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			ProductNameTokenizerFactory.reloadDictionary();
		}
	}

	private void compileDictionary(RestRequest request, NodeClient client) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		boolean distribute = jobj.optBoolean("distribute", true);
		boolean exportFile = jobj.optBoolean("exportFile", true);
		DictionarySource repo = new DictionarySource(client, index);
		ProductNameTokenizerFactory.reloadDictionary(ProductNameTokenizerFactory.compileDictionary(repo, exportFile));
		if (distribute) {
			distributeDictionary();
		}
	}

	public void distributeDictionary() {

	}

	private void infoDictionary(RestRequest request, NodeClient client, JSONWriter builder) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		ProductNameDictionary productNameDictionary = getDictionary();
		builder
			.key("dictionary").array()
				.object()
				.key(ES_DICT_FIELD_TYPE).value("SYSTEM")
				.key("class").value(TagProbDictionary.class.getSimpleName())
				.key("count").value(productNameDictionary.size())
				.endObject();
		Map<String, SourceDictionary<?>> dictionaryMap = productNameDictionary.getDictionaryMap();
		Set<String> keySet = dictionaryMap.keySet();
		for (String key : keySet) {
			String type = key.toUpperCase();
			SourceDictionary<?> sourceDictionary = dictionaryMap.get(key);
			int[] info = ProductNameTokenizerFactory.getDictionaryInfo(sourceDictionary);
			long indexCount = SearchUtil.count(client, index, QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type));
			builder.object()
				.key(ES_DICT_FIELD_TYPE).value(type)
				.key("class").value(sourceDictionary.getClass().getSimpleName())
				.key("count").value(info[0])
				.key("indexCount").value(indexCount);
			if (info[1] != 0) {
				builder.key("words").value(info[1]);
			}
			builder.endObject();
		}
		builder.endArray();
	}

	private void findDictionary(RestRequest request, NodeClient client, JSONWriter builder) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		String word = jobj.optString("word", "");
		ProductNameDictionary productNameDictionary = getDictionary();
		Set<String> keySet = productNameDictionary.getDictionaryMap().keySet();

		builder
			.key("result").array();

		List<TagProb> tagProbs = productNameDictionary.find(CharVector.valueOf(word));
		for (int inx = 0;tagProbs != null && inx < tagProbs.size(); inx++) {
			TagProb tagProb = tagProbs.get(inx);
			builder.object()
				.key(ES_DICT_FIELD_TYPE).value("SYSTEM")
				.key(ES_DICT_FIELD_KEYWORD).value(word)
				.key("posTag").value(tagProb.posTag())
				.key("prob").value(tagProb.prob())
			.endObject();
		}
		for (String type : keySet) {
			type = type.toUpperCase();

			BoolQueryBuilder query = 
				QueryBuilders.boolQuery()
					.must(QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type))
					.must(QueryBuilders.boolQuery()
						.should(QueryBuilders.matchQuery(ES_DICT_FIELD_KEYWORD, word))
						.should(QueryBuilders.matchQuery(ES_DICT_FIELD_VALUE, word))
					);
			Iterator<Map<String, Object>> result = SearchUtil.search(client, index, query, 0, -1, true);
			while (result.hasNext()) {
				Map<String, Object> data = result.next();
				CharVector keyword = CharVector.valueOf(data.get(ES_DICT_FIELD_KEYWORD));
				CharVector value = CharVector.valueOf(data.get(ES_DICT_FIELD_VALUE));
				CharVector id = CharVector.valueOf(data.get(ES_DICT_FIELD_ID));
				builder.object();
				builder.key(ES_DICT_FIELD_TYPE).value(type);
				if (keyword != null && keyword.length() > 0) {
					builder.key(ES_DICT_FIELD_KEYWORD).value(keyword);
				}
				if (value != null && value.length() > 0) {
					builder.key(ES_DICT_FIELD_VALUE).value(value);
				}
				if (id != null && id.length() > 0) {
					builder.key(ES_DICT_FIELD_ID).value(id);
				}
				builder.endObject();
			}
		}
		builder.endArray();
	}

	private void restoreDictionary(RestRequest request, NodeClient client) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		SearchUtil.deleteAllData(client, index);
		DictionarySource repo = new DictionarySource(client, index);
		ProductNameTokenizerFactory.restoreDictionary(repo, index);
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
				indexingThread = new DanawaBulkTextIndexer(indexName, path, enc, flush, client);
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
			JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
			String index = jobj.optString("index", "");
			String[] fields = jobj.optString("fields", "").split("[,]");
			String text = jobj.optString("text", "");
			int from = jobj.optInt("from", 0);
			int size = jobj.optInt("size", 20);
			boolean trackTotal = jobj.optBoolean("total", false);
			boolean trackAnalysis = jobj.optBoolean("analysis", false);

			logger.debug("ANALYZE TEXT : {}", text);
			stream = getAnalyzer(text, true, true, true);

			JSONObject analysis = null;
			if (trackAnalysis) {
				analysis = new JSONObject();
			}

			QueryBuilder query = DanawaSearchQueryBuilder.buildQuery(stream, fields, analysis);

			logger.debug("Q:{}", query.toString());

			SearchSourceBuilder source = new SearchSourceBuilder();
			source.query(query);

			long total = -1;

			if (trackTotal) {
				// NOTE: 부하가 얼마나 걸릴지 체크해 봐야 할듯.
				long time = System.nanoTime();
				total = SearchUtil.count(client, index, query);
				time = System.nanoTime() - time;
				logger.debug("TOTAL:{} takes {} ns", total, ((int) Math.round(time * 100.0 / 1000000.0)) / 100.0);
			}

			boolean doScroll = from + size > 10000;

			builder.object();
			if (trackAnalysis) {
				builder.key("analysis").value(analysis);
			}

			if (total != -1) {
				builder.key("total").value(total);
			}
			builder.key("result").array();

			Iterator<Map<String, Object>> iter = SearchUtil.search(client, index, query, from, size, doScroll);
			while (iter.hasNext()) {
				Map<String, Object> map = iter.next();
				builder.object();
				for (String key : map.keySet()) {
					builder.key(key).value(map.get(key));
				}
				builder.endObject();

			}
			builder.endArray();
			builder.endObject();
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
	}

	private static ProductNameDictionary getDictionary() {
		if (dictionary == null && contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		}
		return dictionary;
	}

	private static TokenStream getAnalyzer(String str, boolean useForQuery, boolean useSynonym, boolean useStopword) {
		// TODO: 토크나이저/분석기를 동적으로 가져올수 없으므로 자체 캐시를 사용하도록 한다.
		TokenStream tstream = null;
		Reader reader = null;
		Tokenizer tokenizer = null;
		ProductNameDictionary dictionary = getDictionary();
		AnalyzerOption option = null;
		option = new AnalyzerOption();
		option.useForQuery(useForQuery);
		option.useSynonym(useSynonym);
		option.useStopword(useStopword);
		reader = new StringReader(str);
		tokenizer = new ProductNameTokenizer(dictionary, false);
		tokenizer.setReader(reader);
		tstream = new ProductNameAnalysisFilter(tokenizer, dictionary, option);
		return tstream;
	}

	public static class DictionarySource extends DictionaryRepository implements Iterator<CharSequence[]> {
		private NodeClient client;
		private String index;
		private Iterator<Map<String, Object>> iterator;

		public DictionarySource(NodeClient client, String index) {
			this.client = client;
			this.index = index;
		}

		@Override public Iterator<CharSequence[]> getSource(String type) {
			try {
				QueryBuilder query = QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type.toUpperCase());
				logger.trace("QUERY:{}", query);
				iterator = SearchUtil.search(client, index, query, 0, -1, true);
			} catch (Exception e) {
				logger.error("", e);
			}
			return this;
		}

		@Override public void restore(String type, boolean ignoreCase, Set<CharSequence> wordSet) {
			Map<String, Object> source = null;
			BulkRequestBuilder builder = null;
			try {
				builder = client.prepareBulk();
				int inx = 0;
				for (CharSequence data : wordSet) {
					String[] words = String.valueOf(data).split(TAB);
					source = new HashMap<>();
					source.put(ES_DICT_FIELD_TYPE, type.toUpperCase());
					if (words.length == 1) {
						source.put(ES_DICT_FIELD_KEYWORD, words[0]);
					} else if (words.length == 2) {
						if (words[0].length() > 0) {
							source.put(ES_DICT_FIELD_KEYWORD, words[0]);
						}
						source.put(ES_DICT_FIELD_VALUE, words[1]);
					} else {
						source.put(ES_DICT_FIELD_KEYWORD, words[0]);
						source.put(ES_DICT_FIELD_VALUE, words[1]);
						source.put(ES_DICT_FIELD_ID, words[2]);
					}
					builder.add(client.prepareIndex(String.valueOf(index), "_doc").setSource(source));
					if (inx > 0 && inx % 1000 == 0) {
						builder.execute().actionGet();
						builder = client.prepareBulk();
					}
					inx++;
				}
				if (inx > 0) {
					builder.execute().actionGet();
				}
			} catch (Exception e) { 
				logger.error("", e);
			}
		}

		@Override public boolean hasNext() {
			return iterator.hasNext();
		}

		@Override public CharSequence[] next() {
			Map<String, Object> data = iterator.next();
			CharSequence id = CharVector.valueOf(data.get(ES_DICT_FIELD_ID));
			CharSequence keyword = CharVector.valueOf(data.get(ES_DICT_FIELD_KEYWORD));
			CharSequence value = CharVector.valueOf(data.get(ES_DICT_FIELD_VALUE));
			return new CharSequence[] { id, keyword, value };
		}

		@Override public void close() { }
	}
}

// GET /_plugin/PRODUCT/analysis-tools-detail?test=test&analyzerId=standard&forQuery=true&skipFilter=true&queryWords=192.168.1.1:8090/_plugin/PRODUCT/analysis-tools-detail?test=test&analyzerId=standard&forQuery=true&skipFilter=true&queryWords=Sandisk Extream Z80 USB 16gb
// {
//  "query":"Sandisk Extream Z80 USB 16gb",
//  "result":
//  [
//    {"key":"불용어","value":""},
//    {"key":"모델명 규칙","value":"<strong>Z80<\/strong> ( Z , 80 , Z80 ) <br/>"},
//    {"key":"단위명 규칙","value":"<strong>16gb<\/strong> : 16gb<br/> >>> 동의어 : 16g, 16기가<br/>"},
//    {"key":"형태소 분리 결과","value":"Sandisk, Extream, Z, 80, Z80, USB, 16gb, Sandisk Extream Z80 USB 16gb"},
//    {"key":"동의어 확장","value":"<strong>Sandisk<\/strong> : 샌디스크, 산디스크, 센디스크, 샌디스크 코리아, 산디스크 코리아<br/><strong>Z<\/strong> : 지, 제트<br/> >>> 단방향 : 지, 제트<br/><strong>USB<\/strong> : 유에스비, usb용, usb형, 유에스비용, 유에스비형<br/><strong>16gb<\/strong> : 16g, 16기가<br/>"},
//    {"key":"복합명사","value":""},
//    {"key":"최종 결과","value":"Sandisk, 샌디스크, 산디스크, 센디스크, 샌디스크 코리아, 산디스크 코리아, Extream, Z, 지, 제트, 80, Z80, USB, 유에스비, usb용, usb형, 유에스비용, 유에스비형, 16gb, 16g, 16기가, Sandisk Extream Z80 USB 16gb"}
//  ],
//  "success":true
// }
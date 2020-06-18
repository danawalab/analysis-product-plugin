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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.danawa.search.analysis.dict.CompoundDictionary;
import com.danawa.search.analysis.dict.CustomDictionary;
import com.danawa.search.analysis.dict.InvertMapDictionary;
import com.danawa.search.analysis.dict.MapDictionary;
import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SourceDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.dict.TagProbDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.search.analysis.korean.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.product.ProductNameTokenizerFactory.DictionaryRepository;
import com.danawa.search.util.SearchUtil;
import com.danawa.util.CharVector;
import com.danawa.util.ContextStore;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
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
import org.json.JSONArray;
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
	private static final String ACTION_SEARCH = "search";
	private static final String ES_DICTIONARY_INDEX = ".fastcatx_dict";
	private static final String ES_DICT_FIELD_ID = "id";
	private static final String ES_DICT_FIELD_TYPE = "type";
	private static final String ES_DICT_FIELD_KEYWORD = "keyword";
	private static final String ES_DICT_FIELD_VALUE = "value";

	public static final String AND = "AND";
	public static final String OR = "OR";
	public static final String TAB = "\t";

	private IndexingThread indexingThread;

	private ProductNameDictionary dictionary;

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
		if (dictionary == null) {
			if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
				dictionary = contextStore.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY,
						ProductNameDictionary.class);
			}
		}

		if (ACTION_RELOAD_DICT.equals(action)) {
			reloadDictionary();
			builder.object()
				.key("action").value(action)
			.endObject();
		} else if (ACTION_INFO_DICT.equals(action)) {
			builder.object()
				.key("action").value(action);
			infoDictionary(request, client, builder);
			builder.endObject();
		} else if (ACTION_FIND_DICT.equals(action)) {
			builder.object()
				.key("action").value(action);
			findDictionary(request, client, builder);
			builder.endObject();
		} else if (ACTION_RESTORE_DICT.equals(action)) {
			restoreDictionary(request, client, dictionary);
			builder.object()
				.key("action").value(action)
			.endObject();
		} else if (ACTION_COMPILE_DICT.equals(action)) {
			compileDictionary(request, client, dictionary);
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

	public void compileDictionary(RestRequest request, NodeClient client, ProductNameDictionary productNameDictionary) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		boolean exportFile = jobj.optBoolean("exportFile", true);

		DictionarySource repo = new DictionarySource(client, index);
		ProductNameTokenizerFactory.reloadDictionary(
			ProductNameTokenizerFactory.compileDictionary(repo, exportFile));
	}

	public String getTwowaySynonymWord(CharSequence word, Map<CharSequence, CharSequence[]> map) {
		Set<CharSequence> sortedSet = new TreeSet<>();
		CharSequence[] values = map.get(word);
		int pass = 0;
		String s0, s1, s2;
		if (values != null && values.length > 0) {
			s0 = String.valueOf(word);
			CharSequence[] target = map.get(values[0]);
			for (int inx1 = 1; target!=null && inx1 < values.length; inx1++) {
				s1 = String.valueOf(values[inx1]);
				for (int inx2 = 0; inx2 < target.length; inx2++) {
					s2 = String.valueOf(target[inx2]);
					if (s1.equals(s2) || s0.contains(s2)) {
						pass++;
					}
				}
			}
		}
		if (pass == values.length) {
			sortedSet.addAll(Arrays.asList(values));
			sortedSet.add(word);
			StringBuilder sb = new StringBuilder();
			for (CharSequence value : sortedSet) {
				if (sb.length() > 0) { sb.append(","); }
				sb.append(String.valueOf(value).trim());
			}
			return sb.toString();
		} else {
			return null;
		}
	}

	public void infoDictionary(RestRequest request, NodeClient client, JSONWriter builder) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		builder
			.key("dictionary").array()
				.object()
				.key(ES_DICT_FIELD_TYPE).value("SYSTEM")
				.key("class").value(TagProbDictionary.class.getSimpleName())
				.key("count").value(this.dictionary.size())
				.endObject();
		Map<String, SourceDictionary<?>> dictionaryMap = this.dictionary.getDictionaryMap();
		Set<String> keySet = dictionaryMap.keySet();
		for (String key : keySet) {
			String type = key.toUpperCase();
			SourceDictionary<?> sourceDictionary = dictionaryMap.get(key);
			QueryBuilder query = QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, type);
			long indexCount = SearchUtil.count(client, index, query);
			builder.object()
				.key(ES_DICT_FIELD_TYPE).value(type);
			if (sourceDictionary.getClass().isAssignableFrom(SetDictionary.class)) {
				SetDictionary dictionary = (SetDictionary) sourceDictionary;
				builder .key("class").value(SetDictionary.class.getSimpleName())
						.key("count").value(dictionary.set().size())
						.key("indexCount").value(indexCount);
			} else if (sourceDictionary.getClass().isAssignableFrom(MapDictionary.class)) {
				MapDictionary dictionary = (MapDictionary) sourceDictionary;
				builder .key("class").value(MapDictionary.class.getSimpleName())
						.key("count").value(dictionary.map().keySet().size())
						.key("indexCount").value(indexCount);
			} else if (sourceDictionary.getClass().isAssignableFrom(SynonymDictionary.class)) {
				SynonymDictionary dictionary = (SynonymDictionary) sourceDictionary;
				builder .key("class").value(SynonymDictionary.class.getSimpleName())
						.key("count").value(dictionary.map().keySet().size())
						.key("indexCount").value(indexCount)
						.key("words").value(dictionary.getWordSet().size());
			} else if (sourceDictionary.getClass().isAssignableFrom(SpaceDictionary.class)) {
				SpaceDictionary dictionary = (SpaceDictionary) sourceDictionary;
				builder .key("class").value(SpaceDictionary.class.getSimpleName())
						.key("count").value(dictionary.map().keySet().size())
						.key("indexCount").value(indexCount)
						.key("words").value(dictionary.getWordSet().size());
			} else if (sourceDictionary.getClass().isAssignableFrom(CustomDictionary.class)) {
				CustomDictionary dictionary = (CustomDictionary) sourceDictionary;
				builder .key("class").value(CustomDictionary.class.getSimpleName())
						.key("count").value(dictionary.map().keySet().size())
						.key("indexCount").value(indexCount)
						.key("words").value(dictionary.getWordSet().size());
			} else if (sourceDictionary.getClass().isAssignableFrom(InvertMapDictionary.class)) {
				InvertMapDictionary dictionary = (InvertMapDictionary) sourceDictionary;
				builder .key("class").value(InvertMapDictionary.class.getSimpleName())
						.key("count").value(dictionary.map().keySet().size())
						.key("indexCount").value(indexCount);
			} else if (sourceDictionary.getClass().isAssignableFrom(CompoundDictionary.class)) {
				CompoundDictionary dictionary = (CompoundDictionary) sourceDictionary;
				builder .key("class").value(CompoundDictionary.class.getSimpleName())
						.key("count").value(dictionary.map().keySet().size())
						.key("indexCount").value(indexCount)
						.key("words").value(dictionary.getWordSet().size());
			}
			builder.endObject();
		}
		builder.endArray();
	}

	public void findDictionary(RestRequest request, NodeClient client, JSONWriter builder) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		String word = jobj.optString("word", "");

		builder
			.key("dictionary").array();

		List<TagProb> tagProbs = dictionary.find(CharVector.valueOf(word));
		for (int inx = 0;tagProbs != null && inx < tagProbs.size(); inx++) {
			TagProb tagProb = tagProbs.get(inx);
			builder.object()
				.key(ES_DICT_FIELD_TYPE).value("SYSTEM")
				.key(ES_DICT_FIELD_KEYWORD).value(word)
				.key("posTag").value(tagProb.posTag())
				.key("prob").value(tagProb.prob())
			.endObject();
		}

		BoolQueryBuilder query = QueryBuilders.boolQuery();
		query.should(QueryBuilders.matchQuery(ES_DICT_FIELD_KEYWORD, word));
		query.should(QueryBuilders.matchQuery(ES_DICT_FIELD_VALUE, word));
		Iterator<Map<String, Object>> result = SearchUtil.search(client, index, query, 0, -1, true);
		while (result.hasNext()) {
			Map<String, Object> data = result.next();
			builder.object()
				.key(ES_DICT_FIELD_TYPE).value(data.get(ES_DICT_FIELD_TYPE))
				.key(ES_DICT_FIELD_KEYWORD).value(data.get(ES_DICT_FIELD_KEYWORD))
				.key(ES_DICT_FIELD_VALUE).value(data.get(ES_DICT_FIELD_VALUE))
				.key(ES_DICT_FIELD_ID).value(data.get(ES_DICT_FIELD_ID))
			.endObject();
		}
		builder.endArray();
	}

	public void restoreDictionary(RestRequest request, NodeClient client, ProductNameDictionary productNameDictionary) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);

		Map<String, SourceDictionary<?>> dictionaryMap = productNameDictionary.getDictionaryMap();
		Set<String> keySet = dictionaryMap.keySet();
		SearchUtil.deleteAllData(client, index);
		for (String key : keySet) {
			SourceDictionary<?> sourceDictionary = dictionaryMap.get(key);
			logger.debug("KEY:{} / {}", key, sourceDictionary);
			if (sourceDictionary.getClass().isAssignableFrom(SetDictionary.class)) {
				SetDictionary dictionary = (SetDictionary) sourceDictionary;
				Set<CharSequence> words = dictionary.set();
				storeDictionary(client, index, key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(MapDictionary.class)) {
				MapDictionary dictionary = (MapDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (CharSequence value : map.get(word)) {
						if (sb.length() > 0) { sb.append(","); }
						sb.append(String.valueOf(value).trim());
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(sb));
				}
				storeDictionary(client, index, key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(SynonymDictionary.class)) {
				SynonymDictionary dictionary = (SynonymDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					String values = getTwowaySynonymWord(word, map);
					if (values == null) {
						StringBuilder sb = new StringBuilder();
						for (CharSequence value : map.get(word)) {
							if (sb.length() > 0) { sb.append(","); }
							sb.append(String.valueOf(value).trim());
						}
						words.add(String.valueOf(word) + TAB + String.valueOf(sb));
					} else {
						words.add(TAB + values);
					}
				}
				storeDictionary(client, index, key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(SpaceDictionary.class)) {
				SpaceDictionary dictionary = (SpaceDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (CharSequence value : map.get(word)) {
						if (sb.length() > 0) { sb.append(" "); }
						sb.append(String.valueOf(value).trim());
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(sb));
				}
				storeDictionary(client, index, key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(CustomDictionary.class)) {
				CustomDictionary dictionary = (CustomDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, Object[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (Object value : map.get(word)) {
						if (value != null) {
							if (sb.length() > 0) { sb.append(","); }
							sb.append(String.valueOf(value).trim());
						}
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(word) + TAB + String.valueOf(sb));
				}
				storeDictionary(client, index, key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(InvertMapDictionary.class)) {
				InvertMapDictionary dictionary = (InvertMapDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					String values = getTwowaySynonymWord(word, map);
					if (values == null) {
						StringBuilder sb = new StringBuilder();
						for (CharSequence value : map.get(word)) {
							if (sb.length() > 0) { sb.append(","); }
							sb.append(String.valueOf(value).trim());
						}
						words.add(String.valueOf(word) + TAB + String.valueOf(sb));
					} else {
						words.add(TAB + values);
					}
				}
				storeDictionary(client, index, key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(CompoundDictionary.class)) {
				CompoundDictionary dictionary = (CompoundDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					String values = getTwowaySynonymWord(word, map);
					if (values == null) {
						StringBuilder sb = new StringBuilder();
						for (CharSequence value : map.get(word)) {
							if (sb.length() > 0) { sb.append(","); }
							sb.append(String.valueOf(value).trim());
						}
						words.add(String.valueOf(word) + TAB + String.valueOf(sb));
					} else {
						words.add(TAB + values);
					}
				}
				storeDictionary(client, index, key, dictionary.ignoreCase(), words);
			}
		}
		logger.debug("dictionary restore finished !");
	}

	public void storeDictionary(NodeClient client, String index, String type, boolean ignoreCase, Set<CharSequence> wordSet) {
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
			boolean trackAnalysis = jobj.optBoolean("analysis", false);

			logger.debug("ANALYZE TEXT : {}", text);
			stream = getAnalyzer(text);

			JSONObject analysis = null;
			if (trackAnalysis) {
				analysis = new JSONObject();
			}

			QueryBuilder query = buildQuery(stream, fields, analysis);

			logger.debug("Q:{}", query.toString());

			SearchSourceBuilder source = new SearchSourceBuilder();
			source.query(query);

			long total = -1;

			if (trackTotal) {
				// NOTE: 부하가 얼마나 걸릴지 체크해 봐야 할듯.
				total = SearchUtil.count(client, index, query);
				logger.debug("TOTAL:{}", total);
			}

			boolean doScroll = (total != -1 && total > 10000) || from + size > 10000;

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
		}
	}

	public static QueryBuilder buildQuery(TokenStream stream, String[] fields, JSONObject analysis) {
		QueryBuilder ret = null;

		boolean doAnalysis = analysis != null;
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);

		try {
			stream.reset();
			BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
			ret = mainQuery;
			JSONObject mainAnalysis = null;
			if (doAnalysis) {
				mainAnalysis = new JSONObject();
				mainAnalysis.put(AND, new JSONArray());
				analysis.put(AND, mainAnalysis);
			}
			while (stream.incrementToken()) {
				String term = String.valueOf(termAttr);
				String type = typeAttr.type();
				logger.debug("TOKEN:{} / {}", term, typeAttr.type());

				JSONArray termAnalysis = null;
				if (doAnalysis) {
					termAnalysis = new JSONArray();
					termAnalysis.put(term);
				}
				QueryBuilder termQuery = QueryBuilders.multiMatchQuery(term, fields);

				List<CharSequence> synonyms = null;
				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					JSONObject subAnalysis = null;
					if (doAnalysis) {
						subAnalysis = new JSONObject();
						subAnalysis.put(OR, new JSONArray());
					}
					BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
					subQuery.should().add(termQuery);
					for (int sinx = 0; sinx < synonyms.size(); sinx++) {
						String synonym = String.valueOf(synonyms.get(sinx));
						if (doAnalysis) {
							subAnalysis.getJSONArray(OR).put(synonym);
						}
						if (synonym.indexOf(" ") == -1) {
							subQuery.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
						} else {
							BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
							for (String field : fields) {
								inQuery.should().add(QueryBuilders.matchPhraseQuery(field, synonym).slop(3));
							}
							subQuery.should().add(inQuery);
						}
						logger.debug(" |_synonym : {}", synonym);
					}
					if (doAnalysis) {
						termAnalysis.put(subAnalysis);
					}
					termQuery = subQuery;
				}
				if (extAttr != null && extAttr.size() > 0) {
					JSONObject subAnalysis = null;
					if (doAnalysis) {
						subAnalysis = new JSONObject();
						subAnalysis.put(AND, new JSONArray());
					}
					BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
					Iterator<String> termIter = extAttr.iterator();
					for (; termIter.hasNext();) {
						String exTerm = termIter.next();
						String exType = typeAttr.type();
						synonyms = synAttr.getSynonyms();
						if (synonyms == null || synonyms.size() == 0) {
							subQuery.must().add(QueryBuilders.multiMatchQuery(exTerm, fields));
							if (doAnalysis) {
								subAnalysis.getJSONArray(AND).put(exTerm);
							}
						} else {
							JSONObject inAnalysis = null;
							if (doAnalysis) {
								inAnalysis = new JSONObject();
								inAnalysis.put(OR, new JSONArray());
							}
							BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
							inQuery.should().add(QueryBuilders.multiMatchQuery(exTerm, fields));
							if (doAnalysis) {
								inAnalysis.getJSONArray(OR).put(exTerm);
							}
							for (int sinx = 0; sinx < synonyms.size(); sinx++) {
								String synonym = String.valueOf(synonyms.get(sinx));
								if (doAnalysis) {
									inAnalysis.getJSONArray(OR).put(synonym);
								}
								if (synonym.indexOf(" ") == -1) {
									inQuery.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
								} else {
									BoolQueryBuilder in2Query = QueryBuilders.boolQuery();
									for (String field : fields) {
										in2Query.should().add(QueryBuilders.matchPhraseQuery(field, synonym).slop(3));
									}
									inQuery.should().add(inQuery);
								}
							}
							if (doAnalysis) {
								subAnalysis.getJSONArray(AND).put(inAnalysis);
							}
							subQuery.must().add(inQuery);
						}
						logger.debug("a-term:{} / type:{} / synonoym:{}", exTerm, exType, synonyms);
					}
					BoolQueryBuilder parent = QueryBuilders.boolQuery();
					parent.should().add(termQuery);
					parent.should().add(subQuery);
					termQuery = parent;
					if (doAnalysis) {
						termAnalysis.put(subAnalysis);
					}
				}
				if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					{
						BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
						for (String field : fields) {
							inQuery.should().add(QueryBuilders.matchPhraseQuery(field, term).slop(10));
						}
						termQuery = inQuery;
					}
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					query.should(termQuery);
					query.should(mainQuery);
					ret = query;
					if (doAnalysis) {
						JSONArray jarr = new JSONArray();
						jarr.put(termAnalysis);
						jarr.put(mainAnalysis);
						analysis.remove(AND);
						analysis.put(OR, jarr);
					}
				} else {
					mainQuery.must().add(termQuery);
					if (doAnalysis) {
						mainAnalysis.getJSONArray(AND).put(termAnalysis);
					}
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}

		return ret;
	}

	public static TokenStream getAnalyzer(String str) {
		/**
		 * FIXME: ES 에서 동적으로 분석기를 가져오는 방법을 생각해 본다.
		 **/
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

	static class DictionarySource extends DictionaryRepository implements Iterator<CharSequence[]> {
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
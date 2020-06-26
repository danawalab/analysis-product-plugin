package com.danawa.search.analysis.product;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SourceDictionary;
import com.danawa.search.analysis.dict.TagProbDictionary;
import com.danawa.search.analysis.dict.ProductNameDictionary.DictionaryRepository;
import com.danawa.search.analysis.korean.PosTagProbEntry.TagProb;
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
import org.elasticsearch.action.admin.cluster.node.info.NodeInfo;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoRequest;
import org.elasticsearch.action.admin.cluster.node.info.NodesInfoResponse;
import org.elasticsearch.action.bulk.BulkRequestBuilder;
import org.elasticsearch.client.node.NodeClient;
import org.elasticsearch.common.inject.Inject;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.plugins.PluginInfo;
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
	private static final String ACTION_TEST = "test";
	private static final String ACTION_INFO_DICT = "info-dict";
	private static final String ACTION_FIND_DICT = "find-dict";
	private static final String ACTION_RELOAD_DICT = "reload-dict";
	private static final String ACTION_RESTORE_DICT = "restore-dict";
	private static final String ACTION_COMPILE_DICT = "compile-dict";
	private static final String ACTION_FULL_INDEX = "full-index";
	private static final String ACTION_ANALYZE_TEXT = "analyze-text";
	private static final String ACTION_SEARCH = "search";

	private static final String ES_DICTIONARY_INDEX = ".fastcatx_dict";
	private static final String ES_DICT_FIELD_ID = "id";
	private static final String ES_DICT_FIELD_TYPE = "type";
	private static final String ES_DICT_FIELD_KEYWORD = "keyword";
	private static final String ES_DICT_FIELD_VALUE = "value";

	private static final String TAG_BR = "<br/>";
	private static final String TAG_STRONG = "<strong>${TEXT}</strong>";
	private static final String REGEX_TAG_TEXT = "[$][{]TEXT[}]";
	private static final String COMMA = ",";

	private static final String TAB = "\t";

	private static final String ANALYZE_SET_FULL_STRING = "00_FULL_STRING_SET";
	private static final String ANALYZE_SET_RESTRICTED = "01_RESTRICTED_SET";
	private static final String ANALYZE_SET_MODEL_NAME = "02_MODEL_NAME_SET";
	private static final String ANALYZE_SET_UNIT = "03_UNIT_SET";
	private static final String ANALYZE_SET_NORMAL = "04_NORMAL_SET";
	private static final String ANALYZE_SET_SYNONYM = "05_SYNONYM_SET";
	private static final String ANALYZE_SET_COMPOUND = "06_COMPOUND_SET";
	private static final String ANALYZE_SET_FINAL = "07_FINAL_SET";

	private static final Map<String, String> ANALYSIS_RESULT_LABELS;
	static {
		Map<String, String> map = new HashMap<>();
		map.put(ANALYZE_SET_FULL_STRING, "전체질의어");
		map.put(ANALYZE_SET_RESTRICTED, "불용어");
		map.put(ANALYZE_SET_MODEL_NAME, "모델명 규칙");
		map.put(ANALYZE_SET_UNIT, "단위명 규칙");
		map.put(ANALYZE_SET_NORMAL, "형태소 분리 결과");
		map.put(ANALYZE_SET_SYNONYM, "동의어 확장");
		map.put(ANALYZE_SET_COMPOUND, "복합명사");
		map.put(ANALYZE_SET_FINAL, "최종 결과");
		ANALYSIS_RESULT_LABELS = Collections.unmodifiableMap(map);
	}
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

		if (ACTION_TEST.equals(action)) {
			testAction(request, client, builder);
			builder.object().key("action").value(action).endObject();
		} else if (ACTION_RELOAD_DICT.equals(action)) {
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
			int count = bulkIndex(request, client);
			builder.object().key("action").value(action);
			if (count > 0) {
				builder.key("working").value("true").key("count").value(count);
			} else {
				builder.key("working").value("false");
			}
			builder.endObject();
		} else if (ACTION_ANALYZE_TEXT.equals(action)) {
			analyzeTextAction(request, client, builder);
		} else if (ACTION_SEARCH.equals(action)) {
			search(request, client, builder);
		}
		return channel -> {
			channel.sendResponse(new BytesRestResponse(RestStatus.OK, CONTENT_TYPE_JSON, buffer.toString()));
		};
	}
	public void testAction(RestRequest request, NodeClient client, JSONWriter writer) {
		logger.debug("TEST-ACTION");
	}

	private void distribute(RestRequest request, NodeClient client, String action, JSONObject body, boolean selfDist) {
		String localNodeId = client.getLocalNodeId();
		NodesInfoRequest infoRequest = new NodesInfoRequest();
		infoRequest.clear().jvm(true).os(true).process(true).http(true).plugins(true).indices(true);
		try {
			NodesInfoResponse response = client.admin().cluster().nodesInfo(infoRequest).get();
			List<NodeInfo> nodes = response.getNodes();
			for (NodeInfo node : nodes) {
				// 자기자신이 아닌경우에만 전파
				if (!selfDist && localNodeId.equals(node.getNode().getId())) {
					continue;
				}
				boolean hasPlugin = false;
				TransportAddress address = node.getHttp().getAddress().publishAddress();
				// 상품명분석기 플러그인을 가진 노드에만 전파
				List<PluginInfo> plugins = node.getPlugins().getPluginInfos();
				for (PluginInfo info : plugins) {
					if (hasPlugin = info.getClassname().equals(AnalysisProductNamePlugin.class.getName())) {
						break;
					}
				}
				if (hasPlugin) {
					logger.debug("NODE: {}:{}", address.getAddress(), address.getPort());
					doRestRequest(address.getAddress(), address.getPort(), action, body);
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
	}

	private String doRestRequest(final String address, final int port, final String action, final JSONObject body) {
		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<String>) () -> {
			StringBuilder sb = new StringBuilder();
			HttpURLConnection con = null;
			OutputStream ostream = null;
			BufferedReader reader = null;
			try {
				String url = "http://" + address + ":" + port + BASE_URI + "/" + action;
				logger.debug("SEND REQUEST {}", url);
				con = (HttpURLConnection) new URL(url).openConnection();
				con.setRequestMethod("POST");
				con.setRequestProperty("Content-Type", "application/json");
				con.setDoOutput(true);
				ostream = con.getOutputStream();
				ostream.write(String.valueOf(body).getBytes());
				ostream.flush();
				int responseCode = con.getResponseCode();
				if (responseCode == HttpURLConnection.HTTP_CREATED) {
					logger.trace("RESPONSE:{}", responseCode);
					reader = new BufferedReader(new InputStreamReader(con.getInputStream()));
					for (String rl; (rl = reader.readLine()) != null;) {
						sb.append(rl).append("\r\n");
					}
					logger.trace("RESPONSE:{}", sb);
				}
			} catch (Exception e) { 
				logger.error("", e);
			} finally {
				try { ostream.close(); } catch (Exception ignore) { }
				try { reader.close(); } catch (Exception ignore) { }
			}
			return String.valueOf(sb);
		});
	}

	private void analyzeTextAction(RestRequest request, NodeClient client, JSONWriter writer) {
		String index = request.param("index", "");
		String text = request.param("text", "");
		boolean detail = request.paramAsBoolean("detail", true);
		boolean useForQuery = request.paramAsBoolean("useForQuery", true);
		boolean useSynonym = request.paramAsBoolean("useSynonym", true);
		boolean useStopword = request.paramAsBoolean("useStopword", true);

		if (text == null || text.length() == 0) {
			JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
			index = jobj.optString("index", "");
			text = jobj.optString("text", "");
			detail = jobj.optBoolean("detail", true);
			useForQuery = jobj.optBoolean("useForQuery", true);
			useSynonym = jobj.optBoolean("useSynonym", true);
			useStopword = jobj.optBoolean("useStopword", true);
		}

		TokenStream stream = null;
		try {
			stream = getAnalyzer(text, useForQuery, useSynonym, useStopword);
			analyzeTextDetail(client, text, stream, detail, index, writer);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}
	}

	public static boolean isOneWaySynonym(NodeClient client, String index, String word) {
		boolean ret = false;
		if (client != null && index != null && !"".equals(index)) {
			BoolQueryBuilder query = QueryBuilders.boolQuery()
				.must(QueryBuilders.matchQuery(ES_DICT_FIELD_TYPE, ProductNameTokenizer.DICT_SYNONYM.toUpperCase()))
				.must(QueryBuilders.matchQuery(ES_DICT_FIELD_KEYWORD, word)
				);
			logger.trace("Q:{}", query);
			long count = 0;
			if ((count = SearchUtil.count(client, index, query)) > 0) {
				ret = true;
			}
			logger.trace("SYNONYM:{}-{} = {}", index, word, count);
		}
		return ret;
	}

	public static void analyzeTextDetail(NodeClient client, String text, TokenStream stream, boolean detail, String index, JSONWriter writer) {
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		OffsetAttribute offAttr = stream.addAttribute(OffsetAttribute.class);
		Map<String, List<List<String>>> result = new HashMap<>();
		Map<String, Boolean> synonymWayMap = new HashMap<>();
		List<List<String>> wordList = null;
		List<String> words = null;
		List<String> words2 = null;
		List<CharSequence> synonyms = synAttr.getSynonyms();
		for (String key : ANALYSIS_RESULT_LABELS.keySet()) {
			result.put(key, new ArrayList<>());
		}

		String term = null;
		String type = null;
		String setName = null;
		String setNamePrev = null;
		int[] offset = { 0, 0 };
		int[] offsetPrev = { 0, 0 };
		boolean oneWaySynonym = false;
		try {
			stream.reset();
			while (stream.incrementToken()) {
				setNamePrev = setName;
				offsetPrev = new int[] { offset[0], offset[1] };
				term = String.valueOf(termAttr);
				type = typeAttr.type();
				offset = new int[] { offAttr.startOffset(), offAttr.endOffset() };
				logger.trace("TERM:{} / {}", term, type);
				if (!ANALYZE_SET_FULL_STRING.equals(setNamePrev) && offset[0] < offsetPrev[1]) {
					// 모델명 / 단위명 등 뒤에 나온 부속단어 (색인시)
					wordList = result.get(setNamePrev);
					if (wordList != null) {
						words = wordList.get(wordList.size() - 1);
						words.add(term);
						setName = setNamePrev;
						offset = new int[] { offsetPrev[0], offsetPrev[1] };
						setAnalyzedResult(result, term, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
					}
				} else if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					// 전체 단어
					setName = ANALYZE_SET_FULL_STRING;
					setAnalyzedResult(result, term, ANALYZE_SET_FULL_STRING, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.MODEL_NAME.equals(type)) {
					// 모델명의 경우 색인시와 질의시 추출방법이 서로 다르다.
					setName = ANALYZE_SET_MODEL_NAME;
					setAnalyzedResult(result, term, ANALYZE_SET_MODEL_NAME, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.UNIT.equals(type)) {
					// 단위명
					setName = ANALYZE_SET_UNIT;
					setAnalyzedResult(result, term, ANALYZE_SET_UNIT, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				} else if (ProductNameTokenizer.COMPOUND.equals(type)) {
					setName = ANALYZE_SET_COMPOUND;
					setAnalyzedResult(result, term, ANALYZE_SET_COMPOUND, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				} else {
					// 일반단어
					setName = ANALYZE_SET_NORMAL;
					setAnalyzedResult(result, term, ANALYZE_SET_NORMAL, ANALYZE_SET_FINAL);
				}

				if (!ANALYZE_SET_FULL_STRING.equals(setName)) {
					if ((synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
						wordList = result.get(ANALYZE_SET_SYNONYM);
						wordList.add(words = new ArrayList<>());
						words.add(term);
						wordList = result.get(setName);
						words2 = wordList.get(wordList.size() - 1);
						oneWaySynonym = isOneWaySynonym(client, index, term);
						synonymWayMap.put(term, oneWaySynonym);
						logger.trace("SYNONYM [{}] {} / {} / {}", setName, term, oneWaySynonym, synonyms);
						for (CharSequence synonym : synonyms) {
							String s = String.valueOf(synonym);
							words.add(s);
							if (!ANALYZE_SET_NORMAL.equals(setName)) {
								words2.add(s);
							}
							setAnalyzedResult(result, s, ANALYZE_SET_FINAL);
						}
					}

					Iterator<String> iter = extAttr.iterator();
					if (iter != null && iter.hasNext()) {
						wordList = result.get(setName);
						words = wordList.get(wordList.size() - 1);
						while (iter.hasNext()) {
							String s = iter.next();
							logger.trace("EXT [{}] {} / {} / {} / {}", setName, term, s, words, wordList);
							words.add(s);
							setAnalyzedResult(result, s, ANALYZE_SET_FINAL);
							synonyms = synAttr.getSynonyms();
							if (synonyms != null && synonyms.size() > 0) {
								oneWaySynonym = isOneWaySynonym(client, index, s);
								synonymWayMap.put(s, oneWaySynonym);
								logger.trace("EXT-SYN [{}] {} / {} / {} / {} / {}", setName, term, s, oneWaySynonym, synonyms, wordList);
								setAnalyzedResult(result, s, ANALYZE_SET_SYNONYM);
								wordList = result.get(ANALYZE_SET_SYNONYM);
								List<String> list = wordList.get(wordList.size() - 1);
								for (CharSequence synonym : synonyms) {
									s = String.valueOf(synonym);
									list.add(s);
									setAnalyzedResult(result, s, ANALYZE_SET_FINAL);
								}
							}
						}
					}
				}
			}
			logger.debug("SYNONYM-WAY : {}", synonymWayMap);
			writer.object()
				.key("query").value(text)
				.key("resutl").array();
			if (detail) {
				for (String key : new TreeSet<String>(result.keySet())) {
					String label = ANALYSIS_RESULT_LABELS.get(key);
					wordList = result.get(key);
					logger.trace("TYPE:{} / {}", label, wordList);
					StringBuilder data = new StringBuilder();

					if (ANALYZE_SET_FULL_STRING.equals(key)) {
						if (wordList != null && wordList.size() > 0) {
							words = wordList.get(0);
							if (words != null && words.size() > 0) {
								data.append(words.get(0));
							}
						}
					} else if (ANALYZE_SET_RESTRICTED.equals(key)) {
						for (List<String> item : wordList) {
							if (data.length() > 0) { data.append(COMMA).append(" "); }
							if (item.size() > 0) {
								data.append(item.get(0));
							}
						}
					} else if (ANALYZE_SET_MODEL_NAME.equals(key)) {
						for (List<String> item : wordList) {
							if (data.length() > 0) { data.append(TAG_BR); }
							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									data.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, w))
										.append(" ( ").append(w);
								} else {
									if (inx > 0) { data.append(", "); }
									data.append(w);
								}
							}
							data.append(" ) ");
						}
					} else if (ANALYZE_SET_UNIT.equals(key)) {
						for (List<String> item : wordList) {
							if (data.length() > 0) { data.append(TAG_BR); }
							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									data.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, w)).append(" : ");
								} else {
									if (inx > 1) { data.append(", "); }
									data.append(w);
								}
							}
							if (ANALYZE_SET_MODEL_NAME.equals(key)) {
								data.append(" ) ");
							}
						}
					} else if (ANALYZE_SET_NORMAL.equals(key)) {
						for (List<String> item : wordList) {
							if (data.length() > 0) { data.append(COMMA).append(" "); }
							if (item.size() > 0) {
								data.append(item.get(0));
							}
						}
					} else if (ANALYZE_SET_SYNONYM.equals(key)) {
						for (List<String> item : wordList) {
							if (data.length() > 0) { data.append(TAG_BR); }
							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									data.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, w)).append(" : ");
								} else {
									if (inx > 1) { data.append(", "); }
									data.append(w);
								}
							}
						}
					} else if (ANALYZE_SET_COMPOUND.equals(key)) {
						for (List<String> item : wordList) {
							if (data.length() > 0) { data.append(TAG_BR); }
							for (int inx = 0; inx < item.size(); inx++) {
								String w = item.get(inx);
								if (inx == 0) {
									data.append(TAG_STRONG.replaceAll(REGEX_TAG_TEXT, w)).append(" : ");
								} else {
									if (inx > 1) { data.append(", "); }
									data.append(w);
								}
							}
						}
					} else if (ANALYZE_SET_FINAL.equals(key)) {
						for (List<String> item : wordList) {
							if (data.length() > 0) { data.append(COMMA).append(" "); }
							if (item.size() > 0) {
								data.append(item.get(0));
							}
						}
					}
					writer.object()
						.key("key").value(label)
						.key("value").value(data);
					writer.endObject();
				}
			} else {
				wordList = result.get(ANALYZE_SET_FINAL);
				logger.trace("RESULT:{}", wordList);
				for (List<String> item : wordList) {
					if (item.size() > 0) {
						writer.value(item.get(0));
					}
				}
			}
			writer
				.endArray()
				.key("success").value(true)
				.endObject();
		} catch (Exception e) {
			logger.error("", e);
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
		if (contextStore.containsKey(ProductNameDictionary.PRODUCT_NAME_DICTIONARY)) {
			ProductNameDictionary.reloadDictionary();
		}
	}

	private void compileDictionary(RestRequest request, NodeClient client) {
		JSONObject jobj = new JSONObject(new JSONTokener(request.content().utf8ToString()));
		String index = jobj.optString("index", ES_DICTIONARY_INDEX);
		boolean distribute = jobj.optBoolean("distribute", true);
		boolean exportFile = jobj.optBoolean("exportFile", true);
		DictionarySource repo = new DictionarySource(client, index);
		ProductNameDictionary.reloadDictionary(ProductNameDictionary.compileDictionary(repo, exportFile));
		if (distribute) {
			jobj.put("distribute", false);
			distribute(request, client, ACTION_COMPILE_DICT, jobj, false);
		}
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
			int[] info = ProductNameDictionary.getDictionaryInfo(sourceDictionary);
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

			BoolQueryBuilder query = QueryBuilders.boolQuery()
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
		ProductNameDictionary.restoreDictionary(repo, index);
	}

	private int bulkIndex(final RestRequest request, final NodeClient client) {
		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<Integer>) () -> {
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
		});
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

			logger.trace("ANALYZE TEXT : {}", text);
			stream = getAnalyzer(text, true, true, true);

			JSONObject analysis = null;
			if (trackAnalysis) {
				analysis = new JSONObject();
			}

			QueryBuilder query = DanawaSearchQueryBuilder.buildQuery(stream, fields, analysis);

			logger.trace("Q:{}", query.toString());

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
		if (dictionary == null && contextStore.containsKey(ProductNameDictionary.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
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
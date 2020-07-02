package com.danawa.search.analysis.product;

import static org.junit.Assert.*;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Set;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.index.DanawaSearchQueryBuilder;
import com.danawa.util.ContextStore;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.queryparser.xml.builders.BooleanQueryBuilder;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.junit.Before;
import org.junit.Test;

public class ProductNameAnalysisActionTest {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisActionTest.class, "");
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);

	@Before
	public void init() {
		TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"), ProductNameTokenizer.class, ProductNameParsingRule.class,
				ProductNameAnalysisFilter.class);
	}

	@Test
	public void queryBuildTest() {
		if (TestUtil.launchForBuild()) {
			return;
		}
		String text = "";
		text = "테스트상품 ab12cd-12345";
		text = "집업WAS1836ER27";
		text = "10.5cm";
		text = "LGNOTEBOOK 판매";

		ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		// ProductNameDictionary dictionary = TestUtil.loadDictionary();
		TokenStream stream = null;
		JSONObject analysis = new JSONObject();

		String[] fields = new String[] { "PRODUCTNAME" };

		stream = getAnalyzer(dictionary, text, true, true, true);
		QueryBuilder query = DanawaSearchQueryBuilder.buildQuery(stream, fields, analysis);
		logger.debug("Q:{}", query.toString());
		logger.debug("ANALYSIS:{}", analysis);
		assertTrue(true);
	}

	@Test public void queryFromStringTest() throws Exception {
		if (TestUtil.launchForBuild()) { return; }
		logger.debug("PARSING..");
		String source = "" +
			"{ " +
			"  \"query\": { " +
			"    \"function_score\": { " +
			"      \"query\": { " +
			"        \"bool\": { " +
			"          \"must\": [ " +
			"            { " +
			"              \"multi_match\": { " +
			"                \"query\": \"바라쿠다\", " +
			"                \"fields\": [ " +
			"                  \"TOTALINDEX\", " +
			"                  \"MODELWEIGHT^300000\", " +
			"                  \"MAKERKEYWORD^200000\", " +
			"                  \"BRANDKEYWORD^200000\", " +
			"                  \"CATEGORYWEIGHT^100000\" " +
			"                ], " +
			"                \"operator\": \"OR\", " +
			"                \"tie_breaker\": 0 " +
			"              } " +
			"            } " +
			"          ], " +
			"          \"filter\": [ " +
			"            { " +
			"              \"term\": { " +
			"                \"DISPYN\": \"Y\" " +
			"              } " +
			"            }, " +
			"            { " +
			"              \"term\": { " +
			"                \"PRICECOMPARISONSTOPYN\": \"N\" " +
			"              } " +
			"            }, " +
			"            { " +
			"              \"term\": { " +
			"                \"CATEGORYDISPYN\": \"Y\" " +
			"              } " +
			"            } " +
			"          ] " +
			"        } " +
			"      }, " +
			"      \"functions\": [ " +
			"        { " +
			"          \"filter\": { " +
			"            \"match\": { " +
			"              \"SHOPPRODUCTCODE\": \"6562693705\" " +
			"            } " +
			"          }, " +
			"          \"weight\": 12 " +
			"        }, " +
			"        { " +
			"          \"filter\": { " +
			"            \"match\": { " +
			"              \"SHOPPRODUCTCODE\": \"P1207582350\" " +
			"            } " +
			"          }, " +
			"          \"weight\": 40 " +
			"        } " +
			"      ], " +
			"      \"boost_mode\": \"sum\" " +
			"    } " +
			"  }, " +
			"  \"sort\": [ " +
			"    { " +
			"      \"_score\": { " +
			"        \"order\": \"desc\" " +
			"      }, " +
			"      \"REGISTERDATE\": { " +
			"        \"order\": \"desc\" " +
			"      } " +
			"    } " +
			"  ] " +
			"} ";

		JSONObject json = new JSONObject(source);
		for (String key : json.keySet()) {
			logger.debug("KEY:{} / {}", key, json.opt(key).getClass());
		}
		source = json.optString("query");

		long time = System.nanoTime();
		// QueryBuilder query = DanawaSearchQueryBuilder.parseQuery(source);
		QueryBuilder query = parseQueryByJson(json);
		time = System.nanoTime() - time;
		logger.trace("Q:{}", query);
		logger.debug("PARSING TAKES {} ms", ((int) Math.round(time * 100.0 / 1000000.0)) / 100.0);
	}

	public static QueryBuilder parseQueryByJson(JSONObject source) {
		QueryBuilder ret = null;
		try {
			Set<String> keys = source.keySet();
			for (String key : keys) {
				Object value = source.get(key);
				logger.debug("KEY:{} / {}", key, value.getClass());
				if ("bool".equals(key)) {
					// BoolQueryBuilder boolQuery = QueryBuilders.boolQuery();
					// ret = boolQuery;
					// JSONObject queryValue = (JSONObject) value;
					// JSONArray queryItems = null;
					// QueryBuilder subQuery = null;
					// if ((queryItems = queryValue.optJSONArray("must")) != null) {
					// 	for (int inx = 0; inx < queryItems.length(); inx++) {
					// 		subQuery = parseQueryByJson(queryItems.getJSONObject(inx));
					// 		if (subQuery != null) { boolQuery.must(subQuery); }
					// 	}
					// }
					// if ((queryItems = queryValue.optJSONArray("should")) != null) {
					// 	for (int inx = 0; inx < queryItems.length(); inx++) {
					// 		subQuery = parseQueryByJson(queryItems.getJSONObject(inx));
					// 		if (subQuery != null) { boolQuery.should(subQuery); }
					// 	}
					// }
					BoolQueryBuilder boolQuery = (BoolQueryBuilder) DanawaSearchQueryBuilder
						.parseQuery("{\"" + key + "\":" + value + "}");
					ret = boolQuery;
					QueryBuilder testQuery = QueryBuilders.queryStringQuery("ABC:TEST");
					boolQuery.must(testQuery);
					logger.debug("Q:{}", boolQuery);
				} else if ("multi_match".equals(key)) {
					ret = DanawaSearchQueryBuilder.parseQuery("{\"" + key + "\":" + value + "}");
				} else if (value instanceof JSONObject) {
					parseQueryByJson((JSONObject) value);
				} else if (value instanceof JSONArray) {
					parseQueryByJson((JSONArray) value);
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	public static QueryBuilder parseQueryByJson(JSONArray source) {
		QueryBuilder ret = null;
		try {
			for (int inx = 0; inx < source.length(); inx++) {
				Object value = source.get(inx);
				logger.debug("INDEX:{} / {}", inx, value.getClass());
				if (value instanceof JSONObject) {
					parseQueryByJson((JSONObject) value);
				} else if (value instanceof JSONArray) {
					parseQueryByJson((JSONArray) value);
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	public static QueryBuilder parseMultiMatchQueryByJson(JSONObject source) {
		MultiMatchQueryBuilder ret = null;
		try {
			JSONArray fieldItems = source.optJSONArray("fields");
			String text = source.optString("query");
			String operator = source.optString("operator");
			String[] fields = new String[fieldItems.length()];
			float[] boosts = new float[fieldItems.length()];
			for (int inx = 0; inx < fields.length; inx++) {
				String[] item = fieldItems.getString(inx).split("\\^");
				if (item.length > 1) {
					fields[inx] = item[0];
					boosts[inx] = Float.parseFloat(item[1]);
				} else {
					fields[inx] = item[0];
					boosts[inx] = 1;
				}
			}
			ret = QueryBuilders.multiMatchQuery(text, fields);
			for (int inx = 0; inx < fields.length; inx++) {
				ret.fields().put(fields[inx], boosts[inx]);
			}
			if (!"".equals(operator)) {
				ret.operator(Operator.fromString(operator));
			}
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	@Test public void testAnalyzeDetail() {
		if (TestUtil.launchForBuild()) { return; }
		String index = "";
		ProductNameDictionary dictionary = null;
		dictionary = TestUtil.loadDictionary();
		// dictionary = TestUtil.loadTestDictionary();
		contextStore.put(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, dictionary);
		String str = "";
		str = "Sandisk Extream Z80 USB 16gb 스위스알파인클럽";
		// str = "nt-ok123";
		boolean useForQuery;
		useForQuery = true;
		// useForQuery = false;
		boolean useSynonym = true;
		boolean useStopword = true;
		boolean detail;
		detail = true;
		// detail = false;
		TokenStream stream = getAnalyzer(dictionary, str, useForQuery, useSynonym, useStopword);
		StringWriter buffer = new StringWriter();
		JSONWriter writer = new JSONWriter(buffer);
		ProductNameAnalysisAction.analyzeTextDetail(null, str, stream, detail, index, writer);
		logger.debug("RESULT : {}", String.valueOf(buffer));
	}

	public static TokenStream getAnalyzer(ProductNameDictionary dictionary, String str, boolean useForQuery, boolean useSynonym, boolean useStopword) {
		TokenStream tstream = null;
		Reader reader = null;
		Tokenizer tokenizer = null;
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
}
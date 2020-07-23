package com.danawa.search.analysis.product;

import static org.junit.Assert.*;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.index.DanawaSearchQueryBuilder;
import com.danawa.util.ContextStore;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.ParseField;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder;
import org.elasticsearch.search.fetch.subphase.highlight.HighlightBuilder.Field;
import org.elasticsearch.search.sort.SortBuilder;
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
		text = "RF85R901301";

		// ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		TokenStream stream = null;
		JSONObject analysis = new JSONObject();

		String[] fields = new String[] { "TOTALINDEX", "BRANDKEYWORD" };
		Map<String, Float> boostMap = new HashMap<>();
		boostMap.put("TOTALINDEX", 1.0f);
		boostMap.put("BRANDKEYWORD", 100000.0f);

		stream = getAnalyzer(dictionary, text, true, true, true, true);
		QueryBuilder query = DanawaSearchQueryBuilder.buildAnalyzedQuery(stream, fields, boostMap, null, null, analysis);
		logger.debug("Q:{}", query.toString());
		logger.debug("ANALYSIS:{}", analysis);
		assertTrue(true);
	}

	@Test public void queryFromStringTest() throws Exception {
		if (TestUtil.launchForBuild()) { return; }
		logger.debug("PARSING..");
		String source = TEST_QUERY;
		JSONObject json = new JSONObject(source);
		for (String key : json.keySet()) {
			logger.debug("KEY:{} / {}", key, json.opt(key).getClass());
		}
		source = json.optString("query");

		long time = System.nanoTime();
		QueryBuilder query = DanawaSearchQueryBuilder.parseQuery(source);

		FunctionScoreQueryBuilder q = (FunctionScoreQueryBuilder) query;
		time = System.nanoTime() - time;
		logger.debug("Q:{}", q.query());
		logger.debug("PARSING TAKES {} ms", ((int) Math.round(time * 100.0 / 1000000.0)) / 100.0);

		XContentParser parser = JsonXContent.jsonXContent
			.createParser(DanawaSearchQueryBuilder.INSTANCE.SEARCH_CONTENT_REGISTRY,
			LoggingDeprecationHandler.INSTANCE, json.optString("query"));
		XContentParser.Token token = parser.nextToken();
		logger.debug("TOKEN:{}", token);

	}

	@Test public void sortFromStringTest() throws Exception {
		if (TestUtil.launchForBuild()) { return; }
		String source = null;
		source = "[{\"REGISTERDATE\":{\"order\":\"desc\"}}]";

		logger.debug("SOURCE:{}", source);
		List<NamedXContentRegistry.Entry> entries = new ArrayList<>();
		NamedXContentRegistry.Entry entry = new NamedXContentRegistry.Entry(List.class, new ParseField("sort"),
			(p) -> { return SortBuilder.fromXContent(p); });
		entries.add(entry);
		NamedXContentRegistry registry = new NamedXContentRegistry(entries);
		XContentParser parser = JsonXContent.jsonXContent.createParser(registry, LoggingDeprecationHandler.INSTANCE, source);
		XContentParser.Token token = parser.currentToken();
		token = parser.nextToken();
		token = parser.currentToken();
		logger.debug("TOKEN:{}", token);
		List<SortBuilder<?>> sort = SortBuilder.fromXContent(parser);
		logger.debug("SORT:{}", sort);
	}

	@Test public void highlightStringTest() throws Exception {
		if (TestUtil.launchForBuild()) { return; }
		String source = null;
		source = "" + 
		"  { " +
		"    \"number_of_fragments\" : 3, " +
		"    \"fragment_size\" : 150, " +
		"    \"pre_tags\" : [\"<tag1>\"], " +
		"    \"post_tags\" : [\"</tag1>\"], " +
		"    \"fields\" : { " +
		"      \"_all\" : { " +
		"        \"fragment_size\" : 15, " +
		"        \"number_of_fragments\" : 3, " +
		"        \"fragmenter\": \"simple\" " +
		"      }, " +
		"      \"PRODUCTNAME\" : { " +
		"      } " +
		"    } " +
		"  } ";
		XContentParser parser = JsonXContent.jsonXContent.createParser(NamedXContentRegistry.EMPTY,
			LoggingDeprecationHandler.INSTANCE, source);
		parser.nextToken();
		HighlightBuilder highlight = HighlightBuilder.fromXContent(parser);
		for (Field field : highlight.fields()) {
			logger.debug("FIELD:{}", field.name());
		}
		logger.debug("HIGHLIGHT:{}", highlight);
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
		boolean useFullString = true;
		boolean detail;
		detail = true;
		// detail = false;
		TokenStream stream = getAnalyzer(dictionary, str, useForQuery, useSynonym, useStopword, useFullString);
		StringWriter buffer = new StringWriter();
		JSONWriter writer = new JSONWriter(buffer);
		ProductNameAnalysisAction.analyzeTextDetail(null, str, stream, detail, index, writer);
		logger.debug("RESULT : {}", String.valueOf(buffer));
	}

	public static TokenStream getAnalyzer(ProductNameDictionary dictionary, String str, boolean useForQuery, boolean useSynonym, boolean useStopword, boolean useFullString) {
		TokenStream tstream = null;
		Reader reader = null;
		Tokenizer tokenizer = null;
		AnalyzerOption option = null;

		option = new AnalyzerOption();
		option.useForQuery(useForQuery);
		option.useSynonym(useSynonym);
		option.useStopword(useStopword);
		option.useFullString(useFullString);
		reader = new StringReader(str);
		tokenizer = new ProductNameTokenizer(dictionary, false);
		tokenizer.setReader(reader);
		tstream = new ProductNameAnalysisFilter(tokenizer, dictionary, option);
		return tstream;
	}

	private static final String TEST_QUERY = "" +
		"{ " +
		"  \"query\": { " +
		"    \"function_score\": { " +
		"      \"query\": { " +
		"        \"bool\": { " +
		"          \"must\": [ " +
		"            { " +
		"              \"query_string\": { " +
		"                \"query\": \"*\" " +
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
}
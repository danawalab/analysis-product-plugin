package com.danawa.search.analysis.product;

import static org.junit.Assert.*;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.index.DanawaSearchQueryBuilder;
import com.danawa.util.ContextStore;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.DeprecationHandler;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.BoostingQueryBuilder;
import org.elasticsearch.index.query.MatchPhraseQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.SimpleQueryStringBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.TermsSetQueryBuilder;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.SearchPlugin;
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

		DeprecationHandler dpHandler = LoggingDeprecationHandler.INSTANCE;

		registerQuery(new SearchPlugin.QuerySpec<>(MatchQueryBuilder.NAME, MatchQueryBuilder::new,
				MatchQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(MultiMatchQueryBuilder.NAME, MultiMatchQueryBuilder::new,
				MultiMatchQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(MatchPhraseQueryBuilder.NAME, MatchPhraseQueryBuilder::new,
				MatchPhraseQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(QueryStringQueryBuilder.NAME, QueryStringQueryBuilder::new,
				QueryStringQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(BoostingQueryBuilder.NAME, BoostingQueryBuilder::new,
				BoostingQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(BoolQueryBuilder.NAME, BoolQueryBuilder::new,
				BoolQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(RangeQueryBuilder.NAME, RangeQueryBuilder::new,
				RangeQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(WrapperQueryBuilder.NAME, WrapperQueryBuilder::new,
				WrapperQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(FunctionScoreQueryBuilder.NAME, FunctionScoreQueryBuilder::new,
				FunctionScoreQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(SimpleQueryStringBuilder.NAME, SimpleQueryStringBuilder::new,
				SimpleQueryStringBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(TermsSetQueryBuilder.NAME, TermsSetQueryBuilder::new,
				TermsSetQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(TermQueryBuilder.NAME, TermQueryBuilder::new,
				TermQueryBuilder::fromXContent));
		registerQuery(new SearchPlugin.QuerySpec<>(TermsQueryBuilder.NAME, TermsQueryBuilder::new,
				TermsQueryBuilder::fromXContent));

		NamedXContentRegistry registry = new NamedXContentRegistry(namedXContents);
		XContentParser parser = JsonXContent.jsonXContent.createParser(registry, dpHandler, source);
		long time = System.nanoTime();
		QueryBuilder query = AbstractQueryBuilder.parseInnerQueryBuilder(parser);
		time = System.nanoTime() - time;
		logger.debug("Q:{}", "", query);
		logger.debug("PARSING TAKES {} ms", ((int) Math.round(time * 100.0 / 1000000.0)) / 100.0);
	}

	private final List<NamedWriteableRegistry.Entry> namedWriteables = new ArrayList<>();
	private final List<NamedXContentRegistry.Entry> namedXContents = new ArrayList<>();
	private void registerQuery(SearchPlugin.QuerySpec<?> spec) {
		namedWriteables.add(new NamedWriteableRegistry.Entry(QueryBuilder.class, spec.getName().getPreferredName(), spec.getReader()));
		namedXContents.add(new NamedXContentRegistry.Entry(QueryBuilder.class, spec.getName(), (p, c) -> spec.getParser().fromXContent(p)));
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
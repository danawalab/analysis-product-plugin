package com.danawa.search.analysis.product;

import static org.junit.Assert.*;

import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.index.DanawaSearchQueryBuilder;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.query.QueryBuilder;
import org.json.JSONObject;
import org.json.JSONWriter;
import org.junit.Before;
import org.junit.Test;

public class ProductNameAnalysisActionTest {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisActionTest.class, "");

	@Before public void init() {
		TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"), 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
	}

	@Test public void queryBuildTest() {
		if (TestUtil.launchForBuild()) { return; }
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

	@Test public void testAnalyzeDetail() {
		if (TestUtil.launchForBuild()) { return; }
		String index = ".fastcatx_dict";
		ProductNameDictionary dictionary = null;
		dictionary = TestUtil.loadDictionary();
		// dictionary = TestUtil.loadTestDictionary();
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
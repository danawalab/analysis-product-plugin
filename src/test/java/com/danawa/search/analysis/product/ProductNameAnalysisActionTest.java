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
		// 상세분석
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

		// 간략분석
		// GET /management/analysis/analysis-tools.json?pluginId=PRODUCT&analyzerId=standard&forQuery=true&queryWords=Sandisk Extream Z80 USB 16gb
		// {
		//  "query":"Sandisk Extream Z80 USB 16gb",
		//  "result":[
		//    "Sandisk",
		//    "Extream",
		//    "Z",
		//    "80",
		//    "USB",
		//    "16gb",
		//    "Sandisk Extream Z80 USB 16gb"
		//  ],
		//  "success":true
		//}

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
		JSONWriter writer = new JSONWriter(new StringWriter());
		ProductNameAnalysisAction.analyzeTextDetail(str, stream, detail, index, writer);
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
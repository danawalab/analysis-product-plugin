package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.search.analysis.korean.KoreanWordExtractor.ExtractedEntry;
import com.danawa.util.CharVector;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class KoreanWordExtractorTest {

	private static Logger logger = Loggers.getLogger(KoreanWordExtractorTest.class, "");

	@Before public void init() {
		TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"), 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
	}

	@Test public void testUserDictionary() {
		if (TestUtil.launchForBuild()) { return; }
		// ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		SetDictionary userDictionary = dictionary.getDictionary("user", SetDictionary.class);
		CharSequence word = new CharVector("JY모터스");
		logger.debug("DICT:{}", userDictionary.set());
		logger.debug("CONTAINS {} = {}", word, userDictionary.contains(word));
		word = new CharVector("jy모터스");
		logger.debug("CONTAINS {} = {}", word, userDictionary.contains(word));
	}

	@Test public void testExtractorSimple() {
		if (TestUtil.launchForBuild()) { return; }
		// ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		KoreanWordExtractor extractor = new KoreanWordExtractor(dictionary);
		String str = "한글분석기테스트중입니다";
		str = "/F20005W_F10011M_F20246W_247W_251W_FMS10";
		char[] buf = str.toCharArray();
		if (extractor.setInput(buf, 0, buf.length) != -1) {
			ExtractedEntry entry = extractor.extract();
			while (entry != null) {
				logger.debug(">> {}", entry.toDetailString(buf));
				entry = entry.next();
			}
		}
		assertTrue(true);
	}
}
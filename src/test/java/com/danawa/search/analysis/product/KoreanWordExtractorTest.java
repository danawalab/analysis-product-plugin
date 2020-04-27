package com.danawa.search.analysis.product;

import static org.junit.Assert.*;

import java.io.File;
import java.util.Properties;

import com.danawa.search.analysis.dict.CommonDictionary;
import com.danawa.search.analysis.dict.PreResult;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.product.KoreanWordExtractor.Entry;
import com.danawa.util.CharVector;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;

public class KoreanWordExtractorTest {

	private static Logger logger = Loggers.getLogger(KoreanWordExtractorTest.class, "");

	@Before public void init() {
		String LOG_LEVEL = System.getProperty("LOG_LEVEL");
		if (LOG_LEVEL == null || "".equals(LOG_LEVEL)) { LOG_LEVEL = "DEBUG"; }
		// ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME)).setLevel(Level.toLevel(LOG_LEVEL));
		// ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProductNameTokenizer.class)).setLevel(Level.toLevel(LOG_LEVEL));
		// ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProductNameParsingRule.class)).setLevel(Level.toLevel(LOG_LEVEL));
		// ((ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ProductNameAnalysisFilter.class)).setLevel(Level.toLevel(LOG_LEVEL));
	}

	@Test public void testUserDictionary() {
		if (!TestUtil.isMassiveTestEnabled()) { return; }
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		if (propFile == null) { return; }
		Properties prop = TestUtil.readProperties(propFile);
		CommonDictionary<TagProb, PreResult<CharSequence>> commonDictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
		SetDictionary userDictionary = commonDictionary.getDictionary("user", SetDictionary.class);
		CharSequence word = new CharVector("JY모터스");
		logger.debug("DICT:{}", userDictionary.set());
		logger.debug("CONTAINS {} = {}", word, userDictionary.contains(word));
		word = new CharVector("jy모터스");
		logger.debug("CONTAINS {} = {}", word, userDictionary.contains(word));
	}

	@Test public void testExtractorSimple() {
		if (!TestUtil.isMassiveTestEnabled()) { return; }
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		if (propFile == null) { return; }
		Properties prop = TestUtil.readProperties(propFile);
		CommonDictionary<TagProb, PreResult<CharSequence>> commonDictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
		KoreanWordExtractor extractor = new KoreanWordExtractor(commonDictionary);
		String str = "한글분석기테스트중입니다";
		char[] buf = str.toCharArray();
		if (extractor.setInput(buf, 0, buf.length) != -1) {
			Entry entry = extractor.extract();
			while (entry != null) {
				logger.debug(">> {}", entry.toDetailString(buf));
				entry = entry.next();
			}
		}
		assertTrue(true);
	}
}
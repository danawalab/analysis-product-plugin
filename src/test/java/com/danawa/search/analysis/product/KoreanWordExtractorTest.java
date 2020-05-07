package com.danawa.search.analysis.product;

import java.io.File;
import java.util.Properties;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.product.KoreanWordExtractor.Entry;
import com.danawa.util.CharVector;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class KoreanWordExtractorTest {

	private static Logger logger = Loggers.getLogger(KoreanWordExtractorTest.class, "");

	@Before public void init() {
		Level level = Level.toLevel(System.getProperty("LOG_LEVEL"), Level.DEBUG);
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		Configuration config = ctx.getConfiguration();
		LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
		loggerConfig.setLevel(level);
		Loggers.setLevel(Loggers.getLogger(ProductNameTokenizer.class, ""), level);
		Loggers.setLevel(Loggers.getLogger(ProductNameParsingRule.class, ""), level);
		Loggers.setLevel(Loggers.getLogger(ProductNameAnalysisFilter.class, ""), level);
		ctx.updateLoggers();
	}

	@Test public void testUserDictionary() {
		if (!TestUtil.isMassiveTestEnabled()) { return; }
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		if (propFile == null) { return; }
		Properties prop = TestUtil.readProperties(propFile);
		ProductNameDictionary commonDictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
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
		ProductNameDictionary commonDictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
		KoreanWordExtractor extractor = new KoreanWordExtractor(commonDictionary);
		String str = "한글분석기테스트중입니다";
		str = "/F20005W_F10011M_F20246W_247W_251W_FMS10";
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
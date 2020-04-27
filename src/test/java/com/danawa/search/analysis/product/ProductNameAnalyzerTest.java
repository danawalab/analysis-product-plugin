package com.danawa.search.analysis.product;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ProductNameAnalyzerTest {

	private static Logger logger = Loggers.getLogger(ProductNameAnalyzerTest.class, "");

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

	@Test public void testAnalyzerSimple() {
		if (!TestUtil.isMassiveTestEnabled()) { return; }
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		if (propFile == null) { return; }

		Properties prop = TestUtil.readProperties(propFile);
		ProductNameDictionary commonDictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
		ProductNameAnalyzer analyzer = null;
		String str = "한글분석기테스트중입니다";
		Reader reader = null;
		TokenStream stream = null;
		try {
			analyzer = new ProductNameAnalyzer(commonDictionary);
			reader = new StringReader(str);
			stream = analyzer.tokenStream("", reader);
			TokenInfoAttribute tokenAttribute = stream.addAttribute(TokenInfoAttribute.class);
			CharTermAttribute termAttribute = stream.addAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttribute = stream.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttribute = stream.addAttribute(TypeAttribute.class);
			stream.reset();
			for (; stream.incrementToken();) {
				logger.debug("TOKEN:{} / {}~{} / {} / {}", termAttribute, offsetAttribute.startOffset(),
					offsetAttribute.endOffset(), tokenAttribute.charVector().length(), typeAttribute.type());
			}
		} catch (Exception e) {
			logger.error("", e);
		}  finally {
			try { analyzer.close(); } catch (Exception ignore) { }
			try { reader.close(); } catch (Exception ignore) { }
		}
		assertTrue(true);
	}
}
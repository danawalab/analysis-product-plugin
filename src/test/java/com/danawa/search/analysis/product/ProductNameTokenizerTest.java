package com.danawa.search.analysis.product;

import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Matcher;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;

import static com.danawa.util.TestUtil.*;
import static org.junit.Assert.*;

public class ProductNameTokenizerTest {

	private static Logger logger = Loggers.getLogger(ProductNameTokenizerTest.class, "");

	private static final String TEXT_STR = "상품명분석기ProductNameTokenizer테스트중입니다1234상품명ABCD";

	private static final String[][] TEST_DATA_NUMBERS = { 
		{ "1", T }, 
		{ "123", T }, 
		{ "1234", T }, 
		{ "12345", T },
		{ "1,234", T }, 
		{ "1,234,567", T }, 
		{ "12,345,678", T }, 
		{ "1,2,3,4", F }, 
		{ "1.2", T }, 
		{ "1.234", T },
		{ "1.234.567", T }, 
		{ "100:100", T }, 
		{ "1,234:5,678", T } 
	};

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

	@Test
	public void testNumberPattern() {
		Matcher mat;
		for (int inx = 0; inx < TEST_DATA_NUMBERS.length; inx++) {
			mat = ProductNameTokenizer.PTN_NUMBER.matcher(TEST_DATA_NUMBERS[inx][0]);
			if (TEST_DATA_NUMBERS[inx][1] == T) {
				assertTrue(mat.find());
				logger.debug("{} : OK", TEST_DATA_NUMBERS[inx][0]);
			} else {
				assertFalse(mat.find());
				logger.debug("{} : BAD", TEST_DATA_NUMBERS[inx][0]);
			}
		}
	}

	@Test
	public void testTokenizer() throws Exception {
		if (TestUtil.launchForBuild()) { return; }
		Reader reader = null;
		Tokenizer tokenizer = null;
		ProductNameDictionary dictionary = null;
		String str = TEXT_STR;
		str = "페이유에 균일가 7종/F20005W_F10011M_F20246W_247W_251W_FMS10";
		try {
			reader = new StringReader(str);
			tokenizer = new ProductNameTokenizer(dictionary);
			tokenizer.setReader(reader);
			TokenInfoAttribute tokenAttribute = tokenizer.addAttribute(TokenInfoAttribute.class);
			OffsetAttribute offsetAttribute = tokenizer.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttribute = tokenizer.addAttribute(TypeAttribute.class);
			tokenizer.reset();
			for (; tokenizer.incrementToken();) {
				logger.debug("TOKEN:{} / {}~{} / {}", tokenAttribute.ref(), offsetAttribute.startOffset(),
					offsetAttribute.endOffset(), typeAttribute.type());
			}
		} finally {
			try { tokenizer.close(); } catch (Exception ignore) { }
		}
	}
}
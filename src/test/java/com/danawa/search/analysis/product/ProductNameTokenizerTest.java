package com.danawa.search.analysis.product;

import static org.junit.Assert.*;
import static com.danawa.util.TestUtil.*;

import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Matcher;

import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductNameTokenizerTest {

	private static Logger logger = LoggerFactory.getLogger(ProductNameTokenizerTest.class);

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

	@Before
	public void init() {

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
		Reader reader = null;
		Tokenizer tokenizer = null;
		try {
			reader = new StringReader(TEXT_STR);
			tokenizer = new ProductNameTokenizer();
			tokenizer.setReader(reader);
			TokenInfoAttribute tokenAttribute = tokenizer.addAttribute(TokenInfoAttribute.class);
			OffsetAttribute offsetAttribute = tokenizer.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttribute = tokenizer.addAttribute(TypeAttribute.class);
			tokenizer.reset();
			for (; tokenizer.incrementToken();) {
				logger.debug("TOKEN:{} / {}~{} / {}", tokenAttribute.charVector(), offsetAttribute.startOffset(),
					offsetAttribute.endOffset(), typeAttribute.type());
			}
		} finally {
			try { tokenizer.close(); } catch (Exception ignore) { }
		}
	}
}
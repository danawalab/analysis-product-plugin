package com.danawa.search.analysis.product;

import static org.junit.Assert.*;
import static com.danawa.util.TestUtil.*;

import java.util.regex.Matcher;

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductNameTokenizerTest {

    private static Logger logger = LoggerFactory.getLogger(ProductNameTokenizerTest.class);

    @Before public void init() {

    }

    @Test public void testNumberPattern() {
		Matcher mat;
		String[][] str = {
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
            { "1,234:5,678", T },
		};
		for (int inx = 0; inx < str.length; inx++) {
            mat = ProductNameTokenizer.PTN_NUMBER.matcher(str[inx][0]);
            if (str[inx][1] == T) {
                assertTrue(mat.find());
				logger.debug("{} : OK", str[inx][0]);
            } else {
                assertFalse(mat.find());
				logger.debug("{} : BAD", str[inx][0]);
            }
        }
    }
}
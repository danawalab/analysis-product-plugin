package com.danawa.search.analysis.product;

import static org.junit.Assert.assertTrue;

import java.io.Reader;
import java.io.StringReader;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Test;

public class ProductNameAnalysisFilterTest {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisFilterTest.class, "");

    @Test public void testFilter() {
		if (TestUtil.launchForBuild()) { return; }
        Reader reader = null;
        ProductNameDictionary dictionary = null;
        Tokenizer tokenizer = null;
        ProductNameAnalysisFilter tstream = null;
        String str = "TokenFilterTest";
		try {
            reader = new StringReader(str);
            tokenizer = new ProductNameTokenizer(dictionary);
            tokenizer.setReader(reader);
            tstream = new ProductNameAnalysisFilter(tokenizer);
            tstream.reset();
            CharTermAttribute termAttr = tstream.addAttribute(CharTermAttribute.class);
            while (tstream.incrementTokenNew()) {
                logger.debug("TOKEN:{}", termAttr);
            }
        } catch (Exception e) {
            logger.error("", e);
        } finally {
            try { tokenizer.close(); } catch (Exception ignore) { }
        }
        assertTrue(true);
    }

}
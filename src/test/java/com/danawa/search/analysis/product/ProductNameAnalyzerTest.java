package com.danawa.search.analysis.product;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

import com.danawa.search.analysis.dict.CommonDictionary;
import com.danawa.search.analysis.dict.PreResult;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;
import com.danawa.util.TestUtil;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductNameAnalyzerTest {
    
    private static Logger logger = LoggerFactory.getLogger(ProductNameAnalyzerTest.class);

    @Test public void testAnalyzerSimple() {
		if (!TestUtil.isMassiveTestEnabled()) { return; }
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
        if (propFile == null) { return; }

		Properties prop = TestUtil.readProperties(propFile);
        CommonDictionary<TagProb, PreResult<CharSequence>> commonDictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
        ProductNameAnalyzer analyzer = null;
        String str = "한글분석기테스트중입니다";
        Reader reader = null;
        TokenStream stream = null;
        try {
            analyzer = new ProductNameAnalyzer(commonDictionary);
            reader = new StringReader(str);
            stream = analyzer.tokenStream("", reader);
			TokenInfoAttribute tokenAttribute = stream.addAttribute(TokenInfoAttribute.class);
			OffsetAttribute offsetAttribute = stream.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttribute = stream.addAttribute(TypeAttribute.class);
			stream.reset();
			for (; stream.incrementToken();) {
				logger.debug("TOKEN:{} / {}~{} / {}", tokenAttribute.charVector(), offsetAttribute.startOffset(),
					offsetAttribute.endOffset(), typeAttribute.type());
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
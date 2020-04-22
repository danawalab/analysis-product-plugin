package com.danawa.search.analysis.product;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.Properties;

import com.danawa.search.analysis.dict.CommonDictionary;
import com.danawa.search.analysis.dict.PreResult;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.product.KoreanWordExtractor.Entry;
import com.danawa.util.TestUtil;

import org.junit.Test;

public class KoreanWordExtractorTest {

	@Test
	public void testExtractorSimple() {
		File propFile = TestUtil.getFileByProperty("PROP_TEST_DICTIONARY_SETTING");
		if (!(propFile != null && propFile.exists())) { return; }
		CommonDictionary<TagProb, PreResult<CharSequence>> koreanDict = null;
		Properties prop = new Properties();
		Reader reader = null;
		try {
			reader = new FileReader(propFile);
			prop.load(reader);
		} catch (Exception ignore) {
		} finally {
			try { reader.close(); } catch (Exception ignore) { }
		}

		koreanDict = ProductNameTokenizerFactory.loadDictionary(null, prop);
		KoreanWordExtractor extractor = new KoreanWordExtractor(koreanDict);
		String str = "한글분석기테스트중입니다";
		char[] buf = str.toCharArray();
		if (extractor.setInput(buf, 0, buf.length) != -1) {
			Entry entry = extractor.extract();
			while (entry != null) {
				System.out.println(">>" + entry.toDetailString(buf));
				entry = entry.next();
			}
		}
		assertTrue(true);
	}
}
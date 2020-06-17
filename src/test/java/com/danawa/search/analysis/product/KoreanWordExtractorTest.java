package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.CompoundDictionary;
import com.danawa.search.analysis.dict.CustomDictionary;
import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.search.analysis.korean.KoreanWordExtractor.ExtractedEntry;
import com.danawa.search.analysis.korean.PosTagProbEntry.TagProb;
import com.danawa.util.CharVector;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.List;

import static com.danawa.search.analysis.product.ProductNameTokenizer.*;

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
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		SetDictionary userDict = dictionary.getDictionary(DICT_USER, SetDictionary.class);
		CustomDictionary brandDict = dictionary.getDictionary(DICT_BRAND, CustomDictionary.class);
		CustomDictionary makerDict= dictionary.getDictionary(DICT_MAKER, CustomDictionary.class);
		SynonymDictionary synonymDict = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
		CompoundDictionary compoundDict = dictionary.getDictionary(DICT_COMPOUND, CompoundDictionary.class);
		SpaceDictionary spaceDict = dictionary.getDictionary(DICT_SPACE, SpaceDictionary.class);
		SetDictionary stopDict = dictionary.getDictionary(DICT_STOP, SetDictionary.class);
		SetDictionary unitDict = dictionary.getDictionary(DICT_UNIT, SetDictionary.class);
		SynonymDictionary unitSynDict = dictionary.getDictionary(DICT_UNIT_SYNONYM, SynonymDictionary.class);


		CharSequence word = null;
		word = new CharVector("JY모터스");
		logger.debug("CONTAINS {} = {}", word, userDict.contains(word));
		word = new CharVector("jy모터스");
		logger.debug("CONTAINS {} = {}", word, userDict.contains(word));
		word = new CharVector("SANDISK");
		CharSequence[] synonyms = synonymDict.get(word);
		for (CharSequence syn : synonyms) {
			logger.debug("SYNONYM:{}", syn);
		}

		String[] compareData = { "z", "지", "제트" };

		for (String key : compareData) {
			word = new CharVector(key);
			List<TagProb> result = dictionary.find(word);
			logger.debug("RESULT:{}", result);

			logger.debug("USER:{} / {}", userDict.contains(word), userDict.set().size());
			logger.debug("BRAND:{} / {}", brandDict.map().containsKey(word), brandDict.getWordSet().size());
			logger.debug("MAKER:{} / {}", makerDict.map().containsKey(word), makerDict.getWordSet().size());
			logger.debug("SYNONYM:{}{} / {}", "", synonymDict.get(word), synonymDict.getWordSet().size());
			logger.debug("COMPOUND:{}{} / {}", "", compoundDict.get(word), synonymDict.getWordSet().size());
			logger.debug("SPACE:{}{} / {}", "", spaceDict.get(word), spaceDict.getWordSet().size());
			logger.debug("STOP:{} / {}", stopDict.contains(word), stopDict.set().size());
			logger.debug("UNIT:{} / {}", unitDict.contains(word), unitDict.set().size());
			logger.debug("UNITSYNONYM:{}{} / {}", "", unitSynDict.get(word), unitSynDict.getWordSet().size());
			logger.debug("--------------------------------------------------------------------------------");
		}
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
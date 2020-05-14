package com.danawa.search.analysis.product;

import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.Reader;
import java.io.StringReader;
import java.util.Properties;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;

public class ProductNameAnalysisFilterTest {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisFilterTest.class, "");

	@Before public void init() {
		TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"), 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
	}

	@Test public void testFilter() {
		if (TestUtil.launchForBuild()) { return; }
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		if (propFile == null) { return; }

		Properties prop = TestUtil.readProperties(propFile);
		ProductNameDictionary dictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
		Reader reader = null;
		Tokenizer tokenizer = null;
		ProductNameAnalysisFilter tstream = null;
		String str = "TokenFilter레이스리퍼Test";
		// str = "아름다운이땅에 금수강산에 단군할아버지가터잡으시고홍익인간뜻으로나라세우니대대손손훌륭한인물도많아고구려세운동명왕백제온조왕알에서나온혁거세만주벌판달려라광개토대왕신라장군이사부백결선생떡방아      삼천궁녀의자왕황산벌의계백맞서싸운관창역사는흐른다말목자른김유신통일문무왕원효대사해골물혜초천축국바다의왕자장보고발해대조영귀주대첩강감찬서희거란족무단정치정중부화포최무선죽림칠현김부식지눌국사  조계종의천천태종대마도정벌이종무일편단심정몽주목화씨는문익점해동공자최충삼국유사일연역사는흐른다황금을보기를돌같이하라최영장군의말씀받들자황희정승맹사성과학장영실신숙주와한명회역사는안다십만양병이율곡주리이퇴계신사임당오죽헌잘싸운다곽재우조헌김시민나라구한이순신태정태세문단세사육신과생육신몸바쳐서논개행주치마권율역사는흐른다번쩍번쩍홍길동의적임꺽정대쪽같은삼학사어사박문수삼년공부한석봉단원풍속도방랑시인김삿갓 지도김정호영조대왕신문고정조규장각목민심서정약용녹두장군전봉준순교김대건서화가무황진이못살겠다홍경래삼일천하김옥균안중근은애국이완용은매국역사는흐른다 별헤는밤윤동주종두지석영삼십삼인손병희만세만세유관순도산안창호 어린이날방정환이수일과심순애장군의아들김두한날자꾸나이상황소그림중섭역사는흐른다";
		try {
			reader = new StringReader(str);
			tokenizer = new ProductNameTokenizer(dictionary);
			tokenizer.setReader(reader);
			tstream = new ProductNameAnalysisFilter(tokenizer);
			tstream.testInit();
			tstream.reset();
			CharTermAttribute termAttr = tstream.addAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttr = tstream.addAttribute(OffsetAttribute.class);
			while (tstream.incrementTokenNew()) {
				logger.debug("TOKEN:{} / {}~{}", termAttr, offsetAttr.startOffset(), offsetAttr.endOffset());
			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { tokenizer.close(); } catch (Exception ignore) { }
		}
		assertTrue(true);
	}
}
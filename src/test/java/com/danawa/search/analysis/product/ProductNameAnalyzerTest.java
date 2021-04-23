package com.danawa.search.analysis.product;

import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
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
		TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"), 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
	}

	@Test public void testAnalyzerSimple() {
		if (TestUtil.launchForBuild()) { return; }

		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		if (dictionary == null) {
			dictionary = TestUtil.loadTestDictionary();
		}

		ProductNameAnalyzer analyzer = null;
		String str = "한글분석기테스트중입니다";
		str = "가보시 통굽 태슬 애나멜 여자로퍼 단화 UH-Ulbeuseu";
		str = "애나멜 버클 여자구두 플랫슈즈 단화 로퍼 RJ-ae3300";
		str = "탠디 소재가고급스러운송아지가죽남성 스니커즈 515323 3가지색 428519";
    	str = "아름다운이땅에금수강산에단군할아버지가터잡으시고홍익인간뜻으로나라세우니대대손손훌륭한인물도많아고구려세운동명왕백제온조왕알에서나온혁거세만주벌판달려라광개토대왕신라장군이사부백결선생떡방아삼천궁녀의자왕황산벌의계백맞서싸운관창역사는흐른다말목자른김유신통일문무왕원효대사해골물혜초천축국바다의왕자장보고발해대조영귀주대첩강감찬서희거란족무단정치정중부화포최무선죽림칠현김부식지눌국사조계종의천천태종대마도정벌이종무일편단심정몽주목화씨는문익점해동공자최충삼국유사일연역사는흐른다황금을보기를돌같이하라최영장군의말씀받들자황희정승맹사성과학장영실신숙주와한명회역사는안다십만양병이율곡주리이퇴계신사임당오죽헌잘싸운다곽재우조헌김시민나라구한이순신태정태세문단세사육신과생육신몸바쳐서논개행주치마권율역사는흐른다번쩍번쩍홍길동의적임꺽정대쪽같은삼학사어사박문수삼년공부한석봉단원풍속도방랑시인김삿갓지도김정호영조대왕신문고정조규장각목민심서정약용녹두장군전봉준순교김대건서화가무황진이못살겠다홍경래삼일천하김옥균안중근은애국이완용은매국역사는흐른다별헤는밤윤동주종두지석영삼십삼인손병희만세만세유관순도산안창호어린이날방정환이수일과심순애장군의아들김두한날자꾸나이상황소그림중섭역사는흐른다";
		// str = "49_5840-F6^BLACK^225(35)";
		// str = "페이유에 균일가 7종/F20005W_F10011M_F20246W_247W_251W_FMS10";
		str = "JBW Mens Luxury Jet Setter 2.34 Carat Diamond Wrist Watch with Stainless Steel Link Bracelet Black/G";
		str = "CF^miniSDHC";
		str = "PC케이스(ATX)|미들타워|파워미포함|Extended-ATX|표준-ATX|Micro-ATX|Mini-ITX|수직 PCI슬롯$기본형|쿨링팬$총4개|LED팬$3개|측면$강화유리| 후면$140mm x1|전면$120mm LED x3|너비(W)$251mm|깊이(D)$545mm|높이(H)$552mm|파워 장착$240mm|파워 위치$하단후면|GPU 장착$420mm|CPU 장착$180mm|LED 색상$RGB";
		str= "G1 OC D6 12GB 11ml";

		Reader reader = null;
		TokenStream stream = null;
		try {
			analyzer = new ProductNameAnalyzer(dictionary);
			reader = new StringReader(str);
			stream = analyzer.tokenStream("", reader);
			TokenInfoAttribute tokenAttribute = stream.addAttribute(TokenInfoAttribute.class);
			CharTermAttribute termAttribute = stream.addAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttribute = stream.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttribute = stream.addAttribute(TypeAttribute.class);
			ExtraTermAttribute addAttribute = stream.addAttribute(ExtraTermAttribute.class);
			stream.reset();
			for (; stream.incrementToken();) {
				logger.debug("TOKEN:{} / {}~{} / {} / {} // {}", termAttribute, offsetAttribute.startOffset(),
					offsetAttribute.endOffset(), tokenAttribute.ref().length(), typeAttribute.type());
				Iterator<String> iter = addAttribute.iterator();
				while (iter != null && iter.hasNext()) {
					String next = iter.next();
					logger.debug(" - ADD:{} / {}", next, typeAttribute.type());
				}
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
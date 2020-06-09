package com.danawa.search.analysis.product;

import java.io.Reader;
import java.io.StringReader;
import java.util.regex.Matcher;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
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
		TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"), 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
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

		// ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		Reader reader = null;
		Tokenizer tokenizer = null;
		String str = TEXT_STR;
		str = "페이유에 균일가 7종/F20005W_F10011M_F20246W_247W_251W_FMS10";
		str = "BC087 레드코코아 가방,패션잡화,아이템,남성,acc 서류가방 레드코코아닷컴 모던 비지니스백  서류가방 정장가방 BC087 5092852 레드코코아 5092852 패션잡화>가방>남성가방";
		str = "MSI MSI, 게임용노트북 500GB 디지털,노트북,pc,완제품 인텔|코어i5-4세대|하스웰|i5-4200M (2.5GHz)|39.62cm(15.6인치)|1920x1080|LED백라이트|4GB|DDR3|500GB|HD 4600|지포스 GT740M|2GB GDDR3|듀얼 그래픽|1Gbps 유선랜|802.11 b/g/n 무선랜|블루투스 4.0|HDMI|D-SUB|웹캠|USB 2.0|USB 3.0|멀티 리더기|숫자 키패드|블록 키보드|운영체제 미포함|2.4Kg CX61-i5 2OD Luna 2170277 뛰어난 성능과 저렴한 가격으로 인기 UP! MSI 2170277 디지털 완제품>노트북>MSI";
		str = "콜한 COLEHAAN   OG GRD KNT WNG TP    그레이 여성 옥스포드  WIDTH:B  CHSO8G702G2";
		str = "아름다운이땅에 금수강산에 단군할아버지가터잡으시고홍익인간뜻으로나라세우니대대손손훌륭한인물도많아고구려세운동명왕백제온조왕알에서나온혁거세만주벌판달려라광개토대왕신라장군이사부백결선생떡방아      삼천궁녀의자왕황산벌의계백맞서싸운관창역사는흐른다말목자른김유신통일문무왕원효대사해골물혜초천축국바다의왕자장보고발해대조영귀주대첩강감찬서희거란족무단정치정중부화포최무선죽림칠현김부식지눌국사  조계종의천천태종대마도정벌이종무일편단심정몽주목화씨는문익점해동공자최충삼국유사일연역사는흐른다황금을보기를돌같이하라최영장군의말씀받들자황희정승맹사성과학장영실신숙주와한명회역사는안다십만양병이율곡주리이퇴계신사임당오죽헌잘싸운다곽재우조헌김시민나라구한이순신태정태세문단세사육신과생육신몸바쳐서논개행주치마권율역사는흐른다번쩍번쩍홍길동의적임꺽정대쪽같은삼학사어사박문수삼년공부한석봉단원풍속도방랑시인김삿갓 지도김정호영조대왕신문고정조규장각목민심서정약용녹두장군전봉준순교김대건서화가무황진이못살겠다홍경래삼일천하김옥균안중근은애국이완용은매국역사는흐른다 별헤는밤윤동주종두지석영삼십삼인손병희만세만세유관순도산안창호 어린이날방정환이수일과심순애장군의아들김두한날자꾸나이상황소그림중섭역사는흐른다";
		str = "Z300CNL ASUS 4G 태블릿, LTE탭, 아수스, 에이수스, 젠패드 해외구매 태블릿 LTE+WiFi|OS:안드로이드 6.0 마시멜로우|25.65㎝(10.1″)|1280x800|149ppi|IPS|16:10|10포인트|인텔아톰(베이트레일)Z3560|쿼드코어|1.83Ghz|램:2GB|내장:16GB|microSD카드슬롯|GPS|블루투스|V4.0|카메라|500만화소|200만화소|microUSB2.0|3.5mm 이어폰|가로:251.6mm|세로:172mm|두께:7.9mm|510g|18WHr ZenPad 10 LTE Z300CNL 16GB 4954113 도킹키보드 별매 ASUS 4954113 태블릿/휴대폰>태블릿>안드로이드OS";
		// str = "1000원";
		// str = "토크나이저    테스트";
		// str = "abc123d4ef한글g56";
		// str = "거너더러머버서어져쳐켜텨펴혀";
		try {
			reader = new StringReader(str);
			tokenizer = new ProductNameTokenizer(dictionary, false);
			// tokenizer = new TestTokenizer(dictionary);
			tokenizer.setReader(reader);
			TokenInfoAttribute tokenAttribute = tokenizer.addAttribute(TokenInfoAttribute.class);
			OffsetAttribute offsetAttribute = tokenizer.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttribute = tokenizer.addAttribute(TypeAttribute.class);
			tokenizer.reset();
			for (; tokenizer.incrementToken();) {
				logger.debug("TOKEN:{} / {}~{} / {} / {}", tokenAttribute.ref(), offsetAttribute.startOffset(),
					offsetAttribute.endOffset(), typeAttribute.type(), tokenAttribute.posTag());
			}
		} finally {
			try { reader.close(); } catch (Exception ignore) { }
			try { tokenizer.close(); } catch (Exception ignore) { }
		}
	}
}
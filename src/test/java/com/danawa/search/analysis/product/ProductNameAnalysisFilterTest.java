package com.danawa.search.analysis.product;

import static org.junit.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.CharVector;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.elasticsearch.common.logging.Loggers;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

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
		if (!propFile.exists()) { return; }

		Properties prop = TestUtil.readProperties(propFile);
		ProductNameDictionary dictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
		Reader reader = null;
		Tokenizer tokenizer = null;
		KoreanWordExtractor extractor = null;
		ProductNameAnalysisFilter tstream = null;
		AnalyzerOption option = null;
		String str = "TokenFilter레이스리퍼Test캐시백";
		str = "페이유에 균일가 7종/F20005W_F10011M_F20246W_247W_251W_FMS10레이스리퍼캐시백";
		str = "BC087 레드코코아 가방,패션잡화,아이템,남성,acc 서류가방 레드코코아닷컴 모던 비지니스백  서류가방 정장가방 BC087 5092852 레드코코아 5092852 패션잡화>가방>남성가방";
		str = "MSI MSI, 게임용노트북 500GB 디지털,노트북,pc,완제품 인텔|코어i5-4세대|하스웰|i5-4200M (2.5GHz)|39.62cm(15.6인치)|1920x1080|LED백라이트|4GB|DDR3|500GB|HD 4600|지포스 GT740M|2GB GDDR3|듀얼 그래픽|1Gbps 유선랜|802.11 b/g/n 무선랜|블루투스 4.0|HDMI|D-SUB|웹캠|USB 2.0|USB 3.0|멀티 리더기|숫자 키패드|블록 키보드|운영체제 미포함|2.4Kg CX61-i5 2OD Luna 2170277 뛰어난 성능과 저렴한 가격으로 인기 UP! MSI 2170277 디지털 완제품>노트북>MSI";
		str = "콜한 COLEHAAN   OG GRD KNT WNG TP    그레이 여성 옥스포드  WIDTH:B  CHSO8G702G2";
		str = "Flat_7072-1_1cm";
		str = "확인중 브랜드,레저,스포츠,자전거 전기자전거|바퀴$66cm(26인치)|기어$7단|프레임 재질$알루미늄|변속레버$시마노 썸 시프터|무게$27.5kg|최고속도$32km/h(24km/h 제한)|출력$350W|배터리$36V 10Ah 리튬이온 벡셀 퀀텀 클래식 4696201 확인중 4696201 스포츠/레저>자전거>자전거브랜드>기타브랜드";
		str = "N6WFL9005 세컨스킨 치렝스, 치마레깅스 패션,여성,하의,의류 스커트|무늬$무지|라인 형태$H라인|길이$기본(무릎) 뉴 나일론 밍크기모 치깅스 N6WFL9005 5029179 색상: 블랙 세컨스킨 5029179 의류>여성 브랜드의류>스커트 세컨스킨";
		str = "NT500R3W-KD2S-WIN8.1 삼성전자 노트북5 NEW SSD 128GB 디지털,노트북,pc,완제품 인텔|펜티엄|카비레이크|4415U (2.3GHz)|33.78cm(13.3인치)|1920x1080|LED백라이트|눈부심방지|광시야각|4GB|DDR4|SSD|128GB|HD 610|시스템메모리공유|802.11 n/ac 무선랜|블루투스 4.1|HDMI|웹캠|USB 2.0|USB 3.0|멀티 리더기|블록 키보드|33Wh|윈도우8.1|18.95mm|1.45Kg|구성변경상품 NT500R3W-KD2S WIN8.1 5091746 삼성 노트북5 5091746 디지털 완제품>노트북>삼성전자 오선미";
		str = "Nintendo 병행수입 게임,콘텐츠이용권 기타주변기기|닌텐도 스위치|병행상품 아미보 링크 -젤다의 전설- (amiibo Link) 젤다의 전설 시리즈 (NS/3DS/WiiU) 4960194 NS/3DS/WiiU 대응 상품입니다. 호환 소프트웨어 확인 후 구매하시기 바랍니다. Nintendo 4960194 게임>게임주변기기>콘텐츠이용권";
		// str = "아름다운이땅에 금수강산에 단군할아버지가터잡으시고홍익인간뜻으로나라세우니대대손손훌륭한인물도많아고구려세운동명왕백제온조왕알에서나온혁거세만주벌판달려라광개토대왕신라장군이사부백결선생떡방아      삼천궁녀의자왕황산벌의계백맞서싸운관창역사는흐른다말목자른김유신통일문무왕원효대사해골물혜초천축국바다의왕자장보고발해대조영귀주대첩강감찬서희거란족무단정치정중부화포최무선죽림칠현김부식지눌국사  조계종의천천태종대마도정벌이종무일편단심정몽주목화씨는문익점해동공자최충삼국유사일연역사는흐른다황금을보기를돌같이하라최영장군의말씀받들자황희정승맹사성과학장영실신숙주와한명회역사는안다십만양병이율곡주리이퇴계신사임당오죽헌잘싸운다곽재우조헌김시민나라구한이순신태정태세문단세사육신과생육신몸바쳐서논개행주치마권율역사는흐른다번쩍번쩍홍길동의적임꺽정대쪽같은삼학사어사박문수삼년공부한석봉단원풍속도방랑시인김삿갓 지도김정호영조대왕신문고정조규장각목민심서정약용녹두장군전봉준순교김대건서화가무황진이못살겠다홍경래삼일천하김옥균안중근은애국이완용은매국역사는흐른다 별헤는밤윤동주종두지석영삼십삼인손병희만세만세유관순도산안창호 어린이날방정환이수일과심순애장군의아들김두한날자꾸나이상황소그림중섭역사는흐른다";
		try {
			reader = new StringReader(str);
			tokenizer = new ProductNameTokenizer(dictionary);
			extractor = new KoreanWordExtractor(dictionary);
			option = new AnalyzerOption();
			option.setForDocument();
			option.useSynonym(true);
			option.useStopword(true);
			tstream = new ProductNameAnalysisFilter(tokenizer, extractor, dictionary, option);
			tokenizer.setReader(reader);
			tstream.testInit();
			tstream.reset();
			CharTermAttribute termAttr = tstream.addAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttr = tstream.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttr = tstream.addAttribute(TypeAttribute.class);
			while (tstream.incrementToken()) {
				logger.debug("TOKEN:{} / {}~{} / {}", termAttr, offsetAttr.startOffset(), offsetAttr.endOffset(), typeAttr.type());
			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { tokenizer.close(); } catch (Exception ignore) { }
		}
		assertTrue(true);
	}

	@Test public void testFilterWithoutDictionary() {
		if (TestUtil.launchForBuild()) { return; }

		Reader reader = null;
		Tokenizer tokenizer = null;
		ProductNameAnalysisFilter tstream = null;
		ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		KoreanWordExtractor extractor = null;
		AnalyzerOption option = null;
		String str = "";
		str = "abc12d-e34";
		str = "10cmx12cm";
		try {
			option = new AnalyzerOption();
			option.useSynonym(true);
			option.useStopword(true);
			reader = new StringReader(str);
			tokenizer = new ProductNameTokenizer(dictionary);
			extractor = new KoreanWordExtractor(dictionary);
			tokenizer.setReader(reader);
			tstream = new ProductNameAnalysisFilter(tokenizer, extractor, dictionary, option);
			tstream.testInit();
			tstream.reset();
			CharTermAttribute termAttr = tstream.addAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttr = tstream.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttr = tstream.addAttribute(TypeAttribute.class);
			SynonymAttribute synAttr = tstream.addAttribute(SynonymAttribute.class);
			AdditionalTermAttribute addAttr = tstream.addAttribute(AdditionalTermAttribute.class);
			while (tstream.incrementToken()) {
				logger.debug("TOKEN:{} / {}~{} / {} / [{}|{}]", termAttr, offsetAttr.startOffset(), offsetAttr.endOffset(), typeAttr.type(), synAttr, addAttr);

				if (synAttr != null && synAttr.getSynonyms() != null) {
					List<?> synonymObj = synAttr.getSynonyms();
					for(int inx3=0 ; inx3 < synonymObj.size(); inx3++) {
						Object obj = synonymObj.get(inx3);
						if(obj instanceof CharVector) {
							logger.debug(" |_synonym : {}", obj);
						} else if(obj instanceof List) {
							String extracted = "";
							@SuppressWarnings("unchecked")
							List<CharVector> synonyms = (List<CharVector>)obj;
							for(CharVector cv : synonyms) {
								extracted += cv+" ";
							}
							logger.debug(" |_synonym : {}", obj);
							logger.trace("EXTRACTED:{}", extracted);
						}
					}
				}
				if (addAttr != null) {
					Iterator<String> termIter = addAttr.iterateAdditionalTerms();
					for (; termIter.hasNext();) {
						String term = termIter.next();
						String type = typeAttr.type();
						logger.debug("a-term:{} / type:{}", term, type);
					}
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { tokenizer.close(); } catch (Exception ignore) { }
		}
		assertTrue(true);
	}

	@Test public void testSamplesPerformance() {
		if (TestUtil.launchForBuild()) { return; }

		TestUtil.setLogLevel(TestUtil.LOG_LEVEL_DEBUG, 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);

		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		if (!propFile.exists()) { return; }

		File textFile = TestUtil.getFileByProperty("SYSPROP_SAMPLE_TEXT_PATH");
		if (!textFile.exists()) { return; }

		Properties prop = TestUtil.readProperties(propFile);
		ProductNameDictionary dictionary = ProductNameTokenizerFactory.loadDictionary(null, prop);
		BufferedReader reader = null;
		Tokenizer tokenizer = null;
		KoreanWordExtractor extractor = null;
		AnalyzerOption option = null;
		ProductNameAnalysisFilter tstream = null;

		long nanoTime = 0;
		long amountTime = 0;
		int count = 0;

		try {
			reader = new BufferedReader(new FileReader(textFile));
			for (String rl; (rl = reader.readLine()) != null; count++) {
				logger.trace("TEST:{}", rl);
				tokenizer = new ProductNameTokenizer(dictionary);
				tokenizer.setReader(new StringReader(rl));
				extractor = new KoreanWordExtractor(dictionary);
				option = new AnalyzerOption();
				tstream = new ProductNameAnalysisFilter(tokenizer, extractor, dictionary, option);
				tstream.testInit();
				tstream.reset();
				CharTermAttribute termAttr = tstream.addAttribute(CharTermAttribute.class);
				OffsetAttribute offsetAttr = tstream.addAttribute(OffsetAttribute.class);
				TypeAttribute typeAttr = tstream.addAttribute(TypeAttribute.class);

				nanoTime = System.nanoTime();
				try {
					while (tstream.incrementToken()) {
						logger.trace("TOKEN:{} / {}~{} / {}", termAttr, offsetAttr.startOffset(), offsetAttr.endOffset(), typeAttr.type());
					}
				} catch (Exception e) {
					logger.debug("EXCEPTION ON {}", rl);
					logger.error("", e);
				}
				nanoTime = System.nanoTime() - nanoTime;
				amountTime += nanoTime;

				if (count > 0 && count % 10000 == 0) {
					logger.debug("{} TAKES {} milliseconds", count, ((int) Math.round(amountTime * 100.0 / 1000000.0)) / 100.0);
				}
				if (count > 100000) { break; }
			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { reader.close(); } catch (Exception ignore) { }
			try { tokenizer.close(); } catch (Exception ignore) { }
		}

		logger.debug("AMOUNT TIME:{}", amountTime);

		assertTrue(true);
	}

	@Test public void testRuleForDocument() throws Exception {
		if (TestUtil.launchForBuild()) { return; }

		TestUtil.setLogLevel(TestUtil.LOG_LEVEL_DEBUG, 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);

		String testFile = "./model_name_sample.txt";
		boolean isForQuery = false;
		testRule(testFile, isForQuery);
		assertTrue(true);
	}

	private void testRule(String testFile, boolean isForQuery) throws Exception {
		//단위명 동의어를 일단 꺼 놓기 위해..
		InputStream stream = null;
		BufferedReader reader = null;
		ProductNameAnalyzer analyzer = null;
		try {
			ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
			stream = getClass().getResourceAsStream(testFile);
			reader = new BufferedReader(new InputStreamReader(stream));
			for (String rline = ""; (rline = reader.readLine()) != null;) {
				if ("".equals(rline) || rline.startsWith("#")) {
					continue;
				}
				String[] testdata = rline.split("\t");
				Reader input = new StringReader(testdata[0]);
				AnalyzerOption option = new AnalyzerOption();
				if (isForQuery) {
					option.setForQuery();
				} else {
					option.setForDocument();
				}
				option.useStopword(true);
				option.useSynonym(true);
				analyzer = new ProductNameAnalyzer(dictionary, option);
				TokenStream tokenStream = analyzer.tokenStream("", input);
				TypeAttribute typeAttribute = tokenStream.addAttribute(TypeAttribute.class);
				CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
				SynonymAttribute synonymAttribute = tokenStream.addAttribute(SynonymAttribute.class);
				AdditionalTermAttribute additionalTermAttribute = tokenStream.addAttribute(AdditionalTermAttribute.class);
				
				logger.debug("--------------------------------------------------------------------------------");
				logger.debug("test for {}{}", "", testdata);
				logger.debug("--------------------------------------------------------------------------------");
				
				int inx = 0;
				tokenStream.reset();

				for (; tokenStream.incrementToken(); inx++) {
					assertTrue(inx + 1 < testdata.length);
					String[] data = testdata[inx + 1].split(" ");
					logger.debug("term:{}:{} / type:{}:{}", termAttribute, data[0], typeAttribute.type(), data[1]);
					
					assertTrue(termAttribute.toString().equals(data[0]));
					assertTrue(typeAttribute.type().toString().equals(data[1]));
					
					int inx2 = 0;
					
					if (synonymAttribute != null && synonymAttribute.getSynonyms() != null) {
						List<?> synonymObj = synonymAttribute.getSynonyms();
						for (int inx3 = 0; inx3 < synonymObj.size(); inx2++, inx3++) {
							Object obj = synonymObj.get(inx3);
							if (obj instanceof CharVector) {
								logger.debug(" |_synonym : {} / {}", obj, data);
								assertTrue(obj.toString().equals(data[2 + inx2]));
							} else if (obj instanceof List) {
								String extracted = "";
								@SuppressWarnings("unchecked")
								List<CharVector> synonyms = (List<CharVector>) obj;
								for (CharVector cv : synonyms) {
									extracted += cv + " ";
								}
								logger.debug(" |_synonym : {}", obj);
								logger.trace("EXTRACTED:{}", extracted);
								assertTrue(obj.toString().equals(data[2 + inx2]));
							}
						}
					}
					
					if (additionalTermAttribute != null) {
						Iterator<String> termIter = additionalTermAttribute.iterateAdditionalTerms();
						for (; termIter.hasNext(); inx2 += 2) {
							String term = termIter.next();
							String type = typeAttribute.type();
							
							try {
								// logger.debug("additional term:{}", term);
								assertTrue(inx + 1 < testdata.length);
								logger.debug("a-term:{}:{} / type:{}:{}", term, data.length > 2 + inx2 ? data[2 + inx2] : null, type, data.length > 3 + inx ? data[3 + inx2] : null);
								assertTrue(term.equals(data[2 + inx2]));
								assertTrue(type.equals(data[3 + inx2]));
							} catch (Exception ex) {
								logger.debug("exception occurs for term \"{}\" / {}", term, type);
								logger.error("", ex);
								throw new Exception(ex);
							}
						}
					}
				}
				
				assertTrue(inx == testdata.length - 1);
				if (tokenStream != null) try { tokenStream.close(); } catch (Exception e) { }
			}
		} finally {
			if (reader != null) try { reader.close(); } catch (Exception ignore) { }
			if (stream != null) try { stream.close(); } catch (Exception ignore) { }
			if (analyzer != null) try { analyzer.close(); } catch (Exception ignore) { }
		}
	}

	@Test public void testSamplesYml() {
		if (TestUtil.launchForBuild()) { return; }
		String filePath = "test_sample_filter.yaml";
		Yaml yaml = new Yaml();
		File file = null;
		InputStream istream = null;
		try {
			file = TestUtil.getFileByClass(ProductNameAnalysisFilterTest.class, filePath);
			istream = new FileInputStream(file);
			yaml.load(istream);
		} catch (Exception e) {
		} finally {
			try { istream.close(); } catch (Exception ignore) { }
		}

	}
}
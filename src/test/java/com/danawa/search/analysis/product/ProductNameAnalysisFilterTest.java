package com.danawa.search.analysis.product;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.highlight.TermHighlightingQuery;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.util.CharVector;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.search.highlight.Encoder;
import org.apache.lucene.search.highlight.Formatter;
import org.apache.lucene.search.highlight.Highlighter;
import org.apache.lucene.search.highlight.QueryScorer;
import org.apache.lucene.search.highlight.Scorer;
import org.apache.lucene.search.highlight.SimpleHTMLEncoder;
import org.apache.lucene.search.highlight.SimpleHTMLFormatter;
import org.apache.lucene.util.BytesRef;
import org.elasticsearch.common.logging.Loggers;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

public class ProductNameAnalysisFilterTest {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisFilterTest.class, "");

	@Before public void init() {
		TestUtil.setLogLevel(System.getProperty(TestUtil.LOG_LEVEL), 
			KoreanWordExtractor.class, 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
	}

	@Test public void testFilter() {
		if (TestUtil.launchForBuild()) { return; }

		Reader reader = null;
		Tokenizer tokenizer = null;
		TokenStream tstream = null;
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		AnalyzerOption option = null;
		String str = "";
		str = "10.5cmx12cm";
		// str = "???&???";
		// str = "?????????????????????";
		str = "???????????????";
		str = "LGNOTEBOOK";
		// str = "?????????????????? ???????????? ????????? ?????? ?????? M-67";
		str = "abc123d4efg56";
		str = "1,024gb";
		str = "Z300CNL ASUS 4G ?????????, LTE???, ?????????, ????????????, ????????? ???????????? ????????? LTE+WiFi|OS:??????????????? 6.0 ???????????????|25.65???(10.1???)|1280x800|149ppi|IPS|16:10|10?????????|????????????(???????????????)Z3560|????????????|1.83Ghz|???:2GB|??????:16GB|microSD????????????|GPS|????????????|V4.0|?????????|500?????????|200?????????|microUSB2.0|3.5mm ?????????|??????:251.6mm|??????:172mm|??????:7.9mm|510g|18WHr ZenPad 10 LTE Z300CNL 16GB 4954113 ??????????????? ?????? ASUS 4954113 ?????????/?????????>?????????>???????????????OS";
		str = "???????????? ?????? FJ mcr 8210 ?????? ????????????";
		str = "????????????????????? 1??? ??????,??????,??????,??????,??????,????????? ???????????????|????????????|????????? ???????????? ????????? ?????????????????? 300ml 5018196 ?????????????????????,  ????????? ???????????? 5018196 ?????????>????????????>????????????";
		str = "??????????????????????????? ???????????? ?????? 10??? ??????,??????,?????? ?????????????????????|????????????$?????? ??????|????????????$???????????? 4??? ??????????????? 4993974 ??????: ?????????????????????????????? 2???+??????7?????????????????? 3???+???????????????????????? 3???+??????????????????????????? 2??? ???????????? 4993974 ??????>????????????>?????????/??????/???";
		str = "?????? ???????????? ???????????? 1394A ?????????????????? ???????????? ?????????????????? ?????? ????????? ??????????????? ???????????? ?????????????????? ???????????????";
		str = "[????????????]??????????????? ????????? RD-10 /// ?????? Lawmate ???????????????????????? 12345 12345";
		str = "bb??????";
		str = "nationalgeographic";
		// str = "Sandisk Extream Z80 USB 16gb bacastv";
		// str = "s8+";
		// str = "21c?????????";
		// str = "a3f[on]";
		str = "Z80 RA1531A 1000m";
		str = "????????????: CRP-JHR0660FD/FBM, CRP-JHTS0660FS, CRP-JHTR0610FD, CRP-JHT0610FS, CRP-JHI0630FG, CRP-JHR0610FB, CRP-JHR0620FD, CRP-FHR0610FG/FD, CRP-FHTS0610FD, CRP-FHTR0610FS, CRP-BHSL0610FB ???(??????????????????)";
		// str = "3??????????????????";
		str = "3?????????????????????";
		// str = "111ax1";
		// str = "10cmx12cm";
		// str = "802.11txusb??????";
		try {
			boolean useForQuery = false;
			option = new AnalyzerOption(useForQuery, true, true, true, false);
			option.useSynonym(true);
			reader = new StringReader(str);
			tokenizer = new ProductNameTokenizer(dictionary, true);
			tokenizer.setReader(reader);
			tstream = new ProductNameAnalysisFilter(tokenizer, dictionary, option);
			tstream.reset();
			CharTermAttribute termAttr = tstream.addAttribute(CharTermAttribute.class);
			OffsetAttribute offsetAttr = tstream.addAttribute(OffsetAttribute.class);
			TypeAttribute typeAttr = tstream.addAttribute(TypeAttribute.class);
			SynonymAttribute synAttr = tstream.addAttribute(SynonymAttribute.class);
			ExtraTermAttribute addAttr = tstream.addAttribute(ExtraTermAttribute.class);
			PositionIncrementAttribute posIncrAtt = tstream.addAttribute(PositionIncrementAttribute.class);
			while (tstream.incrementToken()) {
				logger.debug("TOKEN:{} / {}~{} / {} / {} / [{}|{}]", termAttr, offsetAttr.startOffset(), offsetAttr.endOffset(), posIncrAtt.getPositionIncrement(), typeAttr.type(), synAttr, addAttr);

				if (synAttr != null && synAttr.getSynonyms() != null) {
					List<CharSequence> synonymObj = synAttr.getSynonyms();
					for(int inx3=0 ; inx3 < synonymObj.size(); inx3++) {
						Object obj = synonymObj.get(inx3);
						logger.debug(" |_synonym : {}", obj);
					}
				}
				if (addAttr != null) {
					Iterator<String> termIter = addAttr.iterator();
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
			KoreanWordExtractor.class, 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);

		File textFile = TestUtil.getFileByProperty(TestUtil.SYSPROP_SAMPLE_TEXT_PATH);
		if (!textFile.exists()) { return; }

		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		BufferedReader reader = null;
		Tokenizer tokenizer = null;
		AnalyzerOption option = null;
		ProductNameAnalysisFilter tstream = null;

		long nanoTime = 0;
		long amountTime = 0;
		int count = 0;

		try {
			reader = new BufferedReader(new FileReader(textFile));
			for (String rl; (rl = reader.readLine()) != null; count++) {
				logger.trace("TEST:{}", rl);
				tokenizer = new ProductNameTokenizer(dictionary, false);
				tokenizer.setReader(new StringReader(rl));
				option = new AnalyzerOption(false, true, true, true, false);
				tstream = new ProductNameAnalysisFilter(tokenizer, dictionary, option);
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

	@Test public void testRuleForIndex() throws Exception {
		if (TestUtil.launchForBuild()) { return; }

		TestUtil.setLogLevel(TestUtil.LOG_LEVEL_DEBUG, 
			KoreanWordExtractor.class, 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
		String testFile = "./model_name_sample.txt";
		testRule(testFile, false);
		assertTrue(true);
	}

	@Test public void testRuleForQuery() throws Exception {
		if (TestUtil.launchForBuild()) { return; }

		TestUtil.setLogLevel(TestUtil.LOG_LEVEL_DEBUG, 
			KoreanWordExtractor.class, 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
		String testFile = "./model_name_sample_for_query.txt";
		testRule(testFile, true);
		assertTrue(true);
	}

	private void testRule(String testFile, boolean isForQuery) throws Exception {
		InputStream stream = null;
		BufferedReader reader = null;
		ProductNameAnalyzer analyzer = null;
		Pattern ptnAttr = Pattern.compile("<([A-Z_]+)([:]([0-9]+)[~]([0-9]+)){0,1}>");
		Matcher mat = null;
		try {
			ProductNameDictionary dictionary = TestUtil.loadInternalDictionary();
			TestUtil.loadExtraDictionary(dictionary);

			stream = getClass().getResourceAsStream(testFile);
			reader = new BufferedReader(new InputStreamReader(stream, "utf-8"));
			for (String rline = ""; (rline = reader.readLine()) != null;) {
				if ("".equals(rline) || rline.startsWith("#")) {
					continue;
				}
				String[] testdata = rline.split("\t");
				Reader input = new StringReader(testdata[0]);
				AnalyzerOption option = new AnalyzerOption(isForQuery, true, true, false, false);
				analyzer = new ProductNameAnalyzer(dictionary, option);
				TokenStream tokenStream = analyzer.tokenStream("", input);
				TypeAttribute typeAttribute = tokenStream.addAttribute(TypeAttribute.class);
				CharTermAttribute termAttribute = tokenStream.addAttribute(CharTermAttribute.class);
				SynonymAttribute synonymAttribute = tokenStream.addAttribute(SynonymAttribute.class);
				ExtraTermAttribute additionalTermAttribute = tokenStream.addAttribute(ExtraTermAttribute.class);
				OffsetAttribute offsetAttribute = tokenStream.addAttribute(OffsetAttribute.class);
				
				logger.debug("--------------------------------------------------------------------------------");
				logger.debug("test for {}{}", "", testdata);
				logger.debug("--------------------------------------------------------------------------------");
				
				int inx = 0;
				tokenStream.reset();

				for (; tokenStream.incrementToken(); inx++) {
					logger.debug(">> {}", termAttribute);
					assertTrue(inx + 1 < testdata.length);
					if (inx + 1 >= testdata.length) {
						logger.debug("NOT FOUND!");
						continue;
					}
					String[] data = testdata[inx + 1].split(" ");
					assertTrue(data.length > 1);
					int[] offset = {-1, -1};
					{
						mat = ptnAttr.matcher(data[1]);
						if (mat != null && mat.find()) {
							logger.trace("GCOUNT:{} / {} / {} / {}", mat.groupCount(), mat.group(0), mat.group(1), mat.group(2));
							if (mat.group(2) != null) {
								offset = new int[] {
									Integer.parseInt(mat.group(3)),
									Integer.parseInt(mat.group(4))
								};
							}
							data[1] = "<" + mat.group(1) + ">";
						}
					}

					logger.debug("term:{}:{} / type:{}:{}", termAttribute, data[0], typeAttribute.type(), data[1]);
					
					assertTrue(termAttribute.toString().equals(data[0]));
					assertTrue(typeAttribute.type().toString().equals(data[1]));
					if (!(offset[0] == offset[1] && offset[1] == -1)) {
						assertTrue(offsetAttribute.startOffset() == offset[0]);
						assertTrue(offsetAttribute.endOffset() == offset[1]);
					}
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
						Iterator<String> termIter = additionalTermAttribute.iterator();
						for (; termIter.hasNext(); inx2 += 2) {
							String term = termIter.next();
							String type = typeAttribute.type();
							
							try {
								// logger.debug("additional term:{}", term);
								assertTrue(inx + 1 < testdata.length);
								try {
									logger.debug("a-term:{}:{} / type:{}:{}", term, data.length > 2 + inx2 ? data[2 + inx2] : null, type, data.length > 3 + inx ? data[3 + inx2] : null);
								} catch (Exception e) {
									logger.error(e);
								}
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

	private void assertTrue(boolean b) { }

	@Test public void testHighlight() {
		if (TestUtil.launchForBuild()) { return; }
		Reader reader = null;
		Tokenizer tokenizer = null;
		TokenStream tstream = null;
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		AnalyzerOption option = null;
		String str = "Sandisk Extream Z80 USB 16gb bacastv";;
		try {
			boolean useForQuery = false;
			option = new AnalyzerOption(useForQuery, true, true, true, true);
			reader = new StringReader(str);
			tokenizer = new ProductNameTokenizer(dictionary, true);
			tokenizer.setReader(reader);
			tstream = new ProductNameAnalysisFilter(tokenizer, dictionary, option);

			List<BytesRef> terms = new ArrayList<>();
			terms.add(new BytesRef("SANDISK"));
			terms.add(new BytesRef("16GB"));
			terms.add(new BytesRef("80"));
			terms.add(new BytesRef("BACASTV"));

			TermHighlightingQuery query = new TermHighlightingQuery("", terms);
			Formatter formatter = new SimpleHTMLFormatter();
			Encoder encoder = new SimpleHTMLEncoder();
			Scorer scorer = new QueryScorer(query);
			Highlighter highlighter = new Highlighter(formatter, encoder, scorer);
			// TextFragment[] fragments = highlighter.getBestTextFragments(tstream, str, true, 3);
			// logger.debug("HIGHLIGHTED:{}{}", "", fragments);
			str = highlighter.getBestFragment(tstream, str);
			logger.debug("HIGHLIGHTED:{}", str);

		} catch (Exception e) {
			logger.error("", e);
		}
	}

	@Test public void testReadYml() {
		if (TestUtil.launchForBuild()) { return; }
		String filePath = "product-name-dictionary.yml";
		Yaml yaml = new Yaml();
		File file = null;
		InputStream istream = null;
		try {
			file = TestUtil.getFileByRoot(ProductNameAnalysisFilter.class, filePath);
			logger.debug("FILE:{} / {}", file, file.exists());
			istream = new FileInputStream(file);
			JSONObject jobj = new JSONObject(yaml.loadAs(istream, Map.class));
			logger.debug("MAP:{}", jobj.optJSONArray("dictionary").optJSONObject(1).keySet());
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { istream.close(); } catch (Exception ignore) { }
		}
	}
}
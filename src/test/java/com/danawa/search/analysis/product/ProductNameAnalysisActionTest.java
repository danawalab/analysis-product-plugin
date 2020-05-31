package com.danawa.search.analysis.product;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.Reader;
import java.io.StringReader;
import java.util.Iterator;
import java.util.List;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.util.TestUtil;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;

public class ProductNameAnalysisActionTest {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisActionTest.class, "");

	@Before public void init() {
		TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"), 
			ProductNameTokenizer.class, 
			ProductNameParsingRule.class,
			ProductNameAnalysisFilter.class);
	}

	@Test public void queryBuildTest() {
		if (TestUtil.launchForBuild()) { return; }
		QueryBuilder ret = null;
		// ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		TokenStream stream = null;
		JSONArray analysis = new JSONArray();

		String[] fields = new String[] { "PRODUCTNAME" };
		String text = "";
		text = "테스트상품 ab12cd-12345";
		text = "집업WAS1836ER27";
		text = "10.5cm";

		stream = getAnalyzer(dictionary, text);
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		logger.debug("ANALYZE TEXT : {}", text);

		try {
			stream.reset();
			BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
			List<CharSequence> synonyms = null;
			while (stream.incrementToken()) {
				String termStr = String.valueOf(termAttr);
				logger.debug("TOKEN:{} / {}", termStr, typeAttr.type());
				JSONObject termDetail = new JSONObject();
				termDetail.put("term", termStr);
				QueryBuilder termQuery = QueryBuilders.multiMatchQuery(termStr, fields);

				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					JSONArray synonymList = new JSONArray();
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					query.should().add(termQuery);
					for (int sinx = 0; sinx < synonyms.size(); sinx++) {
						String synonym = String.valueOf(synonyms.get(sinx));
						synonymList.put(synonym);
						if (synonym.indexOf(" ") == -1) {
							query.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
						} else {
							BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
							for (String field : fields) {
								inQuery.should().add(QueryBuilders.matchPhraseQuery(field, synonym).slop(3));
							}
							query.should().add(inQuery);
						}
						logger.debug(" |_synonym : {}", synonym);
					}
					termDetail.put("synonym", synonymList);
					termQuery = query;
				}
				if (extAttr != null && extAttr.size() > 0) {
					JSONArray extraList = new JSONArray();
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					Iterator<String> termIter = extAttr.iterator();
					for (; termIter.hasNext();) {
						String term = termIter.next();
						String type = typeAttr.type();
						extraList.put(term);
						query.must().add(QueryBuilders.multiMatchQuery(String.valueOf(term), fields));
						logger.debug("a-term:{} / type:{}", term, type);
					}
					BoolQueryBuilder parent = QueryBuilders.boolQuery();
					parent.should().add(termQuery);
					parent.should().add(query);
					termDetail.put("extra", extraList);
					termQuery = parent;
				}
				if (analysis != null) {
					analysis.put(termDetail);
				}
				mainQuery.must().add(termQuery);
			}
			ret = mainQuery;
		} catch (Exception e) {
			logger.error("", e);
		}
		assertTrue(true);
		assertNotNull(ret);
	}

	public static TokenStream getAnalyzer(ProductNameDictionary dictionary, String str) {
		TokenStream tstream = null;
		Reader reader = null;
		Tokenizer tokenizer = null;
		KoreanWordExtractor extractor = null;
		AnalyzerOption option = null;

		extractor = new KoreanWordExtractor(dictionary);
		option = new AnalyzerOption();
		option.useForQuery(true);
		option.useSynonym(true);
		option.useStopword(true);
		reader = new StringReader(str);
		tokenizer = new ProductNameTokenizer(dictionary, false);
		extractor = new KoreanWordExtractor(dictionary);
		tokenizer.setReader(reader);
		tstream = new ProductNameAnalysisFilter(tokenizer, extractor, dictionary, option);
		return tstream;
	}
}
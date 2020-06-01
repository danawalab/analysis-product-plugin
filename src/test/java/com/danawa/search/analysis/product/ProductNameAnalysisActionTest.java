package com.danawa.search.analysis.product;

import static org.junit.Assert.*;

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
		String text = "";
		text = "테스트상품 ab12cd-12345";
		text = "집업WAS1836ER27";
		text = "10.5cm";
		text = "LGNOTEBOOK 판매";

		ProductNameDictionary dictionary = TestUtil.loadTestDictionary();
		// ProductNameDictionary dictionary = TestUtil.loadDictionary();
		TokenStream stream = null;
		JSONArray analysis = new JSONArray();

		String[] fields = new String[] { "PRODUCTNAME" };

		stream = getAnalyzer(dictionary, text);
		QueryBuilder query = buildQuery(stream, fields, analysis);
		logger.debug("Q:{}", query.toString());
		assertTrue(true);
	}

	public QueryBuilder buildQuery(TokenStream stream, String[] fields, JSONArray analysis) {
		QueryBuilder ret = null;

		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);

		try {
			stream.reset();
			BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
			ret = mainQuery;
			while (stream.incrementToken()) {
				String term = String.valueOf(termAttr);
				String type = typeAttr.type();
				logger.debug("TOKEN:{} / {}", term, typeAttr.type());

				JSONObject termDetail = new JSONObject();
				termDetail.put("term", term);
				QueryBuilder termQuery = QueryBuilders.multiMatchQuery(term, fields);

				List<CharSequence> synonyms = null;
				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					JSONArray subTerms = new JSONArray();
					BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
					subQuery.should().add(termQuery);
					for (int sinx = 0; sinx < synonyms.size(); sinx++) {
						String synonym = String.valueOf(synonyms.get(sinx));
						subTerms.put(synonym);
						if (synonym.indexOf(" ") == -1) {
							subQuery.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
						} else {
							BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
							for (String field : fields) {
								inQuery.should().add(QueryBuilders.matchPhraseQuery(field, synonym).slop(3));
							}
							subQuery.should().add(inQuery);
						}
						logger.debug(" |_synonym : {}", synonym);
					}
					termDetail.put("synonym", subTerms);
					termQuery = subQuery;
				}
				if (extAttr != null && extAttr.size() > 0) {
					JSONArray subTerms = new JSONArray();
					BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
					Iterator<String> termIter = extAttr.iterator();
					for (; termIter.hasNext();) {
						String exTerm = termIter.next();
						String exType = typeAttr.type();
						synonyms = synAttr.getSynonyms();
						if (synonyms == null || synonyms.size() == 0) {
							subQuery.must().add(QueryBuilders.multiMatchQuery(exTerm, fields));
							subTerms.put(exTerm);
						} else {
							JSONArray inTerms = new JSONArray();
							BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
							inQuery.should().add(QueryBuilders.multiMatchQuery(exTerm, fields));
							for (int sinx = 0; sinx < synonyms.size(); sinx++) {
								String synonym = String.valueOf(synonyms.get(sinx));
								inTerms.put(synonym);
								if (synonym.indexOf(" ") == -1) {
									inQuery.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
								} else {
									BoolQueryBuilder in2Query = QueryBuilders.boolQuery();
									for (String field : fields) {
										in2Query.should().add(QueryBuilders.matchPhraseQuery(field, synonym).slop(3));
									}
									inQuery.should().add(inQuery);
								}
								subTerms.put(inTerms);
							}
							subQuery.must().add(inQuery);
						}
						logger.debug("a-term:{} / type:{} / synonoym:{}", exTerm, exType, synonyms);
					}
					BoolQueryBuilder parent = QueryBuilders.boolQuery();
					parent.should().add(termQuery);
					parent.should().add(subQuery);
					termDetail.put("extra", subTerms);
					termQuery = parent;
				}
				if (analysis != null) {
					analysis.put(termDetail);
				}
				if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					query.should(termQuery);
					query.should(mainQuery);
					ret = query;
				} else {
					mainQuery.must().add(termQuery);
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}

		return ret;
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
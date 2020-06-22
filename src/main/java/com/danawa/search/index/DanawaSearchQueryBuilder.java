package com.danawa.search.index;

import java.util.Iterator;
import java.util.List;

import com.danawa.search.analysis.product.ProductNameTokenizer;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
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

public class DanawaSearchQueryBuilder {

	private static Logger logger = Loggers.getLogger(DanawaSearchQueryBuilder.class, "");

	public static final String AND = "AND";
	public static final String OR = "OR";

	public static QueryBuilder buildQuery(TokenStream stream, String[] fields, JSONObject analysis) {
		QueryBuilder ret = null;

		boolean doAnalysis = analysis != null;
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);

		try {
			stream.reset();
			BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
			ret = mainQuery;
			JSONObject mainAnalysis = null;
			if (doAnalysis) {
				mainAnalysis = new JSONObject();
				mainAnalysis.put(AND, new JSONArray());
				analysis.put(AND, mainAnalysis);
			}
			while (stream.incrementToken()) {
				String term = String.valueOf(termAttr);
				String type = typeAttr.type();
				logger.debug("TOKEN:{} / {}", term, typeAttr.type());

				JSONArray termAnalysis = null;
				if (doAnalysis) {
					termAnalysis = new JSONArray();
					termAnalysis.put(term);
				}
				QueryBuilder termQuery = QueryBuilders.multiMatchQuery(term, fields);

				List<CharSequence> synonyms = null;
				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					JSONObject subAnalysis = null;
					if (doAnalysis) {
						subAnalysis = new JSONObject();
						subAnalysis.put(OR, new JSONArray());
					}
					BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
					subQuery.should().add(termQuery);
					for (int sinx = 0; sinx < synonyms.size(); sinx++) {
						String synonym = String.valueOf(synonyms.get(sinx));
						if (doAnalysis) {
							subAnalysis.getJSONArray(OR).put(synonym);
						}
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
					if (doAnalysis) {
						termAnalysis.put(subAnalysis);
					}
					termQuery = subQuery;
				}
				if (extAttr != null && extAttr.size() > 0) {
					JSONObject subAnalysis = null;
					if (doAnalysis) {
						subAnalysis = new JSONObject();
						subAnalysis.put(AND, new JSONArray());
					}
					BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
					Iterator<String> termIter = extAttr.iterator();
					for (; termIter.hasNext();) {
						String exTerm = termIter.next();
						String exType = typeAttr.type();
						synonyms = synAttr.getSynonyms();
						if (synonyms == null || synonyms.size() == 0) {
							subQuery.must().add(QueryBuilders.multiMatchQuery(exTerm, fields));
							if (doAnalysis) {
								subAnalysis.getJSONArray(AND).put(exTerm);
							}
						} else {
							JSONObject inAnalysis = null;
							if (doAnalysis) {
								inAnalysis = new JSONObject();
								inAnalysis.put(OR, new JSONArray());
							}
							BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
							inQuery.should().add(QueryBuilders.multiMatchQuery(exTerm, fields));
							if (doAnalysis) {
								inAnalysis.getJSONArray(OR).put(exTerm);
							}
							for (int sinx = 0; sinx < synonyms.size(); sinx++) {
								String synonym = String.valueOf(synonyms.get(sinx));
								if (doAnalysis) {
									inAnalysis.getJSONArray(OR).put(synonym);
								}
								if (synonym.indexOf(" ") == -1) {
									inQuery.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
								} else {
									BoolQueryBuilder in2Query = QueryBuilders.boolQuery();
									for (String field : fields) {
										in2Query.should().add(QueryBuilders.matchPhraseQuery(field, synonym).slop(3));
									}
									inQuery.should().add(inQuery);
								}
							}
							if (doAnalysis) {
								subAnalysis.getJSONArray(AND).put(inAnalysis);
							}
							subQuery.must().add(inQuery);
						}
						logger.debug("a-term:{} / type:{} / synonoym:{}", exTerm, exType, synonyms);
					}
					BoolQueryBuilder parent = QueryBuilders.boolQuery();
					parent.should().add(termQuery);
					parent.should().add(subQuery);
					termQuery = parent;
					if (doAnalysis) {
						termAnalysis.put(subAnalysis);
					}
				}
				if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					{
						BoolQueryBuilder inQuery = QueryBuilders.boolQuery();
						for (String field : fields) {
							inQuery.should().add(QueryBuilders.matchPhraseQuery(field, term).slop(10));
						}
						termQuery = inQuery;
					}
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					query.should(termQuery);
					query.should(mainQuery);
					ret = query;
					if (doAnalysis) {
						JSONArray jarr = new JSONArray();
						jarr.put(termAnalysis);
						jarr.put(mainAnalysis);
						analysis.remove(AND);
						analysis.put(OR, jarr);
					}
				} else {
					mainQuery.must().add(termQuery);
					if (doAnalysis) {
						mainAnalysis.getJSONArray(AND).put(termAnalysis);
					}
				}
			}
		} catch (Exception e) {
			logger.error("", e);
		} finally {
			try { stream.close(); } catch (Exception ignore) { }
		}

		return ret;
	}
}
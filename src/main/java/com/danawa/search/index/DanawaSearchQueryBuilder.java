package com.danawa.search.index;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.danawa.search.analysis.product.ProductNameTokenizer;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.elasticsearch.common.io.stream.NamedWriteableRegistry;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.xcontent.LoggingDeprecationHandler;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.common.xcontent.XContentParser;
import org.elasticsearch.common.xcontent.json.JsonXContent;
import org.elasticsearch.index.query.AbstractQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.BoostingQueryBuilder;
import org.elasticsearch.index.query.MatchPhraseQueryBuilder;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.MultiMatchQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.query.QueryStringQueryBuilder;
import org.elasticsearch.index.query.RangeQueryBuilder;
import org.elasticsearch.index.query.SimpleQueryStringBuilder;
import org.elasticsearch.index.query.TermQueryBuilder;
import org.elasticsearch.index.query.TermsQueryBuilder;
import org.elasticsearch.index.query.TermsSetQueryBuilder;
import org.elasticsearch.index.query.WrapperQueryBuilder;
import org.elasticsearch.index.query.functionscore.FunctionScoreQueryBuilder;
import org.elasticsearch.plugins.SearchPlugin;
import org.json.JSONArray;
import org.json.JSONObject;

public class DanawaSearchQueryBuilder {

	private static Logger logger = Loggers.getLogger(DanawaSearchQueryBuilder.class, "");

	private static final DanawaSearchQueryBuilder INSTANCE = new DanawaSearchQueryBuilder();

	public static final String AND = "AND";
	public static final String OR = "OR";

	private final List<NamedWriteableRegistry.Entry> namedWriteables = new ArrayList<>();
	private final List<NamedXContentRegistry.Entry> namedXContents = new ArrayList<>();

	public DanawaSearchQueryBuilder() {
		init();
	}

	private void registerQueryBuilder(SearchPlugin.QuerySpec<?> spec) {
		namedWriteables.add(new NamedWriteableRegistry
			.Entry(QueryBuilder.class, spec.getName().getPreferredName(), spec.getReader()));
		namedXContents.add(new NamedXContentRegistry
			.Entry(QueryBuilder.class, spec.getName(), (p, c) -> spec.getParser().fromXContent(p)));
	}

	public void init() {
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(MatchQueryBuilder.NAME, MatchQueryBuilder::new,
			MatchQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(MultiMatchQueryBuilder.NAME, MultiMatchQueryBuilder::new,
			MultiMatchQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(MatchPhraseQueryBuilder.NAME, MatchPhraseQueryBuilder::new,
			MatchPhraseQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(QueryStringQueryBuilder.NAME, QueryStringQueryBuilder::new,
			QueryStringQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(BoostingQueryBuilder.NAME, BoostingQueryBuilder::new,
			BoostingQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(BoolQueryBuilder.NAME, BoolQueryBuilder::new,
			BoolQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(RangeQueryBuilder.NAME, RangeQueryBuilder::new,
			RangeQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(WrapperQueryBuilder.NAME, WrapperQueryBuilder::new,
			WrapperQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(FunctionScoreQueryBuilder.NAME, FunctionScoreQueryBuilder::new,
			FunctionScoreQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(SimpleQueryStringBuilder.NAME, SimpleQueryStringBuilder::new,
			SimpleQueryStringBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(TermsSetQueryBuilder.NAME, TermsSetQueryBuilder::new,
			TermsSetQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(TermQueryBuilder.NAME, TermQueryBuilder::new,
			TermQueryBuilder::fromXContent));
		registerQueryBuilder(new SearchPlugin.QuerySpec<>(TermsQueryBuilder.NAME, TermsQueryBuilder::new,
			TermsQueryBuilder::fromXContent));
	}

	/**
	 * 질의문자열을 ES 질의객체로 생성한다.
	 * @param source
	 * @return
	 */
	public static QueryBuilder parseQuery(String source) {
		QueryBuilder ret = null;
		try {
			NamedXContentRegistry registry = new NamedXContentRegistry(INSTANCE.namedXContents);
			XContentParser parser = JsonXContent.jsonXContent
				.createParser(registry, LoggingDeprecationHandler.INSTANCE, source);
			ret = AbstractQueryBuilder.parseInnerQueryBuilder(parser);
		} catch (Exception e) {
			logger.error("", e);
		}
		return ret;
	}

	/**
	 * 상품검색용 분석질의 객체 생성기, 분석기 속성 (동의어, 확장어 등) 을 고려한 질의객체를 생성한다.
	 */
	public static QueryBuilder buildQuery(TokenStream stream, String[] fields, JSONObject explain) {
		QueryBuilder ret = null;
		CharTermAttribute termAttr = stream.addAttribute(CharTermAttribute.class);
		TypeAttribute typeAttr = stream.addAttribute(TypeAttribute.class);
		SynonymAttribute synAttr = stream.addAttribute(SynonymAttribute.class);
		ExtraTermAttribute extAttr = stream.addAttribute(ExtraTermAttribute.class);
		try {
			stream.reset();
			BoolQueryBuilder mainQuery = QueryBuilders.boolQuery();
			ret = mainQuery;
			JSONObject mainExplain = null;
			if (explain != null) {
				mainExplain = new JSONObject();
				mainExplain.put(AND, new JSONArray());
				explain.put(AND, mainExplain);
			}
			while (stream.incrementToken()) {
				String term = String.valueOf(termAttr);
				String type = typeAttr.type();
				logger.trace("TOKEN:{} / {}", term, type);

				JSONArray termExplain = null;
				if (explain != null) {
					termExplain = new JSONArray();
					termExplain.put(term);
				}
				List<CharSequence> synonyms = null;
				QueryBuilder termQuery = QueryBuilders.multiMatchQuery(term, fields);

				if (synAttr != null && (synonyms = synAttr.getSynonyms()) != null && synonyms.size() > 0) {
					logger.trace("SYNONYM [{}] = {}", term, synonyms);
					termQuery = synonymQuery(termQuery, synonyms, fields, termAttr, typeAttr, synAttr, extAttr, termExplain);
				}
				if (extAttr != null && extAttr.size() > 0) {
					logger.trace("EXTRA [{}] = {}", term, extAttr);
					termQuery = extraQuery(termQuery, fields, termAttr, typeAttr, synAttr, extAttr, termExplain);
				}

				if (ProductNameTokenizer.FULL_STRING.equals(type)) {
					/**
					 * 전체질의어는 문장검색으로 질의
					 */
					termQuery = phraseQuery(fields, term, 10);
					BoolQueryBuilder query = QueryBuilders.boolQuery();
					query.should(termQuery);
					query.should(mainQuery);
					ret = query;
					if (explain != null) {
						JSONArray jarr = new JSONArray();
						jarr.put(term);
						jarr.put(mainExplain);
						explain.remove(AND);
						explain.put(OR, jarr);
					}
				} else {
					mainQuery.must().add(termQuery);
					if (explain != null) {
						if (termExplain.length() > 1) {
							JSONObject subExplain = new JSONObject();
							subExplain.put(OR, termExplain);
							mainExplain.optJSONArray(AND).put(subExplain);
						} else {
							mainExplain.optJSONArray(AND).put(term);
						}
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

	/**
	 * 문장검색 질의객체 생성
	 */
	public final static QueryBuilder phraseQuery(String[] fields, String phrase, int slop) {
		BoolQueryBuilder ret = QueryBuilders.boolQuery();
		for (String field : fields) {
			ret.should().add(QueryBuilders.matchPhraseQuery(field, phrase).slop(slop));
		}
		return ret;
	}

	/**
	 * 동의어 질의객체 생성
	 */
	public final static QueryBuilder synonymQuery(QueryBuilder query, List<CharSequence> synonyms, String[] fields, 
		CharTermAttribute termAttr, TypeAttribute typeAttr, SynonymAttribute synAttr, 
		ExtraTermAttribute extAttr, JSONArray explain) {
		QueryBuilder ret = query;
		BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
		subQuery.should().add(query);
		for (int sinx = 0; sinx < synonyms.size(); sinx++) {
			String synonym = String.valueOf(synonyms.get(sinx));
			if (explain != null) {
				explain.put(synonym);
			}
			if (synonym.indexOf(" ") == -1) {
				subQuery.should().add(QueryBuilders.multiMatchQuery(synonym, fields));
			} else {
				/**
				 * 공백이 포함된 단어는 문장검색으로 질의
				 */
				subQuery.should().add(phraseQuery(fields, synonym, 3));
			}
		}
		ret = subQuery;
		return ret;
	}

	/**
	 * 확장어 질의객체 생성
	 */
	public final static QueryBuilder extraQuery(QueryBuilder query, String[] fields, 
		CharTermAttribute termAttr, TypeAttribute typeAttr, SynonymAttribute synAttr, 
		ExtraTermAttribute extAttr, JSONArray explain) {
		List<CharSequence> synonyms = null;
		QueryBuilder ret = query;
		if (extAttr != null && extAttr.size() > 0) {
			JSONArray termExplain = null;
			if (explain != null) {
				termExplain = new JSONArray();
				explain.put(new JSONObject().put(AND, termExplain));
			}
			BoolQueryBuilder subQuery = QueryBuilders.boolQuery();
			Iterator<String> termIter = extAttr.iterator();
			for (; termIter.hasNext();) {
				String term = termIter.next();
				String type = typeAttr.type();
				synonyms = synAttr.getSynonyms();
				if (synonyms == null || synonyms.size() == 0) {
					subQuery.must().add(QueryBuilders.multiMatchQuery(term, fields));
					if (explain != null) {
						termExplain.put(term);
					}
				} else {
					/**
					 * 확장어의 동의어가 존재할때
					 */
					JSONArray subExplain = new JSONArray();
					subExplain.put(term);
					termExplain.put(new JSONObject().put(OR, subExplain));
					subQuery.must().add(synonymQuery(query, synonyms, fields, termAttr, typeAttr, synAttr, extAttr, subExplain));
				}
				logger.trace("a-term:{} / type:{} / synonoym:{}", term, type, synonyms);
			}
			BoolQueryBuilder parent = QueryBuilders.boolQuery();
			parent.should().add(query);
			parent.should().add(subQuery);
			ret = parent;
		}
		return ret;
	}

	/**
	 * AND 조건 불리언 질의객체 생성
	 */
	public static QueryBuilder andQuery(QueryBuilder... queries) {
		BoolQueryBuilder ret = QueryBuilders.boolQuery();
		for (QueryBuilder query : queries) {
			ret.must(query);
		}
		return ret;
	}

	/**
	 * OR 조건 불리언 질의객체 생성
	 */
	public static QueryBuilder orQuery(QueryBuilder... queries) {
		BoolQueryBuilder ret = QueryBuilders.boolQuery();
		for (QueryBuilder query : queries) {
			ret.should(query);
		}
		return ret;
	}
}
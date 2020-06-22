package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.ContextStore;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;

public class ProductNameAnalysisFilterFactory extends AbstractTokenFilterFactory {

	private static Logger logger = Loggers.getLogger(ProductNameAnalysisFilterFactory.class, "");

	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);

	private ProductNameDictionary dictionary;
	private AnalyzerOption option;

	public ProductNameAnalysisFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
		super(indexSettings, name, settings);
		boolean useSynonym = settings.getAsBoolean("use_synonym", true);
		boolean useStopword = settings.getAsBoolean("use_stopword", true);
		boolean useForQuery = settings.getAsBoolean("use_for_query", true);
		option = new AnalyzerOption();
		option.useSynonym(useSynonym);
		option.useStopword(useStopword);
		option.useForQuery(useForQuery);
	}

	@Override
	public TokenStream create(TokenStream tokenStream) {
		logger.trace("ProductNameAnalysisFilter::create {}", this);
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		}
		return new ProductNameAnalysisFilter(tokenStream, dictionary, option);
	}
}
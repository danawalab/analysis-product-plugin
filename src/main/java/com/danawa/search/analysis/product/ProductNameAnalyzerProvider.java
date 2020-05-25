package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.ContextStore;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.IndexSettings;

public class ProductNameAnalyzerProvider extends AbstractIndexAnalyzerProvider<Analyzer> {
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);
	private Analyzer analyzer;

	public ProductNameAnalyzerProvider(IndexSettings indexSettings, Environment env, String name, Settings settings) {
		super(indexSettings, name, settings);
		logger.trace("ProductNameAnalyzerProvider::self {}", this);

		boolean useSynonym = settings.getAsBoolean("use_synonym", true);
		boolean useStopword = settings.getAsBoolean("use_stopword", true);
		boolean useForQuery = settings.getAsBoolean("use_for_query", true);
		AnalyzerOption option = new AnalyzerOption();
		option.useSynonym(useSynonym);
		option.useStopword(useStopword);
		option.useForQuery(useForQuery);

		ProductNameDictionary dictionary;
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		} else {
			dictionary = ProductNameTokenizerFactory.loadDictionary(env);
			contextStore.put(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, dictionary);
		}
		analyzer = new ProductNameAnalyzer(dictionary, option);
	}

	@Override
	public Analyzer get() {
		logger.trace("ProductNameAnalyzerProvider::get {}", analyzer);
		return analyzer;
	}
}
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
		ProductNameDictionary dictionary;
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		} else {
			dictionary = ProductNameTokenizerFactory.loadDictionary(env);
			contextStore.put(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, dictionary);
		}
		analyzer = new ProductNameAnalyzer(dictionary);
	}

	@Override
	public Analyzer get() {
		logger.trace("ProductNameAnalyzerProvider::get {}", analyzer);
		return analyzer;
	}
}
package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.ContextStore;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.IndexSettings;

public class ProductNameAnalyzerProvider extends AbstractIndexAnalyzerProvider<ProductNameAnalyzer> {
	private ProductNameAnalyzer analyzer;

	public ProductNameAnalyzerProvider(IndexSettings indexSettings, Environment env, String name, Settings settings) {
		super(indexSettings, name, settings);
		logger.debug("ProductNameAnalyzerProvider::self {}", this);
		ContextStore store = AnalysisProductNamePlugin.getContextStore();
		ProductNameDictionary dictionary;
		if (store.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			dictionary = store.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		} else {
			dictionary = ProductNameTokenizerFactory.loadDictionary(env);
			store.put(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, dictionary);
		}
		analyzer = new ProductNameAnalyzer(dictionary);
	}

	@Override
	public ProductNameAnalyzer get() {
		logger.debug("ProductNameAnalyzerProvider::get {}", analyzer);
		return analyzer;
	}
}
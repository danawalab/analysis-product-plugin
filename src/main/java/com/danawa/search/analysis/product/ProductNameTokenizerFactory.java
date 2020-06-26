package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.ContextStore;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.analysis.AbstractTokenizerFactory;
import org.elasticsearch.index.IndexSettings;

public class ProductNameTokenizerFactory extends AbstractTokenizerFactory {

	protected static Logger logger = Loggers.getLogger(ProductNameTokenizerFactory.class, "");

	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);

	private ProductNameDictionary commonDictionary;
	private boolean exportTerm;

    public ProductNameTokenizerFactory(IndexSettings indexSettings, Environment env, String name, final Settings settings) {
		super(indexSettings, settings, name);
		logger.trace("ProductNameTokenizerFactory::self {}", this);

		exportTerm = settings.getAsBoolean("export_term", false);

		if (contextStore.containsKey(ProductNameDictionary.PRODUCT_NAME_DICTIONARY)) {
			commonDictionary = contextStore.getAs(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		} else {
			commonDictionary = ProductNameDictionary.loadDictionary(env);
			contextStore.put(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, commonDictionary);
		}
	}

	@Override
	public Tokenizer create() {
		logger.trace("ProductNameTokenizer::create {}", this);
		return new ProductNameTokenizer(commonDictionary, exportTerm);
	}
}
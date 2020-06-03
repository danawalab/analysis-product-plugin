package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.ContextStore;

import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenizerFactory;

public class TestTokenizerFactory extends AbstractTokenizerFactory {
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);

	private ProductNameDictionary dictionary;

	public TestTokenizerFactory (IndexSettings indexSettings, Environment env, String name, final Settings settings) {
		super(indexSettings, settings, name);
	}

	@Override public Tokenizer create() {
		if (contextStore.containsKey(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(AnalysisProductNamePlugin.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		}
		return new TestTokenizer(dictionary);
	}
}
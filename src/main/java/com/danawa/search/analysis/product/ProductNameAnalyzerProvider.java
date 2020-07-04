package com.danawa.search.analysis.product;

import java.io.Reader;
import java.io.StringReader;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.ContextStore;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;
import org.elasticsearch.index.IndexSettings;

public class ProductNameAnalyzerProvider extends AbstractIndexAnalyzerProvider<Analyzer> {
	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);
	private static ProductNameDictionary dictionary;
	private Analyzer analyzer;

	public ProductNameAnalyzerProvider(IndexSettings indexSettings, Environment env, String name, Settings settings) {
		super(indexSettings, name, settings);
		logger.trace("ProductNameAnalyzerProvider::self {}", this);
		if (contextStore.containsKey(ProductNameDictionary.PRODUCT_NAME_DICTIONARY)) {
			dictionary = contextStore.getAs(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		} else {
			dictionary = ProductNameDictionary.loadDictionary(env);
			contextStore.put(ProductNameDictionary.PRODUCT_NAME_DICTIONARY, dictionary);
		}
		analyzer = new ProductNameAnalyzer(dictionary);
	}

	@Override
	public Analyzer get() {
		logger.trace("ProductNameAnalyzerProvider::get {}", analyzer);
		return analyzer;
	}

	public static TokenStream getAnalyzer(String str, boolean useForQuery, boolean useSynonym, boolean useStopword, boolean useFullString, boolean toUppercase) {
		TokenStream tstream = null;
		Reader reader = null;
		Tokenizer tokenizer = null;
		AnalyzerOption option = null;
		option = new AnalyzerOption(useForQuery, useSynonym, useStopword, useFullString, toUppercase);
		reader = new StringReader(str);
		tokenizer = new ProductNameTokenizer(dictionary, false);
		tokenizer.setReader(reader);
		tstream = new ProductNameAnalysisFilter(tokenizer, dictionary, option);
		return tstream;
	}
}
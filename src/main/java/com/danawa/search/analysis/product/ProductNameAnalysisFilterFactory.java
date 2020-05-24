package com.danawa.search.analysis.product;

import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenFilterFactory;

public class ProductNameAnalysisFilterFactory extends AbstractTokenFilterFactory {

	public ProductNameAnalysisFilterFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
		super(indexSettings, name, settings);
	}

	@Override
	public TokenStream create(TokenStream tokenStream) {
		return null;
	}
}
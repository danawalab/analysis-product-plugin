package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;

public class ProductNameAnalyzer extends Analyzer {
	private ProductNameDictionary commonDictionary;
	private AnalyzerOption option;

	public ProductNameAnalyzer(ProductNameDictionary commonDictionary) {
		super();
		this.commonDictionary = commonDictionary;
	}

	public ProductNameAnalyzer(ProductNameDictionary commonDictionary, AnalyzerOption option) {
		super();
		this.commonDictionary = commonDictionary;
		this.option = option;
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer tokenizer = new ProductNameTokenizer(commonDictionary);
		TokenStream stream = tokenizer;
		KoreanWordExtractor extractor = new KoreanWordExtractor(commonDictionary);
		stream = new ProductNameAnalysisFilter(stream, extractor, commonDictionary, option);
		return new TokenStreamComponents(tokenizer, stream);
	}
}
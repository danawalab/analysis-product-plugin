package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;

public class ProductNameAnalyzer extends Analyzer {
	private ProductNameDictionary dictionary;
	private AnalyzerOption option;

	public ProductNameAnalyzer(ProductNameDictionary commonDictionary) {
		this(commonDictionary, null);
		option = new AnalyzerOption();
		option.useStopword(true);
		option.useSynonym(true);
	}

	public ProductNameAnalyzer(ProductNameDictionary commonDictionary, AnalyzerOption option) {
		super();
		this.dictionary = commonDictionary;
		this.option = option;
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		// Tokenizer tokenizer = new ProductNameTokenizer(dictionary, false);
		Tokenizer tokenizer = new TestTokenizer(dictionary);
		TokenStream stream = tokenizer;
		KoreanWordExtractor extractor = new KoreanWordExtractor(dictionary);
		stream = new ProductNameAnalysisFilter(stream, extractor, dictionary, option);
		return new TokenStreamComponents(tokenizer, stream);
	}
}
package com.danawa.search.analysis.product;

import java.io.Reader;
import java.io.StringReader;

import com.danawa.search.analysis.dict.ProductNameDictionary;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;

public class ProductNameAnalyzer extends Analyzer {
	private static ProductNameDictionary dictionary;
	private AnalyzerOption option;

	public ProductNameAnalyzer(ProductNameDictionary commonDictionary) {
		this(commonDictionary, null);
		option = new AnalyzerOption();
	}

	public ProductNameAnalyzer(ProductNameDictionary commonDictionary, AnalyzerOption option) {
		super();
		dictionary = commonDictionary;
		this.option = option;
	}

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer tokenizer = new ProductNameTokenizer(dictionary, false);
		TokenStream stream = tokenizer;
		stream = new ProductNameAnalysisFilter(stream, dictionary, option);
		return new TokenStreamComponents(tokenizer, stream);
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
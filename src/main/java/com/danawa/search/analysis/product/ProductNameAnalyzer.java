package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.TokenStream;

public class ProductNameAnalyzer extends Analyzer {
	private ProductNameDictionary commonDictionary;
	// private final Dictionary userDict;
	// private final KoreanTokenizer.DecompoundMode mode;
	// private final Set<POS.Tag> stopTags;
	// private final boolean outputUnknownUnigrams;
	// private final boolean discardPunctuation;

	public ProductNameAnalyzer(ProductNameDictionary commonDictionary) {
		super();
		this.commonDictionary = commonDictionary;
		//this(null, KoreanTokenizer.DEFAULT_DECOMPOUND, KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS, false, true);
	}

	// public ProductNameAnalyzer(Dictionary userDict, DecompoundMode mode, Set<POS.Tag> stopTags,
	// 		boolean outputUnknownUnigrams, boolean discardPunctuation) {
	// 	super();
	// 	this.userDict = userDict;
	// 	this.mode = mode;
	// 	// this.stopTags = stopTags;
	// 	this.outputUnknownUnigrams = outputUnknownUnigrams;
	// 	this.discardPunctuation = discardPunctuation;
	// }

	@Override
	protected TokenStreamComponents createComponents(String fieldName) {
		Tokenizer tokenizer = new ProductNameTokenizer(commonDictionary);
		TokenStream stream = tokenizer;
		KoreanWordExtractor extractor = new KoreanWordExtractor(commonDictionary);
		stream = new ProductNameAnalysisFilter(stream, extractor, commonDictionary);
		return new TokenStreamComponents(tokenizer, stream);
	}
}
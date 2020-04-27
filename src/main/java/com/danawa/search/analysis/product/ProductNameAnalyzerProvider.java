package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.CommonDictionary;
import com.danawa.search.analysis.dict.PreResult;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;

import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractIndexAnalyzerProvider;

public class ProductNameAnalyzerProvider extends AbstractIndexAnalyzerProvider<ProductNameAnalyzer> {
	private ProductNameAnalyzer analyzer;

	public ProductNameAnalyzerProvider(IndexSettings indexSettings, Environment env, String name, Settings settings) {
		super(indexSettings, name, settings);
		logger.debug("ProductNameAnalyzerProvider::self {}", this);
		CommonDictionary<TagProb, PreResult<CharSequence>> commonDict = ProductNameTokenizerFactory.getDictionary(env);
		// final KoreanTokenizer.DecompoundMode mode = ProductNameTokenizerFactory.getMode(settings);
		// final Dictionary userDictionary = ProductNameTokenizerFactory.getUserDictionary(env, settings);
		// final List<String> tagList = Analysis.getWordList(env, settings, "stoptags");
		// final Set<POS.Tag> stopTags = tagList != null ? resolvePOSList(tagList) : KoreanPartOfSpeechStopFilter.DEFAULT_STOP_TAGS;
		//analyzer = new ProductNameAnalyzer(userDictionary, mode, stopTags, false, false);
		analyzer = new ProductNameAnalyzer(commonDict);
	}

	@Override
	public ProductNameAnalyzer get() {
		logger.debug("ProductNameAnalyzerProvider::get {}", analyzer);
		return analyzer;
	}
}
package com.danawa.search.analysis.product;

import java.util.HashMap;
import java.util.Map;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;

import static java.util.Collections.singletonMap;

public class AnalysisProductNamePlugin extends Plugin implements AnalysisPlugin {
	@Override
	public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
		Map<String, AnalysisProvider<TokenFilterFactory>> extra = new HashMap<>();
		// extra.put("product-name_part_of_speech", ProductNamePartOfSpeechStopFilterFactory::new);
		// extra.put("product-name_readingform", ProductNameReadingFormFilterFactory::new);
		// extra.put("product-name_number", ProductNameFilterFactory::new);
		return extra;
	}

	@Override
	public Map<String, AnalysisProvider<TokenizerFactory>> getTokenizers() {
		return singletonMap("product-name_tokenizer", ProductNameTokenizerFactory::new);
	}

	@Override
	public Map<String, AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
		return singletonMap("product-name", ProductNameAnalyzerProvider::new);
	}
}
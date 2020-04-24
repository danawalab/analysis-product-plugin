package com.danawa.search.analysis.product;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.settings.ClusterSettings;
import org.elasticsearch.common.settings.IndexScopedSettings;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.settings.SettingsFilter;
import org.elasticsearch.index.analysis.AnalyzerProvider;
import org.elasticsearch.index.analysis.TokenFilterFactory;
import org.elasticsearch.index.analysis.TokenizerFactory;
import org.elasticsearch.indices.analysis.AnalysisModule.AnalysisProvider;
import org.elasticsearch.plugins.ActionPlugin;
import org.elasticsearch.plugins.AnalysisPlugin;
import org.elasticsearch.plugins.Plugin;
import org.elasticsearch.rest.RestController;
import org.elasticsearch.rest.RestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static java.util.Collections.singletonMap;
import static java.util.Collections.singletonList;

public class AnalysisProductNamePlugin extends Plugin implements AnalysisPlugin, ActionPlugin {

	private static Logger logger = LoggerFactory.getLogger(AnalysisProductNamePlugin.class);

	public AnalysisProductNamePlugin() {
		logger.trace("init");
	}

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

	@Override
	public List<RestHandler> getRestHandlers(Settings settings, RestController controller,
		ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings, SettingsFilter filter,
		IndexNameExpressionResolver resolver, Supplier<DiscoveryNodes> nodes) {
		return singletonList(new ProductNameAnalysisAction(settings, controller));
	}
}
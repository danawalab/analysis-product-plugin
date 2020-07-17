package com.danawa.search.analysis.product;

import java.util.function.Supplier;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.elasticsearch.cluster.metadata.IndexNameExpressionResolver;
import org.elasticsearch.cluster.node.DiscoveryNodes;
import org.elasticsearch.common.logging.Loggers;
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

import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;

import java.util.HashMap;

public class AnalysisProductNamePlugin extends Plugin implements AnalysisPlugin, ActionPlugin {

	private static Logger logger = Loggers.getLogger(AnalysisProductNamePlugin.class, "");

	public AnalysisProductNamePlugin() {
		logger.trace("init");
	}

	/**
	 * 필터 등록. 
	 * ES 에서 product_name 이름으로 상품명 필터를 생성하여 사용할수 있도록 등록.
	 */
	@Override public Map<String, AnalysisProvider<TokenFilterFactory>> getTokenFilters() {
		Map<String, AnalysisProvider<TokenFilterFactory>> extra = new HashMap<>();
		extra.put("product_name", ProductNameAnalysisFilterFactory::new);
		return extra;
	}

	/**
	 * 토크나이저 등록.
	 * ES 에서 product_name 이름으로 토크나이저를 생성하여 사용할수 있도록 토크나이저 등록.
	 */
	@Override public Map<String, AnalysisProvider<TokenizerFactory>> getTokenizers() {
		return singletonMap("product_name", ProductNameTokenizerFactory::new);
	}

	/**
	 * 분석기 등록. 
	 * ES 에서 product_name 이름으로 상품명분석기를 생성하여 사용할수 있도록 분석기를 등록>
	 */
	@Override public Map<String, AnalysisProvider<AnalyzerProvider<? extends Analyzer>>> getAnalyzers() {
		return singletonMap("product_name", ProductNameAnalyzerProvider::new);
	}

	/**
	 * 액션 핸들러 등록
	 * ES 에 상호작용이 가능한 REST 액션 핸들러를 등록한다.
	 */
	@Override public List<RestHandler> getRestHandlers(Settings settings, RestController controller,
		ClusterSettings clusterSettings, IndexScopedSettings indexScopedSettings, SettingsFilter filter,
		IndexNameExpressionResolver resolver, Supplier<DiscoveryNodes> nodes) {
		return singletonList(new ProductNameAnalysisAction(settings, controller));
	}
}
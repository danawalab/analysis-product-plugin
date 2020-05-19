package com.danawa.util;

import java.io.File;
import java.net.URL;
import java.util.Properties;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.TagProbDictionary;
import com.danawa.search.analysis.product.ProductNameAnalysisFilter;
import com.danawa.search.analysis.product.ProductNameTokenizerFactory;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.elasticsearch.common.logging.Loggers;

public final class TestUtil {

	private static Logger logger = Loggers.getLogger(TestUtil.class, "");
	static { logger.trace("init TestUtil.."); }

	public static final String SYSPROP_LAUNCH_FOR_BUILD = "SYSPROP_LAUNCH_FOR_BUILD";
	public static final String SYSPROP_TEST_DICTIONARY_SETTING = "SYSPROP_TEST_DICTIONARY_SETTING";
	public static final String LOG_LEVEL_INFO = Level.INFO.name();
	public static final String LOG_LEVEL_DEBUG = Level.DEBUG.name();
	public static final String LOG_LEVEL_TRACE = Level.TRACE.name();

	public static final String T = "true";
	public static final String F = "false";
	public static final String Y = "y";

	public static final String CLASS_SUFFIX = ".class";

	public static boolean launchForBuild() {
		return T.equals(getSystemProperty(SYSPROP_LAUNCH_FOR_BUILD, "true"));
	}

	public static final String getSystemProperty(String key) {
		return getSystemProperty(key, null);
	}

	public static final String getSystemProperty(String key, String def) {
		String ret = System.getProperty(key);
		if (ret == null) { ret = def; }
		return ret;
	}

	public static final File getFileByProperty(String key) {
		File ret = null;
		String path = getSystemProperty(key);
		if (path != null) { ret = new File(path); }
		if (ret != null && !ret.exists()) {ret = null; }
		return ret;
	}

	public static final File getFileByClass(Class<?> cls, String fileName) {
		File ret = null;
		try {
			String className = cls.getSimpleName();
			URL url = cls.getResource(className + CLASS_SUFFIX);
			ret = new File(url.getFile());
			ret = new File(ret.getParentFile(), fileName);
		} catch (Exception ignore) { }
		if (!ret.exists()) {ret = null; }
		return ret;
	}

	public static final File getFileByRoot(Class<?> cls, String path) {
		File ret = new File(ResourceResolver.getResourceRoot(cls), path);
		if (!ret.exists()) {ret = null; }
		return ret;
	}

	public static final Properties readProperties(File file) {
		return ResourceResolver.readProperties(file);
	}

	public static final void setLogLevel(String levelStr, Class<?>... classes) {
		Level level = Level.toLevel(levelStr, Level.DEBUG);
		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		Configuration config = ctx.getConfiguration();
		LoggerConfig loggerConfig = config.getLoggerConfig(LogManager.ROOT_LOGGER_NAME);
		loggerConfig.setLevel(level);
		for (Class<?> cls : classes) {
			Loggers.setLevel(Loggers.getLogger(cls, ""), level);
		}
		ctx.updateLoggers();
	}

	public static final ProductNameDictionary loadBaseOnlyDictionary() {
		ProductNameDictionary ret = null;
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		String key;
		try {
			Properties prop = TestUtil.readProperties(propFile);
			key = "analysis.product.dictionary.basePath";
			File baseFile = new File(String.valueOf(prop.get(key)));
			key = "analysis.product.dictionary.filePath.product";
			File dictFile = new File(baseFile, String.valueOf(prop.get(key)));
			TagProbDictionary baseDict = new TagProbDictionary(dictFile, true);
			ret = new ProductNameDictionary(baseDict);
			ret.addDictionary(ProductNameAnalysisFilter.DICT_USER, new SetDictionary());
		} catch (Exception e) {
			logger.debug("ERROR LOADING BASE DICTIONARY : {}", e.getMessage());
		}
		return ret;
	}

	public static final ProductNameDictionary loadDictionary() {
		ProductNameDictionary ret = null;
		File propFile = TestUtil.getFileByProperty("SYSPROP_TEST_DICTIONARY_SETTING");
		try {
			Properties prop = TestUtil.readProperties(propFile);
			ret = ProductNameTokenizerFactory.loadDictionary(null, prop);
		} catch (Exception e) {
			logger.debug("ERROR LOADING DICTIONARY : {}", e.getMessage());
		}
		return ret;
	}
}
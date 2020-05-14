package com.danawa.util;

import java.io.File;
import java.net.URL;
import java.util.Properties;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.elasticsearch.common.logging.Loggers;

public final class TestUtil {
	public static final String SYSPROP_LAUNCH_FOR_BUILD = "SYSPROP_LAUNCH_FOR_BUILD";

	public static final String T = "TRUE";
	public static final String F = "FALSE";
	public static final String Y = "Y";

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
}
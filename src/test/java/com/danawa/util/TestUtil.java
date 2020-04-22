package com.danawa.util;

import java.io.File;
import java.net.URL;

public final class TestUtil {
	public static final String SYSPROP_MASSIVE_TEST_ENABLED = "SYSPROP_MASSIVE_TEST_ENABLED";

	public static final String T = "TRUE";
	public static final String F = "FALSE";
	public static final String Y = "Y";

	public static final String CLASS_SUFFIX = ".class";

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
		return ret;
	}

	public static final File getFileByClass(Class<?> cls, String fileName) {
		File file = null;
		try {
			String className = cls.getSimpleName();
			URL url = cls.getResource(className + CLASS_SUFFIX);
			file = new File(url.getFile());
			file = new File(file.getParentFile(), fileName);
		} catch (Exception ignore) {
		}
		return file;
	}

	public static final File getFileByRoot(Class<?> cls, String path) {
		File ret = null;
		try {
			String className = cls.getSimpleName();
			URL url = cls.getResource(className + CLASS_SUFFIX);
			File file = new File(url.getFile());
			String[] split = cls.getPackageName().split("[.]");
			file = file.getParentFile();
			for (int inx = 0; inx < split.length; inx++) {
				file = file.getParentFile();
			}
			ret = new File(file, path);
		} catch (Exception ignore) { }
		return ret;
	}
}
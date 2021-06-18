package com.danawa.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.net.URL;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import com.danawa.search.analysis.dict.CompoundDictionary;
import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.dict.TagProbDictionary;
import com.danawa.search.analysis.korean.PosTagProbEntry.PosTag;
import com.danawa.search.analysis.korean.PosTagProbEntry.TagProb;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.elasticsearch.common.logging.Loggers;
import org.json.JSONObject;

public final class TestUtil {

	private static Logger logger = Loggers.getLogger(TestUtil.class, "");
	static { logger.trace("init TestUtil.."); }

	public static final String SYSPROP_LAUNCH_FOR_BUILD = "SYSPROP_LAUNCH_FOR_BUILD";
	public static final String SYSPROP_TEST_DICTIONARY_SETTING = "SYSPROP_TEST_DICTIONARY_SETTING";
	public static final String SYSPROP_TEST_DICTIONARY_LOAD_EXTRA = "SYSPROP_TEST_DICTIONARY_LOAD_EXTRA";
	public static final String SYSPROP_SAMPLE_TEXT_PATH = "SYSPROP_SAMPLE_TEXT_PATH";
	public static final String LOG_LEVEL = "LOG_LEVEL";
	public static final String LOG_LEVEL_INFO = Level.INFO.name();
	public static final String LOG_LEVEL_DEBUG = Level.DEBUG.name();
	public static final String LOG_LEVEL_TRACE = Level.TRACE.name();
	public static final String HIGH = "HIGH";
	public static final String NONE = "NONE";
	public static final String INTERNAL = "INTERNAL";
	public static final File RESOURCE_ROOT = new File("src/test/resources");

	public static final String T = "true";
	public static final String F = "false";
	public static final String Y = "y";

	public static final String CLASS_SUFFIX = ".class";

	public static boolean launchForBuild() {
		return T.equals(getSystemProperty(SYSPROP_LAUNCH_FOR_BUILD, "false"));
	}

	public static final String getSystemProperty(String key) {
		return getSystemProperty(key, null);
	}

	public static final String getSystemProperty(String key, String def) {
		String ret = System.getProperty(key);
		if (ret == null || "".equals(ret)) { ret = def; }
		return ret;
	}

	public static final File getFileByProperty(String key) {
		return getFileByProperty(key, null);
	}

	public static final File getFileByProperty(String key, String def) {
		File ret = null;
		String path = getSystemProperty(key);
		if (path != null) { ret = new File(path); }
		else if (def != null) {
			ret = new File(def);
		}
		return ret;
	}

	public static final File getFileByPath(String path, String def) {
		File ret = null;
		if (path != null) { ret = new File(path); }
		else if (def != null) {
			ret = new File(def);
		}
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
		return ret;
	}

	public static final File getFileByRoot(Class<?> cls, String path) {
		File ret = new File(ResourceResolver.getResourceRoot(cls), path);
		return ret;
	}

	public static final Properties readProperties(File file) {
		return ResourceResolver.readProperties(file);
	}

	public static final JSONObject readYmlConfig(File file) {
		return ResourceResolver.readYmlConfig(file);
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

	private static void loadTagProbSource(final TagProbDictionary dictionary, final File file) {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(file));
			for (String line; (line = reader.readLine()) != null;) {
				dictionary.addSourceEntry(line);
			}
		} catch (final Exception e) {
			logger.error("", e);
		} finally {
			try { reader.close(); } catch (final Exception ignore) { }
		}
	}

	private static void loadTagProbByFileName (TagProbDictionary dictionary, File file) {
		BufferedReader reader = null;
		try {
			Set<CharSequence> entrySet = new HashSet<>();
			String fileName = file.getName();
			String[] parts = fileName.split("\\.");
			String posName = parts[1];
			String probType = parts[2];
			PosTag posTag = null;
			try {
				posTag = PosTag.valueOf(posName);
			} catch (Exception e) {
				logger.error("Undefined pos tag = {}", posName);
				throw e;
			}
			double prob = TagProb.getProb(probType);

			reader = new BufferedReader(new FileReader(file));
			for (String line; (line = reader.readLine()) != null;) {
				if (line.startsWith("#") || line.startsWith("//") || line.length() == 0) { continue; }
				CharVector cv = new CharVector(line);
				if (dictionary.ignoreCase()) { cv.ignoreCase(); }
				entrySet.add(cv);
			}
			dictionary.appendPosTagEntry(entrySet, posTag, prob);
		} catch (final Exception e) {
			logger.error("", e);
		} finally {
			try { reader.close(); } catch (final Exception ignore) { }
		}
	}

	/**
	 * 컴파일사전에 단어를 직접 추가할수 없으므로, 텍스트 파일을 추가로딩하는 기능제공.
	 * */
	public static final ProductNameDictionary loadExtraDictionary(ProductNameDictionary dictionary) {
		File dictDir = new File(RESOURCE_ROOT, "dict_extra");
		logger.debug("Extra dict dir:{}", dictDir);

		/*사용자 사전*/
		SetDictionary userDict = new SetDictionary(true);
		userDict.loadSource(new File(dictDir, "user.txt"));
		dictionary.addDictionary(ProductNameDictionary.DICT_USER, userDict);
		dictionary.appendAdditionalNounEntry(userDict.set(), HIGH);
		/*유사어 사전*/
		SynonymDictionary synonymDict = new SynonymDictionary(true);
		synonymDict.loadSource(new File(dictDir, "synonym.txt"));
		dictionary.addDictionary(ProductNameDictionary.DICT_SYNONYM, synonymDict);
		dictionary.appendAdditionalNounEntry(synonymDict.getWordSet(), HIGH);
		/*단위명 사전*/
		SetDictionary unitDict = new SetDictionary(true);
		unitDict.loadSource(new File(dictDir, "unit.txt"));
		dictionary.addDictionary(ProductNameDictionary.DICT_UNIT, unitDict);
		/*단위명유사어 사전*/
		SynonymDictionary unitSynDict = new SynonymDictionary(true);
		unitSynDict.loadSource(new File(dictDir, "unit_synonym.txt"));
		dictionary.addDictionary(ProductNameDictionary.DICT_UNIT_SYNONYM, unitSynDict);
		/*분리어 사전*/
		SpaceDictionary spaceDict = new SpaceDictionary(true);
		spaceDict.loadSource(new File(dictDir, "space.txt"));
		dictionary.addDictionary(ProductNameDictionary.DICT_SPACE, spaceDict);
		/*복합명사 사전*/
		CompoundDictionary compDict = new CompoundDictionary(true);
		compDict.loadSource(new File(dictDir, "compound.txt"));
		dictionary.addDictionary(ProductNameDictionary.DICT_COMPOUND, compDict);
		return dictionary;
	}

	public static final ProductNameDictionary loadInternalDictionary() {
		ProductNameDictionary ret = null;
		try {
			File dictDir = new File(RESOURCE_ROOT, com.danawa.search.analysis.dict.TagProbDictionary
				.class.getPackage().getName().replaceAll("[.]", "/"));
			TagProbDictionary baseDict = new TagProbDictionary(true);
			loadTagProbSource(baseDict, new File(dictDir, "0.lnpr_morp.txt"));
			loadTagProbSource(baseDict, new File(dictDir, "words-prob.txt"));
			loadTagProbByFileName(baseDict, new File(dictDir, "01.N.P11.txt"));
			loadTagProbByFileName(baseDict, new File(dictDir, "02.N.MIN.txt"));
			loadTagProbByFileName(baseDict, new File(dictDir, "03.N.LOW.txt"));
			ret = new ProductNameDictionary(baseDict);
		} catch (final Exception e) {
			logger.debug("ERROR LOADING BASE DICTIONARY : {}", e.getMessage());
		}
		return ret;
	}

	public static final ProductNameDictionary loadDictionary() {
		ProductNameDictionary dictionary = null;
		String path = getSystemProperty(SYSPROP_TEST_DICTIONARY_SETTING);
		File propFile = null;
		int loadType = 0;
		/** 
		 * 테스트 실행시 사전 읽어오기
		 *   InteliJ 에서 실행시 :
		 *     Modify Run Configuration -> VM options 에 다음과 같이 추가한다
		 *     -DSYSPROP_TEST_DICTIONARY_SETTING=C:/{{플러그인경로}}/product-name-dictionary.yml
		 * 
		 *   Eclipse 에서 실행시 :
		 *     Run -> Run Configurations -> Arguments -> VM arguments 에 다음과 같이 추가한다.
		 *     -DSYSPROP_TEST_DICTIONARY_SETTING=C:/{{플러그인경로}}/product-name-dictionary.yml
		 *     
		 *   VSCode 에서 실행시 :
		 *     .vscode/settings.json 파일 에서 다음과 같이 추가한다.
		 *     {
		 *       "java.test.config": {
		 *         "vmargs": [
		 *           -DSYSPROP_TEST_DICTIONARY_SETTING=C:/{{플러그인경로}}/product-name-dictionary.yml
		 *         ]
		 *       }
		 *     }
		 * 
		 * 1. 지정하지 않은경우 : 내부적으로 컴파일된 사전(src/test/resources/product-name-dictionary.yml) 읽어옴
		 *   -DSYSPROP_TEST_DICTIONARY_SETTING
		 * 
		 * 2. 직접지정한 경우 : 외부 사전 읽어옴
		 *   -DSYSPROP_TEST_DICTIONARY_SETTING=C:/Temp/analysis-product/product-name-dictionary.yml
		 * 
		 * 3. INTERNAL 로 지정한 경우 : 텍스트베이스로 작성된 사전 읽어옴
		 *   -DSYSPROP_TEST_DICTIONARY_SETTING=INTERNAL
		 * 
		 **/
		if (INTERNAL.equals(path)) {
			/* 3. INTERNAL TEXT BASED DICTIONARY */
			loadType = 1;
		} else if (path != null && !"".equals(path)) {
			/* 2. EXTERNAL DICTIONARY */
			propFile = TestUtil.getFileByPath(path, null);
			loadType = 0;
		} else {
			/* 1. INTERNAL COMPILED BINARY */
			path = new File(RESOURCE_ROOT, "product-name-dictionary.yml").getAbsolutePath();
			propFile = TestUtil.getFileByPath(path, null);
			loadType = 0;
		}
		try {
			if (loadType == 0) {
				JSONObject prop = TestUtil.readYmlConfig(propFile);
				dictionary = ProductNameDictionary.loadDictionary(propFile.getParentFile(), prop);
			} else {
				dictionary = loadInternalDictionary();
			}
		} catch (Exception e) {
			logger.debug("ERROR LOADING DICTIONARY : {}", e.getMessage());
		}
		/**
		 * 테스트 사전에서 추가적인 단어 적재가 필요 없을경우 다음과 같이 지정
		 * -DSYSPROP_TEST_DICTIONARY_LOAD_EXTRA=NONE
		 **/
		if (!NONE.equals(getSystemProperty(SYSPROP_TEST_DICTIONARY_LOAD_EXTRA))) {
			TestUtil.loadExtraDictionary(dictionary);
		}
		return dictionary;
	}
}
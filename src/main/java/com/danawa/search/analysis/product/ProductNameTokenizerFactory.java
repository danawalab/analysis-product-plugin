package com.danawa.search.analysis.product;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import com.danawa.search.analysis.dict.CommonDictionary;
import com.danawa.search.analysis.dict.CompoundDictionary;
import com.danawa.search.analysis.dict.CustomDictionary;
import com.danawa.search.analysis.dict.Dictionary;
import com.danawa.search.analysis.dict.InvertMapDictionary;
import com.danawa.search.analysis.dict.MapDictionary;
import com.danawa.search.analysis.dict.PreResult;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SourceDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.dict.TagProbDictionary;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;
import com.danawa.util.ResourceResolver;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenizerFactory;

public class ProductNameTokenizerFactory extends AbstractTokenizerFactory {

    protected static Logger logger = Loggers.getLogger(ProductNameTokenizerFactory.class, "");

	public static enum TokenType {
		MAX, HIGH, MID, MIN
	}

	public static enum Type {
		SYSTEM, SET, MAP, SYNONYM, SYNONYM_2WAY, SPACE, CUSTOM, INVERT_MAP, COMPOUND
	}

	private static final String dictionaryPath = "dict/";
	private static final String dictionarySuffix = ".dict";

	public static final String USER_DICT_PATH_OPTION = "user_dictionary";
	public static final String USER_DICT_RULES_OPTION = "user_dictionary_rules";

	private static final String ANALYSIS_PROP = "product-name-dictionary.properties";
	private static final String ATTR_DICTIONARY_BASE_PATH = "analysis.product.dictionary.basePath";
	private static final String ATTR_DICTIONARY_ID_LIST = "analysis.product.dictionary.list";
	private static final String ATTR_DICTIONARY_TYPE = "analysis.product.dictionary.type";
	private static final String ATTR_DICTIONARY_TOKEN_TYPE = "analysis.product.dictionary.tokenType";
	private static final String ATTR_DICTIONARY_IGNORECASE = "analysis.product.dictionary.ignoreCase";
	private static final String ATTR_DICTIONARY_FILE_PATH = "analysis.product.dictionary.filePath";

	private static CommonDictionary<TagProb, PreResult<CharSequence>> commonDictionary;

    public ProductNameTokenizerFactory(IndexSettings indexSettings, Environment env, String name, final Settings settings) {
		super(indexSettings, settings, name);
		logger.info("ProductNameTokenizerFactory::self {}", this);
		getDictionary(env);
	}

	@Override
	public Tokenizer create() {
		logger.info("ProductNameTokenizer::create {}", this);
		return new ProductNameTokenizer(commonDictionary);
	}

	public static CommonDictionary<TagProb, PreResult<CharSequence>> getDictionary(Environment env) {
		if (commonDictionary == null) {
			commonDictionary = loadDictionary(env);
		}
		return commonDictionary;
	}

	private static File getDictionaryFile(File envBase, Properties prop, String dictionaryId) {
		File ret = null;
		// 속성에서 발견되면 속성내부 경로를 사용해 파일을 얻어오며, 그렇지 않은경우 지정된 경로에서 사전파일을 얻어온다
		File baseFile = null;
		try {
			// 베이스파일이 속성에 있으면 먼저 시도.
			String attr = prop.getProperty(ATTR_DICTIONARY_BASE_PATH).trim();
			if (attr != null && !"".equals(attr)) {
				baseFile = new File(attr);
			}
			if (baseFile == null || !baseFile.exists()) { baseFile = envBase; }
		} catch (Exception e) { 
			logger.debug("DICTIONARY EXCEPTION : {} / {}", baseFile, e.getMessage());
			baseFile = envBase;
		}

		String attribute = prop.getProperty(ATTR_DICTIONARY_FILE_PATH + "." + dictionaryId).trim();
		ret = new File(baseFile, attribute);
		if (attribute == null || !ret.exists()) {
			ret = new File(new File(envBase, dictionaryPath), dictionaryId + dictionarySuffix);
		}
		return ret;
	}

	private static Type getType(Properties prop, String dictionaryId) {
		Type ret = null;
		String attribute = prop.getProperty(ATTR_DICTIONARY_TYPE + "." + dictionaryId);
		if (attribute != null) {
			attribute = attribute.trim();
			for (Type type : Type.values()) {
				if (type.name().equalsIgnoreCase(attribute)) {
					ret = type;
					break;
				}
			}
		}
		return ret;
	}

	private static boolean getIgnoreCase(Properties prop, String dictionaryId) {
		boolean ret = false;
		String attribute = prop.getProperty(ATTR_DICTIONARY_IGNORECASE + "." + dictionaryId);
		if (attribute != null) {
			attribute = attribute.trim();
			if ("true".equalsIgnoreCase(attribute)) {
				ret = true;
			}
		}
		return ret;
	}

	private static String getTokenType(Properties prop, String dictionaryId) {
		String ret = TokenType.MIN.name();
		String attribute = prop.getProperty(ATTR_DICTIONARY_TOKEN_TYPE + "." + dictionaryId);
		if (attribute != null) {
			attribute = attribute.trim();
			for (TokenType tokenType : TokenType.values()) {
				if (tokenType.name().equalsIgnoreCase(attribute)) {
					ret = attribute;
					break;
				}
			}
		}
		return ret;
	}

	public static CommonDictionary<TagProb, PreResult<CharSequence>> loadDictionary(Environment env) {
		Properties dictProp = new Properties();
		Reader reader = null;
		File baseFile = null;
		File configFile = null;
		try {
			// 플러그인 디렉토리 에서 설정파일을 찾도록 한다.
			for (int tries = 0; tries < 2; tries++) {
				try {
					//baseFile = new File(ResourceResolver.getResourceRoot(CommonDictionary.class), ANALYSIS_PROP);
					if (tries == 0) {
						baseFile = ResourceResolver.getResourceRoot(CommonDictionary.class);
					} else {
						// 설정파일이 플러그인 디렉토리에 존재하지 않는다면 검색엔진 conf 디렉토리에서 설정파일을 찾는다.
						// 추후 불필요시 삭제한다. (설정파일 혼란이 있을수 있음)
						baseFile = env.configFile().toFile();
					}
					if (baseFile != null && baseFile.exists()) {
						logger.debug("PRODUCT DICTIONARY BASE : {}", baseFile);
					}
				} catch (Exception e) { 
					logger.error("", e);
					baseFile = null; 
					configFile = null;
					continue;
				}
				configFile = new File(baseFile, ANALYSIS_PROP);
				if (configFile.exists()) { 
					logger.debug("DICTIONARY PROPERTIES : {}", configFile);
					break;
				} else {
					baseFile = null; 
					configFile = null;
				}
			}
			reader = new FileReader(configFile);
			dictProp.load(reader);
		} catch (IOException e) {
			logger.error("DICTIONARY PROPERTIES FILE NOT FOUND", e);
		} finally {
			try { reader.close(); } catch (Exception ignore) { }
		}
		return loadDictionary(baseFile, dictProp);
	}

	public static CommonDictionary<TagProb, PreResult<CharSequence>> loadDictionary(File baseFile, Properties dictProp) {
		/**
		 * 기본셋팅. 
		 * ${ELASTICSEARCH}/config/product_name_analysis.prop 파일을 사용하도록 한다
		 * NORI 기분석 사전은 기본적으로(수정불가) 사용하되 사용자 사전을 활용하여
		 * 커스터마이징 하도록 한다.
		 * 우선은 JAXB 마샬링 구조를 사용하지 않고 Properties 를 사용하도록 한다.
		 **/
		Dictionary<TagProb, PreResult<CharSequence>> dictionary = null;
		CommonDictionary<TagProb, PreResult<CharSequence>> commonDictionary = null;
		List<String> idList = new ArrayList<>();
		String idStr = dictProp.getProperty(ATTR_DICTIONARY_ID_LIST);
		if (idStr != null) {
			for (String id : idStr.split("[,]")) {
				idList.add(id.trim());
			}
		}

		// 시스템사전을 먼저 읽어오도록 한다. 
		for (String dictionaryId : idList) {
			if (getType(dictProp, dictionaryId) == Type.SYSTEM) {
				dictionary = loadSystemDictionary(baseFile, dictProp, dictionaryId);
				commonDictionary = new CommonDictionary<TagProb, PreResult<CharSequence>>(dictionary);
				break;
			}
		}
		for (String dictionaryId : idList) {
			Type type = getType(dictProp, dictionaryId);
			String tokenType = getTokenType(dictProp, dictionaryId);
			File dictFile = getDictionaryFile(baseFile, dictProp, dictionaryId);
			boolean ignoreCase = getIgnoreCase(dictProp, dictionaryId);
			SourceDictionary<?> sourceDictionary = null;

			if (type == Type.SET) {
				SetDictionary setDictionary = new SetDictionary(dictFile, ignoreCase);
				if (tokenType != null) {
					commonDictionary.appendAdditionalNounEntry(setDictionary.set(), tokenType);
				}
				sourceDictionary = setDictionary;
			} else if (type == Type.MAP) {
				MapDictionary mapDictionary = new MapDictionary(dictFile, ignoreCase);
				if (tokenType != null) {
					commonDictionary.appendAdditionalNounEntry(mapDictionary.map().keySet(), tokenType);
				}
				sourceDictionary = mapDictionary;
			} else if (type == Type.SYNONYM || type == Type.SYNONYM_2WAY) {
				SynonymDictionary synonymDictionary = new SynonymDictionary(dictFile, ignoreCase);
				if (tokenType != null) {
					commonDictionary.appendAdditionalNounEntry(synonymDictionary.getWordSet(), tokenType);
				}
				sourceDictionary = synonymDictionary;
			} else if (type == Type.SPACE) {
				SpaceDictionary spaceDictionary = new SpaceDictionary(dictFile, ignoreCase);
				if (tokenType != null) {
					commonDictionary.appendAdditionalNounEntry(spaceDictionary.map().keySet(), tokenType);
				}
				sourceDictionary = spaceDictionary;
				// Map map = new HashMap<CharSequence, PreResult<CharSequence>>();
				// for(Entry<CharSequence, CharSequence[]> e : spaceDictionary.map().entrySet()){
				// 	PreResult preResult = new PreResult<T>();
				// 	preResult.setResult(e.getValue());
				// 	map.put(e.getKey(), preResult);
				// }
				// commonDictionary.setPreDictionary(map);
			} else if (type == Type.CUSTOM) {
				CustomDictionary customDictionary = new CustomDictionary(dictFile, ignoreCase);
				if (tokenType != null) {
					commonDictionary.appendAdditionalNounEntry(customDictionary.getWordSet(), tokenType);
				}
				sourceDictionary = customDictionary;
			} else if (type == Type.INVERT_MAP) {
				InvertMapDictionary invertMapDictionary = new InvertMapDictionary(dictFile, ignoreCase);
				if (tokenType != null) {
					commonDictionary.appendAdditionalNounEntry(invertMapDictionary.map().keySet(), tokenType);
				}
				sourceDictionary = invertMapDictionary;
			} else if (type == Type.COMPOUND) {
				CompoundDictionary compoundDictionary = new CompoundDictionary(dictFile, ignoreCase);
				if (tokenType != null) {
					commonDictionary.appendAdditionalNounEntry(compoundDictionary.map().keySet(), tokenType);
				}
				sourceDictionary = compoundDictionary;
			} else if (type == Type.SYSTEM) {
				// ignore
			} else {
				// logger.error();
				logger.error("Unknown Dictionary type > {}", type);
			}
			// logger.info("Dictionary {} is loaded. tokenType[{}] ", dictionaryId, tokenType);
			// add dictionary
			if (sourceDictionary != null) {
				commonDictionary.addDictionary(dictionaryId, sourceDictionary);
			}
		}
		return commonDictionary;
	}

	public void reloadDictionary(Environment env) {
		// long st = System.nanoTime();
		CommonDictionary<TagProb, PreResult<CharSequence>> newCommonDictionary = loadDictionary(env);

		// 1. commonDictionary에 systemdictinary셋팅.
		commonDictionary.reset(newCommonDictionary);
		// 2. dictionaryMap 에 셋팅.
		Map<String, SourceDictionary<?>> dictionaryMap = commonDictionary.getDictionaryMap();
		for (Entry<String, SourceDictionary<?>> entry : dictionaryMap.entrySet()) {
			String dictionaryId = entry.getKey();
			SourceDictionary<?> dictionary = entry.getValue();
			// dictionary 객체 자체는 유지하고, 내부 실데이터(map,set등)만 업데이트해준다.
			// 상속시 instanceof로는 정확한 클래스가 판별이 불가능하므로 isAssignableFrom 로 판별한다.
			if (dictionary.getClass().isAssignableFrom(SetDictionary.class)) {
				SetDictionary setDictionary = (SetDictionary) dictionary;
				SetDictionary newDictionary = newCommonDictionary.getDictionary(dictionaryId, SetDictionary.class);
				setDictionary.setSet(newDictionary.set());
			} else if (dictionary.getClass().isAssignableFrom(MapDictionary.class)) {
				MapDictionary mapDictionary = (MapDictionary) dictionary;
				MapDictionary newDictionary = newCommonDictionary.getDictionary(dictionaryId, MapDictionary.class);
				mapDictionary.setMap(newDictionary.map());
			} else if (dictionary.getClass().isAssignableFrom(SynonymDictionary.class)) {
				SynonymDictionary synonymDictionary = (SynonymDictionary) dictionary;
				SynonymDictionary newDictionary = newCommonDictionary.getDictionary(dictionaryId, SynonymDictionary.class);
				synonymDictionary.setMap(newDictionary.map());
				synonymDictionary.setWordSet(newDictionary.getWordSet());
			} else if (dictionary.getClass().isAssignableFrom(SpaceDictionary.class)) {
				SpaceDictionary spaceDictionary = (SpaceDictionary) dictionary;
				SpaceDictionary newDictionary = newCommonDictionary.getDictionary(dictionaryId, SpaceDictionary.class);
				spaceDictionary.setMap(newDictionary.map());
				spaceDictionary.setWordSet(newDictionary.getWordSet());
			} else if (dictionary.getClass().isAssignableFrom(CustomDictionary.class)) {
				CustomDictionary customDictionary = (CustomDictionary) dictionary;
				CustomDictionary newDictionary = newCommonDictionary.getDictionary(dictionaryId, CustomDictionary.class);
				customDictionary.setMap(newDictionary.map());
				customDictionary.setWordSet(newDictionary.getWordSet());
			} else if (dictionary.getClass().isAssignableFrom(CompoundDictionary.class)) {
				CompoundDictionary compoundDictionary = (CompoundDictionary) dictionary;
				CompoundDictionary newDictionary = newCommonDictionary.getDictionary(dictionaryId, CompoundDictionary.class);
				compoundDictionary.setMap(newDictionary.map());
			}
			logger.info("Dictionary {} is updated!", dictionaryId);

		}
		newCommonDictionary = null;
	}

	protected static Dictionary<TagProb, PreResult<CharSequence>> loadSystemDictionary(File baseFile, Properties prop, String dictionaryId) {
		File systemDictFile = getDictionaryFile(baseFile, prop, dictionaryId);
		long st = System.nanoTime();
		boolean ignoreCase = false;
		TagProbDictionary tagProbDictionary = new TagProbDictionary(systemDictFile, ignoreCase);
		logger.debug("Product Dictionary Load {}ms >> {}", (System.nanoTime() - st) / 1000000,
			systemDictFile.getName());
		return tagProbDictionary;
	}
}
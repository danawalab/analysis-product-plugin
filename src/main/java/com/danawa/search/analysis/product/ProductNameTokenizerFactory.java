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

import org.apache.lucene.analysis.Tokenizer;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.env.Environment;
import org.elasticsearch.index.IndexSettings;
import org.elasticsearch.index.analysis.AbstractTokenizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductNameTokenizerFactory extends AbstractTokenizerFactory {

	private static Logger logger = LoggerFactory.getLogger(ProductNameTokenizerFactory.class);

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

	private static final String ANALYSIS_PROP = "product_name_analysis.prop";
	private static final String ATTR_DICTIONARY_ID_LIST = "analysis.product.dictionary.list";
	private static final String ATTR_DICTIONARY_TYPE = "analysis.product.dictionary.type";
	private static final String ATTR_DICTIONARY_TOKEN_TYPE = "analysis.product.dictionary.tokenType";
	private static final String ATTR_DICTIONARY_FILE_PATH = "analysis.product.dictionary.filePath";

	// // private final Dictionary userDictionary;
	// // private final KoreanTokenizer.DecompoundMode decompoundMode;
	// private final boolean discardPunctuation;
	CommonDictionary<TagProb, PreResult<CharSequence>> commonDictionary = null;

	public ProductNameTokenizerFactory(IndexSettings indexSettings, Settings settings, String name) {
		super(indexSettings, settings, name);
	}

	// public ProductNameTokenizerFactory(IndexSettings indexSettings, Environment env, String name, Settings settings) {
	//     super(indexSettings, settings, name);
	//     // decompoundMode = getMode(settings);
	//     // userDictionary = getUserDictionary(env, settings);
	//     // discardPunctuation = settings.getAsBoolean("discard_punctuation", true);
	// }

	// // public static Dictionary getUserDictionary(Environment env, Settings settings) {
	// //     if (settings.get(USER_DICT_PATH_OPTION) != null && settings.get(USER_DICT_RULES_OPTION) != null) {
	// //         throw new IllegalArgumentException("It is not allowed to use [" + USER_DICT_PATH_OPTION + "] in conjunction"
	// //                 + " with [" + USER_DICT_RULES_OPTION + "]");
	// //     }
	// //     List<String> ruleList = Analysis.getWordList(env, settings, USER_DICT_PATH_OPTION, USER_DICT_RULES_OPTION,
	// //             true);
	// //     StringBuilder sb = new StringBuilder();
	// //     if (ruleList == null || ruleList.isEmpty()) {
	// //         return null;
	// //     }
	// //     for (String line : ruleList) {
	// //         sb.append(line).append(System.lineSeparator());
	// //     }
	// //     try (Reader rulesReader = new StringReader(sb.toString())) {
	// //         return UserDictionary.open(rulesReader);
	// //     } catch (IOException e) {
	// //         throw new ElasticsearchException("failed to load product-name user dictionary", e);
	// //     }
	// // }

	// // public static KoreanTokenizer.DecompoundMode getMode(Settings settings) {
	// //     KoreanTokenizer.DecompoundMode mode = KoreanTokenizer.DEFAULT_DECOMPOUND;
	// //     String modeSetting = settings.get("decompound_mode", null);
	// //     if (modeSetting != null) {
	// //         mode = KoreanTokenizer.DecompoundMode.valueOf(modeSetting.toUpperCase(Locale.ENGLISH));
	// //     }
	// //     return mode;
	// // }

	@Override
	public Tokenizer create() {
		return new ProductNameTokenizer();
	}

	private static File getDictionaryFile(Properties prop, Environment env, String dictionaryId) {
		File ret = null;
		// 속성에서 발견되면 속성내부 경로를 사용해 파일을 얻어오며, 그렇지 않은경우 지정된 경로에서 사전파일을 얻어온다
		String attribute = prop.getProperty(ATTR_DICTIONARY_FILE_PATH + "." + dictionaryId).trim();
		ret = new File(attribute);
		if (attribute == null || !ret.exists()) {
			ret = new File(new File(env.configFile().toFile(), dictionaryPath), dictionaryId + dictionarySuffix);
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
		try {
			reader = new FileReader(new File(env.configFile().toFile(), ANALYSIS_PROP));
			dictProp.load(reader);
		} catch (IOException e) {
		} finally {
			try {
				reader.close();
			} catch (Exception ignore) {
			}
		}
		return loadDictionary(env, dictProp);
	}

	public static CommonDictionary<TagProb, PreResult<CharSequence>> loadDictionary(Environment env, Properties dictProp) {
		/**
		 * 기본셋팅. 
		 * ${ELASTICSEARCH}/config/product_name_analysis.prop 파일을 사용하도록 한다
		 * NORI 기분석 사전은 기본적으로(수정불가) 사용하되 사용자 사전을 활용하여
		 * 커스터마이징 하도록 한다.
		 * 우선은 JAXB 마샬링 구조를 사용하지 않고 Properties 를 사용하도록 한다.
		 **/
		// TagProbDictionary dictionary = new TagProbDictionary(builder.map(), true);
		// dictionary.appendAdditionalNounEntry(userDictionary.getUnmodifiableSet(), "HIGH");
		// dictionary.appendAdditionalNounEntry(stopDictionary.getUnmodifiableSet(), "HIGH");
		// dictionary.appendAdditionalNounEntry(synonymDictionary.getWordSet(), "HIGH");

		Dictionary<TagProb, PreResult<CharSequence>> dictionary = null;
		CommonDictionary<TagProb, PreResult<CharSequence>> commonDictionary = null;
		List<String> idList = new ArrayList<>();

		String idStr = dictProp.getProperty(ATTR_DICTIONARY_ID_LIST);
		if (idStr != null) {
			for (String id : idStr.split("[,]")) {
				idList.add(id.trim());
			}
		}

		// dictionary = new SystemDictionary();
		commonDictionary = new CommonDictionary<TagProb, PreResult<CharSequence>>(dictionary);

		for (String dictionaryId : idList) {
			Type type = getType(dictProp, dictionaryId);
			String tokenType = getTokenType(dictProp, dictionaryId);
			File dictFile = getDictionaryFile(dictProp, env, dictionaryId);
			SourceDictionary<?> sourceDictionary = null;
			boolean ignoreCase = false;// dictionarySetting.ignoreCase();

			if (type == Type.SYSTEM) {
				dictionary = loadSystemDictionary(dictProp, env, dictionaryId);
				commonDictionary = new CommonDictionary<TagProb, PreResult<CharSequence>>(dictionary);
			} else if (type == Type.SET) {
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
				logger.error("Unknown Dictionary type > {}", type);
			}
			logger.info("Dictionary {} is loaded. tokenType[{}] ", dictionaryId, tokenType);
			// add dictionary
			if (sourceDictionary != null) {
				commonDictionary.addDictionary(dictionaryId, sourceDictionary);
			}
		}
		return commonDictionary;
	}

	public void reloadDictionary(Environment env, Properties dictProp) {
		// long st = System.nanoTime();
		CommonDictionary<TagProb, PreResult<CharSequence>> newCommonDictionary = loadDictionary(env, dictProp);

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
				SetDictionary newDictionary = (SetDictionary) newCommonDictionary.getDictionary(dictionaryId);
				setDictionary.setSet(newDictionary.set());
			} else if (dictionary.getClass().isAssignableFrom(MapDictionary.class)) {
				MapDictionary mapDictionary = (MapDictionary) dictionary;
				MapDictionary newDictionary = (MapDictionary) newCommonDictionary.getDictionary(dictionaryId);
				mapDictionary.setMap(newDictionary.map());
			} else if (dictionary.getClass().isAssignableFrom(SynonymDictionary.class)) {
				SynonymDictionary synonymDictionary = (SynonymDictionary) dictionary;
				SynonymDictionary newDictionary = (SynonymDictionary) newCommonDictionary.getDictionary(dictionaryId);
				synonymDictionary.setMap(newDictionary.map());
				synonymDictionary.setWordSet(newDictionary.getWordSet());
			} else if (dictionary.getClass().isAssignableFrom(SpaceDictionary.class)) {
				SpaceDictionary spaceDictionary = (SpaceDictionary) dictionary;
				SpaceDictionary newDictionary = (SpaceDictionary) newCommonDictionary.getDictionary(dictionaryId);
				spaceDictionary.setMap(newDictionary.map());
				spaceDictionary.setWordSet(newDictionary.getWordSet());
			} else if (dictionary.getClass().isAssignableFrom(CustomDictionary.class)) {
				CustomDictionary customDictionary = (CustomDictionary) dictionary;
				CustomDictionary newDictionary = (CustomDictionary) newCommonDictionary.getDictionary(dictionaryId);
				customDictionary.setMap(newDictionary.map());
				customDictionary.setWordSet(newDictionary.getWordSet());
			} else if (dictionary.getClass().isAssignableFrom(CompoundDictionary.class)) {
				CompoundDictionary compoundDictionary = (CompoundDictionary) dictionary;
				CompoundDictionary newDictionary = (CompoundDictionary) newCommonDictionary.getDictionary(dictionaryId);
				compoundDictionary.setMap(newDictionary.map());
			}
			logger.info("Dictionary {} is updated!", dictionaryId);

		}
		newCommonDictionary = null;
		// logger.debug("{} Dictionary Reload Done. {}ms", pluginId, (System.nanoTime()
		// - st) / 1000000);
	}

	protected static Dictionary<TagProb, PreResult<CharSequence>> loadSystemDictionary(Properties prop, Environment env, String dictionaryId) {
		File systemDictFile = getDictionaryFile(prop, env, dictionaryId);
		long st = System.nanoTime();
		boolean ignoreCase = false;
		TagProbDictionary tagProbDictionary = new TagProbDictionary(systemDictFile, ignoreCase);
		logger.debug("Product Dictionary Load {}ms >> {}", (System.nanoTime() - st) / 1000000,
				systemDictFile.getName());
		return tagProbDictionary;
	}
}
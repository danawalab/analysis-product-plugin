package com.danawa.search.analysis.dict;

import com.danawa.search.analysis.korean.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.product.AnalysisProductNamePlugin;
import com.danawa.util.ContextStore;
import com.danawa.util.ResourceResolver;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.SpecialPermission;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.env.Environment;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.Map.Entry;

import com.danawa.search.analysis.korean.PreResult;

public class ProductNameDictionary extends CommonDictionary<TagProb, PreResult<CharSequence>> {

	protected static Logger logger = Loggers.getLogger(ProductNameDictionary.class, "");

	public static enum TokenType {
		MAX, HIGH, MID, MIN
	}

	public static enum Type {
		SYSTEM, SET, MAP, SYNONYM, SYNONYM_2WAY, SPACE, CUSTOM, INVERT_MAP, COMPOUND
	}

	public static final String PRODUCT_NAME_DICTIONARY = "PRODUCT_NAME_DICTIONARY";

	public static final String DICT_UNIT_SYNONYM = "unit_synonym";
	public static final String DICT_UNIT = "unit";
	public static final String DICT_SPACE = "space";
	public static final String DICT_SYNONYM = "synonym";
	public static final String DICT_STOP = "stop";
	public static final String DICT_USER = "user";
	public static final String DICT_COMPOUND= "compound";
	public static final String DICT_MAKER = "maker";
	public static final String DICT_BRAND = "brand";
	public static final String DICT_CATEGORY = "category";

	private static final String dictionaryPath = "dict/";
	private static final String dictionarySuffix = ".dict";

	public static final String USER_DICT_PATH_OPTION = "user_dictionary";
	public static final String USER_DICT_RULES_OPTION = "user_dictionary_rules";

	private static final String ANALYSIS_PROP = "product-name-dictionary.yml";
	private static final String ATTR_DICTIONARY_BASE_PATH = "basePath";
	private static final String ATTR_DICTIONARY_LIST = "dictionary";
	private static final String ATTR_DICTIONARY_NAME = "name";
	private static final String ATTR_DICTIONARY_TYPE = "type";
	private static final String ATTR_DICTIONARY_TOKEN_TYPE = "tokenType";
	private static final String ATTR_DICTIONARY_IGNORECASE = "ignoreCase";
	private static final String ATTR_DICTIONARY_FILE_PATH = "filePath";

	public static final String TAB = "\t";

	private static final ContextStore contextStore = ContextStore.getStore(AnalysisProductNamePlugin.class);

	private static File baseFile;
	private static File configFile;

	public ProductNameDictionary(Dictionary<TagProb, PreResult<CharSequence>> systemDictionary) {
		super(systemDictionary);
	}

	private static File getDictionaryFile(File envBase, JSONObject prop, String basePath) {
		File ret = null;
		// 속성에서 발견되면 속성내부 경로를 사용해 파일을 얻어오며, 그렇지 않은경우 지정된 경로에서 사전파일을 얻어온다
		File baseFile = null;
		try {
			// 베이스파일이 속성에 있으면 먼저 시도.
			if (basePath != null && !"".equals(basePath)) {
				baseFile = new File(basePath);
			}
			if (baseFile == null || !baseFile.exists()) { baseFile = envBase; }
		} catch (Exception e) { 
			logger.debug("DICTIONARY EXCEPTION : {} / {}", baseFile, e.getMessage());
			baseFile = envBase;
		}
		String dictionaryId = prop.optString(ATTR_DICTIONARY_NAME, "").trim();
		String path = prop.optString(ATTR_DICTIONARY_FILE_PATH, "").trim();
		ret = new File(baseFile, path);
		if (path == null || !ret.exists()) {
			ret = new File(new File(envBase, dictionaryPath), dictionaryId + dictionarySuffix);
		}
		return ret;
	}

	private static Type getType(JSONObject prop) {
		Type ret = null;
		String attribute = prop.optString(ATTR_DICTIONARY_TYPE, "").trim();
		for (Type type : Type.values()) {
			if (type.name().equalsIgnoreCase(attribute)) {
				ret = type;
				break;
			}
		}
		return ret;
	}

	private static boolean getIgnoreCase(JSONObject prop) {
		boolean ret = false;
		if (prop.optBoolean(ATTR_DICTIONARY_IGNORECASE, true)) {
			ret = true;
		}
		return ret;
	}

	private static String getTokenType(JSONObject prop) {
		String ret = null;
		String attribute = prop.optString(ATTR_DICTIONARY_TOKEN_TYPE, "").trim();
		for (TokenType tokenType : TokenType.values()) {
			if (tokenType.name().equalsIgnoreCase(attribute)) {
				ret = attribute;
				break;
			}
		}
		return ret;
	}

	public static ProductNameDictionary loadDictionary(final Environment env) {
		// 플러그인 디렉토리 에서 설정파일을 찾도록 한다.
		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<ProductNameDictionary>) () -> {
			for (int tries = 0; tries < 2; tries++) {
				try {
					if (tries == 0) {
						baseFile = ResourceResolver.getResourceRoot(ProductNameDictionary.class);
					} else {
						// 설정파일이 플러그인 디렉토리에 존재하지 않는다면 검색엔진 conf 디렉토리에서 설정파일을 찾는다.
						// 추후 불필요시 삭제한다. (설정파일 혼란이 있을수 있음)
						baseFile = env.configFile().toFile();
					}
					logger.debug("TESTING PRODUCT DICTIONARY BASE : {}", baseFile.getAbsolutePath());
					if (baseFile != null && baseFile.exists()) {
						logger.debug("PRODUCT DICTIONARY BASE : {}", baseFile.getAbsolutePath());
					}
				} catch (Exception e) { 
					logger.error("", e);
					baseFile = null; 
					configFile = null;
					continue;
				}
				configFile = new File(baseFile, ANALYSIS_PROP);
				if (configFile.exists()) { 
					logger.debug("DICTIONARY PROPERTIES : {}", configFile.getAbsolutePath());
					break;
				} else {
					baseFile = null; 
					configFile = null;
				}
			}
			JSONObject dictProp = ResourceResolver.readYmlConfig(configFile);
			if (dictProp == null) {
				logger.error("DICTIONARY PROPERTIES FILE NOT FOUND {}", configFile.getAbsolutePath());
			}
			return loadDictionary(baseFile, dictProp);
		});
	}

	public static ProductNameDictionary loadDictionary(final File baseFile, final JSONObject dictProp) {
		/**
		 * 기본셋팅. 
		 * ${ELASTICSEARCH}/config/product_name_analysis.prop 파일을 사용하도록 한다
		 * NORI 기분석 사전은 기본적으로(수정불가) 사용하되 사용자 사전을 활용하여
		 * 커스터마이징 하도록 한다.
		 * 우선은 JAXB 마샬링 구조를 사용하지 않고 Properties 를 사용하도록 한다.
		 **/
		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<ProductNameDictionary>) () -> {
			Dictionary<TagProb, PreResult<CharSequence>> dictionary = null;
			ProductNameDictionary commonDictionary = null;
			JSONArray dictList = dictProp.optJSONArray(ATTR_DICTIONARY_LIST);
			String basePath = dictProp.optString(ATTR_DICTIONARY_BASE_PATH);

			// 시스템사전을 먼저 읽어오도록 한다. 
			for (int inx = 0; inx < dictList.length(); inx++) {
				JSONObject row = dictList.optJSONObject(inx);
				if (getType(row) == Type.SYSTEM) {
					dictionary = loadSystemDictionary(baseFile, row, basePath);
					commonDictionary = new ProductNameDictionary(dictionary);
					break;
				}
			}

			for (int inx = 0; inx < dictList.length(); inx++) {
				JSONObject row = dictList.optJSONObject(inx);
				String dictionaryId = row.optString(ATTR_DICTIONARY_NAME);
				Type type = getType(row);
				String tokenType = getTokenType(row);
				File dictFile = getDictionaryFile(baseFile, row, basePath);
				boolean ignoreCase = getIgnoreCase(row);
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
						commonDictionary.appendAdditionalNounEntry(spaceDictionary.getWordSet(), tokenType);
						Map<CharSequence, PreResult<CharSequence>> map = new HashMap<>();
						for (Entry<CharSequence, CharSequence[]> e : spaceDictionary.map().entrySet()) {
							PreResult<CharSequence> preResult = new PreResult<>();
							preResult.setResult(e.getValue());
							map.put(e.getKey(), preResult);
						}
						// commonDictionary.setPreDictionary(map);
					}
					sourceDictionary = spaceDictionary;
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
				if (sourceDictionary != null) {
					commonDictionary.addDictionary(dictionaryId, sourceDictionary);
				}
			}
			return commonDictionary;
		});
	}

	public static void reloadDictionary() {
		if (baseFile == null || configFile == null) {
			logger.error("DICTIONARY NOT LOADED!");
			return;
		}
		ProductNameDictionary newCommonDictionary = loadDictionary(baseFile, ResourceResolver.readYmlConfig(configFile));
		reloadDictionary(newCommonDictionary);
	}

	public static void reloadDictionary(ProductNameDictionary newCommonDictionary) {
		if (baseFile == null || configFile == null) {
			logger.error("DICTIONARY NOT LOADED!");
			return;
		}
		ProductNameDictionary commonDictionary = contextStore.getAs(PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
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

	public static ProductNameDictionary compileDictionary(DictionaryRepository repo) {
		return compileDictionary(repo, false);
	}

	public static ProductNameDictionary compileDictionary(final DictionaryRepository repo, final boolean exportFile) {
		if (baseFile == null || configFile == null) {
			logger.error("DICTIONARY NOT LOADED!");
			return null;
		}
		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<ProductNameDictionary>) () -> {
			Dictionary<TagProb, PreResult<CharSequence>> dictionary = null;
			ProductNameDictionary commonDictionary = null;
			JSONObject dictProp = ResourceResolver.readYmlConfig(configFile);
			JSONArray dictList = dictProp.optJSONArray(ATTR_DICTIONARY_LIST);
			String basePath = dictProp.optString(ATTR_DICTIONARY_BASE_PATH);

			// 시스템사전은 편집불가
			for (int inx = 0; inx < dictList.length(); inx++) {
				JSONObject row = dictList.optJSONObject(inx);
				if (getType(row) == Type.SYSTEM) {
					dictionary = loadSystemDictionary(baseFile, row, basePath);
					commonDictionary = new ProductNameDictionary(dictionary);
					break;
				}
			}

			for (int inx = 0; inx < dictList.length(); inx++) {
				JSONObject row = dictList.optJSONObject(inx);
				String dictionaryId = row.optString(ATTR_DICTIONARY_NAME);
				Type type = getType(row);
				String tokenType = getTokenType(row);
				boolean ignoreCase = row.optBoolean(ATTR_DICTIONARY_IGNORECASE, true);

				File dictFile = getDictionaryFile(baseFile, row, basePath);
				Iterator<CharSequence[]> source = repo.getSource(dictionaryId);

				SourceDictionary<?> sourceDictionary = null;
				if (type == Type.SET) {
					sourceDictionary = new SetDictionary(ignoreCase);
				} else if (type == Type.MAP) {
					sourceDictionary = new MapDictionary(ignoreCase);
				} else if (type == Type.SYNONYM || type == Type.SYNONYM_2WAY) {
					sourceDictionary = new SynonymDictionary(ignoreCase);
				} else if (type == Type.SPACE) {
					sourceDictionary = new SpaceDictionary(ignoreCase);
				} else if (type == Type.CUSTOM) {
					sourceDictionary = new CustomDictionary(ignoreCase);
				} else if (type == Type.INVERT_MAP) {
					sourceDictionary = new InvertMapDictionary(ignoreCase);
				} else if (type == Type.COMPOUND) {
					sourceDictionary = new CompoundDictionary(ignoreCase);
				}
				if (sourceDictionary != null) {
					int cnt = 0;
					for (; source.hasNext(); cnt++) {
						CharSequence[] data = source.next();
						String id = "";
						String keyword = "";
						String value = "";
						String line = "";
						if (data[0] != null) {
							id = String.valueOf(data[0]).trim();
						}
						if (data[1] != null) {
							keyword = String.valueOf(data[1]).trim();
						}
						if (data[2] != null) {
							value = String.valueOf(data[2]).trim();
						}
						if (type == Type.SYNONYM || type == Type.SYNONYM_2WAY) {
							if (keyword.length() > 0) {
								line = keyword + "\t" + value;
							} else {
								line = value;
							}
						} else if (type == Type.CUSTOM) {
							if (id.length() > 0) {
								line = keyword + "\t" + id;
							} else {
								line = keyword;
							}
						} else {
							if (value.length() > 0) {
								line = keyword + "\t" + value;
							} else {
								line = keyword;
							}
						}
						sourceDictionary.addSourceLineEntry(line);
					}
					commonDictionary.addDictionary(dictionaryId, sourceDictionary);
					logger.debug("LOAD DICTIONARY [{}] / {} / {} / {} / {}", cnt, dictionaryId, type, tokenType, dictFile.getAbsolutePath());
					if (exportFile) {
						OutputStream ostream = null;
						try {
							ostream = new FileOutputStream(dictFile);
							sourceDictionary.writeTo(ostream);
						} catch (Exception ignore) {
						} finally {
							try { ostream.close(); } catch (Exception ignore) { }
						}
					}
				}

				if (type == Type.SET) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((SetDictionary) sourceDictionary).set(), tokenType);
					}
				} else if (type == Type.MAP) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((MapDictionary) sourceDictionary).map().keySet(), tokenType);
					}
				} else if (type == Type.SYNONYM || type == Type.SYNONYM_2WAY) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((SynonymDictionary) sourceDictionary).getWordSet(), tokenType);
					}
				} else if (type == Type.SPACE) {
					if (tokenType != null) {
						SpaceDictionary spaceDictionary = ((SpaceDictionary) sourceDictionary);
						commonDictionary.appendAdditionalNounEntry(spaceDictionary.getWordSet(), tokenType);
						Map<CharSequence, PreResult<CharSequence>> map = new HashMap<>();
						for (Entry<CharSequence, CharSequence[]> e : spaceDictionary.map().entrySet()) {
							PreResult<CharSequence> preResult = new PreResult<>();
							preResult.setResult(e.getValue());
							map.put(e.getKey(), preResult);
						}
						// commonDictionary.setPreDictionary(map);
					}
				} else if (type == Type.CUSTOM) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((CustomDictionary) sourceDictionary).getWordSet(), tokenType);
					}
				} else if (type == Type.INVERT_MAP) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((InvertMapDictionary) sourceDictionary).map().keySet(), tokenType);
					}
				} else if (type == Type.COMPOUND) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((CompoundDictionary) sourceDictionary).map().keySet(), tokenType);
					}
				}
			}
			logger.debug("DICTIONARY LOAD COMPLETE!");
			return commonDictionary;
		});
	}

	public static ProductNameDictionary compileDictionaryOne(final DictionaryRepository repo, final boolean exportFile, ProductNameDictionary currentDictionary, String inputTypes) {
		if (baseFile == null || configFile == null) {
			logger.error("DICTIONARY NOT LOADED!");
			return null;
		}

		Set<String> types = new HashSet<>();
		if(inputTypes != null){
			String[] split = inputTypes.trim().split(",");
			for(String item : split){
				System.out.println("types:" + item.toLowerCase());
				types.add(item.toLowerCase());
			}
		}

		SpecialPermission.check();
		return AccessController.doPrivileged((PrivilegedAction<ProductNameDictionary>) () -> {
			Dictionary<TagProb, PreResult<CharSequence>> dictionary = null;
			ProductNameDictionary commonDictionary = currentDictionary;
			JSONObject dictProp = ResourceResolver.readYmlConfig(configFile);
			JSONArray dictList = dictProp.optJSONArray(ATTR_DICTIONARY_LIST);
			String basePath = dictProp.optString(ATTR_DICTIONARY_BASE_PATH);

			/* compile Dict */
			for (int inx = 0; inx < dictList.length(); inx++) {
				JSONObject row = dictList.optJSONObject(inx);
				String dictionaryId = row.optString(ATTR_DICTIONARY_NAME);
				if(inputTypes != null && !types.contains(dictionaryId)){
					continue;
				}
				System.out.println("dictionaryId:" + dictionaryId);

				Type type = getType(row);
				String tokenType = getTokenType(row);
				boolean ignoreCase = row.optBoolean(ATTR_DICTIONARY_IGNORECASE, true);

				File dictFile = getDictionaryFile(baseFile, row, basePath);
				Iterator<CharSequence[]> source = repo.getSource(dictionaryId);

				SourceDictionary<?> sourceDictionary = null;
				if (type == Type.SET) {
					sourceDictionary = new SetDictionary(ignoreCase);
				} else if (type == Type.MAP) {
					sourceDictionary = new MapDictionary(ignoreCase);
				} else if (type == Type.SYNONYM || type == Type.SYNONYM_2WAY) {
					sourceDictionary = new SynonymDictionary(ignoreCase);
				} else if (type == Type.SPACE) {
					sourceDictionary = new SpaceDictionary(ignoreCase);
				} else if (type == Type.CUSTOM) {
					sourceDictionary = new CustomDictionary(ignoreCase);
				} else if (type == Type.INVERT_MAP) {
					sourceDictionary = new InvertMapDictionary(ignoreCase);
				} else if (type == Type.COMPOUND) {
					sourceDictionary = new CompoundDictionary(ignoreCase);
				}
				if (sourceDictionary != null) {
					int cnt = 0;
					for (; source.hasNext(); cnt++) {
						CharSequence[] data = source.next();
						String id = "";
						String keyword = "";
						String value = "";
						String line = "";
						if (data[0] != null) {
							id = String.valueOf(data[0]).trim();
						}
						if (data[1] != null) {
							keyword = String.valueOf(data[1]).trim();
						}
						if (data[2] != null) {
							value = String.valueOf(data[2]).trim();
						}
						if (type == Type.SYNONYM || type == Type.SYNONYM_2WAY) {
							if (keyword.length() > 0) {
								line = keyword + "\t" + value;
							} else {
								line = value;
							}
						} else if (type == Type.CUSTOM) {
							if (id.length() > 0) {
								line = keyword + "\t" + id;
							} else {
								line = keyword;
							}
						} else {
							if (value.length() > 0) {
								line = keyword + "\t" + value;
							} else {
								line = keyword;
							}
						}
						sourceDictionary.addSourceLineEntry(line);
					}
					commonDictionary.addDictionary(dictionaryId, sourceDictionary);
					logger.debug("LOAD DICTIONARY [{}] / {} / {} / {} / {}", cnt, dictionaryId, type, tokenType, dictFile.getAbsolutePath());
					if (exportFile) {
						OutputStream ostream = null;
						try {
							ostream = new FileOutputStream(dictFile);
							sourceDictionary.writeTo(ostream);
						} catch (Exception ignore) {
						} finally {
							try { ostream.close(); } catch (Exception ignore) { }
						}
					}
				}

				if (type == Type.SET) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((SetDictionary) sourceDictionary).set(), tokenType);
					}
				} else if (type == Type.MAP) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((MapDictionary) sourceDictionary).map().keySet(), tokenType);
					}
				} else if (type == Type.SYNONYM || type == Type.SYNONYM_2WAY) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((SynonymDictionary) sourceDictionary).getWordSet(), tokenType);
					}
				} else if (type == Type.SPACE) {
					if (tokenType != null) {
						SpaceDictionary spaceDictionary = ((SpaceDictionary) sourceDictionary);
						commonDictionary.appendAdditionalNounEntry(spaceDictionary.getWordSet(), tokenType);
						Map<CharSequence, PreResult<CharSequence>> map = new HashMap<>();
						for (Entry<CharSequence, CharSequence[]> e : spaceDictionary.map().entrySet()) {
							PreResult<CharSequence> preResult = new PreResult<>();
							preResult.setResult(e.getValue());
							map.put(e.getKey(), preResult);
						}
						// commonDictionary.setPreDictionary(map);
					}
				} else if (type == Type.CUSTOM) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((CustomDictionary) sourceDictionary).getWordSet(), tokenType);
					}
				} else if (type == Type.INVERT_MAP) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((InvertMapDictionary) sourceDictionary).map().keySet(), tokenType);
					}
				} else if (type == Type.COMPOUND) {
					if (tokenType != null) {
						commonDictionary.appendAdditionalNounEntry(((CompoundDictionary) sourceDictionary).map().keySet(), tokenType);
					}
				}
			}
			logger.debug("DICTIONARY LOAD COMPLETE!");
			return commonDictionary;
		});
	}

	public static int[] getDictionaryInfo(SourceDictionary<?> sourceDictionary) {
		int[] ret = {0, 0};
		if (sourceDictionary.getClass().isAssignableFrom(SetDictionary.class)) {
			SetDictionary dictionary = (SetDictionary) sourceDictionary;
			ret[0] = dictionary.set().size();
		} else if (sourceDictionary.getClass().isAssignableFrom(MapDictionary.class)) {
			MapDictionary dictionary = (MapDictionary) sourceDictionary;
			ret[0] = dictionary.map().keySet().size();
		} else if (sourceDictionary.getClass().isAssignableFrom(SynonymDictionary.class)) {
			SynonymDictionary dictionary = (SynonymDictionary) sourceDictionary;
			ret[0] = dictionary.map().keySet().size();
			ret[1] = dictionary.getWordSet().size();
		} else if (sourceDictionary.getClass().isAssignableFrom(SpaceDictionary.class)) {
			SpaceDictionary dictionary = (SpaceDictionary) sourceDictionary;
			ret[0] = dictionary.map().keySet().size();
			ret[1] = dictionary.getWordSet().size();
		} else if (sourceDictionary.getClass().isAssignableFrom(CustomDictionary.class)) {
			CustomDictionary dictionary = (CustomDictionary) sourceDictionary;
			ret[0] = dictionary.map().keySet().size();
			ret[1] = dictionary.getWordSet().size();
		} else if (sourceDictionary.getClass().isAssignableFrom(InvertMapDictionary.class)) {
			InvertMapDictionary dictionary = (InvertMapDictionary) sourceDictionary;
			ret[0] = dictionary.map().keySet().size();
		} else if (sourceDictionary.getClass().isAssignableFrom(CompoundDictionary.class)) {
			CompoundDictionary dictionary = (CompoundDictionary) sourceDictionary;
			ret[0] = dictionary.map().keySet().size();
			ret[1] = dictionary.getWordSet().size();
		}
		return ret;
	}

	public static List<CharSequence> getTwowaySynonymWord(CharSequence mainWord, Map<CharSequence, CharSequence[]> synonymMap) {
		CharSequence word = null;
		Map<CharSequence, List<CharSequence>> map = new HashMap<>();
		List<CharSequence> keys = new ArrayList<>();
		List<CharSequence> values = null;
		CharSequence[] synonyms = null;
		CharSequence[] subSynonyms = null;
		map.put(mainWord, values = new ArrayList<>());
		keys.addAll(map.keySet());
		values.addAll(Arrays.asList(synonymMap.get(mainWord)));

		for (int kinx = 0; kinx < keys.size(); kinx++) {
			word = keys.get(kinx);
			synonyms = synonymMap.get(word);
			logger.trace("SYN {} : {}", word, synonyms);

			for (int sinx = 0; sinx < synonyms.length; sinx++) {
				CharSequence synonym = synonyms[sinx];
				if (keys.contains(synonym)) { continue; }
				subSynonyms = synonymMap.get(synonym);
				if (subSynonyms != null && synonyms.length > 0) {
					for (CharSequence subSynonym : subSynonyms) {
						if (mainWord.equals(subSynonym)) {
							map.put(synonym, values = new ArrayList<>());
							keys.add(synonym);
							values.addAll(Arrays.asList(subSynonyms));
							break;
						}
					}
				}
			}
		}
		if (map.size() > 1) {
			normalizeSynonymMap(map);
		}
		logger.trace("MAP:{}", map);
		if (map.size() > 1) {
			keys.clear();
			keys.addAll(new TreeSet<CharSequence>(map.keySet()));
			return keys;
		}
		return null;
	}

	public static void normalizeSynonymMap(Map<CharSequence, List<CharSequence>> map) {
		CharSequence word = null;
		List<CharSequence> keys = new ArrayList<>();
		List<CharSequence> values = null;
		keys.addAll(map.keySet());
		// 키값에 없는 동의어 제거
		for (int inx = 0; inx < keys.size(); inx++) {
			word = keys.get(inx);
			values = map.get(word);
			for (int vinx = 0; vinx < values.size(); vinx++) {
				CharSequence value = values.get(vinx);
				if (!keys.contains(value)) {
					values.remove(vinx);
					vinx--;
				}
			}
		}
		List<CharSequence> delete = new ArrayList<>();
		// 동의어에 없는 키값 제거
		logger.trace("MAP:{}", map);
		for (int sinx = 0; sinx < keys.size(); sinx++) {
			word = keys.get(sinx);
			for (int tinx = 0; tinx < keys.size(); tinx++) {
				if (sinx == tinx) { continue; }
				values = map.get(keys.get(tinx));
				if (!values.contains(word)) {
					delete.add(word);
					break;
				}
			}
		}
		for (CharSequence key : delete) {
			map.remove(key);
		}
		logger.trace("MAP:{}", map);
	}

	public static boolean isOneWaySynonym(CharSequence word, Map<CharSequence, CharSequence[]> map) {
		if (map.containsKey(word)) {
			List<CharSequence> twoway = getTwowaySynonymWord(word, map);
			logger.trace("TWO-WAY {} = {}", word, twoway);
			for (CharSequence value : map.get(word)) {
				if (twoway != null && twoway.contains(value)) { continue; }
				// 단방향이 하나라도 있으면 단방향 동의어
				logger.trace("ONE-WAY {} -> {}", word, value);
				return true;
			}
		}
		return false;
	}

	public static void restoreDictionary(final DictionaryRepository repo, String index) {
		ProductNameDictionary productNameDictionary = contextStore.getAs(PRODUCT_NAME_DICTIONARY, ProductNameDictionary.class);
		Map<String, SourceDictionary<?>> dictionaryMap = productNameDictionary.getDictionaryMap();
		Set<String> keySet = dictionaryMap.keySet();
		for (String key : keySet) {
			SourceDictionary<?> sourceDictionary = dictionaryMap.get(key);
			logger.debug("KEY:{} / {}", key, sourceDictionary);
			if (sourceDictionary.getClass().isAssignableFrom(SetDictionary.class)) {
				SetDictionary dictionary = (SetDictionary) sourceDictionary;
				Set<CharSequence> words = dictionary.set();
				repo.restore(key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(MapDictionary.class)) {
				MapDictionary dictionary = (MapDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (CharSequence value : map.get(word)) {
						if (sb.length() > 0) { sb.append(","); }
						sb.append(String.valueOf(value).trim());
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(sb));
				}
				repo.restore(key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(SynonymDictionary.class)) {
				SynonymDictionary dictionary = (SynonymDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					List<CharSequence> twoway = getTwowaySynonymWord(word, map);
					{
						// 단방향 동의어에서 양방향을 뺀 나머지 값들만 저장한다.
						StringBuilder sb = new StringBuilder();
						for (CharSequence value : map.get(word)) {
							if (twoway != null && twoway.contains(value)) { continue; }
							if (sb.length() > 0) { sb.append(","); }
							sb.append(String.valueOf(value).trim());
						}
						if (sb.length() > 0) {
							words.add(String.valueOf(word) + TAB + String.valueOf(sb));
						}
					}
					if (twoway != null && twoway.size() > 0) {
						// 양방향 동의어를 저장한다.
						StringBuilder sb = new StringBuilder();
						for (CharSequence value : twoway) {
							if (sb.length() > 0) { sb.append(","); }
							sb.append(String.valueOf(value).trim());
						}
						if (sb.length() > 0) {
							words.add(TAB + String.valueOf(sb));
						}
					}
				}
				repo.restore(key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(SpaceDictionary.class)) {
				SpaceDictionary dictionary = (SpaceDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (CharSequence value : map.get(word)) {
						if (sb.length() > 0) { sb.append(" "); }
						sb.append(String.valueOf(value).trim());
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(sb));
				}
				repo.restore(key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(CustomDictionary.class)) {
				CustomDictionary dictionary = (CustomDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, Object[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (Object value : map.get(word)) {
						if (value != null) {
							if (sb.length() > 0) { sb.append(","); }
							sb.append(String.valueOf(value).trim());
						}
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(word) + TAB + String.valueOf(sb));
				}
				repo.restore(key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(InvertMapDictionary.class)) {
				InvertMapDictionary dictionary = (InvertMapDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (CharSequence value : map.get(word)) {
						if (sb.length() > 0) { sb.append(","); }
						sb.append(String.valueOf(value).trim());
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(sb));
				}
				repo.restore(key, dictionary.ignoreCase(), words);
			} else if (sourceDictionary.getClass().isAssignableFrom(CompoundDictionary.class)) {
				CompoundDictionary dictionary = (CompoundDictionary) sourceDictionary;
				Set<CharSequence> words = new HashSet<>();
				Map<CharSequence, CharSequence[]> map = dictionary.map();
				for (CharSequence word : map.keySet()) {
					StringBuilder sb = new StringBuilder();
					for (CharSequence value : map.get(word)) {
						if (sb.length() > 0) { sb.append(","); }
						sb.append(String.valueOf(value).trim());
					}
					words.add(String.valueOf(word) + TAB + String.valueOf(sb));
				}
				repo.restore(key, dictionary.ignoreCase(), words);
			}
		}
		logger.debug("dictionary restore finished !");
	}

	public static Dictionary<TagProb, PreResult<CharSequence>> loadSystemDictionary(File baseFile, JSONObject prop, String basePath) {
		File systemDictFile = getDictionaryFile(baseFile, prop, basePath);
		long st = System.nanoTime();
		boolean ignoreCase = getIgnoreCase(prop);
		TagProbDictionary tagProbDictionary = new TagProbDictionary(systemDictFile, ignoreCase);
		logger.debug("Product Dictionary Load {}ms >> {}", (System.nanoTime() - st) / 1000000,
			systemDictFile.getName());
		return tagProbDictionary;
	}

	public static abstract class DictionaryRepository {
		public abstract Iterator<CharSequence[]> getSource(String type);
		public abstract void restore(String type, boolean ignoreCase, Set<CharSequence> wordSet);
		public abstract void close();
	}
}
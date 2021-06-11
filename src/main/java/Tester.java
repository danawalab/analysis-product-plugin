import java.io.File;
import java.io.FileFilter;
import java.io.Reader;
import java.io.StringReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Tester {

	private static final String DictionaryType = "org.fastcatsearch.plugin.analysis.AnalysisPluginSetting$DictionarySetting$Type";
	private static final String DictionarySetting = "org.fastcatsearch.plugin.analysis.AnalysisPluginSetting$DictionarySetting";
	private static final String ProductAnalysisPlugin = "org.fastcatsearch.plugin.analysis.product.ProductAnalysisPlugin";
	private static final String DICT_FASTCATSEARCH = "DICT_FASTCATSEARCH";
	private static final String DICT_DANAWASEARCH = "DICT_DANAWASEARCH";

	public void test(String cmd, String pluginPath, String content) throws Exception {
		if ("input".equals(cmd)) {
			this.testBySingleText(pluginPath, content);
		} else if ("".equals(cmd)) {
		}
	}

	public void testBySingleText(String pluginPath, String str) throws Exception {
		File dir = new File(pluginPath);
		Map<String, Object> context = new HashMap<>();
		ReflectHelper r1 = new ReflectHelper(st("fastcatsearch"));
		ReflectHelper r2 = new ReflectHelper(st("../build/jars"));
		r1.init(st(
			"org.apache.lucene.analysis.TokenStream",
			"org.fastcatsearch.plugin.PluginSetting",
			"org.fastcatsearch.plugin.analysis.AnalysisPluginSetting",
			"org.apache.lucene.analysis.core.AnalyzerOption",
			"org.apache.lucene.analysis.tokenattributes.CharTermAttribute",
			"org.apache.lucene.analysis.tokenattributes.OffsetAttribute",
			"org.apache.lucene.analysis.tokenattributes.CharsRefTermAttribute",
			"org.apache.lucene.analysis.tokenattributes.TypeAttribute",
			"org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute",
			"org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute",
			"org.apache.lucene.analysis.tokenattributes.SynonymAttribute",
			"org.fastcatsearch.plugin.analysis.product.StandardProductAnalyzer"
		));
		r2.init(st(
			"org.apache.lucene.analysis.TokenStream",
			"com.danawa.util.ResourceResolver",
			"com.danawa.search.analysis.dict.ProductNameDictionary",
			"com.danawa.search.analysis.dict.CompoundDictionary",
			"com.danawa.search.analysis.dict.CustomDictionary",
			"com.danawa.search.analysis.dict.SpaceDictionary",
			"com.danawa.search.analysis.dict.SetDictionary",
			"com.danawa.search.analysis.dict.InvertMapDictionary",
			"com.danawa.search.analysis.dict.MapDictionary",
			"com.danawa.search.analysis.dict.SynonymDictionary",
			"com.danawa.search.analysis.product.AnalyzerOption",
			"com.danawa.search.analysis.product.ProductNameTokenizer",
			"com.danawa.search.analysis.product.ProductNameAnalysisFilter",
			"org.apache.lucene.analysis.tokenattributes.CharTermAttribute",
			"org.apache.lucene.analysis.tokenattributes.OffsetAttribute",
			"org.apache.lucene.analysis.tokenattributes.TypeAttribute",
			"org.apache.lucene.analysis.tokenattributes.SynonymAttribute",
			"org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute",
			"org.apache.lucene.analysis.tokenattributes.PositionIncrementAttribute"
		));

		JSON.init(r2.loader());
		JSONWriter.init(r2.loader());
		initDanawasearch(r2, context, dir);
		initFastcatsearch(r1, context, dir);
		Comparator<Object> comp = new Comparator<>() {
			@Override public int compare(Object o1, Object o2) {
				if (o1 instanceof Comparable && o2 instanceof Comparable) {
					@SuppressWarnings("unchecked")
					Comparable<Object> c1 = (Comparable<Object>) o1;
					@SuppressWarnings("unchecked")
					Comparable<Object> c2 = (Comparable<Object>) o2;
					return c1.compareTo(c2);
				}
				return 0;
			}
		};
		for (int inx = 0; inx < 2; inx++) {
			List<Object> list1 = launchDanawasearch(r2, context, str, inx == 0);
			// log("--------------------------------------------------------------------------------");
			List<Object> list2 = launchFastcatsearch(r1, context, str, inx == 0);
			Collections.sort(list1, comp);
			Collections.sort(list2, comp);
			log("TERMS1:{}", list1);
			log("TERMS2:{}", list2);
			// log("================================================================================");
		}
	}

	public static Class<?>[] ty(Class<?> ... types) { return types; }
	public static Object[] ar(Object ... args) { return args; }
	public static String[] st(String ... args) { return args; }

	public static void log(Object ... args) throws Exception {
		if (args != null && args.length > 0) {
			if (args[0] instanceof String) {
				List<CharSequence> p = new ArrayList<>();
				List<StringBuilder> a = new ArrayList<>();
				String str = String.valueOf(args[0]);
				for (int pos; (pos = str.indexOf("{}")) != -1;) {
					StringBuilder sb = new StringBuilder();
					p.add(str.substring(0, pos));
					p.add(sb);
					a.add(sb);
					str = str.substring(pos + 2);
				}
				p.add(str);
				for (int inx = 1; inx < args.length; inx++) {
					a.get(inx - 1).append(String.valueOf(args[inx]));
				}
				StringBuilder sb = new StringBuilder();
				for (int inx = 0; inx < p.size(); inx++) {
					sb.append(p.get(inx));
				}
				System.out.println(String.valueOf(sb));
			}
		}
	}

	public void initFastcatsearch(ReflectHelper rh, Map<String, Object> context, File dir) throws Exception {
		@SuppressWarnings("all")
		List<Map<String, Object>> ctxProps = (List)context.get("ctxProps");
		List<Object> dictSetting = new ArrayList<>();

		for (int inx = 0; inx < ctxProps.size(); inx++) {
			Map<String, Object> prop = ctxProps.get(inx);
			Class<?> clsTypes = rh.cls(DictionaryType);
			Object item = rh.inst(DictionarySetting);
			Object type = rh.runS(clsTypes, "valueOf", ar(prop.get("type")));
			rh.batch(item,
				ar("setId", ar(prop.get("name"))),
				ar("setName", ar(prop.get("name"))),
				ar("setType", ty(clsTypes), ar(type)),
				ar("setIgnoreCase", ty(boolean.class), ar("true".equals(prop.get("ignoreCase"))))
			);
			dictSetting.add(item);
		}
		Class<?> clsSettings = rh.cls("PluginSetting");
		Object settings = rh.inst("AnalysisPluginSetting");
		rh.batch(settings,
			ar("setId", ar("Product")),
			ar("setNamespace", ar("Analysis")),
			ar("setClassName", ar(ProductAnalysisPlugin)),
			ar("setVersion", ar("1.0")),
			ar("setUseDB", ty(boolean.class), ar(false)),
			ar("setDictionarySettingList", ty(List.class), ar(dictSetting))
		);
		Object plugin = rh.inst(ProductAnalysisPlugin,
			ty(File.class, clsSettings, String.class), ar(dir, settings, ""));
		rh.run(plugin, "load", ty(boolean.class), ar(false));
		Object dict = rh.run(plugin, "getDictionary");
		context.put(DICT_FASTCATSEARCH, dict);
	}

	public void initDanawasearch(ReflectHelper rh, Map<String, Object> context, File dir) throws Exception {
		File file = new File(dir, "product-name-dictionary.yml");
		List<Map<String, Object>> ctxProps = new ArrayList<>();
		context.put("ctxProps", ctxProps);

		Object cfgProps = rh.runS(rh.cls("ResourceResolver"), "readYmlConfig", ar(file));
		Object itemList = JSON.optArray(cfgProps, "dictionary");
		Integer length = JSON.size(itemList);
		for (int inx = 0; inx < length; inx++) {
			Object item = JSON.optMap(itemList, inx);
			Map<String, Object> prop = new HashMap<>(Map.of(
				"name", JSON.optStr(item, "name", ""),
				"type", JSON.optStr(item, "type", ""),
				"tokenType", JSON.optStr(item, "tokenType", ""),
				"ignoreCase", JSON.optStr(item, "ignoreCase", ""),
				"filePath", JSON.optStr(item, "filePath", "")
			));
			ctxProps.add(prop);
		}
		Object dict = rh.runS(rh.cls("ProductNameDictionary"), "loadDictionary",
			ty(File.class, cfgProps.getClass()), ar(file.getParentFile(), cfgProps));
		context.put(DICT_DANAWASEARCH, dict);
	}

	public List<Object> launchDanawasearch(ReflectHelper rh, Map<String, Object> context, String str, boolean useForQuery) throws Exception {
		List<Object> termList = new ArrayList<>();
		Object FULL_STRING = rh.field("ProductNameTokenizer", "FULL_STRING");
		Object dict = context.get(DICT_DANAWASEARCH);
		Reader reader = new StringReader(str);
		Object option = rh.inst("AnalyzerOption", 
			ty(boolean.class, boolean.class, boolean.class, boolean.class, boolean.class),
			ar(useForQuery, true, true, true, false));
		rh.run(option, "useSynonym", ty(boolean.class), ar(true));
		Object tokenizer = rh.inst("ProductNameTokenizer", ty(dict.getClass(), boolean.class), ar(dict, true));
		rh.run(tokenizer, "setReader", ty(Reader.class), ar(reader));
		Object tstream = rh.inst("ProductNameAnalysisFilter",
			ty(rh.cls("TokenStream"), dict.getClass(), option.getClass()),
			ar(tokenizer, dict, option));

		Object termAtr = rh.runA(tstream, "addAttribute", ar(rh.cls("CharTermAttribute")));
		Object offsAtr = rh.runA(tstream, "addAttribute", ar(rh.cls("OffsetAttribute")));
		Object typeAtr = rh.runA(tstream, "addAttribute", ar(rh.cls("TypeAttribute")));
		Object synmAtr = rh.runA(tstream, "addAttribute", ar(rh.cls("SynonymAttribute")));
		Object extrAtr = rh.runA(tstream, "addAttribute", ar(rh.cls("ExtraTermAttribute")));
		Object poscAtr = rh.runA(tstream, "addAttribute", ar(rh.cls("PositionIncrementAttribute")));

		rh.run(tstream, "reset");
		while (Boolean.TRUE.equals(rh.run(tstream, "incrementToken"))) {
			Object type = rh.run(typeAtr, "type");
			Object posc = rh.run(poscAtr, "getPositionIncrement");
			Object offst = rh.run(offsAtr, "startOffset");
			Object offed = rh.run(offsAtr, "endOffset");
			List<?> synm = (List<?>) rh.run(synmAtr, "getSynonyms");
			Iterator<?> extr = (Iterator<?>) rh.run(extrAtr, "iterator");
			if (FULL_STRING.equals(type)) { continue; }

			if (!useForQuery) { synm = null; }
			termList.add(String.valueOf(termAtr));
			// log("TERM:{} / {} [{}: {}~{}] / {} / {}", termAtr, type, posc, offst, offed, synm, extr != null);
			for (int inx = 0; synm != null && inx < synm.size(); inx++) {
				Object syn = synm.get(inx);
				if (syn instanceof List) {
					for (Object item : (List<?>) syn) { termList.add(String.valueOf(item)); }
				} else {
					termList.add(String.valueOf(syn));
				}
				// log("SYN:{}", syn);
			}
			while (extr != null && extr.hasNext()) {
				Object eterm = extr.next();
				type = rh.run(typeAtr, "type");
				offst = rh.run(offsAtr, "startOffset");
				offed = rh.run(offsAtr, "endOffset");
				synm = (List<?>) rh.run(synmAtr, "getSynonyms");
				if (!useForQuery) { synm = null; }
				termList.add(String.valueOf(eterm));
				// log("EXT:{} / {} [{}: {}~{}] / {}", eterm, type, posc, offst, offed, synm);
				for (int inx = 0; synm != null && inx < synm.size(); inx++) {
					Object syn = synm.get(inx);
					if (syn instanceof List) {
						for (Object item : (List<?>) syn) { termList.add(String.valueOf(item)); }
					} else {
						termList.add(String.valueOf(syn));
					}
					// log("SYN:{}", syn);
				}
			}

		}
		rh.run(tstream, "close");
		rh.run(tokenizer, "close");
		return termList;
	}

	public List<Object> launchFastcatsearch(ReflectHelper rh, Map<String, Object> context, String str, boolean useForQuery) throws Exception {
		List<Object> termList = new ArrayList<>();
		Object dict = context.get(DICT_FASTCATSEARCH);
		Reader reader = new StringReader(str);
		Object option = rh.inst("AnalyzerOption");
		rh.run(option, useForQuery ? "setForQuery" : "setForDocument");
		rh.run(option, "useSynonym", ty(boolean.class), ar(true));
		Object analyzer = rh.inst("StandardProductAnalyzer", ty(dict.getClass()), ar(dict));
		Object tstream = rh.run(analyzer, "tokenStream",
			ty(String.class, Reader.class, option.getClass()),
			ar("", reader, option));

		Object termAtr = rh.runA(tstream, "getAttribute", ar(rh.cls("CharsRefTermAttribute")));
		Object offsAtr = rh.runA(tstream, "getAttribute", ar(rh.cls("OffsetAttribute")));
		Object typeAtr = rh.runA(tstream, "getAttribute", ar(rh.cls("TypeAttribute")));
		Object synmAtr = rh.runA(tstream, "getAttribute", ar(rh.cls("SynonymAttribute")));
		Object extrAtr = rh.runA(tstream, "getAttribute", ar(rh.cls("AdditionalTermAttribute")));
		Object poscAtr = rh.runA(tstream, "getAttribute", ar(rh.cls("PositionIncrementAttribute")));

		rh.run(tstream, "reset");
		while (Boolean.TRUE.equals(rh.run(tstream, "incrementToken"))) {
			Object type = rh.run(typeAtr, "type");
			Object posc = rh.run(poscAtr, "getPositionIncrement");
			Object offst = rh.run(offsAtr, "startOffset");
			Object offed = rh.run(offsAtr, "endOffset");
			List<?> synm = (List<?>) rh.run(synmAtr, "getSynonyms");
			Iterator<?> extr = (Iterator<?>) rh.run(extrAtr, "iterateAdditionalTerms");
			if (!useForQuery) { synm = null; }
			termList.add(String.valueOf(termAtr));
			// log("TERM:{} / {} [{}: {}~{}] / {} / {}", termAtr, type, posc, offst, offed, synm, extr != null);
			for (int inx = 0; synm != null && inx < synm.size(); inx++) {
				Object syn = synm.get(inx);
				if (syn instanceof List) {
					for (Object item : (List<?>) syn) { termList.add(String.valueOf(item)); }
				} else {
					termList.add(String.valueOf(syn));
				}
				// log("SYN:{}", syn);
			}
			while (extr != null && extr.hasNext()) {
				Object eterm = extr.next();
				type = rh.run(typeAtr, "type");
				offst = rh.run(offsAtr, "startOffset");
				offed = rh.run(offsAtr, "endOffset");
				synm = (List<?>) rh.run(synmAtr, "getSynonyms");
				if (!useForQuery) { synm = null; }
				termList.add(String.valueOf(eterm));
				// log("EXT:{} / {} [{}: {}~{}] / {}", eterm, type, posc, offst, offed, synm);
				for (int inx = 0; synm != null && inx < synm.size(); inx++) {
					Object syn = synm.get(inx);
					if (syn instanceof List) {
						for (Object item : (List<?>) syn) { termList.add(String.valueOf(item)); }
					} else {
						termList.add(String.valueOf(syn));
					}
					// log("SYN:{}", syn);
				}
			}
		}
		rh.run(analyzer, "close");
		return termList;
	}

	public static class ReflectHelper {
		private ClassLoader loader;
		private Map<String, Class<?>> classMap;
		public ClassLoader loader() { return loader; }
		public ReflectHelper(String[] paths) throws Exception {
			final List<URL> urlList = new ArrayList<URL>();
			final FileFilter fileFilter = new FileFilter() {
				@Override public boolean accept(File file) {
					if (!file.exists()) { return false; }
					if (file.isDirectory()) { file.listFiles(this); }
					String path = file.getAbsolutePath();
					if (path.endsWith(".jar")) {
						try {
							urlList.add(new URL("jar:file:" + path + "!/"));
						} catch (Exception ignore) { }
					}
					return false;
				}
			};
			for (String path : paths) {
				new File(path).listFiles(fileFilter);
			}
			URL[] urls = new URL[urlList.size()];
			urls = urlList.toArray(urls);
			this.loader = new URLClassLoader(urls, Thread.currentThread().getContextClassLoader());
			this.classMap = new HashMap<>();
		}

		public void init(String[] classNames) throws Exception {
			for (String clsName : classNames) {
				cls(clsName);
			}
		}

		public Class<?> cls(String clsName) throws Exception {
			Class<?> cls = null;
			try {
				cls = this.classMap.get(clsName);
			} catch (Exception ignore) { }
			if (cls == null) {
				cls = this.loader.loadClass(clsName);
				classMap.put(cls.getSimpleName(), cls);
				classMap.put(cls.getName(), cls);
				// log("LOAD-CLASS:{} / {} / {}", clsName, cls, this.loader);
			}
			return cls;
		}

		public Object inst(String clsName) throws Exception {
			return this.inst(clsName, null, null);
		}

		public Object inst(String clsName, Class<?>[] types, Object[] args) throws Exception {
			Class<?> cls = cls(clsName);
			return this.inst(cls, types, args);
		}

		public Object inst(Class<?> cls) throws Exception {
			return this.inst(cls, null, null);
		}

		public Object inst(Class<?> cls, Class<?>[] types, Object[] args) throws Exception {
			if (types == null) { types = new Class<?>[] {}; }
			if (args == null) { args = new Object[] {}; }
			Constructor<?> constructor = cls.getConstructor(types);
			return constructor.newInstance(args);
		}

		public Method method(Class<?> cls, String name, Class<?>[] types) throws Exception {
			if (types == null) { types = new Class<?>[] {}; }
			return cls.getMethod(name, types);
		}

		public Object runS(String clsName, String name, Class<?>[] types, Object[] args) throws Exception {
			return this.runS(this.cls(clsName), name, types, args);
		}

		public Object runS(Class<?> cls, String name) throws Exception {
			return this.runS(cls, name, null, null);
		}

		public Object runS(Class<?> cls, String name, Class<?>[] types, Object[] args) throws Exception {
			if (types == null) { types = new Class<?>[] {}; }
			if (args == null) { args = new Object[] {}; }
			Method method = cls.getMethod(name, types);
			return method.invoke(null, args);
		}

		public Object run(Object inst, String name) throws Exception {
			return this.run(inst, name, null, null);
		}

		public Object run(Object inst, String name, Class<?>[] types, Object[] args) throws Exception {
			if (types == null) { types = new Class<?>[] {}; }
			if (args == null) { args = new Object[] {}; }
			Class<?> cls = inst.getClass();
			Method method = cls.getMethod(name, types);
			return method.invoke(inst, args);
		}

		public Object runS(Class<?> cls, String name, Object[] args) throws Exception {
			Class<?>[] types = new Class<?>[args.length];
			for (int inx = 0; inx < args.length; inx++) { types[inx] = args[inx].getClass(); }
			return this.runS(cls, name, types, args);
		}

		public Object runA(Object inst, String name, Object[] args) throws Exception {
			Class<?>[] types = new Class<?>[args.length];
			for (int inx = 0; inx < args.length; inx++) { types[inx] = args[inx].getClass(); }
			return this.run(inst, name, types, args);
		}

		public void batch(Object inst, Object[] ... params) throws Exception {
			for (Object[] param : params) {
				String method = (String) param[0];
				if (param.length == 2) {
					Object[] args = (Object[]) param[1];
					Class<?>[] types = new Class<?>[args.length];
					for (int inx = 0; inx < args.length; inx++) { types[inx] = args[inx].getClass(); }
					this.run(inst, method, types, args);
				} else if (param.length == 3) {
					Object[] args = (Object[]) param[2];
					Class<?>[] types = (Class<?>[]) param[1];
					this.run(inst, method, types, args);
				}
			}
		}

		public Object field(String clsName, String name) throws Exception {
			return this.field(this.cls(clsName), null, name);
		}
		public Object field(Class<?> cls,String name) throws Exception {
			return this.field(cls, null, name);
		}
		public Object field(Object inst, String name) throws Exception {
			return this.field(inst.getClass(), inst, name);
		}
		public Object field(Class<?> cls, Object inst, String name) throws Exception {
			Field field = cls.getField(name);
			return field.get(inst);
		}
	}

	public static class JSON {
		private static Class<?> JSONObject;
		private static Class<?> JSONArray;
		public static void init(ClassLoader loader) throws Exception {
			JSON.JSONObject = loader.loadClass("org.json.JSONObject");
			JSON.JSONArray = loader.loadClass("org.json.JSONArray");
		}

		public static Object map() throws Exception {
			return JSONObject.getConstructor(ty()).newInstance(ar());
		}

		public static Object array() throws Exception {
			return JSONArray.getConstructor(ty()).newInstance(ar());
		}

		public static boolean isMap(Object json, Object key) {
			if (json != null && JSONObject.getName().equals(json.getClass().getName())
				&& key != null && key instanceof String) {
				return true;
			}
			return false;
		}
		public static boolean isArray(Object json, Object key) {
			if (json != null && JSONArray.getName().equals(json.getClass().getName())
				&& key != null && key instanceof Integer) {
				return true;
			}
			return false;
		}

		public static Object optMap(Object json, Object key) throws Exception {
			if (json == null) { return null; }
			Object ret = null;
			if (isMap(json, key)) {
				Method method = JSONObject.getMethod("optJSONObject", ty(String.class));
				ret = method.invoke(json, ar(key));
			} else if (isArray(json, key)) {
				Method method = JSONArray.getMethod("optJSONObject", ty(int.class));
				ret = method.invoke(json, ar(key));
			}
			return ret;
		}

		public static Object optArray(Object json, Object key) throws Exception {
			if (json == null) { return null; }
			Object ret = null;
			if (isMap(json, key)) {
				Method method = JSONObject.getMethod("optJSONArray", ty(String.class));
				ret = method.invoke(json, ar(key));
			} else if (isArray(json, key)) {
				Method method = JSONArray.getMethod("optJSONArray", ty(int.class));
				ret = method.invoke(json, ar(key));
			}
			return ret;
		}

		public static String optStr(Object json, Object key, String def) throws Exception {
			if (json == null) { return def; }
			Object ret = null;
			if (isMap(json, key)) {
				Method method = JSONObject.getMethod("optString", ty(String.class, String.class));
				ret = method.invoke(json, ar(key, def));
			} else if (isArray(json, key)) {
				Method method = JSONArray.getMethod("optString", ty(int.class));
				ret = method.invoke(json, ar(key, def));
			}
			if (ret == null) { return null; }
			return String.valueOf(ret);
		}

		public static Integer optInt(Object json, Object key, Integer def) throws Exception {
			if (json == null) { return def; }
			Object ret = null;
			if (isMap(json, key)) {
				Method method = JSONObject.getMethod("optInt", ty(String.class));
				ret = method.invoke(json, ar(key, def));
			} else if (isArray(json, key)) {
				Method method = JSONArray.getMethod("optInt", ty(int.class));
				ret = method.invoke(json, ar(key, def));
			}
			if (ret == null) { return def; }
			Integer iret = null;
			try { iret = Integer.parseInt(String.valueOf(ret)); } catch (Exception ignore) { }
			if (iret == null) { return def; }
			return iret;
		}

		public static Object opt(Object json, Object key, Object def) throws Exception {
			if (json == null) { return def; }
			Object ret = null;
			if (isMap(json, key)) {
				Method method = JSONObject.getMethod("opt", ty(String.class));
				ret = method.invoke(json, ar(key));
			} else if (isArray(json, key)) {
				Method method = JSONArray.getMethod("opt", ty(int.class));
				ret = method.invoke(json, ar(key));
			}
			if (ret == null) { return def; }
			return ret;
		}

		public static void put(Object json, String key, Object value) throws Exception {
			if (isMap(json, key)) {
				Method method = JSONObject.getMethod("put", ty(String.class, Object.class));
				method.invoke(json, ar(key, value));
			} else if (isArray(json, key)) {
				Method method = JSONArray.getMethod("put", ty(int.class, Object.class));
				method.invoke(json, ar(key, value));
			}
		}

		public static void add(Object json, Object value) throws Exception {
			if (isArray(json, 0)) {
				Method method = JSONArray.getMethod("put", ty(Object.class));
				method.invoke(json, ar(value));
			}
		}

		public static int size(Object json) throws Exception {
			if (json == null) { return 0; }
			if (isArray(json, 0)) {
				Method method = JSONArray.getMethod("length", ty());
				return (Integer) method.invoke(json, ar());
			}
			return 0;
		}

		public static Object obj2JSON(Object obj) throws Exception {
			Object ret = null;
			if (obj instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> map = (Map<String, Object>) obj;
				ret = JSON.map();
				for (String key : map.keySet()) {
					Object item = map.get(key);
					if (item instanceof Map || item instanceof List) {
						item = JSON.obj2JSON(item);
					}
					JSON.put(ret, key, item);
				}
			} else if (obj instanceof List) {
				List<?> list = (List<?>) obj;
				ret = JSON.array();
				for (Object item : list) {
					if (item instanceof Map || item instanceof List) {
						item = JSON.obj2JSON(item);
					}
					JSON.add(ret, item);
				}
			}
			return ret;
		}
	}

	public static class JSONWriter {
		private static Class<?> cls;
		public static void init(ClassLoader loader) throws Exception {
			JSONWriter.cls = loader.loadClass("org.json.JSONWriter");
		}
		private Object writer;
		private Object json;
		public JSONWriter(Appendable writer) throws Exception {
			this.writer = writer;
			this.json = JSONWriter.cls.getConstructor(ty(Appendable.class)).newInstance(ar(writer));
		}
		public JSONWriter map() throws Exception {
			JSONWriter.cls.getMethod("object", ty()).invoke(json, ar());
			return this;
		}
		public JSONWriter endMap() throws Exception {
			JSONWriter.cls.getMethod("endObject", ty()).invoke(json, ar());
			return this;
		}
		public JSONWriter array() throws Exception {
			JSONWriter.cls.getMethod("array", ty()).invoke(json, ar());
			return this;
		}
		public JSONWriter endArray() throws Exception {
			JSONWriter.cls.getMethod("endArray", ty()).invoke(json, ar());
			return this;
		}
		public JSONWriter key(String key) throws Exception {
			JSONWriter.cls.getMethod("key", ty(String.class)).invoke(json, ar(key));
			return this;
		}
		public JSONWriter value(boolean value) throws Exception {
			JSONWriter.cls.getMethod("value", ty(boolean.class)).invoke(json, ar(value));
			return this;
		}
		public JSONWriter value(double value) throws Exception {
			JSONWriter.cls.getMethod("value", ty(double.class)).invoke(json, ar(value));
			return this;
		}
		public JSONWriter value(long value) throws Exception {
			JSONWriter.cls.getMethod("value", ty(long.class)).invoke(json, ar(value));
			return this;
		}
		public JSONWriter value(Object value) throws Exception {
			JSONWriter.cls.getMethod("value", ty(Object.class)).invoke(json, ar(value));
			return this;
		}
		@Override public String toString() { return String.valueOf(writer); }
	}

	public static void main(String[] arg) throws Exception {
		if (arg.length < 2) {
			System.out.println("USAGE: Tester {{CMD}} {{ES_PLUGIN_PATH}} {{CONTENT}}");
			System.out.println("EX: Tester input C:/ES/plugins/analysis-product  분석기테스트");
			System.exit(0);
		}
		Tester inst = new Tester();
		inst.test(arg[0], arg[1], arg[2]);
	}
}

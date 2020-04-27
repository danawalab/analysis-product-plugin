package com.danawa.util;

import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("unchecked")
public class ContextStore {
	private Map<String, Object> map;
	public ContextStore() { 
		map = new HashMap<>();
	};

	public boolean containsKey(String key) {
		synchronized (map) {
			return map.containsKey(key);
		}
	}

	public void put(String key, Object obj) {
		synchronized (map) {
			map.put(key, obj);
		}
	}

	public Double getDouble(String key, Double def) {
		synchronized (map) {
			Double ret = null;
			if (map.containsKey(key)) {
				Object obj = map.get(key);
				if (obj != null) {
					if (obj instanceof String) {
						ret = Double.parseDouble((String) obj);
					} else {
						ret = def;
					}
				}
			}
			return ret;
		}
	}

	public Float getFloat(String key, Float def) {
		synchronized (map) {
			Float ret = null;
			if (map.containsKey(key)) {
				Object obj = map.get(key);
				if (obj != null) {
					if (obj instanceof String) {
						ret = Float.parseFloat((String) obj);
					} else {
						ret = def;
					}
				}
			}
			return ret;
		}
	}

	public Long getLong(String key, Long def) {
		synchronized (map) {
			Long ret = null;
			if (map.containsKey(key)) {
				Object obj = map.get(key);
				if (obj != null) {
					if (obj instanceof String) {
						ret = Long.parseLong((String) obj);
					} else {
						ret = def;
					}
				}
			}
			return ret;
		}
	}

	public Integer getInteger(String key, Integer def) {
		synchronized (map) {
			Integer ret = null;
			if (map.containsKey(key)) {
				Object obj = map.get(key);
				if (obj != null) {
					if (obj instanceof String) {
						ret = Integer.parseInt((String) obj);
					} else {
						ret = def;
					}
				}
			}
			return ret;
		}
	}

	public String getString(String key, String def) {
		synchronized (map) {
			String ret = null;
			if (map.containsKey(key)) {
				Object obj = map.get(key);
				if (obj != null) {
					ret = String.valueOf(obj);
				} else {
					ret = def;
				}
			}
			return ret;
		}
	}

	public <T> T getAs(String key, Class<T> cls) {
		synchronized (map) {
			T ret = null;
			if (map.containsKey(key)) {
				Object obj = map.get(key);
				if (obj != null) {
					ret = (T) obj;
				}
			}
			return ret;
		}
	}
}
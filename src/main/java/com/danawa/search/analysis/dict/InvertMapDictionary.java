package com.danawa.search.analysis.dict;

import java.io.File;
import java.util.List;

import com.danawa.util.CharVector;

/**
 * Created by swsong on 2015. 7. 31..
 */
public class InvertMapDictionary extends MapDictionary {

	public InvertMapDictionary() {
	}

	public InvertMapDictionary(boolean ignoreCase) {
		super(ignoreCase);
	}

	public InvertMapDictionary(File file, boolean ignoreCase) {
		super(file, ignoreCase);
	}

	@Override
	public void addEntry(String keyword, Object[] values, List<Object> columnList) {
		if (keyword == null) {
			return;
		}
		keyword = keyword.trim();
		if (keyword.length() == 0) {
			return;
		}
		CharSequence[] value = new CharSequence[] { new CharVector(keyword) };
		for (int i = 0; i < values.length; i++) {
			map.put(new CharVector((String) values[i]), value);
		}
	}
}
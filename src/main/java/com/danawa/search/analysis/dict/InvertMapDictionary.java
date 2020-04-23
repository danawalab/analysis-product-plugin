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
	public void addEntry(CharSequence keyword, Object[] values, List<Object> columnList) {
		if (keyword == null) { return; }
		CharVector cv = CharVector.valueOf(keyword).trim();
		if (cv.length() == 0) { return; }
		CharVector[] value = new CharVector[] { cv };
		for (int i = 0; i < values.length; i++) {
			map.put(CharVector.valueOf(values[i]), value);
		}
	}
}
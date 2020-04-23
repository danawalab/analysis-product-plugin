package com.danawa.search.analysis.dict;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import com.danawa.util.CharVector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CustomDictionary extends SourceDictionary<Object> {
	private static Logger logger = LoggerFactory.getLogger(MapDictionary.class);
	private Set<CharSequence> wordSet;
	private Map<CharSequence, Object[]> map;
	
	public CustomDictionary() {
		this(false);
	}

	public CustomDictionary(boolean ignoreCase) {
		super(ignoreCase);
		map = new HashMap<>();
		wordSet = new HashSet<>();
	}

	public CustomDictionary(File file, boolean ignoreCase) {
		super(ignoreCase);
		wordSet = new HashSet<>();
		if (!file.exists()) {
			map = new HashMap<CharSequence, Object[]>();
			logger.error("사전파일이 존재하지 않습니다. file={}", file.getAbsolutePath());
			return;
		}
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			readFrom(is);
			is.close();
		} catch (IOException e) {
			logger.error("", e);
		}

	}
	
	public CustomDictionary(InputStream is, boolean ignoreCase) {
		super(ignoreCase);
		try {
			readFrom(is);
		} catch (IOException e) {
			logger.error("", e);
		}
	}
	
	public Set<CharSequence> getWordSet() {
		return wordSet;
	}
	
	public Map<CharSequence, Object[]> getUnmodifiableMap() {
		return Collections.unmodifiableMap(map);
	}

	public Map<CharSequence, Object[]> map() {
		return map;
	}

	public void setMap(Map<CharSequence, Object[]> map) {
		this.map = map;
	}
	
	@Override
	@SuppressWarnings("resource")
	public void writeTo(OutputStream out) throws IOException {
		DataOutput output = new OutputStreamDataOutput(out);
		Iterator<CharSequence> keySet = map.keySet().iterator();
		// write size of map
		output.writeVInt(map.size());
		// write key and value map
		for (; keySet.hasNext();) {
			// write key
			CharVector key = CharVector.valueOf(keySet.next());
			output.writeUString(key.array(), key.offset(), key.length());

			// write values
			Object[] values = map.get(key);
			output.writeVInt(values.length);
			for (Object value : values) {
				if (value instanceof CharSequence) {
					output.writeByte(1);
					CharVector v = CharVector.valueOf(value);
					output.writeUString(v.array(), v.offset(), v.length());
				} else if (value instanceof CharSequence[]) {
					output.writeByte(2);
					CharSequence[] list = (CharSequence[]) value;
					output.writeVInt(list.length);
					for (CharSequence v : list) {
						CharVector cv = CharVector.valueOf(v);
						output.writeUString(cv.array(), cv.offset(), cv.length());
					}
				}

			}
		}
		output.writeVInt(wordSet.size());
		Iterator<CharSequence> iterator = wordSet.iterator();
		while (iterator.hasNext()) {
			CharVector value = CharVector.valueOf(iterator.next());
			output.writeUString(value.array(), value.offset(), value.length());
		}
	}

	@Override
	@SuppressWarnings("resource")
	public void readFrom(InputStream in) throws IOException {
		DataInput input = new InputStreamDataInput(in);
		map = new HashMap<CharSequence, Object[]>();
		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			CharSequence key = new CharVector(input.readUString());
			int valueLength = input.readVInt();
			Object[] values = new Object[valueLength];
			for (int valueInx = 0; valueInx < valueLength; valueInx++) {
				int type = input.readByte();
				if (type == 1) {
					values[valueInx] = new CharVector(input.readUString());
				} else if (type == 2) {
					int len = input.readVInt();
					CharSequence[] list = new CharSequence[len];
					for (int j = 0; j < len; j++) {
						list[j] = new CharVector(input.readUString());
					}
				}
			}
			map.put(key, values);
		}
		wordSet = new HashSet<>();
		size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			wordSet.add(new CharVector(input.readUString()));
		}
	}

	@Override
	public void addEntry(CharSequence keyword, Object[] values, List<Object> columnSettingList) {
		if (keyword == null) { return; }
		CharVector cv = CharVector.valueOf(keyword).trim();
		if (cv.length() == 0) { return; }
		Object[] list = new Object[values.length];
		for (int i = 0; i < values.length; i++) {
			CharSequence value = CharVector.valueOf(values[i]);
			// FIXME:ElasticSearch 셋팅에 맞도록 재설계 필요
			// ColumnSetting columnSetting = columnSettingList.get(i);
			// String separator = columnSetting.getSeparator();
			// // separator가 존재하면 쪼개서 CharSequence[] 로 넣고 아니면 그냥 CharSequence 로 넣는다.
			// if (separator != null && separator.length() > 0) {
			// 	String[] e = value.split(separator);
			// 	// list[i] = new CharSequence[e.length];
			// 	CharSequence[] el = new CharSequence[e.length];
			// 	for (int j = 0; j < e.length; j++) {
			// 		el[j] = new CharSequence(e[j].trim());
			// 		wordSet.add(el[j]);
			// 	}
			// 	list[i] = el;
			// } else {
				list[i] = value;
				wordSet.add(value);
			// }
		}
		map.put(cv.removeWhitespaces(), list);
	}
	
	@Override
	public void addSourceLineEntry(CharSequence line) {
		String[] kv = String.valueOf(line).split("\t");
		if (kv.length == 1) {
			String value = kv[0].trim();
			addEntry(null, new Object[] { value }, null);
		} else if (kv.length == 2) {
			String keyword = kv[0].trim();
			String value = kv[1].trim();
			addEntry(keyword, new Object[] { value }, null);
		}
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if (object != null && object instanceof CustomDictionary) {
			CustomDictionary customDictionary = (CustomDictionary) object;
			this.map = customDictionary.map();
		} else {
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}
	
	public void setWordSet(Set<CharSequence> wordSet) {
		this.wordSet = wordSet;
	}
}
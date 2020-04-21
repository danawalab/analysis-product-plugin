package com.danawa.search.analysis.dict;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import com.danawa.util.CharVector;

/**
 * map 범용 사전. 
 * CharSequence : CharSequence[] pair이다.
 * 만약 value에 Object[]를 사용하길 원한다면 custom dictionary를 사용한다.
 * */
public class MapDictionary extends SourceDictionary<Object> {

    protected Map<CharSequence, CharSequence[]> map;

    public MapDictionary() {
        this(false);
    }

    public MapDictionary(boolean ignoreCase) {
        super(ignoreCase);
        map = new HashMap<>();
    }

    public MapDictionary(Map<CharSequence, CharSequence[]> map, boolean ignoreCase) {
        super(ignoreCase);
        this.map = map;
    }

    public MapDictionary(File file, boolean ignoreCase) {
        super(ignoreCase);
        if (!file.exists()) {
            map = new HashMap<>();
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

    public MapDictionary(InputStream is, boolean ignoreCase) {
        super(ignoreCase);
        try {
            readFrom(is);
        } catch (IOException e) {
            logger.error("", e);
        }
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
        CharSequence[] list = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            String value = values[i].toString();
            list[i] = CharVector.valueOf(value);
        }
        CharSequence cv = CharVector.valueOf(keyword).removeWhitespaces();
        map.put(cv, list);
    }

    public Map<CharSequence, CharSequence[]> getUnmodifiableMap() {
        return Collections.unmodifiableMap(map);
    }

    public Map<CharSequence, CharSequence[]> map() {
        return map;
    }
	
    public void setMap(Map<CharSequence, CharSequence[]> map) {
        this.map = map;
    }
	
    public boolean containsKey(CharSequence key) {
        return map.containsKey(key);
    }
	
    public CharSequence[] get(CharSequence key) {
        return map.get(key);
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
            CharSequence[] values = map.get(key);
            output.writeVInt(values.length);
            for (CharSequence v : values) {
                CharVector value = CharVector.valueOf(v);
                output.writeUString(value.array(), value.offset(), value.length());
            }
        }
    }

    @Override
    @SuppressWarnings("resource")
    public void readFrom(InputStream in) throws IOException {
        DataInput input = new InputStreamDataInput(in);
        map = new HashMap<>();
        int size = input.readVInt();
        for (int entryInx = 0; entryInx < size; entryInx++) {
            CharSequence key = CharVector.valueOf(input.readUString());

            int valueLength = input.readVInt();

            CharSequence[] values = new CharSequence[valueLength];

            for (int valueInx = 0; valueInx < valueLength; valueInx++) {
                values[valueInx] = CharVector.valueOf(input.readUString());
            }
            map.put(key, values);
        }
    }

	@Override
    public void addSourceLineEntry(String line) {
        String[] kv = line.split("\t");
        if (kv.length == 1) {
            String value = kv[0].trim();
            addEntry(null, new String[] { value }, null);
        } else if (kv.length == 2) {
            String keyword = kv[0].trim();
            String value = kv[1].trim();
            addEntry(keyword, new String[] { value }, null);
        }
    }

	@Override
    public void reload(Object object) throws IllegalArgumentException {
        if (object != null && object instanceof MapDictionary) {
            MapDictionary mapDictionary = (MapDictionary) object;
            this.map = mapDictionary.map();
        } else {
            throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
        }
    }
}
package com.danawa.search.analysis.dict;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Set;

import com.danawa.io.DataInput;
import com.danawa.io.DataOutput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.io.OutputStreamDataOutput;
import com.danawa.util.CharVector;

public class SpaceDictionary extends MapDictionary {
	
	private final static String DELIMITER = "\\s";
	private static final Pattern ptn = Pattern.compile("^[\\x00-\\x7F]*$");
	private Set<CharSequence> wordSet;

	public SpaceDictionary() {
		this(false);
	}

	public SpaceDictionary(boolean ignoreCase) {
		super(ignoreCase);
		if (wordSet == null) {
			wordSet = new HashSet<>();
		}
	}

	public SpaceDictionary(File file, boolean ignoreCase) {
		super(file, ignoreCase);
	}

	public SpaceDictionary(InputStream is, boolean ignoreCase) {
		super(is, ignoreCase);
	}

	public Set<CharSequence> getWordSet() {
		return wordSet;
	}

	public void setWordSet(Set<CharSequence> wordSet) {
		this.wordSet = wordSet;
	}

	public Set<CharSequence> getUnmodifiableWordSet() {
		return Collections.unmodifiableSet(wordSet);
	}

	@Override
	public void addEntry(CharSequence word, Object[] values, List<Object> columnList) {
		if (values == null || values.length == 0) { return; }
		String keyword = String.valueOf(values[0]).replaceAll(DELIMITER, "");
		wordSet.add(new CharVector(String.valueOf(keyword), ignoreCase));
		String[] list = String.valueOf(values[0]).split(DELIMITER);
		super.addEntry(keyword, list, columnList);
		for (int i = 0; i < list.length; i++) {
			String str = list[i].trim();
			// ASCII 골라내기
			if (!ptn.matcher(str).find()) {
				wordSet.add(new CharVector(String.valueOf(list[i]).trim(), ignoreCase));
			}
		}
	}

	@Override
	@SuppressWarnings("resource")
	public void writeTo(OutputStream out) throws IOException {
		super.writeTo(out);
		DataOutput output = new OutputStreamDataOutput(out);
		// write size of synonyms
		output.writeVInt(wordSet.size());

		// write synonyms
		Iterator<CharSequence> synonymIter = wordSet.iterator();
		for (; synonymIter.hasNext();) {
			CharVector value = CharVector.valueOf(synonymIter.next());
			output.writeUString(value.array(), value.offset(), value.length());
		}
	}

	@Override
	@SuppressWarnings("resource")
	public void readFrom(InputStream in) throws IOException {
		super.readFrom(in);
		DataInput input = new InputStreamDataInput(in);
		wordSet = new HashSet<>();
		int size = input.readVInt();
		for (int entryInx = 0; entryInx < size; entryInx++) {
			wordSet.add(new CharVector(input.readUString(), ignoreCase));
		}
	}

	@Override
	public void reload(Object object) throws IllegalArgumentException {
		if (object != null && object instanceof SpaceDictionary) {
			super.reload(object);
			SpaceDictionary spaceDictionary = (SpaceDictionary) object;
			this.wordSet = spaceDictionary.getWordSet();
		} else {
			throw new IllegalArgumentException("Reload dictionary argument error. argument = " + object);
		}
	}
}
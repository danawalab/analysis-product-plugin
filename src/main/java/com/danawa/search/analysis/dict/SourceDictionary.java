package com.danawa.search.analysis.dict;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

public abstract class SourceDictionary<E> implements ReloadableDictionary, WritableDictionary, ReadableDictionary {
	protected String label;
	protected int seq;
	protected String tokenType;
	protected static Logger logger = Loggers.getLogger(SourceDictionary.class, "");

	protected boolean ignoreCase;

	public boolean ignoreCase() {
		return ignoreCase;
	}
	public String label() {
		return label;
	}
	public int seq() {
		return seq;
	}
	public String tokenType() {
		return tokenType;
	}
	public void ignoreCase(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
	}

	public SourceDictionary(boolean ignoreCase, String label, int seq, String tokenType) {
		this.ignoreCase = ignoreCase;
		this.label = label;
		this.seq = seq;
		this.tokenType = tokenType;
	}

	public void loadSource(File file) {
		InputStream is = null;
		try {
			is = new FileInputStream(file);
			loadSource(is);
		} catch (FileNotFoundException e) {
			logger.error("사전소스파일을 찾을수 없습니다.", e);
		} finally {
			if (is != null) {
				try {
					is.close();
				} catch (IOException ignore) { }
			}
		}
	}

	public void loadSource(InputStream is) {
		try {
			BufferedReader br = new BufferedReader(new InputStreamReader(is, "utf-8"));
			String line = null;
			while ((line = br.readLine()) != null) {
				addSourceLineEntry(line);
			}
		} catch (IOException e) {
			logger.error("", e);
		}
	}

	public void addEntry(CharSequence keyword, Object[] values) {
		addEntry(keyword, values, null);
	}

	public abstract void addEntry(CharSequence keyword, Object[] values, List<E> columnSettingList);

	public abstract void addSourceLineEntry(CharSequence line);
}
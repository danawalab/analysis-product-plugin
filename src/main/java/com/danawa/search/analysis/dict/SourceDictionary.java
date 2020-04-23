package com.danawa.search.analysis.dict;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class SourceDictionary<E> implements ReloadableDictionary, WritableDictionary, ReadableDictionary {
	protected static Logger logger = LoggerFactory.getLogger(SourceDictionary.class);

	protected boolean ignoreCase;

	public SourceDictionary(boolean ignoreCase) {
		this.ignoreCase = ignoreCase;
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
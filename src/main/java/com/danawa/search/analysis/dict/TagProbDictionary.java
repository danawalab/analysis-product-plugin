package com.danawa.search.analysis.dict;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.danawa.io.DataInput;
import com.danawa.io.InputStreamDataInput;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;
import com.danawa.util.CharVector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TagProbDictionary implements Dictionary<TagProb, PreResult<CharSequence>>, ReadableDictionary {
	private static Logger logger = LoggerFactory.getLogger(TagProbDictionary.class);

	// private boolean ignoreCase;
	private Map<CharSequence, List<TagProb>> probMap;
	private Map<CharSequence, PreResult<CharSequence>> preMap;

	public TagProbDictionary(boolean ignoreCase) {
		// this.ignoreCase = ignoreCase;
		probMap = new HashMap<CharSequence, List<TagProb>>();
	}

	public TagProbDictionary(Map<CharSequence, List<TagProb>> probMap, boolean ignoreCase) {
		// this.ignoreCase = ignoreCase;
		this.probMap = new HashMap<CharSequence, List<TagProb>>(probMap);
	}

	public TagProbDictionary(File file, boolean ignoreCase) {
		// this.ignoreCase = ignoreCase;
		if (!file.exists()) {
			probMap = new HashMap<CharSequence, List<TagProb>>();
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

	public void setPreMap(Map<CharSequence, PreResult<CharSequence>> preMap) {
		this.preMap = preMap;
	}

	public void appendNounEntry(Set<CharSequence> entrySet) {
		appendPosTagEntry(entrySet, PosTag.N, TagProb.MAX_PROB);
	}

	@Override
	public void appendAdditionalNounEntry(Set<CharSequence> entrySet, String tokenType) {

		if(entrySet == null) {
			return;
		}
		
		double prob = TagProb.getProb(tokenType);
		if (prob == -1) {
			// 입력하지 않았으면 추가하지 않는다.
			return;
		}
		appendPosTagEntry(entrySet, PosTag.N, prob);
	}

	public void appendPosTagEntry(Set<CharSequence> entrySet, PosTag posTag, double prob) {
		if(entrySet == null) {
			return;
		}
		
		Iterator<CharSequence> iterator = entrySet.iterator();
		TagProb tagProb = new TagProb(posTag, prob);
		while (iterator.hasNext()) {
			CharSequence key = iterator.next();
			putAndReplaceProbMap(key, tagProb);
		}
	}

	private void putAndReplaceProbMap(CharSequence key, TagProb tagProb) {
		List<TagProb> tagProbList = probMap.get(key);
		if (tagProbList == null) {
			tagProbList = new ArrayList<TagProb>(1);
			tagProbList.add(tagProb);
			probMap.put(key, tagProbList);
		} else {
			boolean foundTag = false;
			for (int i = 0; i < tagProbList.size(); i++) {
				TagProb oldTagProb = tagProbList.get(i);
				if (oldTagProb.equals(tagProb)) {
					if (tagProb.prob() > oldTagProb.prob()) {
						// 새로 셋팅할 tagprob가 더 크면 재 셋팅필요.
						tagProbList.remove(i);
					} else {
						foundTag = true;
					}
					break;
				}
			}
			if (!foundTag) {
				tagProbList.add(tagProb);
			}
		}
	}

	// 최종사용시 수정불가 맵으로 변환하여 넘겨준다.
	public Map<CharSequence, List<TagProb>> getUnmodifiableDictionary() {
		return Collections.unmodifiableMap(probMap);
	}

	@Override
	public void readFrom(InputStream in) throws IOException {

		DataInput input = null;
		try {
			input = new InputStreamDataInput(in);
			probMap = new HashMap<CharSequence, List<TagProb>>();
			
			int size = input.readInt();
	
			for (int entryInx = 0; entryInx < size; entryInx++) {
				CharSequence key = new CharVector(input.readString());
	
				int probLength = input.readInt();
	
				List<TagProb> probs = new ArrayList<TagProb>(probLength);
	
				for (int proInx = 0; proInx < probLength; proInx++) {
					probs.add(new TagProb(PosTag.valueOf(input.readString()), input.readDouble()));
				}
	
				probMap.put(key, probs);
			}
		} finally {
			if(input!=null) try {
				input.close();
			} catch (IOException ignore) { }
		}
	}

	@Override
	public List<TagProb> find(CharSequence token) {
		return probMap.get(token);
	}

	@Override
	public PreResult<CharSequence> findPreResult(CharSequence token) {
		if (preMap != null) {
			return preMap.get(token);
		}
		return null;
	}

	@Override
	public int size() {
		return probMap.size();
	}

	@Override
	public void setPreDictionary(Map<CharSequence, PreResult<CharSequence>> map) {
		this.preMap = map;
	}
}
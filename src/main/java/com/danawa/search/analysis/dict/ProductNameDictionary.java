package com.danawa.search.analysis.dict;

import com.danawa.search.analysis.korean.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.korean.PreResult;

public class ProductNameDictionary extends CommonDictionary<TagProb, PreResult<CharSequence>> {
	public ProductNameDictionary(Dictionary<TagProb, PreResult<CharSequence>> systemDictionary) {
		super(systemDictionary);
	}
}

package com.danawa.search.analysis.dict;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface Dictionary<T, P> {
	public abstract List<T> find(CharSequence token);

	public abstract P findPreResult(CharSequence token);

	public abstract void setPreDictionary(Map<CharSequence, P> map);

	public abstract int size();

	public abstract void appendAdditionalNounEntry(Set<CharSequence> set, String tokenType);
}
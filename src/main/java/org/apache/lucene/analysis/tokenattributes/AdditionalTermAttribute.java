package org.apache.lucene.analysis.tokenattributes;

import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.Attribute;

public interface AdditionalTermAttribute extends Attribute {
	
	public void init(TokenStream tokenStream);
	
	public void addAdditionalTerm(String additionalTerm, String type,
			@SuppressWarnings("rawtypes") List synonyms, int subSize, int start, int end);
	
	public int subSize();
	
	public int size();

	public Iterator<String> iterateAdditionalTerms();
}

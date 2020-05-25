package org.apache.lucene.analysis.tokenattributes;

import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.Attribute;

public interface ExtraTermAttribute extends Attribute {
	
	public void init(TokenStream tokenStream);
	
	public void addAdditionalTerm(String additionalTerm, String type,
		List<CharSequence> synonyms, int subSize, int start, int end);
	
	public int subSize();
	
	public int size();

	public Iterator<String> iterator();
}

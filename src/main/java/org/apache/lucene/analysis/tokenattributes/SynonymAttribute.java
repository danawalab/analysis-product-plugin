package org.apache.lucene.analysis.tokenattributes;

import java.util.List;

import org.apache.lucene.util.Attribute;

/**
 */
public interface SynonymAttribute extends Attribute {
	public void setSynonyms(List<?> synonym);
	public List<?> getSynonyms();
}

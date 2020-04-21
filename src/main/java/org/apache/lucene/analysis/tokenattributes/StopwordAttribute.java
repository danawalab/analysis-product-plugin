package org.apache.lucene.analysis.tokenattributes;

import org.apache.lucene.util.Attribute;

/**
 */
public interface StopwordAttribute extends Attribute {
	public void setStopword(boolean isStopword);
	public boolean isStopword();
}

package org.apache.lucene.analysis.tokenattributes;

import com.danawa.search.analysis.dict.PosTag;
import com.danawa.util.CharVector;

import org.apache.lucene.util.Attribute;

public interface TokenInfoAttribute extends Attribute {
	public void ref(char[] buffer, int offset, int length);
	public CharVector ref();
	public void offset(int offset, int length);
	public void posTag(PosTag posTag);
	public PosTag posTag();
}

package org.apache.lucene.analysis.tokenattributes;

import com.danawa.util.CharVector;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class TokenInfoAttributeImpl extends AttributeImpl implements TokenInfoAttribute {

	private CharVector charVector = null;

	@Override
	public void setCharVector(char[] buffer, int offset, int length) {
		charVector = new CharVector(buffer, offset, length);
	}

	@Override
	public void setOffset(int offset, int length) {
		charVector.offset(offset);
		charVector.length(length);
	}

	@Override
	public CharVector charVector() {
		return charVector;
	}

	@Override
	public String toString() {
		return String.valueOf(charVector);
	}

	@Override
	public void copyTo(AttributeImpl attr) {
		if (attr instanceof TokenInfoAttribute) {
			TokenInfoAttribute target = (TokenInfoAttribute) attr;
			target.setCharVector(charVector.array(), charVector.offset(), charVector.length());
		}
	}

	@Override 
	public void clear() { 
		charVector = new CharVector();
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(TokenInfoAttribute.class, "charVector", charVector);
	}
}
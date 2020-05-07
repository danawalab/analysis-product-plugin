package org.apache.lucene.analysis.tokenattributes;

import com.danawa.util.CharVector;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class TokenInfoAttributeImpl extends AttributeImpl implements TokenInfoAttribute {

	private CharVector ref = null;

	@Override
	public void ref(char[] buffer, int offset, int length) {
		ref = new CharVector(buffer, offset, length);
	}

	@Override
	public void setOffset(int offset, int length) {
		ref.offset(offset);
		ref.length(length);
	}

	@Override
	public CharVector ref() {
		return ref;
	}

	@Override
	public String toString() {
		return String.valueOf(ref);
	}

	@Override
	public void copyTo(AttributeImpl attr) {
		if (attr instanceof TokenInfoAttribute) {
			TokenInfoAttribute target = (TokenInfoAttribute) attr;
			target.ref(ref.array(), ref.offset(), ref.length());
		}
	}

	@Override 
	public void clear() { 
		ref = new CharVector();
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(TokenInfoAttribute.class, "ref", ref);
	}
}
package org.apache.lucene.analysis.tokenattributes;

import com.danawa.search.analysis.dict.PosTag;
import com.danawa.util.CharVector;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class TokenInfoAttributeImpl extends AttributeImpl implements TokenInfoAttribute {

	private CharVector ref;
	private PosTag posTag;
	private int state;

	@Override
	public void ref(char[] buffer, int offset, int length) {
		ref = new CharVector(buffer, offset, length);
	}

	@Override
	public void offset(int offset, int length) {
		ref.offset(offset);
		ref.length(length);
	}

	@Override
	public CharVector ref() {
		return ref;
	}

	@Override
	public void posTag(PosTag posTag) {
		this.posTag = posTag;
	}

	@Override
	public PosTag posTag() {
		return posTag;
	}

	@Override
	public void state(int state) {
		this.state = state;
	}

	@Override
	public int state() {
		return state;
	}

	@Override
	public boolean isState(int flag) {
		return (state & flag) != 0;
	}

	@Override
	public void addState(int flag) {
		state = state | flag;
	}

	@Override
	public void rmState(int flag) {
		state = state & ~flag;
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
			target.posTag(posTag);
			target.state(state);
		}
	}

	@Override
	public void clear() {
		ref = new CharVector();
		posTag = null;
		state = STATE_READY;
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(TokenInfoAttribute.class, "ref", ref);
		reflector.reflect(TokenInfoAttribute.class, "posTag", posTag);
		reflector.reflect(TokenInfoAttribute.class, "state", state);
	}
}
package org.apache.lucene.analysis.tokenattributes;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class StopwordAttributeImpl extends AttributeImpl implements StopwordAttribute {

	private boolean isStopword;

	public StopwordAttributeImpl() {
	}

	public StopwordAttributeImpl(boolean isStopword) {
		this.isStopword = isStopword;
	}

	@Override
	public void clear() {
		isStopword = false;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if (other instanceof StopwordAttributeImpl) {
			final StopwordAttributeImpl o = (StopwordAttributeImpl) other;
			return (this.isStopword == o.isStopword);
		}
		return false;
	}

	@Override
	public int hashCode() {
		return isStopword ? 1 : 0;
	}

	@Override
	public void copyTo(AttributeImpl target) {
		StopwordAttribute t = (StopwordAttribute) target;
		t.setStopword(isStopword);
	}

	@Override
	public void setStopword(boolean isStopword) {
		this.isStopword = isStopword;
	}

	@Override
	public boolean isStopword() {
		return isStopword;
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(StopwordAttribute.class, "isStopword", isStopword);
	}
}
package org.apache.lucene.analysis.tokenattributes;

import com.danawa.search.analysis.dict.PosTag;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class PosTagAttributeImpl extends AttributeImpl implements PosTagAttribute {

	private PosTag posTag;

	@Override
	public void setPosTag(PosTag posTag) {
		this.posTag = posTag;
	}

	@Override
	public PosTag posTag() {
		return posTag;
	}

	@Override
	public void clear() {
		posTag = null;
	}

	@Override
	public void copyTo(AttributeImpl target) {
		if (target instanceof PosTagAttributeImpl) {
			PosTagAttributeImpl attr = (PosTagAttributeImpl) target;
			attr.posTag = posTag;
		}
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(PosTagAttribute.class, "posTag", posTag);
	}
}

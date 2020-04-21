package org.apache.lucene.analysis.tokenattributes;

import java.util.List;

import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;

public class SynonymAttributeImpl extends AttributeImpl implements SynonymAttribute {
	private List<?> synonym;

	public SynonymAttributeImpl() { }

	public SynonymAttributeImpl(List<?> synonym) {
		this.synonym = synonym;
	}

	@Override
	public void clear() {
		synonym = null;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}
		if (other instanceof SynonymAttributeImpl) {
			final SynonymAttributeImpl o = (SynonymAttributeImpl) other;
			return (this.synonym == null ? o.synonym == null : this.synonym.equals(o.synonym));
		}
		return false;
	}

	@Override
	public int hashCode() {
		return (synonym == null) ? 0 : synonym.hashCode();
	}

	@Override
	public void copyTo(AttributeImpl target) {
		SynonymAttribute t = (SynonymAttribute) target;
		t.setSynonyms(synonym);
	}

	@Override
	public void setSynonyms(List<?> synonym) {
		if (synonym == null || synonym.size() == 0) {
			this.synonym = null;
		} else {
			this.synonym = synonym;
		}
	}

	@Override
	public List<?> getSynonyms() {
		if (synonym == null || synonym.size() == 0) {
			return null;
		}
		return synonym;
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(SynonymAttribute.class, "synonym", synonym);
	}
}
package org.apache.lucene.analysis.tokenattributes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AdditionalTermAttributeImpl extends AttributeImpl implements AdditionalTermAttribute {

	private static final Logger logger = LoggerFactory.getLogger(AdditionalTermAttributeImpl.class);

	private List<String> additionalTerms = new ArrayList<String>();
	private List<String> types = new ArrayList<String>();
	private List<int[]> offsets = new ArrayList<int[]>();
	@SuppressWarnings("rawtypes")
	private List synonyms;
	private OffsetAttribute offsetAttribute;
	private TypeAttribute typeAttribute;
	private SynonymAttribute synonymAttribute;
	private int subLength;

	@Override
	public void init(TokenStream tokenStream) {
		if (tokenStream != null) {
			if (tokenStream.hasAttribute(OffsetAttribute.class)) {
				this.offsetAttribute = tokenStream.getAttribute(OffsetAttribute.class);
			}
			if (tokenStream.hasAttribute(TypeAttribute.class)) {
				this.typeAttribute = tokenStream.getAttribute(TypeAttribute.class);
			}
			if (tokenStream.hasAttribute(SynonymAttribute.class)) {
				this.synonymAttribute = tokenStream.getAttribute(SynonymAttribute.class);
			}
		}

		this.additionalTerms.clear();
		this.types.clear();
		this.offsets.clear();
		this.subLength = 0;
	}

	@Override
	public void clear() {
		this.additionalTerms.clear();
		this.types.clear();
		this.offsets.clear();
		this.subLength = 0;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}

		if (other instanceof AdditionalTermAttributeImpl) {
			final AdditionalTermAttributeImpl o = (AdditionalTermAttributeImpl) other;
			return (this.additionalTerms == null ? o.additionalTerms == null
					: this.additionalTerms.equals(o.additionalTerms));
		}

		return false;
	}

	@Override
	public int hashCode() {
		return (additionalTerms == null) ? 0 : additionalTerms.hashCode();
	}

	@Override
	public void copyTo(AttributeImpl target) {
		AdditionalTermAttribute t = (AdditionalTermAttribute) target;
		for (int inx = 0; inx < additionalTerms.size(); inx++) {
			String term = additionalTerms.get(inx);
			String type = types.get(inx);
			@SuppressWarnings("rawtypes")
			List synonyms = this.synonyms;
			int[] offset = offsets.get(inx);
			int subLength = this.subLength;
			t.addAdditionalTerm(term, type, synonyms, subLength, offset[0], offset[1]);
		}
	}

	@Override
	public void addAdditionalTerm(String additionalTerm, String type, @SuppressWarnings("rawtypes") List synonyms,
			int subLength, int start, int end) {
		logger.trace("add additional {}", additionalTerm);
		this.additionalTerms.add(additionalTerm);
		this.types.add(type);
		this.synonyms = synonyms;
		this.offsets.add(new int[] { start, end });
		this.subLength = subLength;
	}

	@Override
	public Iterator<String> iterateAdditionalTerms() {
		return new Iterator<String>() {

			@Override
			public boolean hasNext() {
				return additionalTerms != null && additionalTerms.size() > 0;
			}

			@Override
			public String next() {
				String term = additionalTerms.remove(0);
				String type = types.remove(0);
				int[] offset = offsets.remove(0);
				if (offsetAttribute != null) {
					offsetAttribute.setOffset(offset[0], offset[1]);
				}
				if (typeAttribute != null) {
					typeAttribute.setType(type);
				}
				if (synonymAttribute != null) {
					synonymAttribute.setSynonyms(synonyms);
				}
				return term;
			}

			@Override
			public void remove() {
				additionalTerms.remove(0);
			}
		};
	}

	@Override
	public int size() {
		return additionalTerms.size();
	}

	@Override
	public int subSize() {
		return subLength;
	}

	public void cloneTo(AdditionalTermAttributeImpl target) {
		target.additionalTerms = this.additionalTerms;
		target.types = this.types;
		target.offsets = this.offsets;
		target.synonyms = this.synonyms;
		// target.offsetAttribute = offsetAttribute;
		// target.typeAttribute = typeAttribute;
		// target.synonymAttribute = this.synonymAttribute;
	}

	@Override
	public String toString() {
		return additionalTerms.toString();
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {

	}
}

package org.apache.lucene.analysis.tokenattributes;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.util.AttributeImpl;
import org.apache.lucene.util.AttributeReflector;
import org.elasticsearch.common.logging.Loggers;

public class ExtraTermAttributeImpl extends AttributeImpl implements ExtraTermAttribute {

	private static final Logger logger = Loggers.getLogger(ExtraTermAttributeImpl.class, "");

	private List<String> extraTerms = new ArrayList<String>();
	private List<String> types = new ArrayList<String>();
	private List<int[]> offsets = new ArrayList<int[]>();
	private List<CharSequence> synonyms;
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

		this.extraTerms.clear();
		this.types.clear();
		this.offsets.clear();
		this.subLength = 0;
	}

	@Override
	public void clear() {
		this.extraTerms.clear();
		this.types.clear();
		this.offsets.clear();
		this.subLength = 0;
	}

	@Override
	public boolean equals(Object other) {
		if (other == this) {
			return true;
		}

		if (other instanceof ExtraTermAttributeImpl) {
			final ExtraTermAttributeImpl o = (ExtraTermAttributeImpl) other;
			return (this.extraTerms == null ? o.extraTerms == null
					: this.extraTerms.equals(o.extraTerms));
		}

		return false;
	}

	@Override
	public int hashCode() {
		return (extraTerms == null) ? 0 : extraTerms.hashCode();
	}

	@Override
	public void copyTo(AttributeImpl target) {
		ExtraTermAttribute t = (ExtraTermAttribute) target;
		for (int inx = 0; inx < extraTerms.size(); inx++) {
			String term = extraTerms.get(inx);
			String type = types.get(inx);
			List<CharSequence> synonyms = this.synonyms;
			int[] offset = offsets.get(inx);
			int subLength = this.subLength;
			t.addExtraTerm(term, type, synonyms, subLength, offset[0], offset[1]);
		}
	}

	@Override
	public void addExtraTerm(String extraTerm, String type, List<CharSequence> synonyms,
			int subLength, int start, int end) {
		logger.trace("add extra {}", extraTerm);
		this.extraTerms.add(extraTerm);
		this.types.add(type);
		this.synonyms = synonyms;
		this.offsets.add(new int[] { start, end });
		this.subLength = subLength;
	}

	@Override
	public Iterator<String> iterator() {
		return new Iterator<String>() {

			@Override
			public boolean hasNext() {
				return extraTerms != null && extraTerms.size() > 0;
			}

			@Override
			public String next() {
				String term = extraTerms.remove(0);
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
				extraTerms.remove(0);
			}
		};
	}

	@Override
	public int size() {
		return extraTerms.size();
	}

	@Override
	public int subSize() {
		return subLength;
	}

	public void cloneTo(ExtraTermAttributeImpl target) {
		target.extraTerms = this.extraTerms;
		target.types = this.types;
		target.offsets = this.offsets;
		target.synonyms = this.synonyms;
		// target.offsetAttribute = offsetAttribute;
		// target.typeAttribute = typeAttribute;
		// target.synonymAttribute = this.synonymAttribute;
	}

	@Override
	public String toString() {
		return extraTerms.toString();
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {

	}
}

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

	private List<String> extraTerms = new ArrayList<>();
	private List<List<CharSequence>> synonyms = new ArrayList<>();
	private SynonymAttribute synonymAttribute;

	@Override
	public void init(TokenStream tokenStream) {
		if (tokenStream != null) {
			if (tokenStream.hasAttribute(SynonymAttribute.class)) {
				this.synonymAttribute = tokenStream.getAttribute(SynonymAttribute.class);
			}
		}

		this.extraTerms.clear();
		this.synonyms.clear();
	}

	@Override
	public void clear() {
		this.extraTerms.clear();
		this.synonyms.clear();
	}

	@Override
	public void addExtraTerm(String extraTerm, List<CharSequence> synonyms) {
		logger.trace("add extra {}", extraTerm);
		this.extraTerms.add(extraTerm);
		this.synonyms.add(synonyms);
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
				if (synonymAttribute != null) {
					synonymAttribute.setSynonyms(synonyms.remove(0));
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
	public String toString() {
		return extraTerms.toString();
	}

	@Override
	public int hashCode() {
		return (extraTerms == null) ? 0 : extraTerms.hashCode();
	}

	@Override
	public boolean equals(Object target) {
		if (target == this) { return true; }
		if (target instanceof ExtraTermAttributeImpl) {
			final ExtraTermAttributeImpl o = (ExtraTermAttributeImpl) target;

			if (o.extraTerms != null &&  o.extraTerms.size() != extraTerms.size()) {
				return false;
			}
			for (int inx = 0; inx < o.extraTerms.size(); inx++) {
				if (!o.extraTerms.get(inx).equals(extraTerms.get(inx))) {
					return false;
				}
			}
			if (o.synonyms != null &&  o.synonyms.size() != synonyms.size()) {
				return false;
			}
			for (int inx = 0; inx < o.synonyms.size(); inx++) {
				if (!o.synonyms.get(inx).equals(synonyms.get(inx))) {
					return false;
				}
			}
			return true;
		}
		return false;
	}

	@Override
	public void copyTo(AttributeImpl target) {
		if (target == this) { return; }
		if (target instanceof ExtraTermAttributeImpl) {
			ExtraTermAttributeImpl o = (ExtraTermAttributeImpl) target;
			o.extraTerms.clear();
			o.extraTerms.addAll(extraTerms);
			o.synonyms.clear();
			o.synonyms.addAll(synonyms);
			o.synonymAttribute = synonymAttribute;
		}
	}

	@Override
	public void reflectWith(AttributeReflector reflector) {
		reflector.reflect(ExtraTermAttribute.class, "extraTerms", extraTerms );
		reflector.reflect(ExtraTermAttribute.class, "synonyms", synonyms );
		reflector.reflect(ExtraTermAttribute.class, "synonymAttribute", synonymAttribute);
	}
}
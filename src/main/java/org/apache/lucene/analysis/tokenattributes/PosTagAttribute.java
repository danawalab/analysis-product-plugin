package org.apache.lucene.analysis.tokenattributes;

import com.danawa.search.analysis.dict.PosTag;
import org.apache.lucene.util.Attribute;

public interface PosTagAttribute extends Attribute {
	public void setPosTag(PosTag posTag);
	public PosTag posTag();
}

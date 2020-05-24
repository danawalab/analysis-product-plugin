package org.apache.lucene.analysis.tokenattributes;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.korean.PosTagProbEntry.PosTag;
import com.danawa.util.CharVector;

import org.apache.lucene.util.Attribute;

public interface TokenInfoAttribute extends Attribute {

	public static final int STATE_INPUT_READY = 0x0000;
	public static final int STATE_INPUT_BUFFER_EXHAUSTED = 0x0001;
	public static final int STATE_INPUT_FINISHED = 0x0002;
	public static final int STATE_TERM_STOP = 0x0004;

	public void ref(char[] buffer, int offset, int length);
	public CharVector ref();
	public void offset(int offset, int length);
	public void posTag(PosTag posTag);
	public PosTag posTag();
	public void state(int state);
	public int state();
	public boolean isState(int flag);
	public void addState(int flag);
	public void rmState(int flag);
	public void dictionary(ProductNameDictionary dictionary);
	public void baseOffset(int baseOffset);
	public int baseOffset();
	public ProductNameDictionary dictionary();
}
package org.apache.lucene.analysis.tokenattributes;

import com.danawa.search.analysis.dict.PosTag;
import com.danawa.util.CharVector;

import org.apache.lucene.util.Attribute;

public interface TokenInfoAttribute extends Attribute {

	public static final int STATE_READY = 0x0000;
	public static final int STATE_BUFFER_EXHAUSTED = 0x0001;
	public static final int STATE_INPUT_FINISHED= 0x0002;

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
}
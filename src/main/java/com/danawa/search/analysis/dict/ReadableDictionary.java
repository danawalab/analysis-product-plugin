package com.danawa.search.analysis.dict;

import java.io.InputStream;
import java.io.IOException;

public interface ReadableDictionary {
	public void readFrom(InputStream in) throws IOException;
}

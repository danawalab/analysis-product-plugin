package com.danawa.search.analysis.product;

import java.io.IOException;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;

public class CaseIgnoreFilter extends TokenFilter {
    protected CaseIgnoreFilter(TokenStream input) {
        super(input);
    }

    @Override
    public boolean incrementToken() throws IOException {
        // 원본텀과 대문자텀을 같이 내보낸다
        // input.incrementToken()

        return false;
    }
}
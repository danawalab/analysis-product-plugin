package com.danawa.search.analysis.dict;

/**
 * 영문은 I.O.C와 같이 마침표가 포함될수 있다.
 * 영문숫자조합은 ABC-123과 같이 하이픈이 포함될수 있다. 상품명등에 사용됨.
 * */
//SOE : start of Eojul
//EOE : end of Eojul
public enum PosTag {
	SOE, UNK, N, V, M, IC, J, E, EP, X, ALPHA/*영문*/, XPN, EOE, GUESS, DIGIT, SYMBOL
}

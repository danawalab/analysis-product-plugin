package com.danawa.search.analysis.product;

public class AnalyzerOption {
	private boolean useStopword;
	private boolean useSynonym;
	private boolean forQueryPurpose; // 문서색인용도인지, 색인용도인지.

	public static final AnalyzerOption DEFAULT_OPTION = new AnalyzerOption();

	public boolean useStopword() {
		return useStopword;
	}

	public void useStopword(boolean useStopword) {
		this.useStopword = useStopword;
	}

	public boolean useSynonym() {
		return useSynonym;
	}

	public void useSynonym(boolean useSynonym) {
		this.useSynonym = useSynonym;
	}

	public void setForDocument() {
		this.forQueryPurpose = false;
	}

	public void setForQuery() {
		this.forQueryPurpose = true;
	}

	public boolean isForDocument() {
		return forQueryPurpose == false;
	}

	public boolean isForQuery() {
		return forQueryPurpose == true;
	}
}
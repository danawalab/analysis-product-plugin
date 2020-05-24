package com.danawa.search.analysis.product;

public class AnalyzerOption {
	private boolean useStopword;
	private boolean useSynonym;
	private boolean forQuery;

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

	public void useForQuery(boolean  forQuery) {
		this.forQuery = forQuery;
	}

	public boolean useForQuery() {
		return this.forQuery;
	}
}
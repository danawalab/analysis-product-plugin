package com.danawa.search.analysis.product;

public class AnalyzerOption {
	private boolean useForQuery;
	private boolean useSynonym;
	private boolean useStopword;
	private boolean useFullString;

	public AnalyzerOption() {
		this(false, true, true, false);
	}

	public AnalyzerOption(boolean useForQuery, boolean useSynonym, boolean useStopword, boolean useFullString) {
		this.useForQuery = useForQuery;
		this.useSynonym = useSynonym;
		this.useStopword = useStopword;
		this.useFullString = useFullString;
	}

	public void useForQuery(boolean  forQuery) {
		this.useForQuery = forQuery;
	}

	public boolean useForQuery() {
		return this.useForQuery;
	}

	public boolean useSynonym() {
		return useSynonym;
	}

	public void useSynonym(boolean useSynonym) {
		this.useSynonym = useSynonym;
	}

	public boolean useStopword() {
		return useStopword;
	}

	public void useStopword(boolean useStopword) {
		this.useStopword = useStopword;
	}

	public boolean useFullString() {
		return useFullString;
	}

	public void useFullString(boolean useFullString) {
		this.useFullString = useFullString;
	}

	@Override public String toString() {
		return "[" + useForQuery + "," + useSynonym + "," + useStopword + "," + useFullString + "]";
	}
}
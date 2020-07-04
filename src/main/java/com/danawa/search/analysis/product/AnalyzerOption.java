package com.danawa.search.analysis.product;

public class AnalyzerOption {
	private boolean useForQuery;
	private boolean useSynonym;
	private boolean useStopword;
	private boolean useFullString;
	private boolean toUppercase;

	public AnalyzerOption() {
		this(false, true, true, false, false);
	}

	public AnalyzerOption(boolean useForQuery, boolean useSynonym, boolean useStopword, boolean useFullString, boolean toUppercase) {
		this.useForQuery = useForQuery;
		this.useSynonym = useSynonym;
		this.useStopword = useStopword;
		this.useFullString = useFullString;
		this.toUppercase = toUppercase;
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

	public boolean toUppercase() {
		return toUppercase;
	}

	public void toUppercase(boolean toUppercase) {
		this.toUppercase = toUppercase;
	}

	@Override public String toString() {
		return "[" + useForQuery + "," + useSynonym + "," + useStopword + "," + useFullString + "," + toUppercase + "]";
	}
}
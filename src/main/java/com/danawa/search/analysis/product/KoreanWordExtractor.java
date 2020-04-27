package com.danawa.search.analysis.product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

import com.danawa.search.analysis.dict.PosTag;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.dict.PosTagProbEntry;
import com.danawa.search.analysis.dict.PreResult;
import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.CharVector;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

public class KoreanWordExtractor {
	private static Logger logger = Loggers.getLogger(KoreanWordExtractor.class, "");

	private ProductNameDictionary koreanDict;

	private PosTagProbEntry[][] tabular;
	private int[] status;
	private Set<CharVector> josaSet;
	boolean isDebug;
	// 최상의 후보가 만들어지면 바로 리턴. 기본 true;
	boolean fastResultOption = true;
	// 발견되었는지 여부.
	boolean fastResultFound;
	int remnantOffset;
	int remnantLength;
	boolean hasRemnant;
	boolean isUnicode;

	private PriorityQueue<Entry> queue = new PriorityQueue<Entry>(8, new Comparator<Entry>() {
		@Override
		public int compare(Entry o1, Entry o2) {
			return (int) (o2.totalScore() - o1.totalScore());
		}
	});
	
	private static final int QUEUE_MAX = 200;
	private static final int RESULT_MAX = 10;
	private List<Entry> result = new ArrayList<>();

	protected char[] source;
	protected int offset;
	protected int length;

	public KoreanWordExtractor(ProductNameDictionary koreanDict) {
		this(koreanDict, 12);
	}

	public KoreanWordExtractor(ProductNameDictionary koreanDict, int tabularSize) {
		tabular = new PosTagProbEntry[tabularSize][];
		// tabular 파싱 초기화.
		for (int row = 0; row < tabular.length; row++) {
			tabular[row] = new PosTagProbEntry[row + 2];
		}
		status = new int[tabularSize];
		this.koreanDict = koreanDict;
		josaSet = new HashSet<CharVector>();
		String josaList = "은 는 이 가 을 를 에 과 와 의 로 만 께 에게 에서 으로 부터 라서 라고 께서 한테 처럼 같이 라는 하며 하고 까지 이라고 이라는 이라도 이라면 에서도 이기도";
		String[] jl = josaList.split("\\s");
		for (String j : jl) {
			josaSet.add(new CharVector(j));
		}
	}

	public ProductNameDictionary dictionary() {
		return koreanDict;
	}

	public void setKoreanDic(ProductNameDictionary koreanDict) {
		this.koreanDict = koreanDict;
	}

	public void setDebug(boolean isDebug) {
		this.isDebug = isDebug;
	}

	public void setFastResultOption(boolean fastResultOption) {
		this.fastResultOption = fastResultOption;
	}

	public void showTabular() {
		for (int row = 0; row < length; row++) {
			PosTagProbEntry[] el = tabular[row];
			int count = status[row];
			StringBuilder sb = new StringBuilder();
			sb.append("{ ");
			sb.append(count);
			sb.append(" }");
			sb.append(" | ");
			for (int column = 1; column <= row + 1; column++) {
				PosTagProbEntry e = el[column];
				sb.append(new String(source, row - column + 1 + offset, column));
				sb.append("[");
				while (e != null) {
					sb.append(e.get());
					e = e.next();
				}
				sb.append("]");
				sb.append(" | ");
			}
			logger.debug("{}", sb);
		}
	}

	/*
	 * 음절을 조합하여 사전에서 찾아준다. 찾은 단어와 tag는 table에 저장한다.
	 */
	private Entry doSegment() {
		CharVector cv = new CharVector(source, offset, length);
		// 길이가 1~2 단어는 완전매칭이 아니면 UNK이다.
		if (length == 1) {
			// FIXME 나중엔 뺀다. extractor를 제일 먼저받을 것이기 때문..
			// 조사이면 전체 조사로.
			List<TagProb> tag = koreanDict.find(cv);
			if (tag != null) {
				return new Entry(length - 1, length, tag.get(0), offset);
			}
			if (isDigit(cv)) {
				return new Entry(length - 1, length, TagProb.DIGIT, offset);
			} else if (isSymbol(cv)) {
				return new Entry(length - 1, length, TagProb.SYMBOL, offset);
			}
			return new Entry(length - 1, length, TagProb.UNK, offset);
		}
		List<TagProb> tag = koreanDict.find(cv);
		if (tag != null) {
			return new Entry(length - 1, length, tag.get(0), offset);
		}
		int start = length - 1;
		for (int row = start; row >= 0; row--) {
			for (int column = row + 1; column >= 1; column--) {
				cv.init(offset + row - column + 1, column);
				List<TagProb> tagList = null;
				boolean isAlpha = false;
				if (isDigit(cv)) {
					// 무조건 셋팅.
					tabular[row][column] = new PosTagProbEntry(TagProb.DIGIT);
					status[row]++;
				} else if (isSymbol(cv)) {
					tabular[row][column] = new PosTagProbEntry(TagProb.SYMBOL);
					status[row]++;
				} else {
					if (isAlpha(cv)) {
						if (column == 1) {
							tabular[row][column] = new PosTagProbEntry(TagProb.ALPHA);
							/* 2019.3.28 @swsong ALPHA 는 분석발견이 아니라고 가정한다. */
							continue;
						}
						// 길이가 2이상이면 사전에서 확인해본다.
						isAlpha = true;
					}
					tagList = koreanDict.find(cv);
					if (tagList != null) {
						if (column == length) {
							// 완전일치시
							// logger.debug("Exact match {}", cv);
							return new Entry(row, column, tagList.get(0), offset);
						}
						PosTagProbEntry chainedEntry = null;
						for (int i = 0; i < tagList.size(); i++) {
							TagProb tagProb = tagList.get(i);
							if (i == 0) {
								chainedEntry = new PosTagProbEntry(tagProb);
								tabular[row][column] = chainedEntry;
							} else {
								chainedEntry = chainedEntry.next(tagProb);
							}
						}
						status[row]++;
					} else {

						if (isAlpha) {
							// 영문자이면 null이 아닌 ALPHA로 셋팅한다.
							tabular[row][column] = new PosTagProbEntry(TagProb.ALPHA);
						} else {
							tabular[row][column] = null;
						}
					}

					if (column < 3) {
						// 1글자가 조사인지.
						if (josaSet.contains(cv)) {
							PosTagProbEntry entry = new PosTagProbEntry(TagProb.JOSA);
							if (tabular[row][column] == null) {
								tabular[row][column] = entry;
							} else {
								entry.next = tabular[row][column];
								tabular[row][column] = entry;
							}
							status[row]++;
						}
					}
				}
			}
		}
		return null;
	}

	// 유니코드 블럭인지 판단
	private boolean isUnicode(char[] buffer, int offset, int length) {
		for (int inx = 0; inx < length; inx++) {
			char ch = buffer[offset + inx];
			if (ch < 128) {
				return false;
			}
		}
		return true;
	}

	// 단어가 전부숫자인지.
	private boolean isDigit(CharVector cv) {
		for (int i = 0; i < cv.length(); i++) {
			char ch = cv.charAt(i);
			if (ch >= '0' && ch <= '9') {
				// 숫자면 다음을 본다.
			} else {
				return false;
			}
		}
		return true;
	}
	
	private boolean isAlpha(CharVector cv) {
		for (int i = 0; i < cv.length(); i++) {
			char ch = cv.charAt(i);
			if ((ch >= 'a' && ch <= 'z') || ch >= 'A' && ch <= 'Z') {
				// 다음을 본다.
			} else {
				return false;
			}
		}
		return true;
	}

	private boolean isSymbol(CharVector cv) {
		for (int i = 0; i < cv.length(); i++) {
			int chInt = cv.charAt(i);
			if (!Character.isLetterOrDigit(chInt)) {
				// 다음을 본다.
			} else {
				return false;
			}
		}
		return true;
	}
	
	private void makeResult() {
		int headRow = -1;
		for (int row = length - 1; row >= 0; row--) {
			if (status[row] > 0) {
				// 분석결과가 존재하는지.
				headRow = row;
				break;
			}
		}

		try {
			if (headRow == -1) {
				// 통째 미등록어.
				addResult(new Entry(length - 1, length, TagProb.UNK, offset));
				return;
			}
			// 최초 char부터의 단어매칭이 없다면.
			// 예를들어 "대한민국"분석시 "대한"만 사전에 있어서 "민국"은 결과가 없을경우.
			if (headRow < length - 1) {
				// 뒷부분을 미등록어로 처리한다. "대한(N)+민국(UNK)" 이 된다.
				Entry tail = new Entry(length - 1, length - 1 - headRow, TagProb.UNK, offset);
				connectAllTo(headRow, tail);
			} else {
				connectAllTo(headRow, null);
			}
			Entry tail = null;
			while ((tail = queue.poll()) != null) {
				int connectRow = tail.row() - tail.column();
				if (status[connectRow] > 0) {
					connectAllTo(connectRow, tail);
				} else {
					//
					// TODO 앞에 붙을 단어가 없으면 row를 줄여가면서 존재하는 단어를 찾은후 connectAllTo를 붙인다.
					//
					connectTo(null, connectRow, -1, tail);
				}
			}
		} catch (AnalyzeExceedException e) {
			// 분석을 중단하고 탈출한다.
			logger.debug("Analyze exceed : " + e.getMessage());
		}
	}

	private int connectAllTo(int headRow, Entry tail) throws AnalyzeExceedException {
		PosTagProbEntry[] rowData = tabular[headRow];
		int found = 0;
		// 최장길이부터 찾는다.
		for (int headColumn = headRow + 1; headColumn > 0; headColumn--) {
			if (rowData[headColumn] != null) {
				PosTagProbEntry tagEntry = rowData[headColumn];
				/* 2019.3.28 @swsong 알파벳은 무조건 성공분석으로 잡혀서 사전에 없는 단어들도 후보가 되어 ALPHA를 건너뛰게 한다. */
				if (tagEntry.tagProb.posTag() != PosTag.ALPHA) {
					connectTo(tagEntry, headRow, headColumn, tail);
					found++;
				}
			}

			// 갯수만큼 다 찾았으면 일찍 종료한다.
			if (found >= status[headRow]) {
				break;
			}

		}
		// 추가 20190725 (영문인경우 모두 분석되어야 분석성공으로 만들도록)
		if (!isUnicode) {
			for (int inx = 0; inx < result.size(); inx++) {
				if (result.get(inx).last().posTag() == PosTag.UNK) {
					result.remove(inx);
					inx--;
				}
			}
		}
		return found;
	}

	private int connectTo(PosTagProbEntry headTagEntry, int headRow, int headColumn, Entry tail) throws AnalyzeExceedException {
		// headColumn = -1 이면 앞쪽에 연결될 단어가 없는것이다.
		if (tail == null) {
			// 처음
			int found = 0;
			while (headTagEntry != null) {
				Entry headEntry = new Entry(headRow, headColumn, headTagEntry.get(), offset);
				if (headEntry.row() - headEntry.column() < 0) {
					addResult(headEntry);
				} else {
					addQueue(headEntry);
				}
				headTagEntry = headTagEntry.next;
				found++;
			}
			// 바로리턴.
			return found;
		}

		if (headTagEntry == null) {
			// 해당 row의 모든 column을 확인해본다.
			for (int column = 1; column <= headRow + 1; column++) {
				int row2 = headRow - column;
				// head앞에 결합가능한 것이 있다면 현재 head를 미등록처 처리하고 링크로 이어준다.
				// 앞쪽에 결합가능한것이 없으면 현재 head는 버린다.
				// row2 < 0 는 어절의 처음에 도달한것임.
				if (row2 < 0 || status[row2] > 0) {
					// 2014-1-27처리..
				}
			}
			return 1;
		}
		
		int found = 0;
		while (headTagEntry != null) {
			if (isConnectableByRule(headTagEntry.get(), headRow, headColumn, tail)) {
				Entry newTail = modifyAndConnect(headTagEntry.get(), headRow, headColumn, tail);
				if (newTail == null) {
					// null이면 버리는것이므로 다음으로..
				} else {
					if (newTail.row() - newTail.column() < 0) {
						addResult(newTail);
					} else {
						addQueue(newTail);
					}
					found++;
				}
			}
			headTagEntry = headTagEntry.next;
		}
		return found;
	}
	
	protected void addQueue(Entry entry) throws AnalyzeExceedException {
		if (fastResultFound) {
			return;
		}
		// 결과로 넣음.
		queue.add(entry);
		if (queue.size() >= QUEUE_MAX) {
			throw new AnalyzeExceedException("Queue size exceed " + queue.size() + " : " + new String(source));
		}
	}

	protected void addResult(Entry entry) throws AnalyzeExceedException {
		Entry e = finalCheck(entry);
		if (e == null) {
			return;
		}
		// 결과로 넣음.
		result.add(entry);

		// 짧은 문장에서는 사용하지 않음.
		if (fastResultOption && length > 6) {
			fastResultFound = true;
			queue.clear();
		}

		if (result.size() >= RESULT_MAX) {
			Entry tmpResult = getHighResult();
			result.clear();
			if (tmpResult != null) {
				result.add(tmpResult);
			}
		}
	}
	
	public int setInput(char[] buffer, int length) {
		return setInput(buffer, 0, length);
	}
	
	public int setInput(char[] buffer, int offset, int length) {
		remnantOffset = 0;
		remnantLength = 0;
		hasRemnant = false;
		isUnicode = isUnicode(buffer, offset, length);
		Arrays.fill(status, 0);
		
		String type = null;
		String ptype = null;
		String pptype = null;
		if (length > tabular.length) {
			logger.trace("LENGTH IS OVER THAN {} / {} / {}", length, tabular.length, offset);
			//내부적으로 자를 수 있는 기준을 살펴 본다.
			//자를수 있는 기준은 다음과 같다.
			//한글 사이의 특수 문자. ( & 제외 : 존슨&존스 등 )
			for (int inx = offset + length; inx > offset; inx--) {
				pptype = ptype;
				ptype = type;
				type = ProductNameTokenizer.getType(buffer[inx - 1]);
				if (logger.isTraceEnabled()) {
					logger.trace("PP:{}/P:{}/T:{}/C:{} {} [{}/{}/{} | {}/{}/{}]", pptype, ptype, type, buffer[inx - 1],
						inx - offset,
						(pptype != null && (pptype != ProductNameTokenizer.ALPHA && pptype != ProductNameTokenizer.NUMBER)),
						(inx < buffer.length && ptype == ProductNameTokenizer.SYMBOL && buffer[inx] != '&'),
						(type != null), (pptype != null),
						(inx < buffer.length && ptype == ProductNameTokenizer.SYMBOL && buffer[inx] != '&'),
						(type != null && (type != ProductNameTokenizer.ALPHA && type != ProductNameTokenizer.NUMBER)));
				}
				if (((pptype != null && (pptype != ProductNameTokenizer.ALPHA && pptype != ProductNameTokenizer.NUMBER))
						&& (inx < buffer.length && ptype == ProductNameTokenizer.SYMBOL && buffer[inx] != '&')
						&& (type != null))
						|| ((pptype != null) && (inx < buffer.length && ptype == ProductNameTokenizer.SYMBOL && buffer[inx] != '&')
							&& (type != null && (type != ProductNameTokenizer.ALPHA && type != ProductNameTokenizer.NUMBER)))) {
					logger.trace("LENGTH:{} / {}", length - offset, tabular.length);
					length = inx - offset;
					if (length <= tabular.length) {
						logger.trace("BREAK INTO {}", length);
						break;
					}
				}
			}
			//여기까지 와서 찾지 못했다면, 앞에서부터 최초 타입이 달라지는 순간 끊어준다.
			//영숫자+기호 : 한글
			if (length > tabular.length) {
				type = null;
				for (int inx = offset; inx < (offset + length); inx++) {
					ptype = type;
					type = ProductNameTokenizer.getType(buffer[inx]);
					if (ptype != null && (
						( (type == ProductNameTokenizer.ALPHA || type == ProductNameTokenizer.NUMBER || type == ProductNameTokenizer.SYMBOL)
							&& !(ptype == ProductNameTokenizer.ALPHA || ptype == ProductNameTokenizer.NUMBER || ptype == ProductNameTokenizer.SYMBOL) )
						|| (!(type == ProductNameTokenizer.ALPHA || type == ProductNameTokenizer.NUMBER || type == ProductNameTokenizer.SYMBOL)
							&& (ptype == ProductNameTokenizer.ALPHA || ptype == ProductNameTokenizer.NUMBER || ptype == ProductNameTokenizer.SYMBOL)) )) {
						length = inx - offset;
					}
				}
			}
			
			if (length > tabular.length) {
				if (logger.isTraceEnabled()) {
					logger.trace("CUT TABULAR SIZE : {}/ {} -> {}", length, new String(buffer, offset, length), new String(buffer, offset, tabular.length));
				}
				remnantOffset = tabular.length;
				remnantLength = length - tabular.length;
				hasRemnant = remnantLength > 0;
				length = tabular.length;
			}
		}
		length = setInput0(buffer, offset, length);
		return length;
	}
	
	private int setInput0(char[] buffer, int offset, int length) {
		this.source = buffer;
		queue.clear();
		result.clear();
		// tabluar초기화.
		this.offset = offset;
		this.length = length;
		// this.flushCount = 0;
		fastResultFound = false;
		return length;
	}

	public Entry extract() {
		Entry e = extract0();
		Entry last = e.last();
		while (remnantLength > 0) {
			int len = Math.min(tabular.length, remnantLength);
			// 자른다.
			setInput0(source, remnantOffset, len);
			Entry r = extract0();
			if (r != null) {
				// 잘못된 데이터. 순서적으로 나올수 없는 조합.
				if (!(last.offset() + last.column() > r.offset())) {
					last.next(r);
					last = r.last();
				}
			}
			remnantOffset += len;
			remnantLength -= len;
		}
		return e;
	}
	
	public Entry extract0() {
		Entry e = doSegment();
		if (e != null) {
			return e;
		}
		if (isDebug) {
			showTabular();
		}
		makeResult();
		return getBestResult();
	}

	public List<Entry> getAllResult() {
		return result;
	}
	
	private Entry getHighResult() {
		Entry highEntry = null;
		for (int k = 0; k < result.size(); k++) {
			Entry entry = result.get(k);
			entry = finalCheck(entry);
			if (entry == null) {
				continue;
			}
			if (highEntry == null) {
				highEntry = entry;
			} else {
				if (isBetterThan(entry, highEntry)) {
					highEntry = entry;
				}
			}
		}
		return highEntry;
	}

	public Entry getBestResult() {
		Entry bestEntry = getHighResult();
		if (bestEntry == null) {
			// 통째 미등록어.
			bestEntry = new Entry(length - 1, length, TagProb.UNK, offset);
			return bestEntry;
		}
		Entry prevEntry = null;
		Entry entry = bestEntry;
		while (entry != null) {
			CharVector term = new CharVector(source, entry.row() - entry.column() + 1, entry.column());
			PreResult<CharSequence> preResult = koreanDict.findPreResult(term);

			if (preResult != null) {
				CharSequence[] resultList = preResult.getResult();
				if (resultList == null) {
					CharSequence[] additionList = preResult.getAddition();
					if (additionList != null) {
						// 추가단어.
					}
				} else {
					// 분리어 이므로 교체.
					int startRow = entry.row() - entry.column();
					Entry first = null;
					Entry last = null;
					for (int i = 0; i < resultList.length; i++) {
						CharSequence cv = resultList[i];
						startRow += cv.length();
						Entry entry2 = new Entry(startRow, cv.length(), entry.tagProb());
						if (first == null) {
							first = entry2;
							last = first;
						} else {
							last.setNext(entry2);
							last = entry2;
						}
					}
					if (prevEntry == null) {
						// 처음부터 엔트리가 바뀌면 bestEntry를 교체한다.
						bestEntry = first;
					} else if (prevEntry != null) {
						prevEntry.setNext(first);
					}
					prevEntry = last;
					entry = entry.next();
					last.setNext(entry);
					continue;
				}
			}
			prevEntry = entry;
			entry = entry.next();
		}
		return bestEntry;
	}
	
	public boolean hasRemnant() {
		return hasRemnant;
	}
	
	public int getTabularSize() {
		return tabular.length;
	}

	/*
	 * 두 PosTag간의 룰기반 접속문법검사
	 */
	protected boolean isConnectableByRule(TagProb headTagProb, int headRow, int headColumn, Entry tail) {
		//숫자끼리는 과분석된것이므로 연결해주지 않는다. 제일 긴 숫자가 사용하도록함.
		if (headTagProb.posTag() == PosTag.DIGIT && tail.tagProb().posTag() == PosTag.DIGIT) {
			return false;
		}
		if (headTagProb.posTag() == PosTag.ALPHA && tail.tagProb().posTag() == PosTag.ALPHA) {
			return false;
		}
		if (headTagProb.posTag() == PosTag.SYMBOL && tail.tagProb().posTag() == PosTag.SYMBOL) {
			return false;
		}
		if (headTagProb.posTag() != PosTag.ALPHA && headTagProb.posTag() != PosTag.DIGIT && headTagProb.posTag() != PosTag.SYMBOL) {
			// //은,이 와 는,가 일때 앞의 단어에 받침이 있는 지 확인.
			if (tail.tagProb().posTag() == PosTag.J && tail.column() == 1) {
				// "은 는 이 가 을 를 에 과 와 의 께";
				char ch = source[tail.row() - tail.column() + 1];
				if (ch == '은' || ch == '이' || ch == '을' || ch == '과') {
					if (!MorphUtil.hasLastElement(source[headRow])) {
						return false;
					}
				} else if (ch == '는' || ch == '가' || ch == '를' || ch == '와') {
					if (MorphUtil.hasLastElement(source[headRow])) {
						return false;
					}
				}
			}
		}

		// 두개 모두 한글자분석이면서 점수가 -12이하인것이 하나라도 있으면 통짜로 미등록어처리.
		if (headColumn == 1 && tail.column() == 1 && headTagProb.posTag() != PosTag.J && tail.posTag() != PosTag.J) {
			if (tabular[tail.row()][tail.column() + headColumn] != null) {
				// 단어가 존재하므로 버린다.
				return false;
			}
		}
		//3개연속 불허용.
		return true;
	}

	/*
	 * 두 엔트리를 접속시 합치거나 이어붙이는 로직을 구현한다.
	 */
	protected Entry modifyAndConnect(TagProb tagProb, int row, int column, Entry tail) {
		Entry newEntry = new Entry(row, column, tagProb, offset);
		return newEntry.next(tail);
	}

	private Entry finalCheck(Entry headEntry) {
		// 첫글자 조사버림.
		if (headEntry.posTag() == PosTag.J) {
			return null;
		}

		int count = 0;
		if (headEntry.entryCount() >= 2) {
			Entry current = headEntry;
			while (current != null) {
				if (current.column() > 1) {
					break;
				} else {
					count++;
				}
				current = current.next();
			}
		}

		if (count == headEntry.entryCount()) {
			if (headEntry.last().posTag() == PosTag.J) {
				return headEntry;
			}
			return null;
		}
		/*
		 * 한글자 + GUESS(or -15미만) 결합은 합친다.
		 */
		return headEntry;
	}
	
	/*
	 * Best 결과를 뽑을때 사용하는 비교로직.
	 */
	protected boolean isBetterThan(Entry entry, Entry bestEntry) {
		// 적게 잘린쪽이 우선.
		// 2014-1-27 처리.. 이렇게 하면 무조건 항상 통 UNK가 나오게 된다.

		// 점수가 큰쪽이 우선.
		if (entry.totalScore() > bestEntry.totalScore()) {
			return true;
		}
		return false;
	}

	static class Entry implements Cloneable {
		private int row;
		private int column;
		private TagProb tagProb;
		private Entry next; // 다음 엔트리.
		private double score; // 최종 score이다. head가 next이후의 score의 합산을 가지고 있게된다.
		private boolean extracted;
		private int offset;
		
		public Entry(int row, int column, TagProb tagProb) {
			this(row, column, tagProb, (double) 0);
		}

		public Entry(int row, int column, TagProb tagProb, double scoreAdd) {
			this.row = row;
			this.column = column;
			this.tagProb = tagProb;
			this.score += (tagProb.prob() + scoreAdd);
		}
		
		public Entry(int row, int column, TagProb tagProb, int offset) {
			this.row = row;
			this.column = column;
			this.tagProb = tagProb;
			this.score += tagProb.prob();
			this.offset = offset;
		}

		public TagProb tagProb() {
			return tagProb;
		}
		
		public PosTag posTag() {
			return tagProb.posTag();
		}

		public void tagProb(TagProb tagProb) {
			// 이전것을 빼고.
			this.score -= this.tagProb.prob();
			this.tagProb = tagProb;
			this.score += tagProb.prob();
		}

		public int offset() {
			return offset + row - column + 1;
		}

		public int row() {
			return row;
		}

		public void row(int row) {
			this.row = row;
		}

		public int column() {
			return column;
		}

		public void column(int column) {
			this.column = column;
		}

		public void addScore(int score) {
			this.score += score;
		}
		
		public double totalScore() {
			return score;
		}
		
		public int entryCount() {
			Entry nextEntry = this;
			int count = 0;
			while (nextEntry != null) {
				count++;
				nextEntry = nextEntry.next;
			}
			return count;
		}
		
		public int charSize() {
			return column;
		}

		public Entry next() {
			return next;
		}

		public Entry last() {
			Entry l = this;
			while (true) {
				if (l.next() == null) {
					return l;
				} else {
					l = l.next();
				}
			}
		}
		
		public void setNext(Entry next) {
			this.next = next;
		}

		public Entry next(Entry next) {
			this.next = next;
			if (next != null) {
				this.score += next.score;
			}
			return this;
		}
		
		public boolean isExtracted() {
			return extracted;
		}
		
		public void setExtracted(boolean extracted) {
			this.extracted = extracted;
		}
		
		public String getChainedString() {
			if (next == null) {
				return toString();
			} else {
				return toString() + " + " + next.getChainedString();
			}
		}

		public String getChainedShortString(char[] source) {
			if (next == null) {
				return toShortString(source);
			} else {
				return toShortString(source) + " + " + next.getChainedShortString(source);
			}
		}
		
		public String getChainedString(char[] source) {
			if (next == null) {
				return toDetailString(source);
			} else {
				return toDetailString(source) + " + " + next.getChainedString(source);
			}
		}

		@Override
		public Entry clone() {
			Entry entry = null;
			try {
				entry = (Entry) super.clone();
			} catch (CloneNotSupportedException e) {
				logger.error("", e);
			}
			return entry;
		}

		@Override
		public String toString() {
			return "(" + (row + offset) + "," + column + "):" + tagProb + ":" + score;
		}

		public String toWord(char[] source) {
			return new String(source, row + offset - column + 1, column);

		}
		
		public String toShortString(char[] source) {
			return new String(source, row + offset - column + 1, column) + ":" + tagProb.toShortString();

		}
		
		public String toDetailString(char[] source) {
			try {
				return new String(source, row + offset - column + 1, column) + "(" + (row + offset) + "," + column + "):" + tagProb + ":" + score;
			} catch (Exception e) {
				logger.debug("{} ({},{})", new String(source), row + offset - column + 1, column);
				throw new RuntimeException();
			}
		}
	}
}
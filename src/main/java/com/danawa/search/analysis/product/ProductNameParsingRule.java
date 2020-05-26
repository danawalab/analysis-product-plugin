package com.danawa.search.analysis.product;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.danawa.search.analysis.dict.CompoundDictionary;
import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor.ExtractedEntry;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.search.analysis.korean.PosTagProbEntry.PosTag;
import com.danawa.util.CharVector;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.common.logging.Loggers;

import static com.danawa.search.analysis.product.ProductNameTokenizer.*;

/**
 * 숫자파싱기능 구현 (구현됨) 숫자와 단위명을 파싱 한다. (구현됨) 단위명은 숫자 + 단위명 + 유니코드 까지만 인정한다. 숫자 + 단위명
 * + 영문은 모델명 규칙에 더 가까우며, (구현됨) 변형숫자 + 단위명 은 단위명 규칙에 더 가깝다. (폐기) 중간에 숫자만 똑 떼어가는
 * 경우도 있으므로 앞, 뒤 속성 등을 유지하기 위해 큐가 필요하다. (구현됨) 유사어 속성도 같이 변경 되어야 한다.
 * 
 * @author lupfeliz
 */
public class ProductNameParsingRule {
	
	private static Logger logger = Loggers.getLogger(ProductNameParsingRule.class, "");
	
	private KoreanWordExtractor extractor;
	private AnalyzerOption option;
	private int start;
	private int lastPosition;
	private List<RuleEntry> queue;
	private SetDictionary unitDictionary;
	private SpaceDictionary spaceDictionary;
	private SynonymDictionary unitSynonymDictionary;
	private SynonymDictionary synonymDictionary;
	private SetDictionary userDictionary;
	private CompoundDictionary compoundDictionary;
	private SetDictionary stopDictionary;
	private CharVector term;

	private ProductNameParsingRule() { }

	public ProductNameParsingRule(KoreanWordExtractor extractor, ProductNameDictionary dictionary,
		AnalyzerOption option) {
		this.extractor = extractor;
		this.option = option;
		if (dictionary != null) {
			unitDictionary = dictionary.getDictionary(DICT_UNIT, SetDictionary.class);
			synonymDictionary = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
			unitSynonymDictionary = dictionary.getDictionary(DICT_UNIT_SYNONYM, SynonymDictionary.class);
			spaceDictionary = dictionary.getDictionary(DICT_SPACE, SpaceDictionary.class);
			userDictionary = dictionary.getDictionary(DICT_USER, SetDictionary.class);
			compoundDictionary = dictionary.getDictionary(DICT_COMPOUND, CompoundDictionary.class);
			stopDictionary = dictionary.getDictionary(DICT_STOP, SetDictionary.class);
		}
		queue = new ArrayList<>();
	}
	
	public ProductNameParsingRule clone() {
		ProductNameParsingRule clone = new ProductNameParsingRule();
		clone.extractor = this.extractor;
		clone.option = this.option;
		clone.start = 0;
		clone.lastPosition = 0;
		clone.queue = new ArrayList<>();
		clone.unitDictionary = this.unitDictionary;
		clone.spaceDictionary = this.spaceDictionary;
		clone.unitSynonymDictionary = this.unitSynonymDictionary;
		clone.synonymDictionary = this.synonymDictionary;
		clone.userDictionary = this.userDictionary;
		clone.compoundDictionary = this.compoundDictionary;
		clone.term = null;
		return clone;
	}
	
	public List<RuleEntry> getQueue() {
		return queue;
	}
	
//	public void addEntry(CharSequence cv, String type, boolean modifiable) {
//		addEntry(cv, type, modifiable, false);
//	}
//	
//	public void addEntry(CharSequence cv, String type, boolean modifiable, boolean sequencial) {
//		PosTag posTag = null;
//		logger.trace("term:{}/tag:{}/type:{}/{}~{}", cv, posTag, type, offsetAttribute.startOffset(), offsetAttribute.endOffset());
//		boolean doAdd = true;
//		if (sequencial && queue.size() > 0) {
//			if (queue.get(queue.size() - 1).startOffset > offsetAttribute.startOffset()) {
//				doAdd = false;
//			}
//		}
//		
//		if (doAdd) {
//			RuleEntry e1, e2;
//			if (type != FULL_STRING) {
//				if (posTag == PosTag.N) {
//					type = HANGUL;
//				} else if (posTag == PosTag.DIGIT) {
//					type = NUMBER;
//				} else if (posTag == PosTag.ALPHA) {
//					type = ALPHA;
//				} else if (posTag == PosTag.SYMBOL) {
//					type = SYMBOL;
//				} else if (posTag == null || posTag == PosTag.UNK) {
//					type = UNCATEGORIZED;
//				}
//			}
//			
//			if (type == HANGUL && queue.size() > 2) {
//				e1 = queue.get(queue.size() - 1);
//				e2 = queue.get(queue.size() - 2);
//				if (e1.type == SYMBOL && e2.type == HANGUL && e1.length == 1 && e1.buf[e1.start] == '&') {
//					CharVector cv1 = CharVector.valueOf(cv).clone();
//					cv1.init(e1.start, e2.length + e1.length + cv1.length());
//					if (userDictionary.contains(cv1)) {
//						queue.remove(queue.size() - 1);
//						queue.remove(queue.size() - 1);
////						offsetAttribute.setOffset(e1.startOffset, offsetAttribute.endOffset());
//					}
//				}
//			}
//			
//			// 중복된 텀이 발견되었다면 추가하지 않는다
//			if (queue.size() > 1) {
//				e1 = queue.get(queue.size() - 1);
//				if (e1.type == type &&
//					e1.buf[e1.start] == cv.charAt(0) &&
//					e1.length == cv.length() &&
//					e1.startOffset == offsetAttribute.startOffset() &&
//					e1.endOffset == offsetAttribute.endOffset()) {
//					doAdd = false;
//				}
//			}
//			
//			if (doAdd) {
//				RuleEntry entry = makeEntry(CharVector.valueOf(cv), type, offsetAttribute.startOffset(), offsetAttribute.endOffset());
//				entry.modifiable = modifiable;
//				queue.add(entry);
//			}
//		}
//	}
	
	public static void addEntry(List<RuleEntry> termList, CharVector ref, String type, PosTag posTag, int startOffset, int endOffset, SpaceDictionary spaceDictionary) {
		if (type == FULL_STRING) {
			// NOP
		} else if(posTag == PosTag.N) {
			type = HANGUL;
		} else if(posTag == PosTag.DIGIT) {
			type = NUMBER;
		} else if(posTag == PosTag.ALPHA) {
			type = ALPHA;
		} else if(posTag == PosTag.SYMBOL) {
			type = SYMBOL;
		} else if(posTag == null || posTag == PosTag.UNK) {
			type = UNCATEGORIZED;
		}
		if(spaceDictionary != null && spaceDictionary.containsKey(ref)) {
			// 분리어가 발견되면
			int offsetSt = startOffset;
			CharSequence[] splits = spaceDictionary.get(ref);
			logger.trace("SPLIT:{}{}", "", splits);
			for (int sinx = 0, position = 0; sinx < splits.length; sinx++) {
				int length = splits[sinx].length();
				if(sinx > 0) {
					// 강제 분리를 위한 빈공백
					termList.add(new RuleEntry(ref.array(), position, 0, 
						offsetSt + position, offsetSt + position, SYMBOL));
				}
				termList.add(new RuleEntry(ref.array(), ref.offset() + position, length, 
					offsetSt + position, offsetSt + position + length, type));
				position += length;
			}
		} else {
			termList.add(new RuleEntry(ref.array(), ref.offset(), ref.length(), 
				startOffset, endOffset, type));
		}
	}

	public void init(List<RuleEntry> queue) {
		if (queue.size() > 0) {
			this.term = queue.get(0).makeTerm(null);
			start = term.offset();
			int last = queue.size() - 1;
			lastPosition = queue.get(last).start + queue.get(last).length;
		}
	}
	
	public int queueSize() {
		return queue.size();
	}
	
	public boolean processRule(List<RuleEntry> queue, boolean fullExtract) {
		String type = null;
		String typePrev = null;
		logger.trace("queue:{}", queue);
		RuleEntry e0, e1, e2, e3, et;
		e0 = e1 = e2 = e3 = et = null;
		CharVector eTerm;
		
		if (queue.size() == 1 && queue.get(0).type == null) {
			// not type-splited. force split by type
			RuleEntry entry = queue.remove(0);
			split(entry, queue, 0);
		}
		
		if (queue.size() == 1 && queue.get(0).type == SYMBOL) {
			return false;
		}
		
		CharVector cvTmp = new CharVector();
		cvTmp.ignoreCase();
		
		////////////////////////////////////////////////////////////////////////////////
		// 규칙분류 시작.
		Collections.sort(queue);
		
		for (int qinx = 0; qinx < queue.size(); qinx++) {
			e0 = queue.get(qinx);
			if (e0.type == FULL_STRING) {
				continue;
			}
			if (e0.length == 0) {
				e0.modifiable = false;
				if (qinx > 0 && qinx + 1 < queue.size()) {
					queue.get(qinx - 1).modifiable = false;
					queue.get(qinx + 1).modifiable = false;
				}
			}
			
			if (e0.type == UNCATEGORIZED) {
				if (!userDictionary.contains(e0.makeTerm(null))) {
					RuleEntry entry = queue.remove(qinx);
					split(entry, queue, qinx);
				} else {
					e0.type = HANGUL;
				}
			}
			
			if (fullExtract) {
				// extractor 는 타입이 다른 엔트리 에 대해서 체크하지 못하므로
				// 엔트리들을 합쳐서 복합어/사용자 사전에 체크해 본다
				// 최대 3개 엔트리까지 체크
				for (int linx = 2; linx >= 1; linx--) {
					boolean passFlag = false;
					char[] tmpbuf = e0.buf;
					int tmpst = e0.start;
					int tmped = 0;
					
					// 합쳐질 단어들은 사이에 공백이 없고 묶은뒤 앞뒤로
					// 공백이 있는경우에만 합치도록 한다.
					// 로직의 유연화를 위해 루프기반 로직으로 변경 (2020.1.23)
					if (queue.size() > qinx + linx) {
						e1 = queue.get(qinx + linx);
						if (e1.buf == tmpbuf) {
							tmped = e1.start + e1.length;
							if ((tmpst == 0 || (tmpst > 0 && tmpbuf[tmpst - 1] == ' ')) &&
								(tmped == lastPosition || (tmped < lastPosition && tmpbuf[tmped] == ' '))) {
								passFlag = true;
								e1 = e0;
								for (int tinx = 0; tinx < linx; tinx++) {
									e2 = queue.get(qinx + tinx + 1);
									if (e2.start != e1.start + e1.length) {
										passFlag = false;
										break;
									}
									e1 = e2;
								}
							}
						}
					}
					
					if (passFlag) {
						e1 = queue.get(qinx + linx);
						cvTmp.init(e0.buf, e0.start, e1.start + e1.length - e0.start);
						if (spaceDictionary != null && spaceDictionary.containsKey(cvTmp)) {
							CharSequence[] splits = spaceDictionary.get(cvTmp);
							for (int rinx = qinx + linx - 1; rinx >= qinx; rinx--) {
								queue.remove(rinx);
							}
							int startOffset = e0.startOffset;
							for (int sinx = 0; sinx < splits.length; sinx++) {
								CharVector split = CharVector.valueOf(splits[sinx]);
								e1 = new RuleEntry(split.array(), split.offset(), split.length(),
									startOffset, startOffset + split.length(), HANGUL);
								e1.modifiable = false;
								queue.add(qinx + sinx, e1);
								startOffset += e1.length;
							}
							e0 = queue.get(qinx);
						} else if (compoundDictionary != null && compoundDictionary.containsKey(cvTmp)) {
							e0.length = (e1.start + e1.length - e0.start);
							e0.type = HANGUL;
							for (int rinx = qinx + linx; rinx > qinx; rinx--) {
								queue.remove(rinx);
							}
							queue.set(qinx, e0);
							logger.trace("COMPOUND FOUND! : {} / {}", e0, cvTmp);
						} else if ((stopDictionary != null && option.useStopword() && stopDictionary.contains(cvTmp))) {
							// 금칙어 규칙은 아래의 경우 (사용자,동의어,브랜드,메이커) 와 다르게
							// 통합시, 분리시 동시체크를 할 필요가 없으므로 다른 로직을 적용
							e0.length = (e1.start + e1.length - e0.start);
							e0.modifiable = false;
							e0.type = HANGUL;
							for (int rinx = qinx + linx; rinx > qinx; rinx--) {
								queue.remove(rinx);
							}
							queue.set(qinx, e0);
						} else if (containsDictionary(cvTmp)) {
							// 요청에 의해 사용자 사전에 있는 ASCII + UNICODE 조합단어
							// (토크나이저에서 강제분리됨)는 먼저 체크하여 붙여줌.
							// 해당 경우 통합시, 분리시의 규칙을 둘 다 적용해야 하므로
							// 분리된 단어 모두를 추가텀으로 구성하도록 함.
							// 추가텀은 항상 등록하지 않고 사전에 있는 경우에만 등록한다.
							// e0.subEntry.add(new RuleEntry(e0.buf, e0.start, e0.length, e0.startOffset, e0.endOffset, HANGUL));
							e0 = new RuleEntry(e0.buf, e0.start, e0.length, e0.startOffset, e0.endOffset, e0.type);
							e0.length = (e1.start + e1.length - e0.start);
							e0.modifiable = false;
							e0.type = HANGUL;
							
							for (int rinx = qinx + linx; rinx >= qinx; rinx--) {
								queue.remove(rinx);
							}
							queue.add(qinx, e0);
							if (queue.size() > (qinx + 1)) {
								if ((e1 = queue.get(qinx + 1)).type == FULL_STRING) {
									e1.subEntry = e0.subEntry;
									queue.remove(qinx);
								}
							}
						}
						break;
					}
				}
			}

			// 한글인 경우 (사전에 등록된 단어 중) 숫자로 시작하고 단위명으로 끝나는것을 분리한다.
			if (e0.type == HANGUL && e0.modifiable) {
				if (getType(e0.buf[e0.start]) == NUMBER) {
					int numInx = 1;
					boolean numberChar;
					boolean numberTrans = false;
					for (; numInx < e0.length; numInx++) {
						char ch = e0.buf[e0.start + numInx];
						numberChar = containsChar(AVAIL_SYMBOLS_INNUMBER, ch);
						if (numberChar) {
							numberTrans = true;
						}
						if (getType(ch) != NUMBER && !numberChar) {
							break;
						}
					}
					CharVector unitCv = new CharVector(e0.buf, e0.start + numInx, e0.length - numInx);
					if (unitDictionary != null && unitDictionary.contains(unitCv)) {
						e1 = e0.clone();
						e0.length = numInx;
						e0.endOffset = e0.startOffset + e0.length;
						logger.trace("NUMBER FOUND:{} / {}", e0, e1);
						if (numberTrans) {
							e0.type = NUMBER_TRANS;
						} else {
							e0.type = NUMBER;
						}
						e1.start += numInx;
						e1.length -= numInx;
						e1.type = getTermType(unitCv);
						queue.add(qinx + 1, e1);
						qinx++;
						continue;
					}
				} else {
					// 3글자 이하 단어 중 앞 또는 뒤에 인접한 단어인 경우
					// 사용자 단어에 포함되지 않은 것들 중에서 원래 타입으로 환산한다.
					if (e0.length <= 3 && e0.modifiable) {
						eTerm = e0.makeTerm(null);
						boolean joinable = false;
						if (userDictionary != null && !userDictionary.contains(eTerm)) {
							// 영숫자 인 경우에만.
							if (isAlphaNum(eTerm)) {
								if (qinx > 0) {
									e1 = queue.get(qinx - 1);
									joinable = e0.start == e1.start + e1.length && (e1.type == ALPHA || e1.type == NUMBER || e1.type == SYMBOL);
								}
								
								if (!joinable && qinx + 1 < queue.size()) {
									e2 = queue.get(qinx + 1);
									joinable = e2.start == e0.start + e0.length && (e2.type == ALPHA || e2.type == NUMBER || e2.type == SYMBOL);
								}
								if (joinable) {
									queue.remove(qinx);
									split(e0, queue, qinx);
								}
								continue;
							}
						}
						String eType = getTermType(eTerm);
						if (eType == ALPHA) {
							e0.type = ALPHA;
						} else if (eType == NUMBER) {
							logger.trace("NUMBER FOUND:{}", e0);
							e0.type = NUMBER;
						} else if (eType == ASCII || eType == ALPHANUM) {
							e0.type = MODEL_NAME;
						}
					}
				}
			}
			if (qinx + 1 < queue.size()) {
				e1 = queue.get(qinx + 1);
				
				logger.trace("e0:{}/e1:{} {}:{}", e0, e1, e0.endOffset, e1.startOffset);
				if ((e0.type == NUMBER || e0.type == NUMBER_TRANS)
					&& e1.type == NUMBER && e0.endOffset != e1.startOffset) {
					char c = e0.buf[e0.start + e0.length];
					if (logger.isTraceEnabled()) {
						logger.trace("c:{} / type:{} / contains:{}", c, getType(c), containsChar(AVAIL_SYMBOLS_INNUMBER, c));
					}
					if (getType(c) == SYMBOL && containsChar(AVAIL_SYMBOLS_INNUMBER, c)) {
						queue.remove(qinx + 1);
						e0.length += 1 + e1.length;
						e0.endOffset = e1.endOffset;
						e0.type = NUMBER_TRANS;
						qinx--;
						continue;
					}
				}
			}
		}
		////////////////////////////////////////////////////////////////////////////////
		logger.trace("1st process complete / queue:{}", queue);
		
		// 2차 가공, 변경타입을 생성한다 ( NUMBER_TRANS 등 )
		for (int qinx = 0; qinx < queue.size(); qinx++) {
			e0 = queue.get(qinx);
			if (e0.type == FULL_STRING) {
				continue;
			}
			if (qinx + 2 < queue.size()) {
				e1 = queue.get(qinx + 1);
				e2 = queue.get(qinx + 2);
				// 변형숫자 타입을 생성한다.
				if ((e0.type == NUMBER || e0.type == NUMBER_TRANS)
					&& e1.type == SYMBOL && e1.length == 1
					&& e2.type == NUMBER
					&& e1.start == e0.start + e0.length
					&& e2.start == e1.start + e1.length
					&& containsChar(AVAIL_SYMBOLS_INNUMBER, e1.buf[e1.start])) {
					et = e0.clone();
					e0.length += e1.length + e2.length;
					e0.endOffset = e0.startOffset + e0.length;
					/**
					 * 2019.4.1 다나와 요청사항
					 * , 등이 아무렇게나 섞여있는 숫자군을 변형숫자로 인식하지 않도록
					 * 정규식으로 체크한 최종 텀이 조건을 만족하지 않을경우 재분해
					 * 이 곳 이외의 코드에서 변형숫자는 사전에 등록되어 있는 단어를
					 * 재분해하여 조립하는 것이므로 따로 체크하지 않음
					 */
					if (PTN_NUMBER.matcher(e0.makeTerm(null)).find()) {
						e0.type = NUMBER_TRANS;
						queue.remove(qinx + 2);
						queue.remove(qinx + 1);
						qinx--;
						continue;
					} else {
						e0.length = et.length;
						e0.endOffset = et.endOffset;
					}
				}
			}
			
			if (qinx + 1 < queue.size()) {
				e1 = queue.get(qinx + 1);
				if (e1.start == e0.start + e0.length && e0.length > 0 && e1.length > 0) {
					if (e0.type == ALPHA && e1.type == ALPHA) {
						e0 = mergeQueue(qinx + 2, 2, queue, null);
						if (e0 != null) {
							e0.type = ALPHA;
						}
					} else if (e0.type == NUMBER && e1.type == NUMBER) {
						e0 = mergeQueue(qinx + 2, 2, queue, null);
						if (e0 != null) {
							e0.type = NUMBER;
						}
					} else if ((e0.type == JAPANESE && e1.type == JAPANESE)
						|| (e0.type == CHINESE && e1.type == JAPANESE)
						|| (e0.type == JAPANESE && e1.type == CHINESE)) {
						// 외래어 포용성이 높은 문자로 이동 ( 중국어 처럼 보인다 하더라도 일본어가 섞인 블럭이면 일본어 )
						e0.length += e1.length;
						e0.endOffset = e1.endOffset;
						e0.type = JAPANESE;
						queue.remove(qinx + 1);
						qinx--;
						continue;
					}
				}
				
			}
		}
		////////////////////////////////////////////////////////////////////////////////
		logger.trace("2nd process complete / queue:{}", queue);
		
		// 3차 가공, 단위명을 색출한다
		for (int qinx = 0; qinx < queue.size(); qinx++) {
			e0 = queue.get(qinx);
			if (e0.type == FULL_STRING) {
				continue;
			}
			if (qinx + 1 < queue.size()) {
				e1 = queue.get(qinx + 1);
				if (e1.start == e0.start + e0.length 
					&& (e0.type == NUMBER || e0.type == NUMBER_TRANS)) {
					boolean foundUnit = false;
					CharVector unitCandidate = new CharVector();
					// 기호가 섞인 최장텀을 구해야 하기 때문에 재조합 구문이 들어간다.
					unitCandidate.init(e1.buf, e1.start, e1.length);
					int findInx = 5;
					for (; findInx > 0; findInx--) {
						if (qinx + findInx < queue.size()) {
							e2 = queue.get(qinx + findInx);
							// 만약 복합단위명에 한글이 포함되어야 한다면 아래 항목을 삭제
							if (findInx > 1 && e2.type == HANGUL) {
								continue;
							}
							// 2015.9.6 swsong : e1과 e2의 버퍼가 다르다면, 서로의 position 이 영향을 주지 않는다.
							int unitLength = 0;
							if (e2.buf == e1.buf) {
								unitLength = e2.start + e2.length - e1.start;
							} else {
								unitLength = e1.length;
							}
							if (unitLength <= MAX_UNIT_LENGTH) {
								// 2015.9.6 swsong : bound 체크를 한다.
								if (e1.buf.length >= e1.start + unitLength) {
									unitCandidate.init(e1.buf, e1.start, unitLength);
									String unitType = getUniType(unitCandidate);
									if (findUnit(unitCandidate, unitType)) {
										foundUnit = true;
										break;
									}
								}
							}
						}
					}
					
					// TODO:단위명 후보가 꼭 한가지 타입일 수는 없으므로 다중타입에 대해 생각해 봐야 한다.
					if (foundUnit) {
						String unitType = getUniType(unitCandidate);
						char tempch1 = 0x0;
						if (e0.start > start) {
							tempch1 = e0.buf[e0.start - 1];
						}
						
						// 알파벳 중 X 에 대한 특별 규칙 숫자 사이의 X 인 경우 모델명 규칙을 피할 수 있다.
						if ((Character.toLowerCase(tempch1) == 'x') && qinx > 1) {
							e2 = queue.get(qinx - 1);
							e3 = queue.get(qinx - 2);
							if (e2.length == 1 
								&& e2.start == e3.start + e3.length
								&& e0.start == e2.start + e2.length
								&& (e3.type == NUMBER || e3.type == NUMBER_TRANS || e3.type == UNIT || e3.type == UNIT_ALPHA)) {
								tempch1 = 0x0;
							}
						}
						
						if (qinx > 0) {
							typePrev = queue.get(qinx - 1).type;
						} else {
							typePrev = null;
						}
						
						if (e1.length == unitCandidate.length()) {
							// 숫자 이전글자가 영문이며, 단위명 자투리도 영문인 경우 모델명 우선으로 인식 예:a1024mm
							// 단. 단위직후 바로 다시 단위가 나오는 현상에 대해서는 단위로 취급.
							if ((unitType == ALPHA && (typePrev == UNIT || typePrev == UNIT_ALPHA)) ||
								!(getType(tempch1) == ALPHA && unitType == ALPHA)) {
								// 동의어 처리
								// 단위명의 동의어가 있다면 처리한다.
								RuleEntry backup = e0.clone();
								if (fullExtract) {
									e0.subEntry = new ArrayList<>();
									e0.subEntry.add(backup);
								}
								CharSequence[] synonyms = null;
								CharSequence[] units = null;
								if (fullExtract && unitSynonymDictionary != null) {
									units = unitSynonymDictionary.map().get(unitCandidate);
									if (units != null) {
										synonyms = new CharVector[units.length];
										for (int inx = 0; inx < units.length; inx++) {
											char[] ubuf = new char[e0.length + units[inx].length()];
											CharVector unitInx = CharVector.valueOf(units[inx]);
											System.arraycopy(e0.buf, e0.start, ubuf, 0, e0.length);
											System.arraycopy(unitInx.array(), unitInx.offset(), ubuf,
												e0.length, units[inx].length());
											synonyms[inx] = new CharVector(ubuf);
										}
									}
								}
								
								// 변형숫자가 있다면, 일반숫자로 바꾸어 다시한 번 단위명을 산출한다.
								if (e0.type == NUMBER_TRANS) {
									CharVector number = e0.makeTerm(null);
									number = new CharVector(number.toString().replaceAll(",", ""));
									// 변형숫자를 일반숫자로 변경했는데 변경사항이 없다면 동의어를 만들지 않음.
									if (backup.length != number.length()) {
										if (units == null) {
											units = new CharVector[0];
										}
										String unitStr = number.toString() + unitCandidate.toString();
										e1 = new RuleEntry(unitStr.toCharArray(), 0, unitStr.length(),
											e0.startOffset, e0.endOffset + unitCandidate.length(), UNIT);
										e0.subEntry.add(0, e1);
										logger.trace("E0:{}/E1:{}", e0, e1);
										// 동의어를 사용할 경우에만.
										if (option.useSynonym()) {
											CharVector[] usynonyms = new CharVector[units.length];
											for (int inx = 0; inx < units.length; inx++) {
												char[] ubuf = null;
												CharVector synonymStr = CharVector.valueOf(units[inx]);
												// 버퍼를 마련하고 숫자를 복사.
												ubuf = Arrays.copyOf(number.array(),
													number.length() + unitStr.length());
												// 단위명을 복사.
												System.arraycopy(synonymStr.array(),
													synonymStr.offset(), ubuf, number.length(),
													synonymStr.length());
												usynonyms[inx] = new CharVector(ubuf);
											}
											e1.synonym = usynonyms;
										}
									}
								}
								
								if (fullExtract) {
									if (e0.subEntry == null) {
										e0.subEntry = new ArrayList<>();
									}
									if (synonyms != null && synonyms.length > 0) {
										e0.synonym = synonyms;
									}
								}
								
								e0.length += unitCandidate.length();
								e0.endOffset += unitCandidate.length();
								if (unitType == ALPHA) {
									e0.type = UNIT_ALPHA;
								} else {
									e0.type = UNIT;
								}
								logger.trace("E0:{} / SYN:{}", e0, e0.synonym);
								queue.remove(qinx + 1);
							}
						} else if (e1.buf.length > e1.start + unitCandidate.length()) {
							char tempch2 = e1.buf[e1.start + unitCandidate.length()];
							// 단위명이 영문이며, 단위명 자투리도 영문인 경우 모델명으로 인식 예:1024mmcc
							// ※ 2017년 6월 변경사항 : 단위명 사이의 x 에 대한 예외규칙 추가
							if ((getType(tempch1) == ALPHA || getType(tempch2) == ALPHA)
								&& Character.toLowerCase(tempch2) != 'x' && unitType == ALPHA) {
								// 모델명 우선
								e0.modifiable = true;
								e1.modifiable = true;
							} else {
								if (fullExtract) {
									e0.subEntry = new ArrayList<>();
									e0.subEntry.add(e0.clone());
								}
								
								e0.length += unitCandidate.length();
								e0.endOffset += unitCandidate.length();
								if (unitType == ALPHA) {
									e0.type = UNIT_ALPHA;
								} else {
									e0.type = UNIT;
								}
								e1.start += unitCandidate.length();
								e1.length -= unitCandidate.length();
								e1.startOffset += unitCandidate.length();
								
								// 단위텀으로서 합친 텀들을 모조리 없에도록 한다.
								if (findInx > 1) {
									for (; findInx >= 1; findInx--) {
										queue.remove(qinx + findInx);
									}
								}
							}
						}
					}
				}
			}
		}
		////////////////////////////////////////////////////////////////////////////////
		logger.trace("3rd process complete / queue:{}", queue);
		
		type = typePrev = null;
		int typeContinuous = 0;
		int typeContinuousMerge = 0;
		int overIndex = 0;
		boolean isContinue = false;
		boolean isAlphaNum = false;
		boolean isAlphaNumPrev = false;
		
		// 4차 가공, 모델명 색출
		for (int qinx = 0; qinx < queue.size(); qinx++) {
			typePrev = type;
			e0 = queue.get(qinx);
			// 단어사전에 있는 영문단어는 한글로 인식되므로
			// 영문단어인 경우 ALPHA 타입으로 전환
			// 차후 동일타입 결합에 의해 영문뭉치는
			// 결합단어로도 추출됨 (AdditionalTerm)
			// FULL_STRING 은 타입전환을 하지 않는다
			CharVector cv = e0.makeTerm(null);
			if (option.useForQuery() && e0.type != FULL_STRING && getType(cv.charAt(0)) == ALPHA) {
				if (getTermType(cv) == ALPHA) {
					e0.type = ALPHA;
				}
			}
			type = e0.type;
			
			isContinue = true;
			typeContinuousMerge = 0;
			overIndex = 0;
			
			if (e0.type == FULL_STRING) {
				typeContinuousMerge = typeContinuous;
				typeContinuous = 0;
			}
			if (logger.isTraceEnabled()) {
				logger.trace("qinx:{} / type:{} / typePrev:{} / typeCont:{} / e0:{} / char:[{}]:{}", qinx, type, typePrev, typeContinuous, e0, e0.buf[e0.start], containsChar(AVAIL_SYMBOLS_INNUMBER, e0.buf[e0.start]));
			}
			
			isAlphaNumPrev = isAlphaNum;
			isAlphaNum = e0.type == ALPHA || e0.type == NUMBER || e0.type == NUMBER_TRANS;

			if (qinx + 1 < queue.size()) {
				e1 = queue.get(qinx + 1);
			}
			
			if (isAlphaNum || e0.type == SYMBOL || e0.type == UNIT_ALPHA) {
				if (qinx == 0) {
					// NOP
				} else if (qinx < queue.size()) {
					e1 = queue.get(qinx - 1);
					if (e0.length == 0 || e1.length == 0) {
						// 끊어주는 역할. (분리어로 분리된 경우)
						// 두번 볼 것 없이 바로 끊어준다.
						typeContinuousMerge = typeContinuous;
						typeContinuous = 0;
						isContinue = false;
					} else if (e0.start == e1.start + e1.length && (
						// 특수문자는 연달아 나올수 없다.
						(e0.type != SYMBOL && (isAlphaNumPrev || e1.type == UNIT_ALPHA || (e1.type == SYMBOL && typeContinuous > 0))) ||
						(e0.type == SYMBOL && (isAlphaNumPrev || (e1.type == UNIT_ALPHA && typeContinuous > 0)) && e0.length == 1
							&& containsChar(AVAIL_SYMBOLS_CONNECTOR, e0.buf[e0.start])))) {
						typeContinuous++;
						isContinue = true;
					} else {
						// 문자가 끊겼으므로 이전까지의 연결을 머징해주고 클리어한다.
						typeContinuousMerge = typeContinuous;
						typeContinuous = 0;
						isContinue = false;
					}
				}
			} else {
				typeContinuousMerge = typeContinuous;
				typeContinuous = 0;
				isContinue = false;
			}
			if (logger.isTraceEnabled()) {
				logger.trace("qinx:{} / type:{} / typePrev:{} / typeCont:{} / e0:{} / char:[{}]:{}", qinx, type, typePrev, typeContinuous, e0, e0.buf[e0.start], containsChar(AVAIL_SYMBOLS_INNUMBER, e0.buf[e0.start]));
			}
			
			// 병합되기 직전의 처리로 병합을 취소할 수 있는. 예외로직을 기재한다.
			if (type == UNIT_ALPHA && typePrev == UNIT_ALPHA) {
				e1 = queue.get(qinx - 1);
				if (e0.start == e1.start + e1.length) {
					logger.trace("model name cancel 1");
					e0.modifiable = false;
					queue.get(qinx - 1).modifiable = false;
					typeContinuous = 0;
					typeContinuousMerge = 0;
					isContinue = true;
				} else {
					logger.trace("model name break !");
					isContinue = false;
					typePrev = null;
				}
			} else if (type == ALPHA && e0.length == 1 && Character.toLowerCase(e0.buf[e0.start]) == 'x') {
				if (qinx > 0 && qinx + 1 < queue.size()) {
					e1 = queue.get(qinx - 1);
					e2 = queue.get(qinx + 1);
					/**
					 * 숫자x숫자 는 분리하고 x 제거
					 * 
					 * 2018-10-01 
					 * 숫자x단위 앞뒤로 특수문자가 붙으면 모델명으로 인식하되
					 * 특수문자가 모델명으로 결합되는 특수문자인 경우에만 결합하도록
					 **/
					if (e0.start == e1.start + e1.length 
						&& e2.start == e0.start + e0.length
						&& (e1.type == NUMBER || e1.type == NUMBER_TRANS || e1.type == UNIT || e1.type == UNIT_ALPHA)
						&& (e2.type == NUMBER_TRANS || e2.type == NUMBER || e2.type == UNIT || e2.type == UNIT_ALPHA)) {
						// 마지막 체크. 앞 뒤로 영숫자 특수문자가 있다면 취소하지 않는다.
						boolean flag = false;
						if (qinx + 2 < queue.size()) {
							e3 = queue.get(qinx + 2);
							if (e1.start == e3.start + e3.length) {
								if (e3.type == ALPHA || e3.type == NUMBER || e3.type == NUMBER_TRANS || 
									(e3.type == SYMBOL && containsChar(AVAIL_SYMBOLS_CONNECTOR, e3.buf[e3.start]))) {
									flag = true;
								} else {
									typeContinuous--;
								}
							}
						}
						if (!flag && qinx > 1) {
							e3 = queue.get(qinx - 2);
							if (e1.start == e3.start + e3.length) {
								if (e3.type == ALPHA || e3.type == NUMBER || e3.type == NUMBER_TRANS || 
									(e3.type == SYMBOL && containsChar(AVAIL_SYMBOLS_CONNECTOR, e3.buf[e3.start]))) {
									flag = true;
								} else {
									typeContinuous--;
								}
							}
						}
						
						if (!flag) {
							logger.trace("model name cancel 2");
							e1.modifiable = false;
							e2.modifiable = false;
							queue.remove(qinx);
							typeContinuousMerge = typeContinuous - 1;
							typeContinuous = 0;
							isContinue = false;
							qinx--;
						}
					}
				}
			} else if (type == SYMBOL && e0.length == 1) {
				if (e0.buf[e0.start] == '/') {
					// 숫자 사이의 / 는 삭제
					if (qinx > 0 && qinx + 1 < queue.size()) {
						e1 = queue.get(qinx - 1);
						e2 = queue.get(qinx + 1);
						if (e0.start == e1.start + e1.length
							&& e2.start == e0.start + e0.length
							&& (e1.type == NUMBER && e2.type == NUMBER)) {
							// 단일숫자일 경우에만 / 삭제
							if (typeContinuous < 2) {
								logger.trace("model name cancel 3");
								e1.modifiable = false;
								e2.modifiable = false;
								queue.remove(qinx);
								typeContinuous = typeContinuousMerge = 0;
								isContinue = false;
								qinx--;
							}
						}
					}
				} else if (e0.buf[e0.start] == '+') {
					// + 로 이어진 모델명들은 취소한다.
					if (qinx > 0 && qinx + 1 < queue.size()) {
						e1 = queue.get(qinx - 1);
						e2 = queue.get(qinx + 1);
						if (e0.start == e1.start + e1.length
							&& e2.start == e0.start + e0.length) {
							logger.trace("model name cancel 4 / {}:{}", typeContinuous, typeContinuousMerge);
							if (isContinue && typeContinuous != 0 && typeContinuousMerge == 0) {
								e1.modifiable = false;
								e2.modifiable = false;
								typeContinuousMerge = typeContinuous;
								typeContinuous = 0;
								isContinue = false;
							}
							// +이후까지 본 상황 이므로 다음 인덱스는 1개의 오차가 생긴다.
							overIndex = 1;
						}
					}
				}
			} else if (type == UNIT_ALPHA) {
				if (qinx > 0 && qinx + 1 < queue.size()) {
					e1 = queue.get(qinx - 1);
					e2 = queue.get(qinx + 1);
					// 단위명 앞 뒤로 기호일 경우는 일단 모델명에서 제외, 단위명 우선.
					// 단 단위가 연결자일 경우는 모델명 우선.
					if (e1.type == SYMBOL && !(e2.type == ALPHA || e2.type == NUMBER) || 
						e2.type == SYMBOL && !(e1.type == ALPHA || e1.type == NUMBER)) {
						
						// 한쪽이라도 - 연결자가 발견되면 다시 모델명으로 인식.
						if (!(e1.buf[e1.start] == '-' ||
							e2.buf[e2.start] == '-')) {
							e0.modifiable = false;
							if (typeContinuous > 0) {
								logger.trace("model name cancel 6");
								typeContinuousMerge = typeContinuous - 1;
								typeContinuous = 0;
								isContinue = false;
							} else {
								typeContinuous = 0;
								isContinue = true;
							}
						}
					}
				}
			}
			if (isContinue) {
				logger.trace("continue..");
				continue;
			}
			// 모델명 조합에 사용된 모든 텀들을 재조합하여 가지고 있는다.
			if (typeContinuousMerge > 0) {
				typeContinuousMerge++;
				// 마지막 특수기호는 버린다.
				if (type == SYMBOL) {
					// 독립기호가 아닌경우에만 버리도록
					if (!(e0.length == 1 && containsChar(AVAIL_SYMBOLS_STANDALONE, e0.buf[e0.start]))) {
						queue.remove(qinx);
					}
				}
				
				if (typeContinuousMerge > 1) {
					List<RuleEntry> subQueue = null;
					if (fullExtract) {
						subQueue = new ArrayList<>();
					}
					logger.trace("merging..qinx:{} / tcont:{}", qinx, typeContinuousMerge);
					RuleEntry entry = mergeQueue(qinx, typeContinuousMerge, queue, subQueue);
					if (entry != null) {
						if (entry.type == null) { entry.type = MODEL_NAME; }
						logger.trace("qinx:{} / typeContinuousMerge:{} / overIndex:{}", qinx, typeContinuousMerge, overIndex);
						qinx -= typeContinuousMerge;
						logger.trace("qinx:{}", qinx);
						qinx -= overIndex;
						if (fullExtract) {
							logger.trace("## MERGE_1 : {} : {}", entry, subQueue);
							mergeSubQueue(entry, subQueue);
							if (subQueue.size() > 1) {
								entry.subEntry = subQueue;
							}
							logger.trace("entry:{} / subQueue:{}", entry, entry.subEntry);
							logger.trace("## Q[{}] : {}", qinx, queue);
						}
					}
				}
			}
			typeContinuousMerge = 0;
		} // for
		if (typeContinuous > 0 && typeContinuousMerge == 0) {
			typeContinuousMerge = typeContinuous;
		}
		if (typeContinuousMerge > 0) {
			typeContinuousMerge++;
			// 마지막 특수기호는 버린다.
			if (type == SYMBOL) {
				queue.remove(queue.size() - 1);
				typeContinuousMerge--;
			}
			if (typeContinuousMerge > 1) {
				List<RuleEntry> subQueue = null;
				if (fullExtract) {
					subQueue = new ArrayList<>();
				}
				logger.trace("merging.. {} / {} / {}", typeContinuousMerge, queue, subQueue);
				RuleEntry entry = mergeQueue(queue.size(), typeContinuousMerge, queue, subQueue);
				if (entry != null) {
					if (entry.type == null) { entry.type = MODEL_NAME; }
					logger.trace("subQueue:{}", subQueue);
					
					if (fullExtract) {
						logger.trace("## MERGE_2 : {} : {}", entry, subQueue);
						mergeSubQueue(entry, subQueue);
						if (subQueue.size() > 1) {
							entry.subEntry = subQueue;
						}
						logger.trace("entry:{} / subQueue:{}", entry, entry.subEntry);
					}
				}
			}
			typeContinuousMerge = 0;
		}
		logger.trace("4th process complete / queue:{}", queue);
		
		// 5차 가공, 예외규칙을 적용하고, 모델명, 단위명을 제외한 모든 텀을 합쳐서 내보낸다.
		// 특수문자 포함. 단 필요없는 특수문자는 버린다.
		for (int qinx = 0; qinx < queue.size(); qinx++) {
			e0 = queue.get(qinx);
			if (e0.type == FULL_STRING) {
				continue;
			}
			
			// 0길이는 제거한다. (구분자)
			if (e0.length == 0) {
				queue.remove(qinx);
				qinx--;
				continue;
			}
			// 올 수 없는 특수문자는 제외시킨다.
			// TODO:특수문자가 한꺼번에 많이 들어온 경우 허용되는 특수문자만 가린다.
			if (e0.type == SYMBOL && !containsChar(AVAIL_SYMBOLS, e0.buf[e0.start])) {
				queue.remove(qinx);
				typeContinuous = 0;
				qinx--;
				continue;
			}
			// 맨 앞의 독립 기호는 제거
			if (e0.type == SYMBOL && qinx == 0 && qinx + 1 < queue.size()) {
				if (e0.length == 1 && containsChar(AVAIL_SYMBOLS_STANDALONE, e0.buf[e0.start])) {
					// nop
				} else {
					e1 = queue.get(qinx + 1);
					queue.remove(qinx);
					typeContinuous = 0;
					qinx--;
					continue;
				}
			}
			
			// 맨 뒤의 독립 기호는 제거
			if (e0.type == SYMBOL && qinx > 0) {
				e1 = queue.get(qinx - 1);
				if (e0.length == 1 && containsChar(AVAIL_SYMBOLS_STANDALONE, e0.buf[e0.start])) {
					// nop
				} else if (e1.type == ALPHA || e1.type == NUMBER || e1.type == ALPHANUM
					|| e1.type == NUMBER_TRANS || e1.type == SYMBOL) {
					// nop
				} else {
					queue.remove(qinx);
					typeContinuous = 0;
					qinx--;
					continue;
				}
			}
			
			// 공백에 둘러쌓인 단독 기호는 제거 -> 단독기호는 제거
			if (e0.type == SYMBOL && qinx > 0 && qinx < queue.size()) {
				if (e0.length == 1 && containsChar(AVAIL_SYMBOLS_STANDALONE, e0.buf[e0.start])) {
					// nop
				} else {
					queue.remove(qinx);
					typeContinuous = 0;
					qinx--;
					continue;
				}
			}
			
			if (e0.type == SYMBOL && qinx > 0 && qinx + 1 < queue.size()) {
				// 독립가능한 기호가 아닌경우에만 처리
				if (e0.length == 1 && containsChar(AVAIL_SYMBOLS_STANDALONE, e0.buf[e0.start])) {
					// nop
				} else {
					e1 = queue.get(qinx - 1);
					e2 = queue.get(qinx + 1);
					if (e0.start != e1.start + e1.length && e2.start != e0.start + e0.length) {
						queue.remove(qinx);
						typeContinuous = 0;
						qinx--;
						continue;
					}
					// 앞뒤로 모델명 가능성있는 글자들이 아니면 삭제
					if (!(e1.type == ALPHA || e1.type == NUMBER || e1.type == ALPHANUM
						|| e1.type == NUMBER_TRANS || e1.type == SYMBOL)
						|| !(e2.type == ALPHA || e2.type == NUMBER
						|| e2.type == ALPHANUM || e2.type == NUMBER_TRANS || e2.type == SYMBOL)) {
						queue.remove(qinx);
						typeContinuous = 0;
						qinx--;
						continue;
					}
				}
			}
			
			if (e0.type == MODEL_NAME && e0.length > 2) {
				if (getType(e0.buf[e0.start + e0.length - 1]) == SYMBOL) {
					e0.length--;
					e0.endOffset--;
				}
			}
			
			// 5자리 이상의 독립적인 숫자는 모델명으로 리턴.
			if (e0.type == NUMBER && e0.length >= 5) {
				e0.type = MODEL_NAME;
			}
		}
		// 큐의 추가삭제는 없으므로 동시 처리 해 준다.
		for (int qinx = 0; qinx < queue.size(); qinx++) {
			// modifiable 을 특별 용도로 사용한다.
			e0 = queue.get(qinx);
			testEntry(e0, null);
			if (e0.subEntry != null) {
				for (int inx = 0; inx < e0.subEntry.size(); inx++) {
					RuleEntry subEntry = e0.subEntry.get(inx);
					testEntry(subEntry, e0);
				}
			}
			if (e0.subEntry != null && e0.subEntry.size() > 0) {
				logger.trace("subEntry:{}", e0.subEntry);
				e0.modifiable = true;
			} else {
				e0.subEntry = null;
				e0.modifiable = false;
			}

			if (!option.useForQuery()) {
				CharVector token = e0.makeTerm(null);
				if (compoundDictionary != null && compoundDictionary.containsKey(token)) {
					// 복합명사 분리는 색인시에만 수행. 쿼리시에는 분해하지 않고, 표시만.
					// 2017-11-10 swsong 복합명사 분리를 검색, 색인 모두 수행한다.
					// 공백있는 단어가 검색이 안되는 문제가 있어서 추가텀을 T or (A1 and A2 and A3 ..) 하여 검색하게 된다
					CharSequence[] compounds = compoundDictionary.get(token);
					for (CharSequence word : compounds) {
						CharVector cv = CharVector.valueOf(word);
						RuleEntry entry = new RuleEntry(cv.array(), cv.offset(), cv.length(), e0.startOffset, e0.endOffset, COMPOUND);
						if (e0.subEntry == null) { e0.subEntry = new ArrayList<>(); }
						e0.subEntry.add(entry);
					}
					if (e0.subEntry != null) { Collections.sort(e0.subEntry); }
					e0.type = COMPOUND;
				}
			}
		}

		if (queue.size() > 1 && (e0 = queue.get(0)).type == FULL_STRING && 
			(e1 = queue.get(1)).startOffset == e0.startOffset && e1.endOffset == e0.endOffset) {
			queue.remove(0);
		}
		
		logger.trace("5th process complete / queue:{}", queue);
		return true;
	}
	
	private int split(RuleEntry entry, List<RuleEntry> queue, int baseInx) {
		logger.trace("split entry ({}) : {}~{}", entry, entry.start, entry.length);
		String ptype = null;
		String ctype = null;
		int st = 0;
		int addInx = 0;
		int offsetStarts = entry.startOffset;
		for (int inx = 0; inx < entry.length; inx++) {
			char ch = entry.buf[entry.start + inx];
			ctype = getType(ch);
			if (ptype != null && ptype != ctype) {
				RuleEntry entryClone = entry.clone();
				entryClone.start = entry.start + st;
				entryClone.length = inx - st;
				entryClone.type = ptype;
				
				entryClone.startOffset = offsetStarts + st;
				entryClone.endOffset = offsetStarts + st + entryClone.length;
				entryClone.modifiable = true;
				logger.trace("add splited entry : ({}) : {}~{}", entryClone, entryClone.start, entryClone.length);
				if (entryClone.type != WHITESPACE) {
					queue.add(baseInx + addInx, entryClone);
					addInx++;
				}
				// 엔트리 클로닝시 서브엔트리가 복제되는 버그 수정
				if (inx > 0) {
					entryClone.subEntry = new ArrayList<>();
					entryClone.synonym = null;
				}
				st = inx;
			}
			ptype = ctype;
		}
		if (addInx >= 0 && st < entry.length) {
			RuleEntry entryClone = entry.clone();
			if (ptype == null) {
				ptype = ctype;
			}
			entryClone.start = entry.start + st;
			entryClone.length = entry.length - st;
			entryClone.startOffset = offsetStarts + st;
			entryClone.endOffset = offsetStarts + st + entryClone.length;
			entryClone.type = ptype;
			entryClone.subEntry = new ArrayList<>();
			entryClone.synonym = null;
			logger.trace("add splited entry : ({}) : {}~{}", entryClone, entryClone.start, entryClone.length);
			queue.add(baseInx + addInx, entryClone);
			addInx++;
		}
		return addInx;
	}
	
	private void testEntry(RuleEntry entry, RuleEntry parent) {
		if ((parent == null || parent.type == MODEL_NAME) && entry.type == NUMBER && entry.length >= 5) {
			entry.type = MODEL_NAME;
		} else if (entry.type == UNIT_ALPHA) {
			entry.type = UNIT;
		} else if (entry.type == NUMBER_TRANS) {
			entry.type = NUMBER;
			CharVector entryStr = new CharVector(entry.makeTerm(null).toString().replace(",", ""));
			if (entry.length != entryStr.length() && parent != null) {
				int inx = parent.subEntry.indexOf(entry);
				parent.subEntry.add(inx + 1, new RuleEntry(entryStr.array(), entryStr.offset(), entryStr.length(), entry.startOffset, entry.endOffset, NUMBER));
			}
		} else if (entry.type == null) {
			entry.type = UNCATEGORIZED;
		}
	}
	
	private boolean mergeSubQueue(RuleEntry entry, List<RuleEntry> subQueue) {
		RuleEntry e0, e1, e2, e3, e4;
		e0 = e1 = e2 = null;
		boolean settable = true;

		if (entry.type == MODEL_NAME) {
			if (subQueue.size() == 2) {
				// 모델명이 2블럭이며, 1글자씩이 붙어서 나온 것이면 붙여서만 출력 한다.
				e0 = subQueue.get(0);
				e1 = subQueue.get(1);
				if (e0.length == 1 && e1.length == 1) {
					subQueue.clear();
				} else if (e1.type == SYMBOL) {
					entry.length -= e1.length;
					entry.endOffset -= e1.length;
					entry.type = e0.type;
				}
			} else if (subQueue.size() == 3) {
				e0 = subQueue.get(0);
				e1 = subQueue.get(1);
				e2 = subQueue.get(2);
				if (e0.length == 1 && e1.length == 1 && e2.length == 1) {
					// 모델명이 3블럭이며, 가운데 기호가 있는 경우, 붙여서만 출력함.
					if ((e0.type == ALPHA || e0.type == NUMBER)
						&& (e2.type == ALPHA || e2.type == NUMBER)) {
						if (e1.type == SYMBOL) {
							if (e1.buf[e1.start] != '+') {
								subQueue.clear();
							}
						} else if (e0.type == e2.type) {
							e0.length += e1.length + e2.length;
							e0.endOffset = e2.endOffset;
							subQueue.remove(2);
							subQueue.remove(1);
						}
					}
				}
			}

			int continuous = 0;
			
			logger.trace("subQueue : {}", subQueue);
			
			// 2014.08.29 1byte 씩 영문자 숫자가 교차되어 발견되는 구간에서 '-' 등의 특수기호로 연결 되어 있다면 
			// 교차된 영 숫자는 분리하지 않는다.
			for (int inx = 0; inx <= subQueue.size(); inx++) {
				e0 = null;
				if (inx < subQueue.size()) {
					e0 = subQueue.get(inx);
				}
				// 1바이트씩 교차되어 나타나는 영문, 숫자들 1a2b3c....
				if (e0 != null && e0.length == 1 && (e0.type == ALPHA || e0.type == NUMBER)) {
					logger.trace("e0:{} / {}:{}", e0, inx, continuous);
					continuous++;
				} else if (continuous > 1) {
					
					boolean frontMatch = inx > 0 && (inx - continuous) > 0;
					if (frontMatch) {
						e1 = subQueue.get(inx - continuous - 1);
						if (!(e1.type == SYMBOL && e1.length == 1)) {
							frontMatch = false;
						}
					}
					
					boolean rearMatch = inx > 0 && inx < subQueue.size();
					
					if (rearMatch) {
						e1 = subQueue.get(inx);
						if (!(e1.type == SYMBOL && e1.length == 1)) {
							rearMatch = false;
						}
					}
					
					if (inx < subQueue.size()) {
						logger.trace("inx:{}/{} / continuous:{} / fmatch:{} / rmatch:{} / entry:{}", inx, subQueue.size(), continuous, frontMatch, rearMatch, subQueue.get(inx));
					}

					//
					// 2016.05.13 추가규칙.
					// 다음과 같은 규칙으로 변경
					/**
					 * 1b-12345	(1b 1 b 12345 1b-12345) -> I/Q(1b 12345 1b-12345)
					 * b1/a2s	(b1 b 1 a 2 s a2s b1/a2s) -> I/Q(b1 a 2 s a2s b1/a2s)
					 * c9-123567	(c9 c 9 123567 9123567 c9-123567) -> I(c9 123567 9123567 c9-123567)/Q(c9 123567 c9-123567)
					 * d9-b	(d9 d 9 b d9-b) -> I/Q(d9 b db-b)
					 * d8-1	(d8 d 8 1 81 d8-1) -> I(d8 1 81 d8-1)/Q(d8 1 d8-1)
					 * 1b/a2s	(1b 1 b a ba 2 s a2s 1b/a2s) -> I(1b a ba 2 s a2s 1b/a2s)/Q(1b a 2 s a2s 1b/a2s)
					 * 9c-abcdef	(9c 9 c abcdef cabcdef 9c-abcdef) -> I(9c abcdef cabcdef 9c-abcdef)/Q(9c abcdef 9c-abcdef)
					 * 8a/b	(8a 8 a b ab 8a/b) -> I(8a b ab 8a/b)/Q(8a b 8a/b)
					 * 7b-3	(7b 7 b 3 7b-3) -> I(7b 3 7b-3)/Q(7b 3 7b-3)
					 * 1A-B2C3D4	(1A 1 A B AB 2 C 3 D 4 B2C3D4 1A-B2C3D4) -> I(1A B AB 2 C 3 D 4 B2C3D4 1A-B2C3D4)/Q(1A B 2 C 3 D 4 B2C3D4 1A-B2C3D4)
					 * I7-4700MQ (2.4GHZ)	(I7 I 7 4700 74700 MQ I7-4700MQ 2.4 2.4GHZ) -> I(I7 4700 74700 MQ I7-4700MQ 2.4 2.4GHZ)/Q(I7 4700 MQ I7-4700MQ 2.4 2.4GHZ)
					 * AKSO-1X	(AKSO 1 X 1X AKSO-1X) -> I/Q(AKSO 1X AKSO-1X)
					 * AKSO-1G	(AKSO 1 G 1G AKSO-1G) -> I/Q(AKSO 1G AKSO-1G)
					 * Z9PE-D8 WS STCOM	(Z 9 PE D PED 8 D8 WS STCOM) -> I(Z 9 PE D8 PED WS STCOM)/Q(Z 9 PE D8 WS STCOM)
					 * 어답터/DC/AC/출력1.5V/2V/3V/4.5V/5V/7.5V/9V/600MA/충전기	(어답터 DC AC DCAC DC/AC 출력 1.5 V 2 V 2V 3 V 3V 4.5 V 5 V 5V 7.5 V 9 V 9V 600 MA 1.5V/2V/3V/4.5V/5V/7.5V/9V/600MA 충전기) -> I(어답터 DC AC DCAC DC/AC 출력 1.5 V 2V 3V 4.5 V 5V 7.5 V 9V 600 MA 1.5V/2V/3V/4.5V/5V/7.5V/9V/600MA 충전기)/Q(어답터 DC AC DC/AC 출력 1.5 V 2V 3V 4.5 V 5V 7.5 V 9V 600 MA 1.5V/2V/3V/4.5V/5V/7.5V/9V/600MA 충전기)
					 */
					
					// 색인전용 규칙으로 되어 있던 텀 병합 루틴을 질의용으로도 사용하고, 분리된 글자를 지우지 않던 기존 규칙을 다시 취소함.
					// 영숫자 앞 혹은 뒤에 특수문자로 연결되어 있다면.
					if ((inx - continuous == 0 && rearMatch)
						|| (frontMatch && inx == subQueue.size())
						|| frontMatch && rearMatch) {
						int pos = inx - continuous;
						e1 = subQueue.get(pos).clone();
						e1.length += continuous - 1;
						// 붙여서 출력해주기 위해 subentry 들을 삭제.
						// 2016.05.13 교차영숫자 체크를 전구간으로 확대하되 3글자 이하에서만 체크하도록 함.
						// if (pos == 0 || !option.isForDocument())
						if (continuous < 3) {
							for (int subInx = 0; subInx < continuous; subInx++) {
								// 2016.05.13 규칙에 의해 변경됨. 교차영숫자 머징 후 분리된글자는 삭제
								// 2014.12.29 1byte 씩 교차된 영 숫자는 분리하지 않는다
								// 삭제: ---분리된 글자는 지우지 않고 같이 출력하도록 한다.---
								e2 = subQueue.get(pos + subInx);
								subQueue.remove(pos + subInx);
								e1.subEntry.add(e2);
								logger.trace("remove entry : {} , C:{}/{}/{}", e2, continuous, subInx, inx);
								
								continuous--;
								subInx--;
								inx--;
							}
						}
						
						e1.type = ALPHANUM;
						e1.endOffset += e1.length - 1;
						if (frontMatch && rearMatch) {
							subQueue.add(pos + continuous, e1);
						} else if (frontMatch) {
							subQueue.add(pos + continuous, e1);
						} else if (rearMatch) {
							subQueue.add(pos, e1);
						}
						logger.trace("entry : {}", e1);
						inx -= continuous;
					}
					continuous = 0;
				} else {
					continuous = 0;
				}
			}
			
			logger.trace("subQueue : {}", subQueue);
			
			for (int inx = 0; inx < (subQueue.size() - 1); inx++) {
				e1 = subQueue.get(inx);
				e2 = subQueue.get(inx + 1);
				e3 = null;
				e4 = null;
				if (inx > 0) {
					e3 = subQueue.get(inx - 1);
				}
				if ((inx + 2) < subQueue.size()) {
					e4 = subQueue.get(inx + 2);
				}
				if (((e3 == null || (e3.type == SYMBOL && e3.start + e3.length == e1.start))
					&& (e1.type == ALPHA && e2.type == NUMBER && (e1.start + e1.length == e2.start))
					&& (e1.length == 1 && e2.length == 1)
					&& (e4 == null || (e4.type == SYMBOL && e2.start + e2.length == e4.start)))) {

					if (logger.isTraceEnabled()) {
						logger.trace("================================================================================");
						logger.trace("entry : {}{} // I:{} / C:{} / F:{} / R:{} / Q:{}", e1.makeTerm(null), e2.makeTerm(null), inx);
						logger.trace( "================================================================================");
					}
					
					e1.length++;
					e1.type = ALPHANUM;
					subQueue.remove(inx + 1);
				}
			}
			
			logger.trace("subQueue : {}", subQueue);
			List<RuleEntry> tmpList = new ArrayList<>();
			
			for (int inx = 0; inx < subQueue.size(); inx++) {
				e0 = subQueue.get(inx);
				if (e0.type == SYMBOL) {
					char c = e0.buf[e0.start];
					if (c == '-' || c == '_' || c == '/') {
						// 모델명 중 영문 특수문자(-/) 숫자 조합인 경우 첫 영문 형태소를 제외한 모델명도 추출
						// 모델명이 모두 추가텀으로 바뀌어야 하기 때문에 이 쪽은 모두 추가텀으로 돌리도록 한다.
						// 2014.09.02 특수규칙. 영숫자가 번갈아 가며 나온 경우 (ALPHANUM) 와 겹치는 규칙이 있으므로
						// 체크해서 지워준다.
						if (!option.useForQuery()) {
							if (inx == 1 && inx + 2 < subQueue.size()) {
								e1 = subQueue.get(inx - 1);
								e2 = subQueue.get(inx + 1);
								e3 = subQueue.get(inx + 2);
								e4 = subQueue.get(subQueue.size() - 1);
								if (!(e4.start == e2.start && e4.type == ALPHANUM)) {
									if (e1.length > 0 && e2.start > (e1.start + e1.length)
										&& e3.start == (e2.start + e2.length)
										&& e1.type == ALPHA
										&& ((e2.type == ALPHA && e3.type == NUMBER) 
										|| (e2.type == NUMBER && e3.type == ALPHA))) {
										RuleEntry newEntry = new RuleEntry(e2.buf, e2.start, e4.start + e4.length - e2.start, e2.startOffset, e4.endOffset, MODEL_NAME);
										logger.trace("QUEUE : {}", subQueue);
										logger.trace("NEW-ENTRY : {}", newEntry);
										tmpList.add(newEntry);
									}
								} else {
									logger.trace("remove duplicated term : {}", e4);
								}
							}
						}
						
						if (inx > 0 && inx + 1 < subQueue.size()) {
							e1 = subQueue.get(inx - 1);
							e2 = subQueue.get(inx + 1);
							if (!option.useForQuery()) {
								// 색인때에만
								// 모델명 중 숫자블럭 사이, 혹은 문자블럭 사이에 특수문자 가 있다면
								// 각각의 숫자, 혹은 문자 와 특수문자를 제거한 조합도 출력한다.
								int checkLevel = 0;
								if (e2.startOffset == e1.endOffset + 1) {
									if (e1.type == MODEL_NAME || e1.type == ALPHANUM) {
										if (e2.type == ALPHA || e2.type == NUMBER || e2.type == ALPHANUM || e2.type == MODEL_NAME) {
											checkLevel = 2;
										}
									} else if (e2.type == MODEL_NAME || e2.type == ALPHANUM) {
										if (e1.type == ALPHA || e1.type == NUMBER || e1.type == ALPHANUM || e1.type == MODEL_NAME) {
											checkLevel = 2;
										}
									} else {
										if (e1.type == ALPHA || e1.type == NUMBER) {
											if (e1.type == e2.type) {
												checkLevel = 3;
											}
										}
									}
								}
								if (checkLevel == 2) {
									// 앞단어 맨 뒤의 글자타입과 뒷단어 맨 첫번째의 글자타입이 같다면 수행
									String e1Type = getType(e1.buf[e1.start + e1.length - 1]);
									String e2Type = getType(e2.buf[e2.start]);
									if (e1Type == e2Type) {
										// 같은 타입만 잘라낸다.
										// 둘중 하나가 모델명 이었다면, 같은 타입의 부분만 잘라 내야 한다.
										int e1Length = 0;
										for (int sinx = e1.length; sinx > 0; sinx--) {
											if (getType(e1.buf[e1.start + sinx - 1]) == e1Type) {
												e1Length++;
											} else {
												break;
											}
										}
										e3 = e1.clone();
										e3.start += (e1.length - e1Length);
										e3.startOffset += (e1.length - e1Length);
										e3.length = e1Length;
										e3.endOffset = e3.startOffset + e1Length;
										e3.type = e1Type;
										e1 = e3;
										int e2Length = 0;
										for (int sinx = 0; sinx < e2.length; sinx++) {
											if (getType(e2.buf[e2.start + sinx]) == e2Type) {
												e2Length++;
											} else {
												break;
											}
										}
										e4 = e2.clone();
										e4.length = e2Length;
										e4.endOffset = e4.startOffset + e2Length;
										e4.type = e2Type;
										e2 = e4;
										checkLevel = 3;
									}
								}
								if (checkLevel == 3) {
									// 원본변형이 일어나기 때문에 버퍼는 따로 잡아 주어야 한다.
									RuleEntry clone = e1.clone();
									char[] buf = new char[e1.length + e2.length];
									System.arraycopy(e1.buf, e1.start, buf, 0, e1.length);
									System.arraycopy(e2.buf, e2.start, buf, e1.length, e2.length);
									clone.buf = buf;
									clone.start = 0;
									clone.length = buf.length;
									clone.startOffset = e1.startOffset;
									clone.endOffset = e2.endOffset;
									logger.trace("TRANS-MERGE:{} / {} / {}", clone, e0, e1);
									tmpList.add(clone);
								}
							}
						}
					}
					// 개별 특수문자는 모두 제거한다.
					subQueue.remove(inx);
					inx--;
				} else if (e0.type == UNIT_ALPHA) {
					// 단위명이었지만 규칙에 의해 모델명으로 전환되는 경우.
					// 단위명과 숫자를 구분해준다.
					logger.trace("subList:{}{}", "", e0.subEntry);
					e1 = e0.subEntry.remove(0);
					tmpList.add(e1);
					e0.start += e1.length;
					e0.length -= e1.length;
					e0.synonym = null;
					e0.type = ALPHA;
				} else if (e0.type == ALPHA) {
					// 강제분해 등으로 붙지 못한 영문자 끼리 이어준다.
					if (inx + 1 < subQueue.size()) {
						e1 = subQueue.get(inx + 1);
						if (e1.startOffset == e0.endOffset + 1 && e1.type == ALPHA) {
							e0.length += e1.length;
							e0.endOffset = e1.endOffset;
							subQueue.remove(inx + 1);
						}
					}
				}
			}
			
			logger.trace("subQueue : {}", subQueue);
			
			if (subQueue.size() > 2) {
				e0 = subQueue.get(0);
				e1 = subQueue.get(1);
				logger.trace("TERM:{}/{}", e1.start, e1.length);
				String finalType = getType(e1.buf[e1.start + e1.length - 1]);
				// 모델명 맨 앞의 두바이트가 타입이 틀리고 뒤에 기호가 나오면 붙여준다.
				if (e1.start == e0.start + e0.length &&
					e0.length == 1 && e1.length == 1 && finalType == SYMBOL
					&& ((e0.type == NUMBER && e1.type == ALPHA)
					|| (e0.type == ALPHA && e1.type == NUMBER))) {
					subQueue.remove(1);
					e0.length += e1.length;
					e0.endOffset = e0.startOffset + e0.length + e1.length;
					e0.type = MODEL_NAME;
				}
			}
			if (tmpList.size() > 0) {
				subQueue.addAll(tmpList);
			}
			////////////////////////////////////////////////////////////////////////////////
			Collections.sort(subQueue);
		}
		return settable;
	}
	
	private RuleEntry mergeQueue(int qinx, int typeContinuous, List<RuleEntry> queue, List<RuleEntry> subQueue) {
		int removeInx = qinx - typeContinuous;
		if (removeInx < 0) {
			removeInx = 0;
			typeContinuous--;
		}
		
		if (queue.get(removeInx + typeContinuous - 1).type == SYMBOL) {
			typeContinuous--;
		}
		
		if (typeContinuous == 1) {
			logger.trace("queue has only one entry. so don't merge it");
			return null;
		}
		
		logger.trace("queue[{}]={}", removeInx + typeContinuous - 1, queue.get(removeInx + typeContinuous - 1));
		
		RuleEntry entry = queue.get(removeInx).clone();
		logger.trace("ENTRY-CLONE:{}", entry);
		// 클로닝 되기 때문에 동의어를 반드시 삭제 해 주어야 한다.
		if (entry.type == UNIT_ALPHA) {
			entry.synonym = null;
		}
		entry.length = 0;
		entry.endOffset = entry.startOffset;
		String type = entry.type;
		for (; typeContinuous > 0; typeContinuous--) {
			RuleEntry subEntry = queue.remove(removeInx);
			logger.trace("pop entry:{}/{}:{}[{}~{}] / {}", subEntry, subEntry.start, subEntry.length, subEntry.startOffset, subEntry.endOffset, subEntry.synonym);
			if (type != subEntry.type) { type = null; }
			int start = subEntry.start;
			int length = subEntry.length;
			int endOffset = subEntry.endOffset;
			if (subQueue != null) {
				if (subEntry.type == UNIT_ALPHA) {
					// 단위명이었지만 규칙에 의해 모델명으로 전환되는 경우.
					// 단위명과 숫자를 구분해준다.
					logger.trace("subList:{}{}", "", subEntry.subEntry);
					RuleEntry number = subEntry.subEntry.remove(0);
					subQueue.add(number);
					subEntry.start += number.length;
					subEntry.length -= number.length;
					subEntry.startOffset += number.length;
					subEntry.synonym = null;
					subEntry.type = ALPHA;
					subQueue.add(subEntry);
				} else {
					subQueue.add(subEntry);
				}
			}
			logger.trace("merge : {}[{}:{}/{}~{}]", subEntry, subEntry.start, subEntry.length, subEntry.startOffset, subEntry.endOffset);
			logger.trace("subQueue : {}", subQueue);
			entry.length = start + length - entry.start;
			entry.endOffset = endOffset;
		}
		entry.type = type;
		queue.add(removeInx, entry);
		return entry;
	}
	
	private boolean findUnit(CharVector key, String type) {
		// 영문의 경우에만 줄여가면서 단위명 체크를 시도한다.
		key.ignoreCase();
		if (type == ALPHA) {
			// 단위명으로 5글자 이상 보지 않는다.
			if (key.length() > MAX_UNIT_LENGTH) {
				key.length(MAX_UNIT_LENGTH);
			}
			for (int len = key.length(); len >= 0; len--) {
				key.length(len);
				if (unitDictionary != null && unitDictionary.set().contains(key)) {
					logger.trace("found unit : {}", key);
					return true;
				}
			}
			key.length(key.length() - 1);
			return false;
		} else {
			if (unitDictionary != null && unitDictionary.set().contains(key)) {
				logger.trace("found unit : {}", key);
				return true;
			}
		}
		return false;
	}
	
	public List<CharSequence> synonymExtract(List<CharSequence> synonyms, RuleEntry entry) {
		ProductNameParsingRule parsingRule = this.clone();
		parsingRule.option = new AnalyzerOption();
		parsingRule.option.useSynonym(false);
		parsingRule.option.useStopword(true);
		List<RuleEntry> entryList = parsingRule.getQueue();

		List<CharSequence> result = null;
		Set<String> dupSet = new HashSet<>();
		for (int synonymInx = 0; synonymInx < synonyms.size(); synonymInx++) {
			CharVector synonym = CharVector.valueOf(synonyms.get(synonymInx));
			logger.trace("synonym:{}", synonym);

			List<CharVector> synonymTokens = new ArrayList<>(1);
			if (synonym instanceof CharVector) {
				CharVector synonymCV = (CharVector) synonym;
				// 유사어가 공백이 포함된 여러어절로 구성되어 있을 경우 처리.
				if (synonymCV.hasWhitespaces()) {
					List<CharVector> terms = synonymCV.splitByWhitespace();
					for (CharVector term : terms) {
						if (term.length() > 0) {
							synonymTokens.add(term);
						}
					}
				} else {
					synonymTokens.add(synonymCV);
				}
			}

			// 어절별로 처리한다. 하나의 어절에서 여러 단어가 나올수 있다.
			StringBuilder sb = new StringBuilder();
			for (CharVector synonymCV : synonymTokens) {
				logger.trace("TRACING SYNONYM : {}", synonymCV);
				extractor.setInput(synonymCV.array(), synonymCV.offset(), synonymCV.length());
				ExtractedEntry cvEntry = extractor.extract();
				// 분석된 동의어에서 분석된 단어들은 차후 AND 조건으로 이어져야 한다.
				while (cvEntry != null) {
					CharVector cv = synonymCV.clone();
					cv.offset(cvEntry.offset());
					cv.length(cvEntry.column());
					if (cv.length() < synonymCV.length()) {
						cv = cv.trim();
						if (cv.length() > 0) {
							// NOTE! 동의어를 모델명분석을 하는편과 하지 않는 편 어느쪽이 더 분석결과가 나은지 비교할 것.
							addEntry(entryList, cv, entry.type, cvEntry.posTag(), 
								entry.startOffset, entry.endOffset, spaceDictionary);
						}
					} else {
						addEntry(entryList, synonymCV, entry.type, cvEntry.posTag(), 
							entry.startOffset, entry.endOffset, spaceDictionary);
						break;
					}
					cvEntry = cvEntry.next();
				}

				if (entryList.size() > 0) {
					logger.trace("SYNONYM-QUEUE:{}", entryList);
					parsingRule.init(entryList);
					parsingRule.processRule(entryList, false);
					CharVector token = new CharVector();
					while (entryList.size() > 0) {
						entryList.remove(0).makeTerm(token);
						if (sb.length() > 0) {
							sb.append(" ");
						}
						sb.append(token.toString());
					}
				}
			}

			if (sb.length() > 0) {
				String idString = sb.toString();
				if (!dupSet.contains(idString)) {
					if (result == null) {
						result = new ArrayList<>();
					}
					result.add(idString);
					logger.trace("synonym:{}", idString);
					dupSet.add(idString);
				}
			}
		}
		return result;
	}
	
	private boolean containsDictionary(CharVector cv) {
		if (extractor.dictionary().find(cv) != null ||
			userDictionary.contains(cv)) {
			return true;
		}
		return false;
	}
	
	public static class RuleEntry implements Comparable<RuleEntry> {
		char[] buf;
		int start;
		int length;
		int startOffset;
		int endOffset;
		String type;
		boolean modifiable;
		CharSequence[] synonym;
		List<RuleEntry> subEntry;
		
		public RuleEntry(char[] buf, int start, int length, int startOffset, int endOffset, String type) {
			this.buf = buf;
			this.start = start;
			this.length = length;
			this.startOffset = startOffset;
			this.endOffset = endOffset;
			this.type = type;
			this.subEntry = new ArrayList<>();
			this.modifiable = true;
		}

		public CharVector makeTerm(CharVector cv) {
			return makeTerm(cv, start, length);
		}
		
		public CharVector makeTerm(CharVector cv, int start, int length) {
			if (cv == null) {
				cv = new CharVector();
			}
			cv.init(buf, start, length);
			return cv;
		}
		
		public RuleEntry clone() {
			RuleEntry entry = new RuleEntry(buf, start, length, startOffset, endOffset, type);
			entry.synonym = synonym;
			entry.subEntry = subEntry;
			return entry;
		}
		
		@Override public String toString() {
			String str = "";
			try {
				str = "\"" + new String(buf, start, length) + "\"";
			} catch (Exception ex) {
				str = "\"" + new String(buf) + "\" [" + start + ":" + length + "]";
				logger.debug("EE:{}", ex.getMessage());
			}
			str += " / " + type + " / " + modifiable + " (" + start + "~" + (start + length) + "/" + startOffset + "~" + endOffset + ")";
			if (subEntry != null) {
				str += "[" + subEntry.size() + "]";
			}
			return str;
		}

		@Override public int compareTo(RuleEntry entry) {
			int ret = 0;
			ret = this.startOffset - entry.startOffset;
			if (ret == 0) {
				ret = entry.endOffset - this.endOffset;
			}
			return ret;
		}
	}
}
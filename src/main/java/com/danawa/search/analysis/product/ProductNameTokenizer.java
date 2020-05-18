package com.danawa.search.analysis.product;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.product.KoreanWordExtractor.Entry;
import com.danawa.util.CharVector;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.util.AttributeFactory;
import org.elasticsearch.common.logging.Loggers;

public class ProductNameTokenizer extends Tokenizer {

	private static Logger logger = Loggers.getLogger(ProductNameTokenizer.class, "");

	public static final int IO_BUFFER_SIZE = 100;
	public static final int FULL_TERM_LENGTH = 64;

	public static final String WHITESPACE = "<WHITESPACE>";
	public static final String SYMBOL = "<SYMBOL>";
	public static final String ALPHA = "<ALPHA>";
	public static final String NUMBER = "<NUMBER>";
	public static final String HANGUL = "<HANGUL>";
	public static final String HANGUL_JAMO = "<HANGUL_JAMO>";
	public static final String JAPANESE = "<JAPANESE>";
	public static final String CHINESE = "<CHINESE>";
	public static final String OTHER_LANGUAGE = "<OTHER_LANGUAGE>";
	public static final String UNCATEGORIZED = "<UNCATEGORIZED>"; // 글자에대한 분류없음.

	public static final String NUMBER_TRANS = "<NUMBER_TRANS>";
	public static final String MODEL_NAME = "<MODEL_NAME>";
	public static final String ALPHANUM = "<ALPHANUM>";
	public static final String ASCII = "<ASCII>";
	public static final String UNIT = "<UNIT>";
	public static final String UNIT_ALPHA = "<UNIT_ALPHA>";
	public static final String FULL_STRING = "<FULL_STRING>";
	public static final String MAKER = "<MAKER>";
	public static final String BRAND = "<BRAND>";
	public static final String COMPOUND = "<COMPOUND>";

	public static final int MAX_UNIT_LENGTH = 5;

	public static final char[] AVAIL_SYMBOLS = new char[] {
		// 일반적으로 포함할 수 있는 모든 특수기호들
		'-', '.', '/', '+', '&' };

	public static final char[] AVAIL_SYMBOLS_STANDALONE = new char[] {
		// 독립적으로 사용될수 있는 특수기호들 (변경시 추가)
	};

	public static final char[] AVAIL_SYMBOLS_CONNECTOR = new char[] {
		// 예외특수문자, 연결자로 사용될 수 있는 기호들
		'-', '.', '/', '&' };

	public static final char SYMBOL_COLON = ':';
	public static final Pattern PTN_NUMBER = 
		//숫자판단 패턴
		Pattern.compile(
			"^(" //규칙시작 (앞공백 없음)
				+ "("
					+ "("
						+ "([0-9]{0,3}([,][0-9]{3})*)" + "|"  //3자리(,) 단위웃자의 연속이거나
						+ "([0-9]+)" //순수숫자의 연속이거나
					+ ")"
					+ "([.][0-9]+)*" //소숫점이 나온다면 뒤에는 순수숫자의 연속이어야 함
				+ ")"
			+ ")"
			+ "("
				+ "[:]" // : 뒤에 같은 규칙으로 숫자가 나열되는 경우 (1,000:10.5 등)
				+ "("
					+ "("
						+ "([0-9]{0,3}([,][0-9]{3})*)" + "|"
						+ "([0-9]+)"
					+ ")"
					+ "([.][0-9]+)*"
				+ ")"
			+ "){0,1}" //없거나 한번등장
			+ "$" //규칙끝 (뒷공백 없음)
		);

	protected static final char[] AVAIL_SYMBOLS_INNUMBER = new char[] {
		// 예외특수문자, 숫자에 사용이 가능한 기호들
		// 아래 항목이 바뀌는 경우 숫자와 관련된 정규식도 같이 바뀌어야 함
		',', '.', SYMBOL_COLON };

	protected static final char[] AVAIL_SYMBOLS_SPLIT = new char[] {
		// 단어사이에 구분자로 올 수 있는 기호들, 사용자단어에 들어가는 기호는 삭제해야 한다.
		',', '|', '[', ']', '<', '>', '{', '}' };

	protected final CharTermAttribute charAttribute = addAttribute(CharTermAttribute.class);
	private final TokenInfoAttribute tokenAttribute = addAttribute(TokenInfoAttribute.class);
	private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
	private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);

	private char[] buffer;
	private char[] workBuffer;
	// private char[] freshBuffer;
	// private char[] fullTermBuffer;
	private int position;
	// private int positionPrev;
	private int readLength;
	// private int readLengthPrev;
	// private int lastLength;
	private int baseOffset;
	// private String splitType;
	private int offset;

	private SynonymDictionary synonymDictionary;
	private KoreanWordExtractor extractor;

	protected ProductNameTokenizer(ProductNameDictionary dictionary) {
		if (dictionary != null) {
			synonymDictionary = dictionary.getDictionary(ProductNameAnalysisFilter.DICT_SYNONYM, SynonymDictionary.class);
			extractor = new KoreanWordExtractor(dictionary);
			tokenAttribute.dictionary(dictionary);
		}
		init();
	}

	public ProductNameTokenizer(AttributeFactory factory) {
		super(factory);
		init();
	}

	private void init() {
		workBuffer = new char[IO_BUFFER_SIZE];
		// fullTermBuffer = new char[FULL_TERM_LENGTH];
		position = readLength = baseOffset = offset = 0;
		super.clearAttributes();
	}

	// public final boolean incrementTokenOld() throws IOException {
	// 	boolean ret = false;
	// 	CharVector token = new CharVector();
	// 	// 전체동의어는 전체 단어중 동의어가 존재하는것만 체크해야 하므로 먼저 체크한다.
	// 	if (readLength == -1 && readLengthPrev > 0 && readLengthPrev >= position) {
	// 		if (fullTermBuffer != null) {
	// 			CharSequence fullString = new CharVector(fullTermBuffer, 0, readLengthPrev);
	// 			if (synonymDictionary != null && synonymDictionary.map().containsKey(fullString)) {
	// 				offsetAttribute.setOffset(0, readLengthPrev);
	// 				CharVector charsRef = tokenAttribute.ref();
	// 				charsRef.init(fullTermBuffer, 0, readLengthPrev);
	// 				// 앞 뒤 공백을 제거한다.
	// 				for (int inx = charsRef.length() - 1; inx >= 0; inx--) {
	// 					if (getType(charsRef.array()[inx]) != WHITESPACE) {
	// 						break;
	// 					}
	// 					charsRef.length(charsRef.length() - 1);
	// 				}
	// 				for (int inx = 0; inx < charsRef.length(); inx++) {
	// 					if (getType(charsRef.array()[inx]) != WHITESPACE) {
	// 						break;
	// 					}
	// 					charsRef.offset(charsRef.offset() + 1);
	// 					charsRef.length(charsRef.length() - 1);
	// 				}
	// 				typeAttribute.setType(FULL_STRING);
	// 				fullTermBuffer = null;
	// 				return true;
	// 			}
	// 		}
	// 		return false;
	// 	}

	// 	ret = hasToken();


	// 	if (ret) {
	// 		token = tokenAttribute.ref();
	// 		offsetAttribute.setOffset(baseOffset + token.offset(), baseOffset + token.offset() + token.length());
	// 		logger.trace("return \"{}\" {}~{}", tokenAttribute, offsetAttribute.startOffset(),
	// 			offsetAttribute.endOffset());
	// 		if (tokenAttribute.ref() != null && tokenAttribute.ref().offset() == 0
	// 			&& tokenAttribute.ref().length() == 0) {
	// 			return true;
	// 		}
	// 	}

	// 	// 공백은 건너 뜀.
	// 	for (; position < readLength; position++) {
	// 		// if(logger.isTraceEnabled()) {
	// 		// logger.trace("char[{}]:{}", position,getType(buffer[position]));
	// 		// }
	// 		if (getType(workBuffer[position]) != WHITESPACE) {
	// 			break;
	// 		}
	// 	}
	// 	positionPrev = position;
	// 	return ret;
	// }

	// private boolean hasToken() throws IOException {
	// 	// IO_BUFFER_SIZE 에 맞게 잘라서 읽어들인 후
	// 	// 공백문자별로 토크닝 하여 규칙성 분류로 넘겨줌
	// 	// logger.trace("position:{} / positionPrev:{} / readLength:{}", position,
	// 	// positionPrev, readLength);
	// 	while (true) {
	// 		if (position == -1) {
	// 			position = 0;
	// 			tokenAttribute.ref(workBuffer, 0, 0);
	// 			return true;
	// 		}

	// 		if (position == 0 && freshBuffer != null) {
	// 			// logger.trace("readPrev:{} / lastLength:{}", readLengthPrev, lastLength);
	// 			baseOffset += (readLengthPrev - lastLength);
	// 			// logger.trace("baseOffset : {}", baseOffset);
	// 			readLength += lastLength;
	// 			// 0번 주소로 밀어내고
	// 			logger.trace("move buffer offset {} => {} / {} chars", positionPrev, 0, lastLength);
	// 			// CharVector 특성상 내보내지 않은 버퍼에 영향을 줄 수 있으므로 새로 버퍼를 잡아준다.
	// 			workBuffer = Arrays.copyOf(workBuffer, workBuffer.length);
	// 			System.arraycopy(workBuffer, positionPrev, workBuffer, 0, lastLength);
	// 			// 신규데이터를 뒤이어 입력한다.
	// 			System.arraycopy(freshBuffer, 0, workBuffer, lastLength, readLength - lastLength);
	// 			freshBuffer = null;
	// 			tokenAttribute.ref(workBuffer, 0, readLength);
	// 			// if(logger.isTraceEnabled()) {
	// 			// logger.trace("readLength:{} / lastLength:{}", readLength, lastLength);
	// 			// logger.trace("buffer : {} / read : {} char : {}", termAttribute, readLength,
	// 			// new String(buffer, lastLength, readLength - lastLength));
	// 			// }
	// 			freshBuffer = null;
	// 		}

	// 		if (readLength > position) {
	// 			if (splitType != null) {
	// 				splitType = null;
	// 				position++;
	// 				return true;
	// 			}
	// 			// FIXME: 앞공백 제거는 읽어온 이후 최초 한번만 필요함.
	// 			// 앞 공백 제거
	// 			for (; position < readLength; position++) {
	// 				if (getType(workBuffer[position]) != WHITESPACE) {
	// 					break;
	// 				}
	// 			}
	// 			positionPrev = position;

	// 			char c1 = workBuffer[positionPrev];
	// 			char c2 = 0;
	// 			char c0 = 0;
	// 			String t1 = getType(c1);
	// 			String t2 = null;
	// 			for (; position < readLength; position++) {
	// 				// 기본적으로는 공백단위로 토크닝 한다.
	// 				// 만약 한글과 특수문자가 섞여있다면 우선은 분리한다
	// 				c2 = workBuffer[position];
	// 				c0 = 0;
	// 				t2 = getType(c2);
	// 				if (position > 0) {
	// 					c0 = workBuffer[position - 1];
	// 				}
	// 				// logger.trace("C1:{} [{}] / C2:{} [{}]", c1, t1, c2, t2);
	// 				if (t1 == WHITESPACE) {
	// 					tokenAttribute.offset(positionPrev, position - positionPrev);
	// 					position++;
	// 					return true;
	// 				} else if (t2 == WHITESPACE) {
	// 					tokenAttribute.offset(positionPrev, position - positionPrev);
	// 					position++;
	// 					return true;
	// 				} else if (position > 0 && getType(workBuffer[position - 1]) == NUMBER && c2 > 128) {
	// 					/**
	// 					 * 숫자직후 유니코드는 단위명일 확률이 높으므로 연결하여 출력
	// 					 */
	// 					c1 = c2;
	// 					t1 = t2;
	// 				} else if (t1 == SYMBOL && (containsChar(AVAIL_SYMBOLS_SPLIT, c1) || c1 > 128)
	// 						&& position != positionPrev) {
	// 					tokenAttribute.offset(positionPrev, position - positionPrev);
	// 					return true;
	// 				} else if (t2 == SYMBOL && (containsChar(AVAIL_SYMBOLS_SPLIT, c2) || c2 > 128)
	// 						&& position != positionPrev) {
	// 					tokenAttribute.offset(positionPrev, position - positionPrev);
	// 					return true;
	// 				} else if (((c0 < 128 && c2 > 128) || (c2 < 128 && c0 > 128)) && position != positionPrev) {
	// 					/**
	// 					 * 기존 토크나이징은 타입별로 분리가 되지 않기 때문에 바로 직전 문자타입과 비교하여 알파벳 과 유니코드 정도는 우선 분리해 주도록 한다
	// 					 */
	// 					tokenAttribute.offset(positionPrev, position - positionPrev);
	// 					return true;
	// 				}
	// 			}
	// 		}

	// 		if (readLength == position) {
	// 			if (readLength != 0 && positionPrev == 0) {
	// 				// 한번도 플러시되지 않은 프로세스에서 버퍼를 다 읽은 경우라면 한번 플러시 해 준다
	// 				positionPrev = position;
	// 				return true;
	// 			}

	// 			lastLength = readLength - positionPrev;
	// 			// 버퍼를 다 읽었으므로, 이어서 읽을것이 있는지 먼저 확인 해야 한다.

	// 			// logger.trace("read position reached. readLength:{} / position:{} /
	// 			// positionPrev:{} / lastLength:{} / bufferLength:{}", readLength, position,
	// 			// positionPrev, lastLength, buffer.length);

	// 			if (readLength != -1) {
	// 				// if(logger.isTraceEnabled()) {
	// 				// logger.trace("try read from {} length:{}", lastLength,
	// 				// buffer.length-lastLength);
	// 				// }
	// 				freshBuffer = new char[IO_BUFFER_SIZE];
	// 				readLengthPrev = readLength;
	// 				readLength = input.read(freshBuffer, 0, workBuffer.length - lastLength);
	// 				// 읽기에 성공한 경우
	// 				if (readLength > 0) {
	// 					// 전체문자열을 저장할 버퍼 전체문자열 제한 크기가 넘어가면 그냥 버린다.
	// 					if (fullTermBuffer != null && baseOffset + readLength < fullTermBuffer.length) {
	// 						System.arraycopy(freshBuffer, 0, fullTermBuffer, baseOffset + readLengthPrev, readLength);
	// 						// if(logger.isTraceEnabled()) {
	// 						// logger.trace("total string : \"{}\"",
	// 						// new String(stringbuffer,0,baseOffset + readLengthPrev + readLength));
	// 						// }
	// 					} else {
	// 						fullTermBuffer = null;
	// 					}
	// 					position = -1;
	// 				} else {
	// 					freshBuffer = null;
	// 					// 이전버퍼의 남은부분을 리턴하고 끝.
	// 					if (lastLength > 0) {
	// 						tokenAttribute.ref(workBuffer, positionPrev, lastLength);
	// 						// logger.trace("readLength:{} / positionPrev:{} / readLengthPrev:{} / last
	// 						// term:{}", lastLength, positionPrev, readLengthPrev, termAttribute);
	// 						// 딱 맞게 읽어 더이상 읽을것이 없는 경우.
	// 						return true;
	// 					} else {
	// 						return false;
	// 					}
	// 				}
	// 			} else {
	// 				return false;
	// 			}
	// 			continue;
	// 		}
	// 		break;
	// 	}
	// 	return false;
	// }

	////////////////////////////////////////////////////////////////////////////////

	private Entry entry;

	@Override
	public final boolean incrementToken() throws IOException {
		boolean ret = false;
		while (!tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_FINISHED)) {
// 임시코드. 테스트시 무한루프에 의한 프리징 방지
// try { Thread.sleep(300); } catch (Exception ignore) { }
			if (position >= readLength) {
				////////////////////////////////////////////////////////////////////////////////
				// 1. 원본 읽어오기 (버퍼 크기만큼 읽어옴)
				// 읽어온 버퍼가 없거나 모두 처리한 상태라면  리더에서 읽어온다.
				////////////////////////////////////////////////////////////////////////////////
				buffer = new char[IO_BUFFER_SIZE];
				baseOffset += readLength;
				readLength = input.read(buffer, 0, buffer.length);
				if (readLength != -1) {
					tokenAttribute.ref(buffer, 0, 0);
					tokenAttribute.rmState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED);
					position = offset = 0;
					entry = null;
				} else {
					tokenAttribute.addState(TokenInfoAttribute.STATE_INPUT_FINISHED);
				}
				if (readLength > 0 && readLength < FULL_TERM_LENGTH && baseOffset == 0) {
					// FULL-TERM, 테스트 후 (공백포함여부, 유니코드포함여부) TokenInfoAttribute 에 입력.
					ret = true;
					for (int inx = 0; inx < readLength; inx++) {
						char ch = buffer[inx];
						String type = getType(ch);
						if (!(type != WHITESPACE && (type == ALPHA || type == NUMBER || type == SYMBOL))) {
							ret = false;
						}
					}
					if (ret) {
						tokenAttribute.ref(buffer, 0, readLength);
						tokenAttribute.posTag(null);
						offsetAttribute.setOffset(0, readLength);
						typeAttribute.setType(FULL_STRING);
						break;
					}
				}
			} else {
				if (entry == null) {
					////////////////////////////////////////////////////////////////////////////////
					// 2. 버퍼 내 단순 토크닝 (공백 / 특수기호에 의한 분해)
					////////////////////////////////////////////////////////////////////////////////
					if (offset < position) { offset = position; }
					char c1 = buffer[position];
					char c2 = 0;
					String t1 = getType(c1);
					String t2 = null;
					// 기본적으로는 공백단위로 토크닝 한다. 만약 한글과 특수문자가 섞여있다면 우선은 분리한다
					for (offset++ ; offset < readLength; offset++) {
						c2 = buffer[offset];
						t2 = getType(c2);
						if (t1 == WHITESPACE && t2 != WHITESPACE) {
							// 공백뒤 일반문자. 토크닝 시작 위치 설정
							position = offset;
						} else if (t1 == WHITESPACE && t2 == WHITESPACE) {
							// 공백뒤 공백. 아무일 하지 않음.
						} else if (t1 != WHITESPACE && t2 == WHITESPACE) {
							// 일반문자 뒤 공백. 끊어줌.
							ret = true;
							break;
						} else if (t1 == NUMBER && c2 > 128) {
							// 숫자직후 유니코드. 단위명일 확률이 높으므로 연결하여 출력
						} else if (t1 != SYMBOL && t2 == SYMBOL && (containsChar(AVAIL_SYMBOLS_SPLIT, c2) || c2 > 128)) {
							ret = true;
							break;
						} else if ((c1 < 128 && c2 > 128) || (c2 < 128 && c1 > 128)) {
							// 알파벳 과 유니코드 분리
							ret = true;
							break;
						}
						c1 = c2;
						t1 = t2;
					} // LOOP (offset)
					// 버퍼의 끝까지 간 경우 토큰으로 인정
					if (offset >= readLength) { 
						if (!ret && t1 != WHITESPACE) {
							ret = true; 
						} else {
							// 공백으로 끝난경우 종료조건 충족
							position = readLength;
						}
					}
					if (ret == true) {
						if (logger.isTraceEnabled()) {
							logger.trace("BLOCK:{}", new CharVector(buffer, position, offset - position));
						}
						if (extractor != null) {
							int length = offset - position;
							if (length > extractor.getTabularSize()) {
								length = extractor.getTabularSize();
							}
							extractor.setInput(buffer, position, length);
							entry = extractor.extract();
						} else {
							tokenAttribute.ref(buffer, position, offset - position);
							offsetAttribute.setOffset(baseOffset + position, baseOffset + offset);
							position = offset;
							// 마지막 공백이 있는경우 건너뜀
							for (; position < readLength; position++) { 
								if (getType(buffer[position]) != WHITESPACE) { break;} 
							}
							if (position == readLength) {
								tokenAttribute.addState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED);
							}
							break;
						}
					}
				} else {
					////////////////////////////////////////////////////////////////////////////////
					// 3. 한글분해
					////////////////////////////////////////////////////////////////////////////////
					// 분해된 한글이 있으므로 속성에 출력해 준다
					if (entry.column() < 0) {
						entry = entry.next();
						continue;
					}
					tokenAttribute.ref(buffer, entry.offset(), entry.column());
					tokenAttribute.posTag(entry.posTag());
					offsetAttribute.setOffset(baseOffset + entry.offset(), baseOffset + entry.offset() + entry.column());

					position = entry.offset() + entry.column();
					entry = entry.next();
					if (entry == null) {
						// 분해된 한글이 없다면 다음구간 한글분해 시도
						if (position < offset) {
							int length = offset - position;
							if (length > extractor.getTabularSize()) {
								length = extractor.getTabularSize();
							}
							extractor.setInput(buffer, position, length);
							entry = extractor.extract();
						}
					}
					// 마지막 공백이 있는경우 건너뜀
					for (; position < readLength; position++) {
						if (getType(buffer[position]) != WHITESPACE) { break;} 
					}
					if (position == readLength) {
						tokenAttribute.addState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED);
					}
					ret = true;
					break;
				}
			}
		} // LOOP
		return ret;
	}

	protected static boolean containsChar(char[] array, char c) {
		// 문자소지여부확인
		for (char ch : array) {
			if (ch == c) {
				return true;
			}
		}
		return false;
	}

	protected static boolean containsChar(char[] array, char[] buf, int length) {
		// 여러개 문자중 하나라도 소지하고 있는지 확인
		for (char ch : array) {
			if (!containsChar(array, ch)) {
				return false;
			}
		}
		return true;
	}

	public static String isSplit(char c) {
		String ret = getType(c);
		if (ret == WHITESPACE || ret == SYMBOL) {
			return ret;
		} else if (ret == SYMBOL && (containsChar(AVAIL_SYMBOLS_SPLIT, c) || (c > 128))) {
			// unicode 특수문자는 모두 구분자로 사용한다.
			return ret;
		}
		return null;
	}

	public static String getUniType(CharSequence cv) {
		String ret = null;
		String prevType = null;
		String type = null;
		for (int inx = 0; inx < cv.length(); inx++) {
			prevType = type;
			type = getType(cv.charAt(inx));
			if (prevType != null && type != prevType) {
				if (prevType != type) {
					type = UNCATEGORIZED;
					break;
				}
			}
		}
		ret = type;
		return ret;
	}

	public static String getTermType(CharSequence cv) {
		String ret = null;
		String prevType = null;
		String type = null;
		for (int inx = 0; inx < cv.length(); inx++) {
			prevType = type;
			type = getType(cv.charAt(inx));
			logger.trace("TYPE:{} / {}", type, cv);
			if (prevType != null && type != prevType) {
				if ((prevType == ALPHA && type == NUMBER) || (prevType == NUMBER && type == ALPHA)) {
					type = ALPHANUM;
				} else if (prevType == ALPHANUM && (type == ALPHA || type == NUMBER)) {
					type = ALPHANUM;
				} else if ((prevType == ALPHA && type == SYMBOL) || (prevType == NUMBER && type == SYMBOL)
						|| (prevType == ALPHANUM && type == SYMBOL)) {
					type = ASCII;
				} else if (prevType == ASCII && (type == ALPHA || type == NUMBER || type == SYMBOL)) {
					type = ASCII;
				} else {
					type = UNCATEGORIZED;
				}
			} else {

			}
			logger.trace("TYPE:{} / PREV:{}", type, prevType);
		}
		ret = type;
		return ret;
	}

	public static String getType(int ch) {
		if(Character.isWhitespace(ch)){
			return WHITESPACE;
		}
		
		int type = Character.getType(ch);
		
		switch(type){
		case Character.DASH_PUNCTUATION:
		case Character.START_PUNCTUATION:
		case Character.END_PUNCTUATION:
		case Character.CONNECTOR_PUNCTUATION:
		case Character.OTHER_PUNCTUATION:
		case Character.MATH_SYMBOL:
		case Character.CURRENCY_SYMBOL:
		case Character.MODIFIER_SYMBOL:
		case Character.OTHER_SYMBOL:
		case Character.INITIAL_QUOTE_PUNCTUATION:
		case Character.FINAL_QUOTE_PUNCTUATION:
			return SYMBOL;
		case Character.OTHER_LETTER:
			//외국어.
			Character.UnicodeBlock unicodeBlock = Character.UnicodeBlock.of(ch);
			if (unicodeBlock == Character.UnicodeBlock.HANGUL_SYLLABLES){
				return HANGUL;
			} else if (unicodeBlock == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO) {
				return HANGUL_JAMO;
			} else if (unicodeBlock == Character.UnicodeBlock.HIRAGANA || unicodeBlock == Character.UnicodeBlock.KATAKANA) {
				return JAPANESE;
			} else if (unicodeBlock == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS) {
				return CHINESE;
			}else{
				return OTHER_LANGUAGE;
			}
		case Character.UPPERCASE_LETTER:
		case Character.LOWERCASE_LETTER:
			//영어.
			return ALPHA;
			
		case Character.DECIMAL_DIGIT_NUMBER:
			return NUMBER;
		}
		
		return UNCATEGORIZED;
	}

	@Override
	public void reset() throws IOException {
		super.reset();
		Arrays.fill(workBuffer, (char) 0);
		// if(fullTermBuffer == null) {
		// 	fullTermBuffer = new char[FULL_TERM_LENGTH];
		// }
		// position = positionPrev = readLength = readLengthPrev = 0;
		position = readLength = 0;
		baseOffset = offset = 0;
		entry = null;
		this.clearAttributes();
	}

	@Override
	public void end() throws IOException {
		super.end();
	}

	@Override
	public void close() throws IOException {
		super.close();
	}
}
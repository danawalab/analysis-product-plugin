package com.danawa.search.analysis.product;

import java.io.IOException;
import java.util.Arrays;
import java.util.regex.Pattern;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor.ExtractedEntry;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
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
	
	public static final String DICT_UNIT_SYNONYM = "unit_synonym";
	public static final String DICT_UNIT = "unit";
	public static final String DICT_SPACE = "space";
	public static final String DICT_SYNONYM = "synonym";
	public static final String DICT_STOP = "stop";
	public static final String DICT_USER = "user";
	public static final String DICT_COMPOUND= "compound";
	public static final String DICT_MAKER = "maker";
	public static final String DICT_BRAND = "brand";

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

	private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
	private final TokenInfoAttribute tokenAttribute = addAttribute(TokenInfoAttribute.class);
	private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
	private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);

	private ExtractedEntry entry;
	private char[] buffer;
	private char[] workBuffer;
	private int position;
	private int readLength;
	private int baseOffset;
	private int offset;
	private boolean exportTerm;

	private KoreanWordExtractor extractor;
	private SynonymDictionary synonymDictionary;

	protected ProductNameTokenizer(ProductNameDictionary dictionary, boolean exportTerm) {
		if (dictionary != null) {
			extractor = new KoreanWordExtractor(dictionary);
			tokenAttribute.dictionary(dictionary);
			synonymDictionary = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
		}
		this.exportTerm = exportTerm;
		init();
	}

	public ProductNameTokenizer(AttributeFactory factory) {
		super(factory);
		init();
	}

	private void init() {
		workBuffer = new char[IO_BUFFER_SIZE];
		position = readLength = baseOffset = offset = 0;
		super.clearAttributes();
	}

	@Override
	public final boolean incrementToken() throws IOException {
		boolean ret = false;
		typeAttribute.setType(null);
		while (!tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_FINISHED)) {
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
						if (synonymDictionary != null && synonymDictionary.containsKey(
							new CharVector(buffer, 0, readLength))) {
							tokenAttribute.ref(buffer, 0, readLength);
							tokenAttribute.posTag(null);
							offsetAttribute.setOffset(0, readLength);
							typeAttribute.setType(FULL_STRING);
							break;
						} else {
							ret = false;
						}
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
					if (exportTerm) {
						termAttribute.copyBuffer(buffer, entry.offset(), entry.column());
					}

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

	public static boolean isAlphaNum(CharVector cv) {
		boolean ret = true;
		if (cv.length() == 0) {
			return false;
		}
		for (int inx = 0; inx < cv.length(); inx++) {
			String type = ProductNameTokenizer.getType(cv.array()[cv.offset() + inx]);
			if (!(type == ProductNameTokenizer.ALPHA || type == ProductNameTokenizer.NUMBER)) {
				ret = false;
				break;
			}
		}
		return ret;
	}
	

	public static boolean containsChar(char[] array, char c) {
		// 문자소지여부확인
		for (char ch : array) {
			if (ch == c) {
				return true;
			}
		}
		return false;
	}

	public static boolean containsChar(char[] array, char[] buf, int length) {
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

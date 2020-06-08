package com.danawa.search.analysis.product;

import java.io.IOException;
import java.util.regex.Pattern;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.search.analysis.korean.KoreanWordExtractor.ExtractedEntry;
import com.danawa.util.CharVector;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.util.AttributeFactory;
import org.elasticsearch.common.logging.Loggers;

public final class ProductNameTokenizer extends Tokenizer {

	private static Logger logger = Loggers.getLogger(ProductNameTokenizer.class, "");

	public static final int DEFAULT_MAX_WORD_LEN = 255;
	private static final int IO_BUFFER_SIZE = 200;
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

	private final CharTermAttribute termAtt = addAttribute(CharTermAttribute.class);
	private final TokenInfoAttribute tokenAtt = addAttribute(TokenInfoAttribute.class);
	private final OffsetAttribute offsetAtt = addAttribute(OffsetAttribute.class);
	private final TypeAttribute typeAtt = addAttribute(TypeAttribute.class);

	private int 
		baseOffset = 0, 
		position = 0, 
		extLength = 0, 
		readLength = 0, 
		tokenLength = 0,
		finalOffset = 0;
	private char[] buffer = new char[IO_BUFFER_SIZE];
	private ExtractedEntry entry;
	private KoreanWordExtractor extractor;
	private char chrCurrent;
	private String typeCurrent;
	private boolean exportTerm;

	public ProductNameTokenizer(ProductNameDictionary dictionary, boolean exportTerm) {
		if (dictionary != null) {
			extractor = new KoreanWordExtractor(dictionary);
			tokenAtt.dictionary(dictionary);
		}
		this.exportTerm = exportTerm;
	}

	public ProductNameTokenizer(AttributeFactory factory) {
		super(factory);
	}

	protected boolean isTokenChar(int c1, int c2) {
		return !Character.isWhitespace(c1);
	}

	@Override
	public final boolean incrementToken() throws IOException {
		typeAtt.setType(null);
		logger.trace("INCREMENT-TOKEN");
		int length = 0;
		int start = -1;
		int end = -1;
		int bufferStart = position;
		while (!tokenAtt.isState(TokenInfoAttribute.STATE_INPUT_FINISHED)) {
			if (entry == null) {
				while (true) {
					if (position >= readLength) {
						////////////////////////////////////////////////////////////////////////////////
						// 1. 원본 읽어오기 (버퍼 크기만큼 읽어옴)
						// 읽어온 버퍼가 없거나 모두 처리한 상태라면  리더에서 읽어온다.
						////////////////////////////////////////////////////////////////////////////////
						char[] newBuffer = new char[IO_BUFFER_SIZE];
						baseOffset += readLength;
						readLength = input.read(newBuffer, 0, newBuffer.length);
						logger.trace("READ {}/{}", newBuffer.length, readLength);
						if (readLength > 0) {
							buffer = newBuffer;
							tokenAtt.ref(newBuffer, 0, 0);
							tokenAtt.rmState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED);
							entry = null;
							for (tokenLength = readLength; tokenLength > 0; tokenLength--) {
								if (getType(newBuffer[tokenLength - 1]) != WHITESPACE) {
									break;
								}
							}
						} else {
							readLength = 0;
							logger.trace("LENGTH:{} / {}", length, readLength);
							if (length > 0) {
								break;
							} else {
								finalOffset = correctOffset(baseOffset);
								logger.trace("TOKENIZER STOPPED!!");
								tokenAtt.addState(TokenInfoAttribute.STATE_INPUT_FINISHED);
								return false;
							}
						}
						// if (tokenLength > 0 && tokenLength < FULL_TERM_LENGTH && baseOffset == 0) {
						// 	// FULL-TERM. 색인시에는 추출하지 않음 (필터에서 걸러짐)
						// 	tokenAtt.ref(newBuffer, 0, tokenLength);
						// 	tokenAtt.posTag(null);
						// 	offsetAtt.setOffset(0, tokenLength);
						// 	typeAtt.setType(FULL_STRING);
						// 	return true;
						// }
						position = 0;
						bufferStart = 0;
						chrCurrent = 0;
						typeCurrent = null;
					}
					{
						////////////////////////////////////////////////////////////////////////////////
						// 2. 버퍼 내 단순 토크닝 (공백 / 특수기호에 의한 분해)
						////////////////////////////////////////////////////////////////////////////////
						char chrPrev = chrCurrent;
						String typePrev = typeCurrent;
						chrCurrent = buffer[position];
						typeCurrent = getType(chrCurrent);
						position ++;
						int pass = 1;

						if (typePrev == null) {
							// 이전문자가 없다면 패스
						} else if (position >= tokenLength) {
							// 
							pass = 0;
						} else if (typePrev == WHITESPACE && typeCurrent != WHITESPACE) {
							// 
						} else if (typePrev == WHITESPACE && typeCurrent == WHITESPACE) {
							// 공백뒤 공백. 오프셋 위치 변경
							pass = 2;
						} else if (typePrev != WHITESPACE && typeCurrent == WHITESPACE) {
							// 일반문자 뒤 공백. 끊어줌.
							pass = 0;
						} else if (typePrev == NUMBER && chrCurrent > 128) {
							// 숫자직후 유니코드. 단위명일 확률이 높으므로 연결하여 출력
						} else if (typePrev != SYMBOL && typeCurrent == SYMBOL && (containsChar(AVAIL_SYMBOLS_SPLIT, chrCurrent) || chrCurrent > 128)) {
							pass = 0;
						} else if ((chrPrev < 128 && chrCurrent > 128) || (chrCurrent < 128 && chrPrev > 128)) {
							// 알파벳 과 유니코드 분리
							pass = 0;
						}
						// logger.trace("CH:{}[{}] / {} / {} / {} / {}", chrCurrent, typeCurrent, typePrev, position, length, pass);
						if (pass == 1) {
							// 최초 토크닝 위치 결정
							if (length == 0) {
								assert start == -1;
								start = baseOffset + position - 1;
								end = start;
							}
							end ++;
							length++;
						} else if (pass == 2) {
							// 공백 건너뜀
							bufferStart = position;
							continue;
						} else if (length > 0) {
							// 토크닝 길이 결정
							if (position >= tokenLength) {
								position++;
							}
							if (typePrev != WHITESPACE && typeCurrent == WHITESPACE) {
								position--; 
							} else if (typePrev != WHITESPACE && typeCurrent != WHITESPACE) {
								position--; 
							}
							length = position - bufferStart;
							break;
						}
					}
				}

				assert start != -1;
				int startOffset = correctOffset(start);
				int endOffset = correctOffset(end);

				if (extractor != null) {
					logger.trace("TOKEN:{} / {}~{} / {}", new CharVector(buffer, bufferStart, length), position - bufferStart, length);
					extLength = length;
					if (extLength > extractor.getTabularSize()) {
						extLength = extractor.getTabularSize();
					}
					logger.trace("EXTRACT:{} / {}~{} / {}", new CharVector(buffer, bufferStart, extLength), bufferStart, extLength, baseOffset);
					extractor.setInput(buffer, bufferStart, extLength);
					entry = extractor.extract();
				continue;
				} else {
					// 한글분석기가 없는경우 (한글사전 적재 실패) 분해된 토큰으로만 출력
					tokenAtt.ref(buffer, bufferStart, length);
					offsetAtt.setOffset(startOffset, finalOffset = endOffset);
					logger.trace("TOKEN:{} / {}~{}", tokenAtt.ref(), startOffset, endOffset);
				}
			} else {
				while (entry != null) {
					////////////////////////////////////////////////////////////////////////////////
					// 3. 한글분해
					////////////////////////////////////////////////////////////////////////////////
					// 분해된 한글이 있으므로 속성에 출력해 준다
					if (entry.column() <= 0) {
						entry = entry.next();
						continue;
					}
					int startOffset = correctOffset(baseOffset + entry.offset());
					int endOffset = correctOffset(baseOffset + entry.offset() + entry.column());
					tokenAtt.ref(buffer, entry.offset(), entry.column());
					tokenAtt.posTag(entry.posTag());
					offsetAtt.setOffset(startOffset, endOffset);
					logger.trace("TERM:{} / {}~{} / {}", tokenAtt.ref(), startOffset, endOffset, baseOffset);
					if (exportTerm) {
						termAtt.copyBuffer(buffer, entry.offset(), entry.column());
					}

					int extPosition = entry.offset() + entry.column();
					entry = entry.next();
					if (entry == null) {
						// 분해된 한글이 없다면 다음구간 한글분해 시도
						if (extPosition < position) {
							extLength = position - extPosition;
							if (extLength > extractor.getTabularSize()) {
								extLength = extractor.getTabularSize();
							}
							logger.trace("EXTRACT:{} / {}~{} / {}", new CharVector(buffer, extPosition, extLength), extPosition, extLength, baseOffset);
							extractor.setInput(buffer, extPosition, extLength);
							entry = extractor.extract();
						}
					}
					// 마지막 공백이 있는경우 건너뜀
					for (; extPosition < tokenLength; extPosition++) {
						if (getType(buffer[extPosition]) != WHITESPACE) { break;} 
					}
					if (extPosition == tokenLength) {
						logger.trace("TOKENIZER BUFFER EXHAUSTED!");
						tokenAtt.addState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED);
						continue;
					}
					break;
				}
			}
			return true;
		}
		return false;
	}
  
	@Override
	public final void end() throws IOException {
		logger.trace("TOKENIZER-END {}", finalOffset);
		super.end();
		offsetAtt.setOffset(finalOffset, finalOffset);
	}

	@Override
	public void reset() throws IOException {
		logger.trace("TOKENIZER-RESET");
		super.reset();
		position = 0;
		extLength = 0;
		baseOffset = 0;
		readLength = 0;
		finalOffset = 0;
		tokenLength = 0;
		chrCurrent = 0;
		typeCurrent = null;
	}

	@Override
	public void close() throws IOException {
		logger.trace("TOKENIZER-CLOSE");
		super.close();
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
}
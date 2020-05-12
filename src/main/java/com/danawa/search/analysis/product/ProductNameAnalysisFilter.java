package com.danawa.search.analysis.product;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.danawa.search.analysis.dict.PosTag;
import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.product.KoreanWordExtractor.Entry;
import com.danawa.search.analysis.product.ProductNameParsingRule.RuleEntry;
import com.danawa.util.CharVector;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PosTagAttribute;
import org.apache.lucene.analysis.tokenattributes.StopwordAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.logging.Loggers;

public class ProductNameAnalysisFilter extends TokenFilter {

	private static final Logger logger = Loggers.getLogger(ProductNameAnalysisFilter.class, "");
	
	public static final String DICT_UNIT_SYNONYM = "unit_synonym";
	public static final String DICT_UNIT = "unit";
	public static final String DICT_SPACE = "space";
	public static final String DICT_SYNONYM = "synonym";
	public static final String DICT_STOP = "stop";
	public static final String DICT_USER = "user";
	public static final String DICT_COMPOUND= "compound";
	public static final String DICT_MAKER = "maker";
	public static final String DICT_BRAND = "brand";
	
	private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
	private final TokenInfoAttribute tokenAttribute = addAttribute(TokenInfoAttribute.class);
	private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
	private final PosTagAttribute posTagAttribute = addAttribute(PosTagAttribute.class);
	private final AdditionalTermAttribute additionalTermAttribute = addAttribute(AdditionalTermAttribute.class);
	private final StopwordAttribute stopwordAttribute = addAttribute(StopwordAttribute.class);
	private final SynonymAttribute synonymAttribute = addAttribute(SynonymAttribute.class);
	private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
	private SynonymAttribute tokenSynonymAttribute;
	private AnalyzerOption analyzerOption;
	
	private Entry entry;
	private KoreanWordExtractor extractor;
	private int baseOffset;
	//private int termIncrementCount; // 한글분석시 여러개의 텀으로 나누어지기 때문에, 증가된 텀갯수만큼 뒤의 텀 position에 더해주어야한다.
	
	private SynonymDictionary synonymDictionary;
	private SpaceDictionary spaceDictionary;
	private SetDictionary stopDictionary;
	private ProductNameDictionary dictionary;
	
	public ProductNameAnalysisFilter(TokenStream input) {
		super(input);
	}

	protected ProductNameAnalysisFilter(TokenStream input, KoreanWordExtractor extractor, ProductNameDictionary dictionary, AnalyzerOption analyzerOption) {
		super(input);
		this.extractor = extractor;
		this.dictionary = dictionary;
		this.synonymDictionary = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
		this.spaceDictionary = dictionary.getDictionary(DICT_SPACE, SpaceDictionary.class);
		this.stopDictionary = dictionary.getDictionary(DICT_STOP, SetDictionary.class);
		this.tokenSynonymAttribute = input.getAttribute(SynonymAttribute.class);
		this.analyzerOption = analyzerOption;
		additionalTermAttribute.init(this);
		logger.trace("init");
	}
	
	char[] buffer;
	int offset;
	
	ProductNameParsingRule parsingRule;
	int prevOffset;
	int currentOffset;
	int lastOffset;
	int extractOffset;
	int extractFinal;
	int finalOffset;
	CharVector token;
	boolean hasToken;

	@Override
	@SuppressWarnings("unchecked")
	public final boolean incrementToken() throws IOException {
		while(true) {
			if (parsingRule == null) {
				parsingRule = new ProductNameParsingRule(extractor, dictionary, analyzerOption, offsetAttribute, typeAttribute, synonymAttribute, additionalTermAttribute);
				hasToken = false;
				while(hasToken()) {
					
					//중단신호. 버퍼를 넘게 읽었으므로, 큐를 소모하고 다음차례에 다시 읽도록 한다.
					if(tokenAttribute.ref().offset() == 0 && tokenAttribute.ref().length() == 0) {
						hasToken = true;
						break;
					}
					//여기서 분리어를 체크 한다.
					CharVector ctoken = tokenAttribute.ref();
					
					if(typeAttribute.type() == ProductNameTokenizer.FULL_STRING) {
						parsingRule.addEntry(ctoken, posTagAttribute, typeAttribute.type(), false);
					} else {
						if(spaceDictionary != null && spaceDictionary.containsKey(ctoken)) {
							int offsetSt = offsetAttribute.startOffset();
							int offsetEd = offsetAttribute.endOffset();
							//분리어.
							CharSequence[] splits = spaceDictionary.get(ctoken);
							for (int cinx = 0, pos = ctoken.offset(), offd = 0; cinx < splits.length; cinx++) {
								int termLength = splits[cinx].length();
								if(cinx > 0) {
									//빈공백
									offsetAttribute.setOffset(offsetSt + offd, offsetSt + offd);
									parsingRule.addEntry(new CharVector(ctoken.array(),
											pos, 0), posTagAttribute, ProductNameTokenizer.SYMBOL, false);
								}
								offsetAttribute.setOffset(offsetSt + offd, offsetSt + offd + termLength);
								parsingRule.addEntry(new CharVector(ctoken.array(),
										pos, termLength), posTagAttribute, typeAttribute.type(), false);
								pos += termLength;
								offd += termLength;
							}
							offsetAttribute.setOffset(offsetSt, offsetEd);
						} else {
							parsingRule.addEntry(ctoken, posTagAttribute, typeAttribute.type(), true);
						}
					}
				}
				parsingRule.init();
				token = new CharVector();
			}
			synonymAttribute.setSynonyms(null);
			while (parsingRule!=null && parsingRule.hasNext(token)) {
				//logger.trace("token : {} / type : {}", token, typeAttribute.type());
				tokenAttribute.ref(token.array(), token.offset(), token.length());

				if(analyzerOption.useStopword() && stopDictionary!=null && stopDictionary.set().contains(token)) {
					stopwordAttribute.setStopword(true);
				}else{
					stopwordAttribute.setStopword(false);
				}
				if(analyzerOption.useSynonym()) {
					if(synonymDictionary!=null && synonymDictionary.map().containsKey(token)) {
						List<Object> synonyms = new ArrayList<>();
						CharSequence[] wordSynonym = synonymDictionary.map().get(token);
						if(synonymAttribute.getSynonyms()!=null) {
							synonyms.addAll(synonymAttribute.getSynonyms());
						}
						if(wordSynonym != null) {
							synonyms.addAll(Arrays.asList(wordSynonym));
						}

						//
						// 동의어는 한번 더 분석해 준다.
						// 단 단위명은 더 분석하지 않는다.
						//
						if (typeAttribute.type() != ProductNameTokenizer.UNIT) {
                            List<?> synonymsExt = parsingRule.synonymExtract(synonyms);
                            if(synonymsExt != null) {
                                synonyms = (List<Object>) synonymsExt;
                            }
						}
						synonymAttribute.setSynonyms(synonyms);
					}
				}
				// Lucene 구조와 호환성유지를 위해 CharTermAttirubte 에 복사해 준다
				// array 크기 등을 고려할 필요가 있다.
				CharVector ref = tokenAttribute.ref();
				termAttribute.copyBuffer(ref.array(), ref.offset(), ref.length());
				return true;
			}
			
			if(hasToken) {
				parsingRule = null;
				continue;
			}
		
			return false;
		}
	}

	public boolean hasToken() throws IOException {
		while (true) {
			if (entry == null) {
				//한글분석되지 않은 버퍼는 이어서 수행한다.
				// logger.trace("EXTRACTOFFSET:{} / FINAL:{}", extractOffset, extractFinal);
				if(extractOffset != 0 && extractOffset < extractFinal) {
					int length = extractFinal - extractOffset;
					// logger.trace("SET-EXTRACTOR(1):{}~{}", extractOffset, length);
					int localLength = extractor.getTabularSize();
					if (length < localLength) {
						localLength = length;
					}
					extractor.setInput(buffer, extractOffset, localLength);
					entry = extractor.extract();
					extractOffset += localLength;
				} else {
					if (currentOffset > 0 && finalOffset > currentOffset) {
						synonymAttribute.setSynonyms(null);
						tokenAttribute.offset(currentOffset, finalOffset - currentOffset);
						posTagAttribute.setPosTag(PosTag.UNK);
						if (baseOffset < currentOffset) {
							offsetAttribute.setOffset(currentOffset, finalOffset);
						} else {
							offsetAttribute.setOffset(baseOffset + currentOffset, baseOffset + finalOffset);
						}
						currentOffset = finalOffset;
						return true;
					} else {
	
						boolean hasNext = false;
	
						while ((hasNext = input.incrementToken())) {
							if (tokenAttribute.ref().offset() == 0
									&& tokenAttribute.ref().length() == 0) {
								return true;
							}
							break;
						}
						if (!hasNext) {
							return false;
						}
						// 전체단어, 사용자 단어는 분해하지 않는다.
						if (typeAttribute.type() == ProductNameTokenizer.FULL_STRING) {
							List<?> synonyms = tokenSynonymAttribute.getSynonyms();
							synonymAttribute.setSynonyms(synonyms);
							return true;
						} else {
							CharVector charsRef = tokenAttribute.ref();
							buffer = new char[charsRef.length()];
							offset = charsRef.offset();
							System.arraycopy(charsRef.array(), charsRef.offset(), buffer, 0, charsRef.length());
							int length = charsRef.length();
							
							int localLength = extractor.getTabularSize();
							if (length < localLength) {
								localLength = length;
							}
							extractor.setInput(buffer, localLength);
							// logger.trace("SET-EXTRACTOR(2):{}~{} / {}", 0, length, localLength);
							entry = extractor.extract();
							baseOffset = offsetAttribute.startOffset();
							extractOffset = localLength;
							extractFinal = length;
							finalOffset = offset + length;
							currentOffset = lastOffset = 0;
						}
					}
				}
			}

			while (entry != null && (entry.tagProb().posTag() == PosTag.J)) {
				entry = entry.next();
			}
			logger.trace("entry:{} / {} / {}", entry, lastOffset, currentOffset);

			if (entry != null) {
				synonymAttribute.setSynonyms(null);
				tokenAttribute.offset(offset + entry.offset(), entry.column());
				posTagAttribute.setPosTag(entry.posTag());
				offsetAttribute.setOffset(baseOffset + entry.offset(),
					baseOffset + entry.offset() + entry.column());
				int currentRow = entry.row();
				lastOffset = currentOffset;
				currentOffset = offset + entry.offset() + entry.column();
				entry = entry.next();

				// logger.trace("entry:{} / {} / {}", entry, lastOffset, currentOffset);
				if (lastOffset > currentOffset) {
					entry = null;
				}
				if (entry != null && entry.row() < currentRow) {
					entry = null;
				}
				return true;
			} else {
				posTagAttribute.setPosTag(PosTag.UNK);
				continue;
			}
		}
	}

	private List<RuleEntry> termList;

	public final boolean incrementTokenNew() throws IOException {
// 임시코드
if (termList == null) { termList = new ArrayList<>(); }
		boolean ret = false;
		// FIXME : 큐 마지막에 ASCII 텀이 남아 있다면 모델명규칙 등을 위해 남겨 두어야 함.
		// INFO : 텀 오프셋 불일치를 막기 위해 절대값을 사용 (버퍼 상대값은 되도록 사용하지 않음)
		while (true) {
// 임시코드. 테스트시 무한루프에 의한 프리징 방지
// try { Thread.sleep(300); } catch (Exception ignore) { }
			if (termList.size() == 0) {
				if (tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_FINISHED)) {
					ret = false;
					break;
				}
				while (input.incrementToken()) {
					CharVector ref = tokenAttribute.ref();
					termList.add(new RuleEntry(ref.array(), ref.offset(), ref.length(), offsetAttribute.startOffset(), offsetAttribute.endOffset(), null));
					if (tokenAttribute.isState(TokenInfoAttribute.STATE_BUFFER_EXHAUSTED)) {
						break;
					}
				}
			} else {
				RuleEntry entry = termList.remove(0);
				termAttribute.copyBuffer(entry.buf, entry.start, entry.length);
				offsetAttribute.setOffset(entry.startOffset, entry.endOffset);
				ret = true;
				break;
			}
		}
		return ret;
	}
	
	public void reset() throws IOException {
		super.reset();
		additionalTermAttribute.init(this);
		entry = null;
		offset = 0;
		baseOffset = 0;
		prevOffset = 0;
		currentOffset = 0;
		lastOffset = 0;
		parsingRule = null;
		token = null;
		hasToken = false;
		extractOffset = 0;
		extractFinal = 0;
		finalOffset = 0;
	}
}

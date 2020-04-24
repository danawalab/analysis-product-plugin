package com.danawa.search.analysis.product;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.danawa.search.analysis.dict.CommonDictionary;
import com.danawa.search.analysis.dict.PosTag;
import com.danawa.search.analysis.dict.PreResult;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.dict.PosTagProbEntry.TagProb;
import com.danawa.search.analysis.product.KoreanWordExtractor.Entry;
import com.danawa.util.CharVector;

import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.PosTagAttribute;
import org.apache.lucene.analysis.tokenattributes.StopwordAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ProductNameAnalysisFilter extends TokenFilter {

	private static final Logger logger = LoggerFactory.getLogger(ProductNameAnalysisFilter.class);
	
	public static final String DICT_UNIT_SYNONYM = "unit_synonym";
	public static final String DICT_UNIT = "unit";
	public static final String DICT_SPACE = "space";
	public static final String DICT_SYNONYM = "synonym";
	public static final String DICT_STOP = "stop";
	public static final String DICT_USER = "user";
	public static final String DICT_COMPOUND= "compound";
	public static final String DICT_MAKER = "maker";
	public static final String DICT_BRAND = "brand";
	
	//private final CharTermAttribute charAttribute = addAttribute(CharTermAttribute.class);
	private final TokenInfoAttribute termAttribute = addAttribute(TokenInfoAttribute.class);
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
	@SuppressWarnings("rawtypes")
	private CommonDictionary dictionary;
	
	// protected ProductNameAnalysisFilter(TokenStream input) {
	// 	super(input);
	// 	// TODO Auto-generated constructor stub
	// }

	protected ProductNameAnalysisFilter(TokenStream input, KoreanWordExtractor extractor, CommonDictionary<TagProb, PreResult<CharSequence>> dictionary) {
		super(input);
		this.extractor = extractor;
		this.dictionary = dictionary;
		this.synonymDictionary = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
		this.spaceDictionary = dictionary.getDictionary(DICT_SPACE, SpaceDictionary.class);
		this.stopDictionary = dictionary.getDictionary(DICT_STOP, SetDictionary.class);
		this.tokenSynonymAttribute = input.getAttribute(SynonymAttribute.class);
		this.analyzerOption = new AnalyzerOption();
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
	boolean extractRemnant;
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
					if(termAttribute.charVector().offset() == 0 && termAttribute.charVector().length() == 0) {
						hasToken = true;
						break;
					}
					
					//logger.trace("term:{}", termAttribute);
					//여기서 분리어를 체크 한다.
					CharVector ctoken = termAttribute.charVector();
					
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
				termAttribute.setCharVector(token.array(), token.offset(), token.length());

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
				if(extractOffset != 0 && extractOffset < extractFinal) {
					int length = extractFinal - extractOffset;
					int localLength = extractor.setInput(buffer, extractOffset, length);
					extractRemnant = extractor.hasRemnant();
					entry = extractor.extract();
					extractOffset += localLength;
				} else {
					if (currentOffset > 0 && finalOffset > currentOffset) {
						synonymAttribute.setSynonyms(null);
						termAttribute.setOffset(currentOffset, finalOffset - currentOffset);
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
							if (termAttribute.charVector().offset() == 0
									&& termAttribute.charVector().length() == 0) {
								return true;
							}
							break;
						}
						if (!hasNext) {
							return false;
						}
	
						// logger.trace("tokenized : {} / type : {}", termAttribute, typeAttribute.type());
	
						// 전체단어, 사용자 단어는 분해하지 않는다.
						if (typeAttribute.type() == ProductNameTokenizer.FULL_STRING) {
							List<?> synonyms = tokenSynonymAttribute.getSynonyms();
							synonymAttribute.setSynonyms(synonyms);
							return true;
						} else {
							CharVector charsRef = termAttribute.charVector();
							buffer = new char[charsRef.length()];
							offset = charsRef.offset();
							System.arraycopy(charsRef.array(), charsRef.offset(), buffer, 0, charsRef.length());
							int length = charsRef.length();
							
							int localLength = extractor.setInput(buffer, length);
							extractRemnant = extractor.hasRemnant();
							entry = extractor.extract();
							baseOffset = offsetAttribute.startOffset();
							//termIncrementCount = -1;
							extractOffset = localLength;
							extractFinal = length;
							finalOffset = offset + length;
						}
					}
				}
			}

			while (entry != null && (entry.tagProb().posTag() == PosTag.J)) {
				entry = entry.next();
			}
			// logger.trace("entry:{}", entry);

			if (entry != null) {
				synonymAttribute.setSynonyms(null);
				termAttribute
						.setOffset(offset + entry.offset(), entry.column());
				posTagAttribute.setPosTag(entry.posTag());
				offsetAttribute.setOffset(baseOffset + entry.offset(),
						baseOffset + entry.offset() + entry.column());
				int currentRow = entry.row();
				lastOffset = currentOffset;
				currentOffset = offset + entry.offset() + entry.column();
				entry = entry.next();

				if (lastOffset > currentOffset) {
					entry = null;
				}
				if (entry != null && entry.row() < currentRow) {
					entry = null;
				}
				
				if(entry == null && extractRemnant) {
					//나머지는 그냥 뽑지 않고 다시 extractor 로 보낸다. 
					//비록 oversize 에 대한 전체적인 한글분석이 이루어지지 않더라도.
					//끊기는 단어는 없어질것으로 생각됨.
					if(termAttribute.charVector().length() < extractor.getTabularSize()) {
						extractOffset -= termAttribute.charVector().length();
						extractRemnant = false;
						continue;
					}
				}
				
				//termIncrementCount++;
				// logger.trace("term:{} / next entry:{}", termAttribute,
				// entry);
				return true;
			} else {
				// logger.trace("not extracted.");
				posTagAttribute.setPosTag(PosTag.UNK);
				//termIncrementCount = -1;
				continue;
			}
		}
	}
	
	@Override
	public void reset() throws IOException {
		super.reset();
		//termIncrementCount = 0;
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
		extractRemnant = false;
	}
}
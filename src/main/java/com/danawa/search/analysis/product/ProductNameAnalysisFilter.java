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
import com.danawa.search.analysis.product.ProductNameParsingRule.RuleEntry;
import com.danawa.util.CharVector;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.tokenattributes.AdditionalTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
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
	private final AdditionalTermAttribute additionalTermAttribute = addAttribute(AdditionalTermAttribute.class);
	private final StopwordAttribute stopwordAttribute = addAttribute(StopwordAttribute.class);
	private final SynonymAttribute synonymAttribute = addAttribute(SynonymAttribute.class);
	private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
	private SynonymAttribute tokenSynonymAttribute;
	private AnalyzerOption analyzerOption;
	
	private KoreanWordExtractor extractor;
	
	private SynonymDictionary synonymDictionary;
	private SpaceDictionary spaceDictionary;
	private SetDictionary stopDictionary;
	private ProductNameDictionary dictionary;
	
	public ProductNameAnalysisFilter(TokenStream input) {
		super(input);
	}

	public ProductNameAnalysisFilter(TokenStream input, KoreanWordExtractor extractor, ProductNameDictionary dictionary, AnalyzerOption analyzerOption) {
		super(input);
		this.extractor = extractor;
		if (dictionary != null) {
			this.dictionary = dictionary;
			this.synonymDictionary = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
			this.spaceDictionary = dictionary.getDictionary(DICT_SPACE, SpaceDictionary.class);
			this.stopDictionary = dictionary.getDictionary(DICT_STOP, SetDictionary.class);
		}
		this.tokenSynonymAttribute = input.getAttribute(SynonymAttribute.class);
		this.analyzerOption = analyzerOption;
		additionalTermAttribute.init(this);
		termList = new ArrayList<>();
		super.clearAttributes();
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

	private List<RuleEntry> termList;

	public void testInit() {
		termList = new ArrayList<>();
		if (dictionary != null) {
			this.dictionary = tokenAttribute.dictionary();
			this.synonymDictionary = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
			this.spaceDictionary = dictionary.getDictionary(DICT_SPACE, SpaceDictionary.class);
			this.stopDictionary = dictionary.getDictionary(DICT_STOP, SetDictionary.class);
		}
		this.tokenSynonymAttribute = input.getAttribute(SynonymAttribute.class);
		this.analyzerOption = new AnalyzerOption();
		this.extractor = new KoreanWordExtractor(dictionary);

		analyzerOption.useSynonym(true);
		analyzerOption.useStopword(true);
		// analyzerOption.setForDocument();
	}

	public final boolean incrementToken() throws IOException {
		boolean ret = false;
		// FIXME : 큐 마지막에 ASCII 텀이 남아 있다면 모델명규칙 등을 위해 남겨 두어야 함.
		// INFO : 텀 오프셋 불일치를 막기 위해 절대값을 사용 (버퍼 상대값은 되도록 사용하지 않음)
		if (parsingRule == null) {
			parsingRule = new ProductNameParsingRule(extractor, dictionary, analyzerOption, 
				offsetAttribute, typeAttribute, synonymAttribute, additionalTermAttribute);
		}
		synonymAttribute.setSynonyms(null);
		while (true) {
// 임시코드. 테스트시 무한루프에 의한 프리징 방지
// try { Thread.sleep(300); } catch (Exception ignore) { }
			if (termList.size() == 0) {
				if (tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_FINISHED)) {
					ret = false;
					break;
				}
				while (input.incrementToken()) {
					// 한번 읽어온 버퍼를 다 소진할때까지 큐에 넣는다.
					CharVector ref = tokenAttribute.ref();
					String type = typeAttribute.type();
					PosTag posTag = tokenAttribute.posTag();
					if (type == ProductNameTokenizer.FULL_STRING) {
						// List<?> synonyms = tokenSynonymAttribute.getSynonyms();
						// synonymAttribute.setSynonyms(synonyms);
						// NOP
					} else if(posTag == PosTag.N) {
						type = ProductNameTokenizer.HANGUL;
					} else if(posTag == PosTag.DIGIT) {
						type = ProductNameTokenizer.NUMBER;
					} else if(posTag == PosTag.ALPHA) {
						type = ProductNameTokenizer.ALPHA;
					} else if(posTag == PosTag.SYMBOL) {
						type = ProductNameTokenizer.SYMBOL;
					} else if(posTag == null || posTag == PosTag.UNK) {
						type = ProductNameTokenizer.UNCATEGORIZED;
					}
					if(spaceDictionary != null && spaceDictionary.containsKey(ref)) {
						int offsetSt = offsetAttribute.startOffset();
						CharSequence[] splits = spaceDictionary.get(ref);
						logger.trace("SPLIT:{}{}", "", splits);
						for (int sinx = 0, position = 0; sinx < splits.length; sinx++) {
							int length = splits[sinx].length();
							if(sinx > 0) {
								// 강제 분리를 위한 빈공백
								termList.add(new RuleEntry(ref.array(), position, 0, 
									offsetSt + position, offsetSt + position, ProductNameTokenizer.SYMBOL));
							}
							termList.add(new RuleEntry(ref.array(), ref.offset() + position, length, 
								offsetSt + position, offsetSt + position + length, type));
							position += length;
						}
					} else {
						termList.add(new RuleEntry(ref.array(), ref.offset(), ref.length(), 
							offsetAttribute.startOffset(), offsetAttribute.endOffset(), type));
					}
					if (tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED)) {
						break;
					}
				} // LOOP (incrementToken())
				// RULE PROCESS
				if (termList.size() > 0) {
					parsingRule.processRule(termList, true);
				}

				logger.trace("ENTRY QUEUE-SIZE:{}", termList.size());
			} else {
				// 엔트리를 출력할때 오프셋 순서대로 정렬하여 출력한다.
				RuleEntry entry = null;
				while ((entry = termList.get(0)) == null) { termList.remove(0); }

				List<RuleEntry> subEntryList = entry.subEntry;
				logger.trace("SUB:{}", subEntryList);

				// 분기. 색인시에는 모델명을 일반텀으로 추출, 질의시에는 추가텀으로 추출
				// 색인시에는 오프셋이 앞으로 갈수 없으므로 일반텀으로 추출한다

				if (analyzerOption.isForDocument()) {
					if (entry.buf != null) {
						token = applyEntry(entry);
						if (analyzerOption.useSynonym()) {
							List<Object> synonyms = new ArrayList<>();
							if (entry.synonym != null && analyzerOption.useSynonym()) {
								synonyms.addAll(Arrays.asList(entry.synonym));
							}
							if (synonymDictionary != null && synonymDictionary.map().containsKey(token)) {
								CharSequence[] wordSynonym = synonymDictionary.map().get(token);
								logger.trace("SYNONYM-FOUND:{}{}", "", wordSynonym);
								if (synonymAttribute.getSynonyms() != null) {
									synonyms.addAll(synonymAttribute.getSynonyms());
								}
								if (wordSynonym != null) {
									synonyms.addAll(Arrays.asList(wordSynonym));
								}
								// 동의어는 한번 더 분석해 준다.
								// 단 단위명은 더 분석하지 않는다.
								if (typeAttribute.type() != ProductNameTokenizer.UNIT) {
									List<?> synonymsExt = parsingRule.synonymExtract(synonyms);
									if (synonymsExt != null) {
										synonyms = (List<Object>) synonymsExt;
									}
								}
							}
							if (synonyms.size() > 0) {
								synonymAttribute.setSynonyms(synonyms);
							}
						}
						if (subEntryList == null) {
							termList.remove(0);
						} else {
							// 서브엔트리가 존재하는 경우 출력한 버퍼를 null 처리 하고 서브엔트리를 처리하도록 한다.
							entry.buf = null;
						}
						ret = true;
						break;
					} else if (subEntryList.size() > 0) {
						RuleEntry subEntry = subEntryList.get(0);
						token = applyEntry(subEntry);
						if (subEntry.synonym != null && analyzerOption.useSynonym()) {
							synonymAttribute.setSynonyms(Arrays.asList(subEntry.synonym));
						}
						subEntryList.remove(0);
						ret = true;
						break;
					} else if (subEntryList.size() == 0) {
						termList.remove(0);
					}
				} else {
					// if (subEntryList != null) {
					// 	while(subEntryList.size() > 0) {
					// 		RuleEntry nextEntry = subEntryList.get(0);
					// 		logger.trace("ADD-ENTRY:{}", nextEntry);
					// 		// if(nextEntry.type != ProductNameTokenizer.MODEL_NAME) {
					// 		// 	break;
					// 		// }
					// 		if(nextEntry.type == ProductNameTokenizer.MODEL_NAME) {
					// 			subEntryList.remove(0);
					// 			continue;
					// 		}
					// 		//모델명으로 추가된 단어들 (첫머리를 뗀 나머지 모델명 등)
					// 		if(additionalTermAttribute != null) {
					// 			additionalTermAttribute.addAdditionalTerm(nextEntry.makeTerm(null).toString(), 
					// 				nextEntry.type, null, 0, nextEntry.startOffset, nextEntry.endOffset);
					// 		}
					// 		subEntryList.remove(0);
					// 	}
					// }
				}
			}
		} // LOOP
		return ret;
	}

	private CharVector applyEntry(RuleEntry entry) {
		CharVector ret;

		termAttribute.copyBuffer(entry.buf, entry.start, entry.length);
		offsetAttribute.setOffset(entry.startOffset, entry.endOffset);
		typeAttribute.setType(entry.type);
		token = entry.makeTerm(null);
		if (analyzerOption.useStopword() && stopDictionary != null && stopDictionary.set().contains(token)) {
			tokenAttribute.addState(TokenInfoAttribute.STATE_TERM_STOP);
		} else {
			tokenAttribute.rmState(TokenInfoAttribute.STATE_TERM_STOP);
		}
		ret = entry.makeTerm(null);
		return ret;

		// token.init(entry.buf, entry.start, entry.length);
		// token.ignoreCase();
		// typeAttribute.setType(entry.type);
		// if (entry.synonym != null) {
		// 	if (analyzerOption.useSynonym()) {
		// 		synonymAttribute.setSynonyms(Arrays.asList(entry.synonym));
		// 	} else {
		// 		synonymAttribute.setSynonyms(null);
		// 	}
		// }
		// // 복합명사 분해는 색인시에만 적용한다. 쿼리시에는 적용하지 않음.
		// if (compoundDictionary != null && compoundDictionary.containsKey(token)) {
		// 	typeAttribute.setType(ProductNameTokenizer.COMPOUND);
		// 	// 복합명사 분리는 색인시에만 수행. 쿼리시에는 분해하지 않고, 표시만.
		// 	// 2017-11-10 swsong 복합명사 분리를 검색, 색인 모두 수행한다.
		// 	// 공백있는 단어가 검색이 안되는 문제가 있어서 추가텀을 T or (A1 and A2 and A3 ..) 하여 검색하게 된다
		// 	if (additionalTermAttribute != null) {
		// 		CharSequence[] compounds = compoundDictionary.get(token);
		// 		for (CharSequence word : compounds) {
		// 			additionalTermAttribute.addAdditionalTerm(word.toString(), ProductNameTokenizer.COMPOUND, null, 0, entry.start, entry.start + entry.length);
		// 		}
		// 	}
		// }
		// logger.trace("token:{} / start:{} / end:{} / lstart:{} / length:{}", token,
		// 	entry.startOffset, entry.endOffset, entry.start, entry.length);
		
		// if (setOffset) {
		// 	offsetAttribute.setOffset(entry.startOffset, entry.endOffset);
		// }
	}

	public void reset() throws IOException {
		super.reset();
		additionalTermAttribute.init(this);
		offset = 0;
		prevOffset = 0;
		currentOffset = 0;
		lastOffset = 0;
		parsingRule = null;
		token = null;
		hasToken = false;
		extractOffset = 0;
		extractFinal = 0;
		finalOffset = 0;
		termList = new ArrayList<>();
		this.clearAttributes();
	}
}
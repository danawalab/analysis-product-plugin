package com.danawa.search.analysis.product;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.search.analysis.dict.SetDictionary;
import com.danawa.search.analysis.dict.SpaceDictionary;
import com.danawa.search.analysis.dict.SynonymDictionary;
import com.danawa.search.analysis.korean.KoreanWordExtractor;
import com.danawa.search.analysis.korean.PosTagProbEntry.PosTag;
import com.danawa.search.analysis.product.ProductNameParsingRule.RuleEntry;
import com.danawa.util.CharVector;

import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.tokenattributes.ExtraTermAttribute;
import org.apache.lucene.analysis.tokenattributes.CharTermAttribute;
import org.apache.lucene.analysis.tokenattributes.OffsetAttribute;
import org.apache.lucene.analysis.tokenattributes.SynonymAttribute;
import org.apache.lucene.analysis.tokenattributes.TokenInfoAttribute;
import org.apache.lucene.analysis.tokenattributes.TypeAttribute;
import org.apache.lucene.analysis.TokenFilter;
import org.apache.lucene.analysis.TokenStream;
import org.elasticsearch.common.logging.Loggers;

import static com.danawa.search.analysis.product.ProductNameTokenizer.*;

public class ProductNameAnalysisFilter extends TokenFilter {

	private static final Logger logger = Loggers.getLogger(ProductNameAnalysisFilter.class, "");
	
	private final CharTermAttribute termAttribute = addAttribute(CharTermAttribute.class);
	private final TokenInfoAttribute tokenAttribute = addAttribute(TokenInfoAttribute.class);
	private final OffsetAttribute offsetAttribute = addAttribute(OffsetAttribute.class);
	private final ExtraTermAttribute extraTermAttribute = addAttribute(ExtraTermAttribute.class);
	private final SynonymAttribute synonymAttribute = addAttribute(SynonymAttribute.class);
	private final TypeAttribute typeAttribute = addAttribute(TypeAttribute.class);
	private AnalyzerOption option;
	
	private KoreanWordExtractor extractor;
	
	private SynonymDictionary synonymDictionary;
	private SpaceDictionary spaceDictionary;
	private SetDictionary stopDictionary;
	private ProductNameDictionary dictionary;
	
	private ProductNameParsingRule parsingRule;
	private CharVector token;
	private List<RuleEntry> termList;
	
	public ProductNameAnalysisFilter(TokenStream input) {
		super(input);
	}

	public ProductNameAnalysisFilter(TokenStream input, ProductNameDictionary dictionary, AnalyzerOption option) {
		super(input);
		if (dictionary != null) {
			this.dictionary = dictionary;
			this.synonymDictionary = dictionary.getDictionary(DICT_SYNONYM, SynonymDictionary.class);
			this.spaceDictionary = dictionary.getDictionary(DICT_SPACE, SpaceDictionary.class);
			this.stopDictionary = dictionary.getDictionary(DICT_STOP, SetDictionary.class);
		}
		this.extractor = new KoreanWordExtractor(dictionary);
		this.option = option;
		extraTermAttribute.init(this);
		termList = new ArrayList<>();
		super.clearAttributes();
		logger.trace("init");
	}

	public final boolean incrementToken() throws IOException {
		boolean ret = false;
		// FIXME : 큐 마지막에 ASCII 텀이 남아 있다면 모델명규칙 등을 위해 남겨 두어야 함.
		// INFO : 텀 오프셋 불일치를 막기 위해 절대값을 사용 (버퍼 상대값은 되도록 사용하지 않음)
		if (parsingRule == null) {
			parsingRule = new ProductNameParsingRule(extractor, dictionary, option);
		}
		synonymAttribute.setSynonyms(null);
		extraTermAttribute.init(this);

		while (true) {
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

					// 색인시에는 전체텀을 추출하지 않는다.
					if (!option.useForQuery() && FULL_STRING.equals(type)) {
						continue;
					}
					ProductNameParsingRule.addEntry(termList, ref, type, posTag, 
						offsetAttribute.startOffset(), offsetAttribute.endOffset(), spaceDictionary);
					if (tokenAttribute.isState(TokenInfoAttribute.STATE_INPUT_BUFFER_EXHAUSTED)) {
						break;
					}
				} // LOOP (incrementToken())
				// RULE PROCESS
				if (termList.size() > 0) {
					parsingRule.init(termList);
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

				if (option.useForQuery()) {
					// 질의용
					token = applyEntry(entry);
					applySynonym(token, entry);
					if (entry.subEntry != null && entry.subEntry.size() > 0) {
						for (RuleEntry subEntry : entry.subEntry) {
							List<CharSequence> synonyms = null;
							CharVector cv = subEntry.makeTerm(null);
							if (synonymDictionary.containsKey(cv)) {
								synonyms = Arrays.asList(synonymDictionary.get(cv));
								logger.trace("token:{} / synonym:{}", cv, synonyms);
							}
							extraTermAttribute.addExtraTerm(String.valueOf(cv), subEntry.type, synonyms);
						}
					}
					termList.remove(0);
					ret = true;
					break;
				} else {
					// 색인용
					if (entry.buf != null) {
						token = applyEntry(entry);
						// applySynonym(token, entry);
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
						// if (subEntry.synonym != null && option.useSynonym()) {
						// 	synonymAttribute.setSynonyms(Arrays.asList(subEntry.synonym));
						// }
						subEntryList.remove(0);
						ret = true;
						break;
					} else if (subEntryList.size() == 0) {
						termList.remove(0);
					}
				}
			}
		} // LOOP
		if (logger.isTraceEnabled()) {
			if (ret) {
				logger.trace("TERM:{} / {}~{} / {}", termAttribute, offsetAttribute.startOffset(), offsetAttribute.endOffset());
			} else {
				logger.trace("FILTER STOPPED!!");
			}
		}
		return ret;
	}

	private void applySynonym(CharVector token, RuleEntry entry) {
		if (option.useSynonym() && option.useForQuery()) {
			List<CharSequence> synonyms = new ArrayList<>();
			if (synonymDictionary != null && synonymDictionary.containsKey(token)) {
				CharSequence[] wordSynonym = synonymDictionary.get(token);
				logger.trace("SYNONYM-FOUND:{}{}", "", wordSynonym);
				if (wordSynonym != null) {
					synonyms.addAll(Arrays.asList(wordSynonym));
				}
				// 동의어는 한번 더 분석해 준다.
				// 단 단위명은 더 분석하지 않는다.
				if (typeAttribute.type() != UNIT) {
					List<CharSequence> synonymsExt = parsingRule.synonymExtract(synonyms, entry);
					if (synonymsExt != null) {
						synonyms = synonymsExt;
					}
				}
			}
			// 본래 entry 에 있던 동의어는 이미 분석된 동의어 이므로 따로 처리할 필요가 없다.
			if (entry.synonym != null && option.useSynonym()) {
				synonyms.addAll(Arrays.asList(entry.synonym));
			}
			if (synonyms.size() > 0) {
				logger.trace("SET-SYNONYM:{}", synonyms);
				synonymAttribute.setSynonyms(synonyms);
			}
		}
	}

	private CharVector applyEntry(RuleEntry entry) {
		CharVector ret;

		termAttribute.copyBuffer(entry.buf, entry.start, entry.length);
		offsetAttribute.setOffset(entry.startOffset, entry.endOffset);
		typeAttribute.setType(entry.type);
		token = entry.makeTerm(null);
		if (option.useStopword() && stopDictionary != null && stopDictionary.set().contains(token)) {
			tokenAttribute.addState(TokenInfoAttribute.STATE_TERM_STOP);
		} else {
			tokenAttribute.rmState(TokenInfoAttribute.STATE_TERM_STOP);
		}
		ret = entry.makeTerm(null);
		return ret;
	}

	public void reset() throws IOException {
		super.reset();
		extraTermAttribute.init(this);
		parsingRule = null;
		token = null;
		termList = new ArrayList<>();
		this.clearAttributes();
	}
}
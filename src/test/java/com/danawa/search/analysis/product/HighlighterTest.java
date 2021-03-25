package com.danawa.search.analysis.product;

import com.danawa.search.analysis.dict.ProductNameDictionary;
import com.danawa.util.TestUtil;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.highlight.InvalidTokenOffsetsException;
import org.apache.lucene.search.highlight.QueryScorer;
import org.elasticsearch.common.logging.Loggers;
import org.elasticsearch.search.fetch.subphase.highlight.CustomQueryScorer;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class HighlighterTest {
    private static Logger logger = Loggers.getLogger(HighlighterTest.class, "");

    @Before
    public void init() {
        TestUtil.setLogLevel(System.getProperty("LOG_LEVEL"),
                ProductNameTokenizer.class,
                ProductNameParsingRule.class,
                ProductNameAnalysisFilter.class);
    }
    @Test
    public void testPlain() throws IOException, InvalidTokenOffsetsException {
        Query query = new TermQuery(new Term("field", "CRP"));
        QueryScorer queryScorer = new CustomQueryScorer(query);
        org.apache.lucene.search.highlight.Highlighter highlighter = new org.apache.lucene.search.highlight.Highlighter(queryScorer);

		ProductNameDictionary dictionary = TestUtil.loadDictionary();
		if (dictionary == null) {
			dictionary = TestUtil.loadTestDictionary();
		}
        Analyzer analyzer = new ProductNameAnalyzer(dictionary);

        String text = "적용모델: CRP-JHR0660FD/FBM, CRP-JHTS0660FS, CRP-JHTR0610FD, CRP-JHT0610FS, CRP-JHI0630FG, CRP-JHR0610FB, CRP-JHR0620FD, CRP-FHR0610FG/FD, CRP-FHTS0610FD, CRP-FHTR0610FS, CRP-BHSL0610FB 등(상세정보참고)";
        String[] frags = highlighter.getBestFragments(analyzer, "field", text, 3);
        logger.debug("{}{}", "", frags);
    }
}

package com.scaleunlimited.wikiwords.flow;

import static org.junit.Assert.*;

import org.junit.Test;

import cascading.flow.Flow;
import cascading.tap.SinkMode;
import cascading.tap.Tap;
import cascading.tuple.TupleEntry;
import cascading.tuple.TupleEntryIterableChainIterator;
import cascading.tuple.TupleEntryIterator;

import com.scaleunlimited.cascading.BasePath;
import com.scaleunlimited.cascading.BasePlatform;
import com.scaleunlimited.cascading.FlowResult;
import com.scaleunlimited.cascading.FlowRunner;
import com.scaleunlimited.wikiwords.WikiwordsCounters;
import com.scaleunlimited.wikiwords.WorkingConfig;
import com.scaleunlimited.wikiwords.tools.AnalyzeTermsTool.AnalyzeTermsOptions;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool.GenerateTermsOptions;

public class AnalyzeTermsFlowTest {

    @Test
    public void test() throws Exception {
        GenerateTermsOptions termsOptions = GenerateTermsFlowTest.generateTerms("build/test/AnalyzeTermsFlowTest/test");
        
        AnalyzeTermsOptions options = new AnalyzeTermsOptions(termsOptions);
        options.setDebug(true);
        Flow flow = AnalyzeTermsFlow.createFlow(options);
        
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(AnalyzeTermsFlow.class, fr.getCounters());
        
        BasePlatform platform = termsOptions.getPlatform(AnalyzeTermsFlowTest.class);
        BasePath termScoresPath = options.getWorkingSubdirPath(WorkingConfig.TERM_SCORES_SUBDIR_NAME);
        Tap termScoresTap = platform.makeTap(platform.makeTextScheme(), termScoresPath, SinkMode.KEEP);
        TupleEntryIterator iter = termScoresTap.openForRead(platform.makeFlowProcess());
        boolean foundZeno = false;
        while (iter.hasNext()) {
            TupleEntry te = iter.next();
            String[] pieces = te.getString("line").split("\t", 4);
            assertEquals(4, pieces.length);
            // We should have one entry that looks like.
            // zeno    Zeno_of_Citium  3.1653316  1
            if (pieces[0].equals("zeno")) {
                foundZeno = true;
                assertEquals(1, Integer.parseInt(pieces[3]));
            }
        }
        
        iter.close();
        assertTrue(foundZeno);
        
        BasePath termCategoriesPath = options.getWorkingSubdirPath(WorkingConfig.TERM_CATEGORIES_SUBDIR_NAME);
        Tap termCategoriesTap = platform.makeTap(platform.makeTextScheme(), termCategoriesPath, SinkMode.KEEP);
        iter = termCategoriesTap.openForRead(platform.makeFlowProcess());
        while (iter.hasNext()) {
            TupleEntry te = iter.next();
            // term <tab> category <tab> score
            String[] pieces = te.getString("line").split("\t", 3);
            assertEquals(3, pieces.length);
        }
        
        iter.close();
    }

    @Test
    public void testTermAndScoreLimits() throws Exception {
        GenerateTermsOptions termsOptions = GenerateTermsFlowTest.generateTerms("build/test/AnalyzeTermsFlowTest/testTermAndScoreLimits");
        
        AnalyzeTermsOptions options = new AnalyzeTermsOptions(termsOptions);
        // Use a min term count of 3, and a min score of 2.0
        options.setMinArticleRefs(3);
        options.setMinScore(2.0);
        Flow flow = AnalyzeTermsFlow.createFlow(options);
        
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(AnalyzeTermsFlow.class, fr.getCounters());
        
        BasePlatform platform = termsOptions.getPlatform(AnalyzeTermsFlowTest.class);
        BasePath termScoresPath = options.getWorkingSubdirPath(WorkingConfig.TERM_SCORES_SUBDIR_NAME);
        Tap termScoresTap = platform.makeTap(platform.makeTextScheme(), termScoresPath, SinkMode.KEEP);
        TupleEntryIterator iter = termScoresTap.openForRead(platform.makeFlowProcess());
        
        boolean foundZhuangzi = false;
        while (iter.hasNext()) {
            TupleEntry te = iter.next();
            String[] pieces = te.getString("line").split("\t", 4);
            assertEquals(4, pieces.length);
            // We should have one entry that looks like.
            // zhuangzi Zhuangzi    2.4678922   3
            if (pieces[0].equals("zhuangzi")) {
                foundZhuangzi = true;
                assertEquals(3, Integer.parseInt(pieces[3]));
            }
            
            // Should have nothing with score < 2.0
            // french  Pierre-Joseph_Proudhon  1.8447921       3
            assertFalse(pieces[0].equals("french"));
        }
        
        iter.close();
        assertTrue(foundZhuangzi);
    }

}

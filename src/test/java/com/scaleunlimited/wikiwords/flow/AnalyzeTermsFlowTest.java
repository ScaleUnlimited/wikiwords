package com.scaleunlimited.wikiwords.flow;

import static org.junit.Assert.*;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

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
import com.scaleunlimited.wikiwords.Category;
import com.scaleunlimited.wikiwords.CategoryGraph;
import com.scaleunlimited.wikiwords.WikiwordsCounters;
import com.scaleunlimited.wikiwords.WorkingConfig;
import com.scaleunlimited.wikiwords.tools.AnalyzeTermsTool.AnalyzeTermsOptions;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool.GenerateTermsOptions;

public class AnalyzeTermsFlowTest {

    @Test
    public void test() throws Exception {
        GenerateTermsOptions termsOptions = GenerateTermsFlowTest.generateTerms("build/test/AnalyzeTermsFlowTest/test");
        
        AnalyzeTermsOptions options = new AnalyzeTermsOptions(termsOptions);
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
        
        // Verify we have category data that seems (in general) to be correct.
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
    public void testCategoryExpansion() throws Exception {
        GenerateTermsOptions termsOptions = GenerateTermsFlowTest.generateTerms("build/test/AnalyzeTermsFlowTest/testCategoryExpansion");
        BasePlatform platform = termsOptions.getPlatform(AnalyzeTermsFlowTest.class);
    
        // Create category map for our run.
        CategoryGraph cg = new CategoryGraph();
        cg.add(new Category("Geography of the Caucasus", makeParentSet(new Category("Geography of Europe"))));
        
        // Add in the existing categories as well.
        cg.add(new Category("Caucasus Mountains"));
        cg.add(new Category("Caucasus"));
        cg.add(new Category("Mountain ranges of the Caucasus"));
        cg.add(new Category("Eastern Europe"));
        cg.add(new Category("Western Asia"));
        cg.add(new Category("Mountain ranges of Asia"));
        cg.add(new Category("Mountain ranges of Europe"));
        cg.add(new Category("Mountain ranges of Armenia"));
        cg.add(new Category("Mountain ranges of Azerbaijan"));
        cg.add(new Category("Mountain ranges of Georgia (country)"));
        cg.add(new Category("Mountain ranges of Iran"));
        cg.add(new Category("Mountain ranges of Russia"));
        cg.add(new Category("Mountain ranges of Turkey"));
        cg.add(new Category("Geology of the Caucasus"));
        cg.add(new Category("Landforms of the Caucasus"));
        cg.add(new Category("Geography of Western Asia"));
        cg.add(new Category("Physiographic provinces"));
        cg.add(new Category("Landforms of Kabardino-Balkaria"));
        cg.add(new Category("Landforms of Karachay-Cherkessia"));
        cg.add(new Category("Mountains of Georgia (country)"));

        BasePath categoryGraphPath = platform.makePath(termsOptions.getWorkingPath(), "categorygraph.bin");
        try (OutputStream os = categoryGraphPath.openOutputStream()) {
            DataOutput out = new DataOutputStream(os);
            cg.write(out);
        }
        
        AnalyzeTermsOptions options = new AnalyzeTermsOptions(termsOptions);
        // options.setCategoryGraphFilename(categoryGraphPath.getAbsolutePath());
        options.setCategoryGraphFilename("/Users/kenkrugler/Downloads/categories.map");
        Flow flow = AnalyzeTermsFlow.createFlow(options);
        FlowResult fr = FlowRunner.run(flow);
        
        // Verify we have expanded category data
        BasePath termCategoriesPath = options.getWorkingSubdirPath(WorkingConfig.TERM_CATEGORIES_SUBDIR_NAME);
        Tap termCategoriesTap = platform.makeTap(platform.makeTextScheme(), termCategoriesPath, SinkMode.KEEP);
        TupleEntryIterator iter = termCategoriesTap.openForRead(platform.makeFlowProcess());
        boolean foundExpandedCat = false;
        while (iter.hasNext()) {
            TupleEntry te = iter.next();
            // term <tab> category <tab> score
            String[] pieces = te.getString("line").split("\t", 3);
            assertEquals(3, pieces.length);
            String term = pieces[0];
            String category = pieces[1];
            if (term.equals("range") && category.equals("Geography of Europe")) {
                foundExpandedCat = true;
            }
        }
        
        iter.close();
        
        assertTrue(foundExpandedCat);
    }

    private Set<Category> makeParentSet(Category... categories) {
        Set<Category> result = new HashSet<>();
        for (Category category : categories) {
            result.add(category);
        }
        
        return result;
    }

    @Test
    public void testTermAndScoreLimits() throws Exception {
        GenerateTermsOptions termsOptions = GenerateTermsFlowTest.generateTerms("build/test/AnalyzeTermsFlowTest/testTermAndScoreLimits");
        
        AnalyzeTermsOptions options = new AnalyzeTermsOptions(termsOptions);
        // Use a min term count of 3, and a min score of 2.0
        final int minArticleRefs = 3;
        final float minScore = 2.0f;
        
        options.setMinArticleRefs(minArticleRefs);
        options.setMinArticleScore(minScore);
        Flow flow = AnalyzeTermsFlow.createFlow(options);
        
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(AnalyzeTermsFlow.class, fr.getCounters());
        
        BasePlatform platform = termsOptions.getPlatform(AnalyzeTermsFlowTest.class);
        BasePath termScoresPath = options.getWorkingSubdirPath(WorkingConfig.TERM_SCORES_SUBDIR_NAME);
        Tap termScoresTap = platform.makeTap(platform.makeTextScheme(), termScoresPath, SinkMode.KEEP);
        TupleEntryIterator iter = termScoresTap.openForRead(platform.makeFlowProcess());
        
        Set<String> filteredTerms = new HashSet<>();
        while (iter.hasNext()) {
            TupleEntry te = iter.next();
            // term <tab> article ref <tab> score <tab> count
            String[] pieces = te.getString("line").split("\t", 4);
            assertEquals(4, pieces.length);
            String term = pieces[0];
            filteredTerms.add(term);
            
            float score = Float.parseFloat(pieces[2]);
            assertTrue(score >= minScore);
            
            int count = Integer.parseInt(pieces[3]);
            assertTrue(count >= minArticleRefs);
        }
        
        iter.close();
        
        // Should have this specific entry, since it passes our test
        // zhuangzi Zhuangzi    2.4678922   3
        assertTrue(filteredTerms.contains("zhuangzi"));

        // Should have nothing with score < 2.0, even though it has
        // three occurrences.
        // mountain    Georgia_(country)   1.2108924   3
        assertFalse(filteredTerms.contains("mountain"));
        
        // Make sure our categories all have a score >= the min score.
        BasePath termCategoriesPath = options.getWorkingSubdirPath(WorkingConfig.TERM_CATEGORIES_SUBDIR_NAME);
        Tap termCategoriesTap = platform.makeTap(platform.makeTextScheme(), termCategoriesPath, SinkMode.KEEP);
        iter = termCategoriesTap.openForRead(platform.makeFlowProcess());
        while (iter.hasNext()) {
            TupleEntry te = iter.next();
            // term <tab> category <tab> score
            String[] pieces = te.getString("line").split("\t", 3);
            assertEquals(3, pieces.length);
            
            float score = Float.parseFloat(pieces[2]);
            assertTrue(score >= minScore);
        }
        
        iter.close();

    }

}

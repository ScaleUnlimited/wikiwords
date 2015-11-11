package com.scaleunlimited.wikiwords.flow;

import static org.junit.Assert.*;

import org.junit.Test;

import cascading.flow.Flow;

import com.scaleunlimited.cascading.FlowResult;
import com.scaleunlimited.cascading.FlowRunner;
import com.scaleunlimited.wikiwords.WikiwordsCounters;
import com.scaleunlimited.wikiwords.tools.AnalyzeTermsTool.AnalyzeTermsOptions;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool.GenerateTermsOptions;

public class AnalyzeTermsFlowTest {

    @Test
    public void test() throws Exception {
        GenerateTermsOptions termsOptions = GenerateTermsFlowTest.generateTerms("build/test/AnalyzeTermsFlowTest/test");
        
        long numArticles = termsOptions.getCounter(GenerateTermsFlow.class, WikiwordsCounters.ARTICLES);
        AnalyzeTermsOptions options = new AnalyzeTermsOptions(termsOptions);
        options.setTotalArticles((int)numArticles);
        Flow flow = AnalyzeTermsFlow.createFlow(options);
        
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(AnalyzeTermsFlow.class, fr.getCounters());
        
        // TODO validate results.
    }

}

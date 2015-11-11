package com.scaleunlimited.wikiwords.flow;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.xml.sax.SAXException;

import cascading.flow.Flow;
import cascading.tap.Tap;
import cascading.tuple.TupleEntryIterator;

import com.scaleunlimited.cascading.BasePath;
import com.scaleunlimited.cascading.BasePlatform;
import com.scaleunlimited.cascading.FlowResult;
import com.scaleunlimited.cascading.FlowRunner;
import com.scaleunlimited.wikiwords.WikiwordsCounters;
import com.scaleunlimited.wikiwords.WorkflowOptions;
import com.scaleunlimited.wikiwords.WorkingConfig;
import com.scaleunlimited.wikiwords.datum.WikiTermDatum;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool.GenerateTermsOptions;
import com.scaleunlimited.wikiwords.tools.WikiDumpTool;

public class GenerateTermsFlowTest {

    @Test
    public void test() throws Exception {
        GenerateTermsOptions options = generateTerms("build/test/GenerateTermsFlowTest/test");
        
        // Verify that we get expected results in the output
        BasePlatform platform = options.getPlatform(GenerateTermsFlowTest.class);
        Tap tap = platform.makeTap(platform.makeBinaryScheme(WikiTermDatum.FIELDS), options.getWorkingSubdirPath(WorkingConfig.TERMS_SUBDIR_NAME));
        TupleEntryIterator iter = tap.openForRead(platform.makeFlowProcess());
        WikiTermDatum datum = new WikiTermDatum();
        while (iter.hasNext()) {
            datum.setTupleEntry(iter.next());
            // TODO verify that each field looks correct?
            // System.out.println(datum.getTuple());
        }
        
        // Verify we got the expected number of results.
        Map<String, Long> counters = options.getCounters(GenerateTermsFlow.class);
        String counterName = WorkflowOptions.getFlowCounterName(WikiwordsCounters.ARTICLES);
        assertEquals(18, (long)counters.get(counterName));
    }

    protected static GenerateTermsOptions generateTerms(String testDirname) throws Exception {
        final String inputDirname = testDirname + "/in";
        File inputDir = new File(inputDirname);
        inputDir.mkdirs();
        
        final String metadataDirname = testDirname + "/meta";
        File metadataDir = new File(metadataDirname);
        metadataDir.mkdirs();
        
        // Run WikiDumpTool to generate a file
        WikiDumpTool tool = new WikiDumpTool();
        tool.run("src/test/resources/enwiki-snippet.xml", inputDirname, metadataDirname, 100, 100);
        
        // Run our flow, in test mode
        final String workingDirname = testDirname + "/working";
        File workingDir = new File(workingDirname);
        workingDir.mkdirs();
        
        GenerateTermsOptions options = new GenerateTermsOptions();
        options.setDebug(true);
        options.setMaxDistance(20);
        options.setInputDirname(inputDirname);
        options.setWorkingDirname(workingDirname);
        
        Flow flow = GenerateTermsFlow.createFlow(options);
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(GenerateTermsFlow.class, fr.getCounters());

        return options;
    }
}

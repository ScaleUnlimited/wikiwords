package com.scaleunlimited.wikiwords.flow;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.junit.Test;
import org.xml.sax.SAXException;

import cascading.flow.Flow;
import cascading.tap.SinkMode;
import cascading.tap.Tap;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;
import cascading.tuple.TupleEntryCollector;
import cascading.tuple.TupleEntryIterator;

import com.scaleunlimited.cascading.BasePath;
import com.scaleunlimited.cascading.BasePlatform;
import com.scaleunlimited.cascading.FlowResult;
import com.scaleunlimited.cascading.FlowRunner;
import com.scaleunlimited.cascading.local.LocalPlatform;
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
        assertEquals(15, (long)counters.get(counterName));
    }

    @Test
    public void testTermDistance() throws Exception {
        final String inputDirname = "build/test/GenerateTermsFlowTest/testTermDistance/in";
        File inputDir = new File(inputDirname);
        inputDir.mkdirs();
        
        BasePlatform platform = new LocalPlatform(GenerateTermsFlowTest.class);
        BasePath inputPath = platform.makePath(inputDir.getAbsolutePath());
        Tap inputTap = platform.makeTap(platform.makeTextScheme(), inputPath, SinkMode.REPLACE);
        TupleEntryCollector writer = inputTap.openForWrite(platform.makeFlowProcess());

        Tuple t = new Tuple("Article1\t" + new String(Base64.encodeBase64("term1 term2 [[Article2a article2b]] term3 term4 term5 [[Article3a]]".getBytes("UTF-8")), "UTF-8"));
        writer.add(t);
        writer.close();
        
        final String workingDirname = "build/test/GenerateTermsFlowTest/testTermDistance/working";
        File workingDir = new File(workingDirname);
        workingDir.mkdirs();

        GenerateTermsOptions options = new GenerateTermsOptions();
        options.setDebug(true);
        options.setMaxDistance(1);
        options.setInputDirname(inputDirname);
        options.setWorkingDirname(workingDirname);
        
        Flow flow = GenerateTermsFlow.createFlow(options);
        FlowResult fr = FlowRunner.run(flow);

        // Verify that we got the expected results.
        BasePath outputPath = options.getWorkingSubdirPath(WorkingConfig.TERMS_SUBDIR_NAME);
        Tap outputTap = platform.makeTap(platform.makeBinaryScheme(WikiTermDatum.FIELDS), outputPath, SinkMode.KEEP);
        Iterator<TupleEntry> iter = outputTap.openForRead(platform.makeFlowProcess());
        
        WikiTermDatum datum = new WikiTermDatum();
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article2a_article2b", datum.getArticleRef());
        assertEquals("term2", datum.getTerm());
        assertEquals(1, datum.getDistance());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article2a_article2b", datum.getArticleRef());
        assertEquals("article2a", datum.getTerm());
        assertEquals(0, datum.getDistance());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article2a_article2b", datum.getArticleRef());
        assertEquals("article2b", datum.getTerm());
        assertEquals(0, datum.getDistance());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article2a_article2b", datum.getArticleRef());
        assertEquals("term3", datum.getTerm());
        assertEquals(1, datum.getDistance());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article3a", datum.getArticleRef());
        assertEquals("term5", datum.getTerm());
        assertEquals(1, datum.getDistance());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article3a", datum.getArticleRef());
        assertEquals("article3a", datum.getTerm());
        assertEquals(0, datum.getDistance());
        
        assertFalse(iter.hasNext());
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

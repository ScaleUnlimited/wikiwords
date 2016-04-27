package com.scaleunlimited.wikiwords.flow;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
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
import com.scaleunlimited.wikiwords.WorkflowPlanner;
import com.scaleunlimited.wikiwords.WorkingConfig;
import com.scaleunlimited.wikiwords.datum.WikiCategoryDatum;
import com.scaleunlimited.wikiwords.datum.WikiTermDatum;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool.GenerateTermsOptions;
import com.scaleunlimited.wikiwords.tools.WikiDumpTool;

public class GenerateTermsFlowTest {

    private static final int MAX_DISTANCE = 20;

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
            
            // Verify that none of the terms are outside of the Latin (Base + extended) blocks.
            String term = datum.getTerm();
            assertFalse(term.isEmpty());
            
            Character.UnicodeBlock block = Character.UnicodeBlock.of(term.charAt(0));
            assertTrue("Term outside of Latin blocks: " + term, (block == Character.UnicodeBlock.BASIC_LATIN) || (block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT));

            assertTrue(datum.getDistance() >= 0);
            assertTrue("Distance is greater than max: " + datum.getDistance(), datum.getDistance() <= MAX_DISTANCE);
       }
        
        // Verify we got the expected number of results.
        Map<String, Long> counters = options.getCounters(GenerateTermsFlow.class);
        String counterName = WorkflowOptions.getFlowCounterName(WikiwordsCounters.ARTICLES);
        assertEquals(16, (long)counters.get(counterName));
    }

    @Test
    public void testCategoryExtraction() throws Exception {
        final String inputDirname = "build/test/GenerateTermsFlowTest/testCategoryExtraction/in";
        File inputDir = new File(inputDirname);
        inputDir.mkdirs();
        
        BasePlatform platform = new LocalPlatform(GenerateTermsFlowTest.class);
        BasePath inputPath = platform.makePath(inputDir.getAbsolutePath());
        Tap inputTap = platform.makeTap(platform.makeTextScheme(), inputPath, SinkMode.REPLACE);
        TupleEntryCollector writer = inputTap.openForWrite(platform.makeFlowProcess());

        writer.add(makeWikiLine("Article1", "term1 term2 [[Category: Fizz]] [[Category: Ball]]"));
        writer.add(makeWikiLine("Article2a Article2b", "term1 term2 [[Category: Foo|blah]] [[Category: Bar]]"));
        writer.close();
        
        final String workingDirname = "build/test/GenerateTermsFlowTest/testCategoryExtraction/working";
        File workingDir = new File(workingDirname);
        workingDir.mkdirs();

        GenerateTermsOptions options = new GenerateTermsOptions();
        options.setPlanner(WorkflowPlanner.LOCAL);
        options.setDebugLogging(true);
        options.setMaxDistance(1);
        options.setInputDirname(inputDirname);
        options.setWorkingDirname(workingDirname);
        
        Flow flow = GenerateTermsFlow.createFlow(options);
        FlowResult fr = FlowRunner.run(flow);
        
        // Verify we get categories as one of the results.
        BasePath outputPath = options.getWorkingSubdirPath(WorkingConfig.CATEGORIES_SUBDIR_NAME);
        Tap outputTap = platform.makeTap(platform.makeBinaryScheme(WikiCategoryDatum.FIELDS), outputPath, SinkMode.KEEP);
        Iterator<TupleEntry> iter = outputTap.openForRead(platform.makeFlowProcess());

        WikiCategoryDatum datum = new WikiCategoryDatum();
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Fizz", datum.getCategory());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Ball", datum.getCategory());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article2a_Article2b", datum.getArticle());
        assertEquals("Foo", datum.getCategory());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article2a_Article2b", datum.getArticle());
        assertEquals("Bar", datum.getCategory());
        
        assertFalse(iter.hasNext());
    }
    
    @Test
    public void testTermFiltering() throws Exception {
        final String baseDirname = "build/test/GenerateTermsFlowTest/testTermFiltering/";
        final String inputDirname = baseDirname + "in";
        File inputDir = new File(inputDirname);
        inputDir.mkdirs();
        
        BasePlatform platform = new LocalPlatform(GenerateTermsFlowTest.class);
        BasePath inputPath = platform.makePath(inputDir.getAbsolutePath());
        Tap inputTap = platform.makeTap(platform.makeTextScheme(), inputPath, SinkMode.REPLACE);
        TupleEntryCollector writer = inputTap.openForWrite(platform.makeFlowProcess());

        Tuple t1 = new Tuple("Article1\t" + new String(Base64.encodeBase64("a ab [[Article2]] term1 123 [[Article3]] term4".getBytes("UTF-8")), "UTF-8"));
        writer.add(t1);
        writer.close();

        final String workingDirname = baseDirname + "working";
        File workingDir = new File(workingDirname);
        workingDir.mkdirs();

        GenerateTermsOptions options = new GenerateTermsOptions();
        options.setDebugLogging(true);
        options.setPlanner(WorkflowPlanner.LOCAL);
        options.setMaxDistance(2);
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
        assertEquals("Article2", datum.getArticleRef());
        assertEquals("article2", datum.getTerm());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article2", datum.getArticleRef());
        assertEquals("term1", datum.getTerm());
        
        // Note that term1 won't be associated with Article3, as it's already been
        // associated with Article2
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article3", datum.getArticleRef());
        assertEquals("article3", datum.getTerm());
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article1", datum.getArticle());
        assertEquals("Article3", datum.getArticleRef());
        assertEquals("term4", datum.getTerm());
        
        assertFalse(iter.hasNext());
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

        Tuple t1 = new Tuple("Article1\t" + new String(Base64.encodeBase64("term1 term2 [[Article2a article2b]] term3 term4 term5 [[Article3a]]".getBytes("UTF-8")), "UTF-8"));
        writer.add(t1);
        
        // Verify we keep "123" as a term, so term1 isn't associated with Article4, but we don't emit it
        Tuple t2 = new Tuple("Article2\t" + new String(Base64.encodeBase64("term1 123 [[Article4]]".getBytes("UTF-8")), "UTF-8"));
        writer.add(t2);
        writer.close();
        
        final String workingDirname = "build/test/GenerateTermsFlowTest/testTermDistance/working";
        File workingDir = new File(workingDirname);
        workingDir.mkdirs();

        GenerateTermsOptions options = new GenerateTermsOptions();
        options.setDebugLogging(true);
        options.setPlanner(WorkflowPlanner.LOCAL);
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
        
        datum.setTupleEntry(iter.next());
        assertEquals("Article2", datum.getArticle());
        assertEquals("Article4", datum.getArticleRef());
        assertEquals("article4", datum.getTerm());
        assertEquals(0, datum.getDistance());
        
        assertFalse(iter.hasNext());
    }
    
    protected static Tuple makeWikiLine(String article, String text) {
        try {
            return new Tuple(article + "\t" + new String(Base64.encodeBase64(text.getBytes("UTF-8"))));
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("Impossible exception", e);
        }
    }
    
    protected static GenerateTermsOptions generateTerms(String testDirname) throws Exception {
        return generateTerms("src/test/resources/enwiki-snippet.xml", testDirname);
    }
    
    protected static GenerateTermsOptions generateTerms(String inputFile, String testDirname) throws Exception {
        final String inputDirname = testDirname + "/in";
        File inputDir = new File(inputDirname);
        inputDir.mkdirs();
        
        final String metadataDirname = testDirname + "/meta";
        File metadataDir = new File(metadataDirname);
        metadataDir.mkdirs();
        
        // Run WikiDumpTool to generate a file
        WikiDumpTool tool = new WikiDumpTool();
        tool.run(inputFile, inputDirname, metadataDirname, 100, 100);
        
        // Run our flow, in test mode
        final String workingDirname = testDirname + "/working";
        File workingDir = new File(workingDirname);
        workingDir.mkdirs();
        
        GenerateTermsOptions options = new GenerateTermsOptions();
        options.setDebugLogging(true);
        options.setPlanner(WorkflowPlanner.LOCAL);
        options.setMaxDistance(MAX_DISTANCE);
        options.setInputDirname(inputDirname);
        options.setWorkingDirname(workingDirname);
        
        Flow flow = GenerateTermsFlow.createFlow(options);
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(GenerateTermsFlow.class, fr.getCounters());

        return options;
    }
    
}

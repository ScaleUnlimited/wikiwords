package com.scaleunlimited.wikiwords.tools;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileReader;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.scaleunlimited.wikiwords.tools.WikiDumpTool.WikiDumpFilter;

public class WikiDumpToolTest {

    @Test
    public void test() throws Exception {
        WikiDumpTool tool = new WikiDumpTool();
        
        File outputDir = new File("build/test/WikiDumpToolTest/test/");
        outputDir.mkdirs();
        FileUtils.cleanDirectory(outputDir);
        
        Map<String, Integer> counters = tool.run("src/test/resources/enwiki-snippet.xml", outputDir.getAbsolutePath(), 5, 10);
        
        assertEquals(10, (int)counters.get(WikiDumpTool.MAIN_PAGE_COUNTER));
        assertEquals(20, (int)counters.get(WikiDumpTool.REDIRECT_PAGE_COUNTER));
        assertEquals(1, (int)counters.get(WikiDumpTool.FILE_PAGE_COUNTER));
        assertNull(counters.get(WikiDumpTool.EXCEPTION_COUNTER));
        
        // We should wind up with two part files
        assertTrue(new File(outputDir, "part-000.txt").exists());
        assertTrue(new File(outputDir, "part-001.txt").exists());
        assertFalse(new File(outputDir, "part-002.txt").exists());

        // Verify each part file has 5 lines in it, tab-separated.
        List<String> lines = IOUtils.readLines(new FileReader(new File(outputDir, "part-000.txt")));
        assertEquals(5, lines.size());
        for (String line : lines) {
            assertEquals(2, line.split("\t").length);
        }
        
        lines = IOUtils.readLines(new FileReader(new File(outputDir, "part-001.txt")));
        assertEquals(5, lines.size());
        for (String line : lines) {
            assertEquals(2, line.split("\t").length);
        }
    }

    @Test
    public void testCompressedOutput() throws Exception {
        WikiDumpTool tool = new WikiDumpTool();
        
        File outputDir = new File("build/test/WikiDumpToolTest/testCompressedOutput/");
        outputDir.mkdirs();
        FileUtils.cleanDirectory(outputDir);
        
        Map<String, Integer> counters = tool.run("src/test/resources/enwiki-snippet.xml", outputDir.getAbsolutePath(), 5, 10, true, 1.0f);
        assertEquals(10, (int)counters.get(WikiDumpTool.MAIN_PAGE_COUNTER));
        assertEquals(20, (int)counters.get(WikiDumpTool.REDIRECT_PAGE_COUNTER));
        assertEquals(1, (int)counters.get(WikiDumpTool.FILE_PAGE_COUNTER));
        assertNull(counters.get(WikiDumpTool.EXCEPTION_COUNTER));

        assertTrue(new File(outputDir, "part-000.gz").exists());
        assertTrue(new File(outputDir, "part-001.gz").exists());
    }
    
    @Test
    public void testRedirectHandling() throws Exception {
        File outputDir = new File("build/test/WikiDumpToolTest/testRedirectHandling");
        outputDir.mkdirs();
        
        WikiDumpFilter filter = new WikiDumpFilter(outputDir, 1, 1, false, 1.0f);
        assertEquals("In_One_Breath", filter.getRedirect("#REDIRECT [[In One Breath]]"));
        assertEquals("In_One_Breath", filter.getRedirect("#redirect [[In One Breath]]"));
        assertEquals("King_of_the_Hill_(season_4)", filter.getRedirect("#REDIRECT:[[King of the Hill (season 4)]]"));
        assertEquals("Uzel_Holding", filter.getRedirect("#REDIRECT: [[Uzel Holding]]"));
        assertEquals("Taif_Agreement", filter.getRedirect("#REDIRECT\n\n[[Taif Agreement]]"));
        filter.close();
    }
    
    @Test
    public void testSampling() throws Exception {
        WikiDumpTool tool = new WikiDumpTool();
        
        File outputDir = new File("build/test/WikiDumpToolTest/testSampling/");
        outputDir.mkdirs();
        FileUtils.cleanDirectory(outputDir);
        
        // Note that we get more redirect and file pages, since we're processing the entire
        // dataset while trying to get to 10 pages, with a sampling rate of 0.2f
        Map<String, Integer> counters = tool.run("src/test/resources/enwiki-snippet.xml", outputDir.getAbsolutePath(), 5, 10, false, 0.2f);
        assertEquals(2, (int)counters.get(WikiDumpTool.MAIN_PAGE_COUNTER));
        assertEquals(25, (int)counters.get(WikiDumpTool.REDIRECT_PAGE_COUNTER));
        assertEquals(4, (int)counters.get(WikiDumpTool.FILE_PAGE_COUNTER));
        assertNull(counters.get(WikiDumpTool.EXCEPTION_COUNTER));

        assertTrue(new File(outputDir, "part-000.txt").exists());
        assertFalse(new File(outputDir, "part-001.txt").exists());
    }
}

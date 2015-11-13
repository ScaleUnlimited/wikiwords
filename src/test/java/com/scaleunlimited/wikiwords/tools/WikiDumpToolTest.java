package com.scaleunlimited.wikiwords.tools;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileReader;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.junit.Test;

import com.scaleunlimited.wikiwords.tools.WikiDumpTool.WikiDumpFilter;

public class WikiDumpToolTest {

    @Test
    public void test() throws Exception {
        WikiDumpTool tool = new WikiDumpTool();
        
        File outputDir = new File("build/test/WikiDumpToolTest/test/output/");
        outputDir.mkdirs();
        
        File metadataDir = new File("build/test/WikiDumpToolTest/test/metadata/");
        metadataDir.mkdirs();
        
        Map<String, Integer> counters = tool.run("src/test/resources/enwiki-snippet.xml", outputDir.getAbsolutePath(), metadataDir.getAbsolutePath(), 5, 100);
        
        assertEquals(15, (int)counters.get(WikiDumpTool.MAIN_PAGE_COUNTER));
        assertEquals(25, (int)counters.get(WikiDumpTool.REDIRECT_PAGE_COUNTER));
        assertEquals(4, (int)counters.get(WikiDumpTool.FILE_PAGE_COUNTER));
        assertEquals(3, (int)counters.get(WikiDumpTool.DISAMBIGUATION_PAGE_COUNTER));
        assertEquals(2, (int)counters.get(WikiDumpTool.CATEGORY_PAGE_COUNTER));
        assertNull(counters.get(WikiDumpTool.EXCEPTION_COUNTER));
        
        // We should wind up with two part files
        assertTrue(new File(outputDir, "part-000.txt").exists());
        assertTrue(new File(outputDir, "part-001.txt").exists());
        assertTrue(new File(outputDir, "part-002.txt").exists());
        assertFalse(new File(outputDir, "part-003.txt").exists());

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
        
        lines = IOUtils.readLines(new FileReader(new File(outputDir, "part-002.txt")));
        assertEquals(5, lines.size());
        for (String line : lines) {
            assertEquals(2, line.split("\t").length);
        }
        
        // Verify we got a redirects file with 25 entries
        lines = IOUtils.readLines(new FileReader(new File(metadataDir, "redirects.txt")));
        assertEquals(25, lines.size());
        
        // Verify we got a dismbig file with 3 entries
        lines = IOUtils.readLines(new FileReader(new File(metadataDir, "disambigs.txt")));
        assertEquals(3, lines.size());
        
        // Verify we got a categories file with 2 entries
        lines = IOUtils.readLines(new FileReader(new File(metadataDir, "categories.txt")));
        assertEquals(2, lines.size());
    }

    @Test
    public void testCompressedOutput() throws Exception {
        WikiDumpTool tool = new WikiDumpTool();
        
        File outputDir = new File("build/test/WikiDumpToolTest/testCompressedOutput/output/");
        outputDir.mkdirs();
        
        File metadataDir = new File("build/test/WikiDumpToolTest/testCompressedOutput/metadata/");
        metadataDir.mkdirs();
        
        Map<String, Integer> counters = tool.run("src/test/resources/enwiki-snippet.xml", outputDir.getAbsolutePath(), metadataDir.getAbsolutePath(), 5, 10, true, 1.0f);
        assertEquals(10, (int)counters.get(WikiDumpTool.MAIN_PAGE_COUNTER));
        assertEquals(20, (int)counters.get(WikiDumpTool.REDIRECT_PAGE_COUNTER));
        assertEquals(1, (int)counters.get(WikiDumpTool.FILE_PAGE_COUNTER));
        assertNull(counters.get(WikiDumpTool.EXCEPTION_COUNTER));

        assertTrue(new File(outputDir, "part-000.gz").exists());
        assertTrue(new File(outputDir, "part-001.gz").exists());
    }
    
    @Test
    public void testCategoryHandling() throws Exception {
        File outputDir = new File("build/test/WikiDumpToolTest/testRedirectHandling");
        outputDir.mkdirs();
        
        WikiDumpFilter filter = new WikiDumpFilter(outputDir, 1, 1, false, 1.0f);
        assertEquals(makeSet("Cold_War_military_installations", "Radar_stations") , filter.getParentCategories("[[Category:Cold War military installations]]\t[[Category:Radar stations]]"));
        filter.close();
    }
    
    private Set<String> makeSet(String... elements) {
        Set<String> result = new HashSet<>();
        for (String element : elements) {
            result.add(element);
        }
        return result;
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
        assertEquals("Beşiktaş_J.K.", filter.getRedirect("\n\n#REDIRECT: [[Beşiktaş J.K.]]"));
        assertEquals("How_I_Met_Your_Mother", filter.getRedirect("#redirect : [[How I Met Your Mother]]"));
        assertEquals("Blah", filter.getRedirect("#redirect [[Blah# Anchor]]"));
        filter.close();
    }
    
    @Test
    public void testDisambiguationHandling() throws Exception {
        File outputDir = new File("build/test/WikiDumpToolTest/testDisambiguationHandling");
        outputDir.mkdirs();
        
        WikiDumpFilter filter = new WikiDumpFilter(outputDir, 1, 1, false, 1.0f);
        assertTrue(filter.isDisambiguation("{{disambiguation}}"));
        assertTrue(filter.isDisambiguation("blah {{disambiguation}}"));
        assertTrue(filter.isDisambiguation("{{ disambiguation }}"));
        
        assertTrue(filter.isDisambiguation("blah {{Disambiguation}} blah"));
        assertTrue(filter.isDisambiguation("{{disambiguation|geo|ship}}"));
        assertTrue(filter.isDisambiguation("{{disambiguation |geo|ship}}"));
        
        assertTrue(filter.isDisambiguation("{{disambig}}"));
        assertTrue(filter.isDisambiguation("{{disambig|xx}}"));
        assertTrue(filter.isDisambiguation("{{Dab}}"));
        assertTrue(filter.isDisambiguation("{{DAB}}"));
        assertTrue(filter.isDisambiguation("{{Disamb}}"));
        
        assertTrue(filter.isDisambiguation("{{Disambiguation cleanup}}"));
        assertTrue(filter.isDisambiguation("{{Airport disambiguation}}"));
        assertTrue(filter.isDisambiguation("{{Numberdis}}"));
        assertTrue(filter.isDisambiguation("{{Letter-NumberCombDisambig}}"));
        assertTrue(filter.isDisambiguation("{{Hndis}}"));
        assertTrue(filter.isDisambiguation("{{Hndis-cleanup}}"));
        assertTrue(filter.isDisambiguation("{{Geodis}}"));
        assertTrue(filter.isDisambiguation("{{Disambig-Plants}}"));
        assertTrue(filter.isDisambiguation("{{Mil-unit-dis}}"));

        assertFalse(filter.isDisambiguation("{{disambiguation needed|date=June 2012}}"));
        assertFalse(filter.isDisambiguation("disambiguation"));
        
        filter.close();
    }
    
    @Test
    public void testSampling() throws Exception {
        WikiDumpTool tool = new WikiDumpTool();
        
        File outputDir = new File("build/test/WikiDumpToolTest/testSampling/output/");
        outputDir.mkdirs();
        
        File metadataDir = new File("build/test/WikiDumpToolTest/testSampling/metadata/");
        metadataDir.mkdirs();
        
        // Note that we get more redirect and file pages, since we're processing the entire
        // dataset while trying to get to 10 pages, with a sampling rate of 0.2f
        Map<String, Integer> counters = tool.run("src/test/resources/enwiki-snippet.xml", outputDir.getAbsolutePath(), metadataDir.getAbsolutePath(), 5, 10, false, 0.2f);
        assertEquals(1, (int)counters.get(WikiDumpTool.MAIN_PAGE_COUNTER));
        assertEquals(25, (int)counters.get(WikiDumpTool.REDIRECT_PAGE_COUNTER));
        assertEquals(4, (int)counters.get(WikiDumpTool.FILE_PAGE_COUNTER));
        assertNull(counters.get(WikiDumpTool.EXCEPTION_COUNTER));

        assertTrue(new File(outputDir, "part-000.txt").exists());
        assertFalse(new File(outputDir, "part-001.txt").exists());
    }
}

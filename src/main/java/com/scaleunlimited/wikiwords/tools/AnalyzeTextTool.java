package com.scaleunlimited.wikiwords.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.scaleunlimited.cascading.BaseTool;

public class AnalyzeTextTool extends BaseTool {
    private static final Logger LOGGER = Logger.getLogger(AnalyzeTextTool.class);

    private Map<String, Map<String, Float>> _termToCatMap;
    
    private void load(AnalyzeTextOptions options) throws Exception {
        _termToCatMap = new HashMap<>();
        
        File inputFile = new File(options.getCategoryFilename());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] pieces = line.split("\t");
                if (pieces.length != 3) {
                    LOGGER.error("Invalid category line: " + line);
                    continue;
                }
                
                String term = pieces[0];
                String categoryName = pieces[1];
                float score = Float.parseFloat(pieces[2]);
             
                Map<String, Float> categories = _termToCatMap.get(term);
                if (categories == null) {
                    categories = new HashMap<>();
                    _termToCatMap.put(term, categories);
                }
                
                Float categoryScore = categories.get(categoryName);
                if (categoryScore == null) {
                    categories.put(categoryName, score);
                } else {
                    LOGGER.warn("term/cat duplicate found: " + term + "/" + categoryName);
                }
            }
        }
        
        LOGGER.info("Number of terms: " + _termToCatMap.size());
    }

    private List<CatAndScore> analyze(String text) {
        // TODO use same parser as code that generates terms

        Map<String, Float> catScores = new HashMap<>();
        
        BreakIterator iter = BreakIterator.getWordInstance();
        iter.setText(text);
        
        int start = iter.first();
        for (int end = iter.next(); end != BreakIterator.DONE; start = end, end = iter.next()) {
            String term = text.substring(start, end);
            term = term.toLowerCase();
            term = StringUtils.strip(term);
            term = StringUtils.strip(term, "[](),?!;:.'\"");
            
            Map<String, Float> termCatScores = _termToCatMap.get(term);
            if (termCatScores != null) {
                for (String catName : termCatScores.keySet()) {
                    float catScore = termCatScores.get(catName);
                    Float curScore = catScores.get(catName);
                    if (curScore == null) {
                        catScores.put(catName, catScore);
                    } else {
                        catScores.put(catName, curScore + catScore);
                    }
                }
            }
        }

        List<CatAndScore> result = new ArrayList<>();
        for (String catName : catScores.keySet()) {
            result.add(new CatAndScore(catName, catScores.get(catName)));
        }
        
        Collections.sort(result);
        
        return result.subList(0, Math.min(20, result.size()));
    }

    public static void main(String[] args) {
        AnalyzeTextOptions options = new AnalyzeTextOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        AnalyzeTextTool tool = new AnalyzeTextTool();
        
        try {
            tool.load(options);
            
            while (true) {
                String text = readInputLine("Text to analyze: ");
                if (text.isEmpty()) {
                    break;
                }
                
                List<CatAndScore> result = tool.analyze(text);
                for (CatAndScore cas : result) {
                    System.out.println(cas);
                }
                
                System.out.println();
            }
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }
    
    /**
     * Read one line of input from the console, after displaying prompt
     * 
     * @return Text that the user entered
     * @throws IOException
     */
    private static String readInputLine(String prompt) throws IOException {
        System.out.print(prompt);
        return readInputLine();
    }

    /**
     * Read one line of input from the console.
     * 
     * @return Text that the user entered
     * @throws IOException
     */
    private static String readInputLine() throws IOException {
        InputStreamReader isr = new InputStreamReader(System.in);
        BufferedReader br = new BufferedReader(isr);
        return br.readLine();
    }

    private static class CatAndScore implements Comparable<CatAndScore> {
        private String _categoryName;
        private float _score;
        
        public CatAndScore(String categoryName, float score) {
            _categoryName = categoryName;
            _score = score;
        }

        public String getCategoryName() {
            return _categoryName;
        }

        public float getScore() {
            return _score;
        }

        @Override
        public String toString() {
            return String.format("%s: %f", _categoryName, _score);
        }
        
        @Override
        public int compareTo(CatAndScore o) {
            if (_score > o._score) {
                return -1;
            } else if (_score < o._score) {
                return 1;
            } else {
                return _categoryName.compareTo(o._categoryName);
            }
        }
        
        
        
    }
    public static class AnalyzeTextOptions {
        private String _categoryFilename;
        
        @Option(name = "-catfile", usage = "path to term->cat mapping data", required = true)
        public void setCatagoryFile(String categoryFilename) {
            _categoryFilename = categoryFilename;
        }

        public String getCategoryFilename() {
            return _categoryFilename;
        }

    }
}

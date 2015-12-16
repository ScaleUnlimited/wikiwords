package com.scaleunlimited.wikiwords.tools;

import java.io.BufferedReader;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.scaleunlimited.wikiwords.Category;
import com.scaleunlimited.wikiwords.CategoryGraph;

public class CategoryGraphTool {
    private static final Logger LOGGER = Logger.getLogger(CategoryGraphTool.class);

    public CategoryGraphTool() {
    }

    public void run(CategoryGraphOptions options) throws IOException {
        
        // We'll get lines of text with the following format:
        //
        //  <category name> <tab> <parent category> | <parent category> ...
        //
        // There are 0...n parent categories. There can also be cyclic loops
        // in the resulting graph.
        
        Map<Category, String> categoriesWithParents = new HashMap<>();
        CategoryGraph graph = new CategoryGraph();
        
        File inputFile = new File(options.getInputFilename());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "UTF-8"))) {
            
            String line;
            while ((line = reader.readLine()) != null) {
                String[] pieces = line.split("\t");
                if ((pieces.length == 0) || (pieces.length > 2)) {
                    LOGGER.error("Invalid category line: " + line);
                    continue;
                }
                
                String categoryName = Category.normalizeName(pieces[0]);
                if (graph.exists(categoryName)) {
                    if (categoriesWithParents.containsKey(categoryName) && (pieces.length == 2)) {
                        LOGGER.error(String.format("Multiple entries for category '%s' (with parents), got '%s'", categoryName, pieces[0]));
                    } else {
                        LOGGER.warn(String.format("Multiple entries for category '%s', got '%s'", categoryName, pieces[0]));
                    }
                    
                    continue;
                }

                Category cat = new Category(categoryName);
                graph.add(cat);

                if (pieces.length == 2) {
                    if (categoriesWithParents.put(cat, pieces[1]) != null) {
                        LOGGER.error(String.format("Multiple entries for parents of category '%s'", categoryName));
                    }
                }
            }
        }
        
        LOGGER.info("Number of categories: " + graph.size());
        
        // Now we have to run over the categories with parents, and update those categories
        // now that we've created the parent category objects and added them to the graph.
        LOGGER.info("Linking parents...");
        
        for (Category category : categoriesWithParents.keySet()) {
            String parentNames = categoriesWithParents.get(category);
            Set<Category> parents = new HashSet<>();
            for (String parentName : parentNames.split("\\|")) {
                parentName = Category.normalizeName(parentName);
                Category parent = graph.get(parentName);
                if (parent == null) {
                    // We have a category that has a parent category that doesn't exist,
                    // so go add it.
                    parent = new Category(parentName);
                    graph.add(parent);
                }
                
                parents.add(parent);
            }
            
            category.setParents(parents);
        }
        
        File outputFile = new File(options.getOutputFilename());
        File outputDir = outputFile.getParentFile();
        outputDir.mkdirs();
        FileOutputStream fos = new FileOutputStream(outputFile);
        DataOutput out = new DataOutputStream(fos);
        graph.write(out);
        fos.close();
        
        FileUtils.sizeOf(outputFile);
        LOGGER.info(String.format(  "Wrote category map (size = %s) to %s", 
                                    FileUtils.byteCountToDisplaySize(FileUtils.sizeOf(outputFile)),
                                    outputFile));
    }

    public static void main(String[] args) {
        CategoryGraphOptions options = new CategoryGraphOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        CategoryGraphTool tool = new CategoryGraphTool();
        
        try {
            tool.run(options);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
    }

    public static class CategoryGraphOptions {
        private String _inputFilename;
        private String _outputFilename;
        
        @Option(name = "-inputfile", usage = "path to categories data file generated by WikiDumpTool", required = true)
        public void setInputFilename(String inputFilename) {
            _inputFilename = inputFilename;
        }

        public String getInputFilename() {
            return _inputFilename;
        }

        @Option(name = "-outputfile", usage = "path to file for generated graph", required = true)
        public void setOutputFilename(String outputFilename) {
            _outputFilename = outputFilename;
        }

        public String getOutputFilename() {
            return _outputFilename;
        }

    }

}

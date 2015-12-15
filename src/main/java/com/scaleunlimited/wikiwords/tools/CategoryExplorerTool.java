package com.scaleunlimited.wikiwords.tools;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;

import org.apache.log4j.Logger;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import com.scaleunlimited.wikiwords.Category;
import com.scaleunlimited.wikiwords.CategoryGraph;

public class CategoryExplorerTool {
    private static final Logger LOGGER = Logger.getLogger(CategoryExplorerTool.class);

    private CategoryGraph _categoryGraph;
    
    public void load(CategoryExplorerOptions options) throws IOException {
        
        _categoryGraph = new CategoryGraph();
        try (InputStream is = new FileInputStream(options.getInputFilename())) {
            DataInput in = new DataInputStream(is);
            System.out.println("Loading categories...");
            _categoryGraph.readFields(in);
        } catch (Exception e) {
            throw new RuntimeException("Error opening category graph file", e);
        }

    }

    public void analyze(String categories) {
        for (String category : categories.split(",")) {
            category = category.trim();
            if (category.isEmpty()) {
                continue;
            }

            Category cat = _categoryGraph.get(category);
            if (cat == null) {
                System.out.println(category + " doesn't exist in the graph");
            } else {
                System.out.println(category);
                
                LinkedList<Category> queue = new LinkedList<>();
                Set<String> result = new HashSet<>();
                cat.setDepth(0);
                queue.add(cat);
                
                while (!queue.isEmpty()) {
                    cat = queue.pop();
                    int catDepth = cat.getDepth();
                    // Not too deep, and we haven't already processed it, so push parents.
                    if (result.add(cat.getName())) {
                        for (Category parent : cat.getParents()) {
                            parent.setDepth(catDepth + 1);
                            queue.add(parent);
                            
                            for (int i = 0; i < catDepth + 1; i++) {
                                System.out.print('\t');
                            }
                            
                            System.out.println(parent.getName());
                        }
                    }            
                }
            }
        }
    }
    
    public static void main(String[] args) {
        CategoryExplorerOptions options = new CategoryExplorerOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        CategoryExplorerTool tool = new CategoryExplorerTool();
        
        try {
            tool.load(options);
            
            while (true) {
                String categories = readInputLine("Comma-separated list of categories: ");
                if (categories.isEmpty()) {
                    break;
                }
                
                tool.analyze(categories);
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

    private static void printUsageAndExit(CmdLineParser parser) {
        parser.printUsage(System.err);
        System.exit(-1);
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

    public static class CategoryExplorerOptions {
        private String _inputFilename;
        
        @Option(name = "-inputfile", usage = "path to categories map file created by CategoryGraphTool", required = true)
        public void setInputFilename(String inputFilename) {
            _inputFilename = inputFilename;
        }

        public String getInputFilename() {
            return _inputFilename;
        }

    }

}

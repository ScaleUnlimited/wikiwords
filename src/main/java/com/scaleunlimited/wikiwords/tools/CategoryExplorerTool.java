package com.scaleunlimited.wikiwords.tools;

import java.io.BufferedReader;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
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
    private CategoryGraph _invertedGraph;
    
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

    public void tree() throws IOException {
        while (true) {
            String category = readInputLine("Category name for tree: ");
            if (category.isEmpty()) {
                break;
            }

            category = Category.normalizeName(category);
            if (category.isEmpty()) {
                continue;
            }

            Category cat = _categoryGraph.get(category);
            if (cat == null) {
                System.out.println(category + " doesn't exist in the graph");
                continue;
            }

            System.out.println();
            printCategoryTree(new HashSet<String>(), cat, 0);
        }
    }
    
    private void printCategoryTree(Set<String> processed, Category cat, int depth) {
        if ((depth >= 5) || !processed.add(cat.getName())) {
            return;
        }

        for (int i = 0; i < depth + 1; i++) {
            System.out.print('\t');
        }

        System.out.println(cat);

        for (Category parent : cat.getParents()) {
            printCategoryTree(processed, parent, depth + 1);
        }
    }

    /**
     * Find all categories that connect to the target category.
     * 
     * @throws IOException
     */
    public void path() throws IOException {
        while (true) {
            String category = readInputLine("Category name for paths: ");
            if (category.isEmpty()) {
                break;
            }

            category = Category.normalizeName(category);
            if (category.isEmpty()) {
                continue;
            }

            if (_invertedGraph == null) {
                System.out.println("Inverting graph...");
                _invertedGraph = _categoryGraph.invertGraph();
            }
            
            Category cat = _invertedGraph.get(category);
            if (cat == null) {
                System.out.println(category + " doesn't exist in the graph");
                continue;
            }

            System.out.println();
            printCategoryTree(new HashSet<String>(), cat, 0);
        }
    }
    
    private void printPathTree(Set<String> processed, Category cat, int depth) {
        if ((depth >= 5) || !processed.add(cat.getName())) {
            return;
        }

        for (int i = 0; i < depth + 1; i++) {
            System.out.print('\t');
        }

        System.out.println(cat);

        for (Category child : _categoryGraph.getChildren(cat)) {
            printPathTree(processed, child, depth + 1);
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
                String cmd = readInputLine("Enter command - (t)ree, (p)ath: ");
                if (cmd.isEmpty()) {
                    break;
                }
                
                if (cmd.equals("t")) {
                    tool.tree();
                } else if (cmd.equals("p")) {
                    tool.path();
                } else {
                    System.out.println("Unknown command: " + cmd);
                }
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

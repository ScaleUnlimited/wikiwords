package com.scaleunlimited.wikiwords.tools;

import static org.junit.Assert.*;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;

import org.junit.Test;

import com.scaleunlimited.wikiwords.Category;
import com.scaleunlimited.wikiwords.CategoryGraph;
import com.scaleunlimited.wikiwords.tools.CategoryGraphTool.CategoryGraphOptions;

public class CategoryGraphToolTest {

    @Test
    public void test() throws Exception {
        CategoryGraphTool tool = new CategoryGraphTool();
        
        final String outputFilename = "build/test/CategoryGraphToolTest/test/categories.map";
        CategoryGraphOptions options = new CategoryGraphOptions();
        options.setInputFilename("src/test/resources/categories.txt");
        options.setOutputFilename(outputFilename);
        
        tool.run(options);
        
        // Now try to deserialize the result.
        FileInputStream fis = new FileInputStream(new File(outputFilename));
        DataInput in = new DataInputStream(fis);
        CategoryGraph graph = new CategoryGraph();
        graph.readFields(in);
        fis.close();
        
        assertEquals(4, graph.size());
        
        assertEquals(0, graph.getCategory(new Category("Fayetteville FireAntz players")).getParents().size());
    }

}

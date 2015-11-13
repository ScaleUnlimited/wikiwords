package com.scaleunlimited.wikiwords;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.apache.hadoop.yarn.state.Graph;
import org.junit.Test;

public class CategoryGraphTest {

    @Test
    public void test() {
        CategoryGraph graph = new CategoryGraph();
        Category cat1 = new Category("cat1");
        graph.add(cat1);
        
        final Category cat2 = new Category("cat2");
        graph.add(cat2);
        
        Category cat3 = new Category("cat3", new HashSet<Category>(){{add(cat2);}});
        graph.add(cat3);

        Set<Category> terminals = graph.findTails();
        assertEquals(2, terminals.size());
        assertTrue(terminals.contains(new Category("cat1", null)));
        assertTrue(terminals.contains(new Category("cat3", null)));
    }

    @Test
    public void testGetCategory() {
        CategoryGraph graph = new CategoryGraph();
        Category cat2 = new Category("cat2");
        graph.add(cat2);

        assertEquals(cat2, graph.getCategory(new Category("cat2")));
        assertEquals(cat2, graph.getCategory(new Category("cat2", Category.makeSet(new Category("cat7")))));
        assertNull(graph.getCategory(new Category("cat1")));
        assertNull(graph.getCategory(new Category("cat3")));
    }
    
    @Test
    public void testBinaryCycle() {
        final Category cat1 = new Category("cat1");
        final Category cat2 = new Category("cat2");
        
        cat1.setParents(new HashSet<Category>(){{add(cat2);}});
        cat2.setParents(new HashSet<Category>(){{add(cat1);}});
        
        CategoryGraph graph = new CategoryGraph();
        graph.add(cat1);
        graph.add(cat2);
        
        assertEquals(0, graph.findTails().size());
        assertTrue(graph.hasCycles());
    }
    

    @Test
    public void testBigCycle() {
        // First create a graph that starts with a single parent, and expands
        // out 2x each layer.
        int curId = 0;
        CategoryGraph graph = new CategoryGraph();
        Category headCat = new Category("cat-" + curId++);
        graph.add(headCat);
        
        for (int depth = 1; depth < 4; depth++) {
            for (Category terminal : graph.findTails()) {
                Set<Category> parents = new HashSet<>();
                parents.add(terminal);
                
                // Add two children
                graph.add(new Category("cat-" + curId++, parents));
                graph.add(new Category("cat-" + curId++, parents));
            }
        }
        
        assertFalse(graph.hasCycles());
        
        // Now set the head category's parent to be one of the terminals.
        Set<Category> headParent = new HashSet<>();
        headParent.add(graph.getCategory(new Category("cat-" + (curId - 1))));
        headCat.setParents(headParent);
        
        assertTrue(graph.hasCycles());
        
        assertEquals(1, graph.breakCycles());
        assertFalse(graph.hasCycles());
    }

    @Test
    public void testWritable() throws Exception {
        // First create a graph that starts with a single parent, and expands
        // out 2x each layer.
        int curId = 0;
        CategoryGraph graph = new CategoryGraph();
        Category headCat = new Category("cat-" + curId++);
        graph.add(headCat);
        
        for (int depth = 1; depth < 4; depth++) {
            for (Category terminal : graph.findTails()) {
                Set<Category> parents = new HashSet<>();
                parents.add(terminal);
                
                // Add two children
                graph.add(new Category("cat-" + curId++, parents));
                graph.add(new Category("cat-" + curId++, parents));
            }
        }

        int graphSize = graph.size();
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(baos);
        graph.write(out);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInput in = new DataInputStream(bais);
        CategoryGraph graph2 = new CategoryGraph();
        graph2.readFields(in);
        
        Iterator<Category> iter1 = graph.iterator();
        Iterator<Category> iter2 = graph2.iterator();
        while (iter1.hasNext()) {
            assertTrue(iter2.hasNext());
            assertTrue(iter1.next().equalsWithParents(iter2.next()));
        }
        
    }
    

}

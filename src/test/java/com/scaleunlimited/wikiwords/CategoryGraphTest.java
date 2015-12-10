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
import java.util.LinkedList;
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
        
        assertEquals(3, graph.size());
    }

    @Test
    public void testGetCategory() {
        CategoryGraph graph = new CategoryGraph();
        Category cat2 = new Category("cat2");
        graph.add(cat2);
        
        assertEquals(cat2, graph.get("cat2"));
        assertNull(graph.get("cat1"));
    }
    
    @Test
    public void testFindingParents() throws Exception {
        // First create a graph that starts with a single parent, and expands
        // out 2x each layer.
        CategoryGraph graph = makeBinaryGraph(4);
        
        assertEquals(makeSet("cat-0"), graph.getTree("cat-0"));
        assertEquals(makeSet("cat-0", "cat-1", "cat-4", "cat-9"), graph.getTree("cat-9"));
        
        // Now let's add a cycle. We'll link cat-9 to cat-8, thus also adding in cat-3.
        graph.get("cat-9").getParents().add(graph.get("cat-8"));
        assertEquals(makeSet("cat-0", "cat-1", "cat-4", "cat-9", "cat-8", "cat-3"), graph.getTree("cat-9"));
    }
    
    private Set<String> makeSet(String... strings) {
        Set<String> result = new HashSet<>();
        for (String s : strings) {
            result.add(s);
        }
        
        return result;
    }

    @Test
    public void testWritable() throws Exception {
        // First create a graph that starts with a single parent, and expands
        // out 2x each layer.
        CategoryGraph graph = makeBinaryGraph(4);
        
        assertEquals(15, graph.size());
        
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutput out = new DataOutputStream(baos);
        graph.write(out);
        
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        DataInput in = new DataInputStream(bais);
        CategoryGraph graph2 = new CategoryGraph();
        graph2.readFields(in);
        
        assertEquals(graph, graph2);
    }

    private CategoryGraph makeBinaryGraph(int maxDepth) {
        int curId = 0;
        CategoryGraph graph = new CategoryGraph();
        Category headCat = new Category("cat-" + curId++);
        graph.add(headCat);
        
        LinkedList<Category> inQueue = new LinkedList<>();
        inQueue.add(headCat);

        LinkedList<Category> outQueue = new LinkedList<>();

        for (int depth = 1; depth < maxDepth; depth++) {
            while (!inQueue.isEmpty()) {
                Set<Category> parents = new HashSet<>();
                parents.add(inQueue.poll());
                
                // Add two children
                Category leftChild = new Category("cat-" + curId++, parents);
                graph.add(leftChild);
                outQueue.add(leftChild);
                
                Category rightChild = new Category("cat-" + curId++, parents);
                graph.add(rightChild);
                outQueue.add(rightChild);
            }
            
            // Swap queues and continue.
            LinkedList<Category> savedQueue = inQueue;
            inQueue = outQueue;
            outQueue = savedQueue;
        }
        
        return graph;
    }
    

}

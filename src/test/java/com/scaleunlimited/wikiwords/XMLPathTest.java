package com.scaleunlimited.wikiwords;

import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

public class XMLPathTest {

    @Test
    public void test() {
        XMLPath path = new XMLPath();
        
        // With empty path, we're at the top
        assertTrue(path.atNode("/"));
        assertTrue(path.inNode("/"));
        
        // But we're not at or in any other path
        assertFalse(path.atNode("/one"));
        assertFalse(path.atNode("one"));
        assertFalse(path.inNode("/one"));
        assertFalse(path.inNode("one"));

        // Throw exception with empty stack
        try {
            path.popNode("blah");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }
        
        path.pushNode("one");
        // path is now "/one"
        assertTrue(path.atNode("/one"));
        assertTrue(path.atNode("/one/"));
        assertTrue(path.atNode("one"));
        assertTrue(path.atNode("one/"));
        
        assertTrue(path.inNode("/one"));
        assertTrue(path.inNode("/one/"));
        assertTrue(path.inNode("one"));
        assertTrue(path.inNode("one/"));
        
        assertFalse(path.atNode("/one/two"));
        
        // Ignore slash at end
        
        // Don't allow an empty path element.
        try {
            path.atNode("");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }
        
        try {
            path.atNode("xxx//yyy");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }
        
        try {
            path.atNode("//yyy");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }
        
        try {
            path.atNode("xxx/yyy//");
            fail("Should throw exception");
        } catch (Exception e) {
            // expected
        }
    }

}

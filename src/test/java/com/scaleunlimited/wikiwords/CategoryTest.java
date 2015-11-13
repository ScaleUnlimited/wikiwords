package com.scaleunlimited.wikiwords;

import static org.junit.Assert.*;

import java.util.HashSet;

import org.junit.Test;

public class CategoryTest {

    @Test
    public void testToString() {
        Category cat1 = new Category("cat1");
        System.out.println(cat1);
        Category cat2 = new Category("cat2", Category.makeSet());
        System.out.println(cat2);
        System.out.println(new Category("cat3", Category.makeSet(cat1)));
        System.out.println(new Category("cat4", Category.makeSet(cat2)));
        System.out.println(new Category("cat5", Category.makeSet(cat1, cat2)));
        
        Category cat6 = new Category("cat6");
        Category cat7 = new Category("cat7", Category.makeSet(cat6));
        cat6.setParents(Category.makeSet(cat7));
        System.out.println(cat6);
        System.out.println(cat7);
    }

}

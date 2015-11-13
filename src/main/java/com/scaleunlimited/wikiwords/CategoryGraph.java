package com.scaleunlimited.wikiwords;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.apache.hadoop.io.Writable;
import org.apache.log4j.Logger;

public class CategoryGraph implements Writable, Iterable<Category> {
    private static final Logger LOGGER = Logger.getLogger(CategoryGraph.class);

    private SortedSet<Category> _categories;

    public CategoryGraph() {
        _categories = new TreeSet<>();
    }
    
    public int size() {
        return _categories.size();
    }
    
    public void add(Category category) {
        if (_categories.contains(category)) {
            throw new IllegalArgumentException("Category already exists: " + category.getName());
        }
        
        _categories.add(category);
    }
    
    public Category getCategory(Category category) {
        SortedSet<Category> tailSet = _categories.tailSet(category);
        if (tailSet.isEmpty()) {
            return  null;
        } else {
            Category result = tailSet.first();
            if (result.equals(category)) {
                return result;
            } else {
                return null;
            }
        }
    }

    public Set<Category> findTails() {
        unmarkAll();
        
        for (Category category : _categories) {
            // If it's already marked, then we've processed
            // it (and its parents), so don't bother.
            if (!category.isMarked()) {
                for (Category parent : category.getParents()) {
                    markTree(parent);
                }
            }
        }
        
        Set<Category> result = new HashSet<>();
        for (Category category : _categories) {
            if (!category.isMarked()) {
                result.add(category);
            }
        }
        
        return result;
    }

    public boolean hasCycles() {
        for (Category category : _categories) {
            unmarkAll();
            
            if (markTree(category)) {
                return true;
            }
        }
        
        return false;
    }
    
    public int breakCycles() {
        int numCycles = 0;
        unmarkAll();
        
        for (Category category : _categories) {
            if (category.hasParents()) {
                int numBroken = breakCycle(category);
                if (numBroken > 0) {
                    numCycles += numBroken;
                    LOGGER.info(String.format("Broken %d cycles starting from %s", numBroken, category.getName()));
                }
                
                unmark(category);
            }
        }
        
        return numCycles;
    }
    
    private int breakCycle(Category category) {
        int numCycles = 0;
        
        category.setMarked(true);
        List<Category> parents = new ArrayList<>(category.getParents());
        for (Category parent : parents) {
            if (parent.isMarked()) {
                category.getParents().remove(parent);
                numCycles += 1;
            } else {
                numCycles += breakCycle(parent);
            }
        }
        
        return numCycles;
    }
    
    private void unmarkAll() {
        for (Category category : _categories) {
            category.setMarked(false);
        }        
    }
    
    private void unmark(Category category) {
        category.setMarked(false);
        for (Category parent : category.getParents()) {
            unmark(parent);
        }
    }
    
    /**
     * Mark the category, and all parents.
     * 
     * @param category
     * @return true if category was already marked
     */
    private boolean markTree(Category category) {
        if (!category.isMarked()) {
            category.setMarked(true);
            for (Category parent : category.getParents()) {
                if (markTree(parent)) {
                    return true;
                }
            }
            
            return false;
        } else {
            return true;
        }
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        int numCategories = in.readInt();
        for (int i = 0; i < numCategories; i++) {
            Category c = new Category();
            c.readFields(in);
            add(c);
        }
        
        // Now we have to resolve all parents, as these currently aren't the
        // actual category objects at the top level.
        List<Category> oldParents = new ArrayList<>();
        for (Category category : _categories) {
           Set<Category> parents = category.getParents();
           
           oldParents.clear();
           oldParents.addAll(parents);
           parents.clear();
           
           for (Category parent : oldParents) {
               Category realParent = getCategory(parent);
               
               // Shouldn't happen, but be safe.
               if (realParent != null) {
                   parents.add(realParent);
               }
           }
        }
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(_categories.size());
        for (Category category : _categories) {
            category.write(out);
        }
    }

    @Override
    public Iterator<Category> iterator() {
        return _categories.iterator();
    }

}

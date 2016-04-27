package com.scaleunlimited.wikiwords;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.hadoop.io.Writable;

public class CategoryGraph implements Writable {

    private Map<String, Category> _categories;

    public CategoryGraph() {
        _categories = new HashMap<>();
    }
    
    public int size() {
        return _categories.size();
    }
    
    public void add(Category category) {
        if (_categories.containsKey(category.getName())) {
            throw new IllegalArgumentException("Category already exists: " + category.getName());
        }
        
        _categories.put(category.getName(), category);
    }
    
    public Category get(String categoryName) {
        return _categories.get(categoryName);
    }
    
    public Set<Category> getChildren(Category category) {
        Set<Category> result = new HashSet<>();
        for (Category cat : _categories.values()) {
            for (Category parent : cat.getParents()) {
                if (parent == category) {
                    result.add(cat);
                    break;
                }
            }
        }

        return result;
    }
    
    public boolean exists(String categoryName) {
        return _categories.containsKey(categoryName);
    }
    
    public Set<String> getTree(String leafName) {
        return getTree(leafName, Integer.MAX_VALUE);
    }

    public Set<String> getTree(String leafName, int maxDepth) {
        Category leaf = get(leafName);
        if (leaf == null) {
            throw new IllegalArgumentException("No category in the graph named " + leafName);
        }
        
        return getTree(leaf, maxDepth);
    }

    /**
     * Return the names of all categories, starting from <leaf> on up.
     * 
     * @param leaf Starting category
     * @return All category names in hierarchy.
     */
    public Set<String> getTree(Category leaf) {
        return getTree(leaf, Integer.MAX_VALUE);
    }
    
    public CategoryGraph invertGraph() {
        CategoryGraph inverted = new CategoryGraph();
        for (Category cat : _categories.values()) {
            Category newCat = inverted.get(cat.getName());
            if (newCat == null) {
                newCat = new Category(cat.getName());
                inverted.add(newCat);
            }
            
            for (Category parent : cat.getParents()) {
                if (inverted.exists(parent.getName())) {
                    inverted.get(parent.getName()).addParent(newCat);
                } else {
                    inverted.add(new Category(parent.getName(), newCat));
                }
            }
        }
        
        return inverted;
    }
    
    /**
     * Return the names of all categories, starting from <leaf> on up.
     * 
     * @param leaf Starting category
     * @return All category names in hierarchy.
     */
    public Set<String> getTree(Category leaf, int maxDepth) {
        Set<String> result = new HashSet<>();
        
        LinkedList<Category> queue = new LinkedList<>();
        leaf.setDepth(0);
        queue.push(leaf);
        
        while (!queue.isEmpty()) {
            Category cat = queue.pop();
            int catDepth = cat.getDepth();
            // Not too deep, and we haven't already processed it, so push parents.
            if ((catDepth < maxDepth) && result.add(cat.getName())) {
                for (Category parent : cat.getParents()) {
                    parent.setDepth(catDepth + 1);
                    queue.add(parent);

                }
            }            
        }
        
        return result;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_categories == null) ? 0 : _categories.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        CategoryGraph other = (CategoryGraph) obj;
        if (_categories == null) {
            if (other._categories != null)
                return false;
        } else if (!_categories.equals(other._categories))
            return false;
        return true;
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        int numCategories = in.readInt();
        for (int i = 0; i < numCategories; i++) {
            String categoryName = in.readUTF();
            int numParents = in.readInt();
            @SuppressWarnings("unchecked")
            Set<Category> parents = numParents == 0 ? (Set<Category>)Collections.EMPTY_SET : new HashSet<Category>();
            for (int j = 0; j < numParents; j++) {
                String parentName = in.readUTF();
                if (_categories.containsKey(parentName)) {
                    parents.add(_categories.get(parentName));
                } else {
                    Category parent = new Category(parentName);
                    parents.add(parent);
                    _categories.put(parent.getName(), parent);
                }
            }
            
            if (_categories.containsKey(categoryName)) {
                _categories.get(categoryName).setParents(parents);
            } else {
                _categories.put(categoryName, new Category(categoryName, parents));
            }
        }
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(_categories.size());
        for (Category category : _categories.values()) {
            out.writeUTF(category.getName());
            Set<Category> parents = category.getParents();
            out.writeInt(parents.size());
            for (Category parent : parents) {
                out.writeUTF(parent.getName());
            }
        }
    }

}

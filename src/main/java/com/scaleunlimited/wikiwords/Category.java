package com.scaleunlimited.wikiwords;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.apache.hadoop.io.Writable;

public class Category implements Comparable<Category>, Writable {

    private String _name;
    private Set<Category> _parents;
    
    private transient int _depth;
    
    public Category() {
        this("");
    }
    
    public Category(String name) {
        this(name, (Set<Category>)Collections.EMPTY_SET);
    }
    
    public Category(String name, Set<Category> parents) {
        _name = name;
        _parents = parents;
    }

    public String getName() {
        return _name;
    }

    public void setName(String name) {
        _name = name;
    }
    
    public int getDepth() {
        return _depth;
    }
    
    public void setDepth(int depth) {
        _depth = depth;
    }
    
    public boolean hasParents() {
        return _parents != null && !_parents.isEmpty();
    }
    
    public Set<Category> getParents() {
        return _parents == null ? (Set<Category>)Collections.EMPTY_SET : _parents;
    }

    public void setParents(Set<Category> parents) {
        _parents = parents;
    }
    
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((_name == null) ? 0 : _name.hashCode());
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
        Category other = (Category) obj;
        if (_name == null) {
            if (other._name != null)
                return false;
        } else if (!_name.equals(other._name))
            return false;
        return true;
    }
    
    public boolean equalsWithParents(Category o) {
        if (!equals(o)) {
            return false;
        }
        
        Set<Category> myParents = getParents();
        Set<Category> oParents = o.getParents();
        if (myParents.size() != oParents.size()) {
            return false;
        }
        
        for (Category myParent : myParents) {
            if (!oParents.contains(myParent)) {
                return false;
            }
        }
        
        return true;
    }
    
    @Override
    public String toString() {
        return String.format("%s: %s", _name, makeParentString());
    }
    
    private String makeParentString() {
        if (_parents == null) {
            return "[]";
        } else {
            StringBuilder result = new StringBuilder("[");
            boolean firstParent = true;
            for (Category parent : _parents) {
                if (!firstParent) {
                    result.append(", ");
                } else {
                    firstParent = false;
                }
                
                result.append(parent.getName());
            }
            
            result.append("]");
            return result.toString();
        }
    }
    
    public static Set<Category> makeSet(Category... categories) {
        if (categories == null) {
            return Collections.EMPTY_SET;
        } else {
            Set<Category> result = new HashSet<>(categories.length);
            for (Category category : categories) {
                result.add(category);
            }
            return result;
        }
    }

    @Override
    public int compareTo(Category o) {
        return _name.compareTo(o._name);
    }

    @Override
    public void readFields(DataInput in) throws IOException {
        _name = in.readUTF();
        int numParents = in.readInt();
        Set<Category> parents = new HashSet<>(numParents);
        for (int i = 0; i < numParents; i++) {
            // NOTE - these parents will need to be resolved to categories in
            // the graph post-deserialization.
            parents.add(new Category(in.readUTF()));
        }
        
        _parents = parents;
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeUTF(_name);
        out.writeInt(getParents().size());
        for (Category parent : getParents()) {
            out.writeUTF(parent.getName());
        }
    }
}

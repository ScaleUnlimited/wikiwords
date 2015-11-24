package com.scaleunlimited.wikiwords;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.hadoop.io.Writable;

public class ArticleToCategoriesMap implements Writable {

    private Map<String, Set<String>> _articles;
    
    public ArticleToCategoriesMap() {
        _articles = new HashMap<>();
    }
    
    public boolean put(String article, Set<String> categories) {
        return _articles.put(article, categories) != null;
    }

    public boolean containsKey(String article) {
        return _articles.containsKey(article);
    }
    
    public Set<String> get(String article) {
        return _articles.get(article);
    }
    
    @Override
    public void readFields(DataInput in) throws IOException {
        _articles.clear();
        
        int numEntries = in.readInt();
        for (int i = 0; i < numEntries; i++) {
            String article = in.readUTF();
            Set<String> categories = new HashSet<>();
            int numCategories = in.readInt();
            for (int j = 0; j < numCategories; j++) {
                categories.add(in.readUTF());
            }
            
            _articles.put(article, categories);
        }
    }

    @Override
    public void write(DataOutput out) throws IOException {
        out.writeInt(_articles.size());
        for (Entry<String, Set<String>> entry : _articles.entrySet()) {
            out.writeUTF(entry.getKey());
            out.writeInt(entry.getValue().size());
            for (String category : entry.getValue()) {
                out.writeUTF(category);
            }
        }
    }

    public int size() {
        return _articles.size();
    }

}

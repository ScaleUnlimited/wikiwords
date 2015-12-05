package com.scaleunlimited.wikiwords.datum;

import java.lang.reflect.Type;

import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

import com.scaleunlimited.cascading.BaseDatum;

@SuppressWarnings("serial")
public class WikiCategoryDatum extends BaseDatum {

    public static final String ARTICLE_NAME_FN = fieldName(WikiCategoryDatum.class, "articleName");
    public static final String CATEGORY_FN = fieldName(WikiCategoryDatum.class, "category");
    
    private static final Comparable<?>[] FIELD_NAMES = {
        ARTICLE_NAME_FN, 
        CATEGORY_FN
    };
    
    private static final Type[] FIELD_TYPES = {
        String.class, 
        String.class,
    };
    
    public static final Fields FIELDS = new Fields (FIELD_NAMES, FIELD_TYPES);


    public WikiCategoryDatum() {
        super(FIELDS);
    }

    public WikiCategoryDatum(Fields fields) {
        super(fields);
    }

    public WikiCategoryDatum(TupleEntry tupleEntry) {
        super(tupleEntry);
    }

    public WikiCategoryDatum(Fields fields, Tuple tuple) {
        super(fields, tuple);
    }
    
    public WikiCategoryDatum(String article, String category) {
        super(FIELDS);
        
        setArticle(article);
        setCategory(category);
    }
    
    public void setArticle(String article) {
        _tupleEntry.setString(ARTICLE_NAME_FN, article);
    }
    
    public String getArticle() {
        return _tupleEntry.getString(ARTICLE_NAME_FN);
    }
    
    public void setCategory(String category) {
        _tupleEntry.setString(CATEGORY_FN, category);
    }
    
    public String getCategory() {
        return _tupleEntry.getString(CATEGORY_FN);
    }
    
}

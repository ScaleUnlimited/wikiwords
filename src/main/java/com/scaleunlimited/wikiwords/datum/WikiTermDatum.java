package com.scaleunlimited.wikiwords.datum;

import java.lang.reflect.Type;

import cascading.tuple.Fields;
import cascading.tuple.Tuple;
import cascading.tuple.TupleEntry;

import com.scaleunlimited.cascading.BaseDatum;

@SuppressWarnings("serial")
public class WikiTermDatum extends BaseDatum {

    public static final String ARTICLE_NAME_FN = fieldName(WikiTermDatum.class, "articleName");
    public static final String TERM_FN = fieldName(WikiTermDatum.class, "term");
    public static final String ARTICLE_REF_FN = fieldName(WikiTermDatum.class, "articleRef");
    public static final String TERM_DISTANCE_FN = fieldName(WikiTermDatum.class, "termDistance");
    
    private static final Comparable<?>[] FIELD_NAMES = {
        ARTICLE_NAME_FN, 
        TERM_FN, 
        ARTICLE_REF_FN,
        TERM_DISTANCE_FN
    };
    
    private static final Type[] FIELD_TYPES = {
        String.class, 
        String.class, 
        String.class, 
        int.class, 
    };
    
    public static final Fields FIELDS = new Fields (FIELD_NAMES, FIELD_TYPES);


    public WikiTermDatum() {
        super(FIELDS);
    }

    public WikiTermDatum(Fields fields) {
        super(fields);
    }

    public WikiTermDatum(TupleEntry tupleEntry) {
        super(tupleEntry);
    }

    public WikiTermDatum(Fields fields, Tuple tuple) {
        super(fields, tuple);
    }
    
    public WikiTermDatum(String article, String term, String articleRef, int distance) {
        super(FIELDS);
        
        setArticle(article);
        setTerm(term);
        setArticleRef(articleRef);
        setDistance(distance);
    }
    
    public void setArticle(String article) {
        _tupleEntry.setString(ARTICLE_NAME_FN, article);
    }
    
    public String getArticle() {
        return _tupleEntry.getString(ARTICLE_NAME_FN);
    }
    
    public void setTerm(String term) {
        _tupleEntry.setString(TERM_FN, term);
    }
    
    public String getTerm() {
        return _tupleEntry.getString(TERM_FN);
    }
    
    public void setArticleRef(String articleRef) {
        _tupleEntry.setString(ARTICLE_REF_FN, articleRef);
    }
    
    public String getArticleRef() {
        return _tupleEntry.getString(ARTICLE_REF_FN);
    }
    
    public void setDistance(int distance) {
        _tupleEntry.setInteger(TERM_DISTANCE_FN, distance);
    }

    public int getDistance() {
        return _tupleEntry.getInteger(TERM_DISTANCE_FN);
    }
}

package com.scaleunlimited.wikiwords;

public enum WikiwordsCounters {

    HTML_PARSE_ERROR,   // Number of articles we failed to parse.
    HTML_PARSE,         // Number of articles we parsed successfully
    
    WIKITERM,           // Number of article ref/term pairs we generated.
    ARTICLES            // Number of articles read by GenerateTerms
}

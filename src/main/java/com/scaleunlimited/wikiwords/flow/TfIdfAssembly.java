package com.scaleunlimited.wikiwords.flow;

import cascading.operation.aggregator.Count;
import cascading.operation.expression.ExpressionFilter;
import cascading.operation.expression.ExpressionFunction;
import cascading.pipe.CoGroup;
import cascading.pipe.Each;
import cascading.pipe.Every;
import cascading.pipe.GroupBy;
import cascading.pipe.HashJoin;
import cascading.pipe.Pipe;
import cascading.pipe.SubAssembly;
import cascading.pipe.assembly.CountBy;
import cascading.pipe.assembly.Discard;
import cascading.pipe.assembly.Rename;
import cascading.pipe.assembly.Retain;
import cascading.pipe.assembly.SumBy;
import cascading.pipe.assembly.Unique;
import cascading.tuple.Fields;

/**
 * Cascading sub-assembly that generates TF*IDF values for every unique term.
 * 
 * The <termsPipe> passed to the constructor must contain tuples with the following fields:
 * 
 *  - a "doc" field, which is a string with a document identifier.
 *  - a "term" field, which is a string.
 *  - a "termcount" field, which is an integer.
 *  
 * The output is a pipe that contains tuples with the following fields:
 * 
 *  - a "term" field, which is a string
 *  - a "tf-idf" field, which is a float.
 *
 */
@SuppressWarnings("serial")
public class TfIdfAssembly extends SubAssembly {

    public static final String DOC_FN = "doc";
    public static final String TERM_FN = "term";
    public static final String TERM_COUNT_FN = "term_count";
    
    // Output fields
    public static final String TF_IDF_FN = "tf-idf";
    public static final String TERM_COUNT_PER_DOC_FN = "term_count_per_doc";

    // Intermediate fields
    private static final String TOTAL_TERM_COUNT_PER_DOC_FN = "total_term_count_per_doc";
    private static final String TEMP_DOC_FN = "temp_doc";
    private static final String TEMP_TERM_FN = "temp_term";
    
    private static final String TF_FN = "tf";
    private static final String IDF_FN = "idf";
    private static final String DOC_COUNT_PER_TERM_FN = "doc_count_per_term";
    private static final String TOTAL_DOC_COUNT_FN = "total_doc_count";
    
    public TfIdfAssembly(Pipe termsPipe, int minTermDocCount) {
        super(termsPipe);
        
        // For each term, we need to get a per-document count.
        Pipe termCountPerDocPipe = new Pipe("term count per doc pipe", termsPipe);
        
        termCountPerDocPipe = new SumBy(termCountPerDocPipe, 
                                        new Fields(DOC_FN, TERM_FN), 
                                        new Fields(TERM_COUNT_FN), 
                                        new Fields(TERM_COUNT_PER_DOC_FN), 
                                        Integer.class);
        // Output is DOC_FN, TERM_FN, TERM_COUNT_PER_DOC_FN
        
        // If the number of times the term occurs with the doc is too low, strip it out.
        if (minTermDocCount > 0) {
            termCountPerDocPipe = new Each( termCountPerDocPipe,
                                            new Fields(TERM_COUNT_PER_DOC_FN),
                                            new ExpressionFilter(String.format("$0 < %d",  minTermDocCount), Integer.class));
        }
        
        // For each doc, we need to know the total # of terms too.
        Pipe totalCountPerDocPipe = new Pipe("total term count per doc pipe", termCountPerDocPipe);
        totalCountPerDocPipe = new SumBy(totalCountPerDocPipe,
                                        new Fields(DOC_FN),
                                        new Fields(TERM_COUNT_PER_DOC_FN),
                                        new Fields(TOTAL_TERM_COUNT_PER_DOC_FN),
                                        Integer.class);
        totalCountPerDocPipe = new Rename(totalCountPerDocPipe, new Fields(DOC_FN), new Fields(TEMP_DOC_FN));
        // Output is TEMP_DOC_FN, TOTAL_TERM_COUNT_PER_DOC_FN
        
        // Now we can calculate the TF, by joining our two pipes on DOC_FN
        Pipe tfPipe = new CoGroup(  "term frequency pipe",
                                    termCountPerDocPipe,
                                    new Fields(DOC_FN),
                                    totalCountPerDocPipe,
                                    new Fields(TEMP_DOC_FN));
        tfPipe = new Discard(tfPipe, new Fields(TEMP_DOC_FN));
        // Output is DOC_FN, TERM_FN, TERM_COUNT_PER_DOC_FN, TOTAL_TERM_COUNT_PER_DOC_FN
        
        // Use Fields.ALL so we keep TERM_COUNT_PER_DOC_FN, and then toss TOTAL_TERM_COUNT_PER_DOC_FN
        tfPipe = new Each(tfPipe, new Fields(TERM_COUNT_PER_DOC_FN, TOTAL_TERM_COUNT_PER_DOC_FN), new ExpressionFunction(new Fields(TF_FN), "(float)$0/(float)$1", Float.class), Fields.ALL);
        tfPipe = new Discard(tfPipe, new Fields(TOTAL_TERM_COUNT_PER_DOC_FN));
        
        // Convert to Lucene-style TF value (square root of TF)
        tfPipe = new Each(tfPipe, new Fields(TF_FN), new ExpressionFunction(new Fields(TF_FN), "(float)Math.sqrt((double)$0)", Float.class), Fields.REPLACE);
        // Output is DOC_FN, TERM_FN, TF_FN, TERM_COUNT_PER_DOC_FN
        
        // Now for each term we need an IDF (inverse doc frequency).
        // TODO split this off of termCountPerDocPipe after first SumBy, as that already has unique term/doc results.
        Pipe docCountPerTermPipe = new Pipe("doc count per term pipe", termsPipe);
        docCountPerTermPipe = new Retain(docCountPerTermPipe, new Fields(DOC_FN, TERM_FN));
        docCountPerTermPipe = new Unique(docCountPerTermPipe, new Fields(DOC_FN, TERM_FN));
        
        // Split this off after the unique, as that's the reduced data set we want for the total doc count
        // Use it to calculate the total number of docs. GroupBy(Fields.NONE) gives us a single group that
        // we can count up.
        Pipe totalDocCountPipe = new Pipe("total doc count pipe", docCountPerTermPipe);
        totalDocCountPipe = new Unique(totalDocCountPipe, new Fields(DOC_FN));
        totalDocCountPipe = new GroupBy(totalDocCountPipe, Fields.NONE);
        totalDocCountPipe = new Every(totalDocCountPipe, new Count(new Fields(TOTAL_DOC_COUNT_FN)));
        // Output is a single record with TOTAL_DOC_COUNT_FN
        
        // Now get the number of docs per term
        docCountPerTermPipe = new CountBy(docCountPerTermPipe, new Fields(TERM_FN), new Fields(DOC_COUNT_PER_TERM_FN));
        // Output is TERM_FN, DOC_COUNT_PER_TERM_FN
        
        // We can calculate the actual IDF, equal to 1 + log(total docs/(term docs + 1))
        // Pipe idfPipe = new HashJoin(docCountPerTermPipe, Fields.NONE, totalDocCountPipe, Fields.NONE);
        Pipe idfPipe = new CoGroup(docCountPerTermPipe, Fields.NONE, totalDocCountPipe, Fields.NONE);

        
        idfPipe = new Each(idfPipe, new Fields(TOTAL_DOC_COUNT_FN, DOC_COUNT_PER_TERM_FN), new ExpressionFunction(new Fields(IDF_FN), "1 + Math.log((double)$0/($1 + 1))", Float.class), Fields.SWAP);
        idfPipe = new Rename(idfPipe, new Fields(TERM_FN), new Fields(TEMP_TERM_FN));
        // Output is TEMP_TERM_FN, IDF_FN
        
        // Finally we can calculate TF*IDF. We might have lots of unique terms, so we'll do a regular CoGroup
        Pipe tfIdfPipe = new CoGroup(tfPipe, new Fields(TERM_FN), idfPipe, new Fields(TEMP_TERM_FN));
        tfIdfPipe = new Discard(tfIdfPipe, new Fields(TEMP_TERM_FN));
        tfIdfPipe = new Each(tfIdfPipe, new Fields(TF_FN, IDF_FN), new ExpressionFunction(new Fields(TF_IDF_FN), "$0 * $1", Float.class), Fields.SWAP);
        // Output is TERM_FN, DOC_FN, TF_IDF_FN, TERM_COUNT_PER_DOC_FN

        setTails(tfIdfPipe);
    }
}

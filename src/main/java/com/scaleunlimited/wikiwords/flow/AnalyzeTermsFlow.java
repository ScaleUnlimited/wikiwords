package com.scaleunlimited.wikiwords.flow;

import org.apache.log4j.Logger;

import cascading.flow.Flow;
import cascading.flow.FlowDef;
import cascading.operation.Debug;
import cascading.operation.DebugLevel;
import cascading.operation.aggregator.First;
import cascading.operation.expression.ExpressionFilter;
import cascading.operation.expression.ExpressionFunction;
import cascading.pipe.CoGroup;
import cascading.pipe.Each;
import cascading.pipe.Every;
import cascading.pipe.GroupBy;
import cascading.pipe.Pipe;
import cascading.pipe.assembly.CountBy;
import cascading.pipe.assembly.Rename;
import cascading.pipe.assembly.Retain;
import cascading.pipe.assembly.SumBy;
import cascading.pipe.assembly.Unique;
import cascading.tap.SinkMode;
import cascading.tap.Tap;
import cascading.tuple.Fields;

import com.scaleunlimited.cascading.BasePath;
import com.scaleunlimited.cascading.BasePlatform;
import com.scaleunlimited.wikiwords.WikiwordsCounters;
import com.scaleunlimited.wikiwords.WorkingConfig;
import com.scaleunlimited.wikiwords.datum.WikiCategoryDatum;
import com.scaleunlimited.wikiwords.datum.WikiTermDatum;
import com.scaleunlimited.wikiwords.tools.AnalyzeTermsTool.AnalyzeTermsOptions;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool;

public class AnalyzeTermsFlow {
    private static final Logger LOGGER = Logger.getLogger(AnalyzeTermsFlow.class);

    public AnalyzeTermsFlow() {
        // TODO Auto-generated constructor stub
    }
    
    public static Flow createFlow(AnalyzeTermsOptions options) throws Exception {
        
        
        BasePlatform platform = options.getPlatform(AnalyzeTermsFlow.class);
        FlowDef flowDef = new FlowDef()
            .setName("analyze terms")
            .setDebugLevel(options.isDebug() ? DebugLevel.VERBOSE : DebugLevel.NONE);
        
        // We're reading in files generated by GenerateTermsFlow, which contain WikiTermDatum records
        BasePath termPath = options.getWorkingSubdirPath(WorkingConfig.TERMS_SUBDIR_NAME);
        Tap termTap = platform.makeTap(platform.makeBinaryScheme(WikiTermDatum.FIELDS), termPath, SinkMode.KEEP);
        Pipe terms = new Pipe("terms");
        // terms = new Each(terms, DebugLevel.VERBOSE, new Debug("terms", true));
        flowDef.addSource(terms, termTap);
        
        BasePath categoryPath = options.getWorkingSubdirPath(WorkingConfig.CATEGORIES_SUBDIR_NAME);
        Tap categoryTap = platform.makeTap(platform.makeBinaryScheme(WikiCategoryDatum.FIELDS), categoryPath, SinkMode.KEEP);
        Pipe categories = new Pipe("categories");
        categories = new Each(categories, DebugLevel.VERBOSE, new Debug("categories", true));
        flowDef.addSource(categories, categoryTap);
        
        // Calculate the DF for each term.
        /*
        Pipe termDF = new Pipe("term DF", p);
        termDF = new Retain(termDF, new Fields(WikiTermDatum.TERM_FN, WikiTermDatum.ARTICLE_NAME_FN));
        termDF = new Unique(termDF, new Fields(WikiTermDatum.TERM_FN, WikiTermDatum.ARTICLE_NAME_FN));
        termDF = new CountBy(termDF, new Fields(WikiTermDatum.TERM_FN), new Fields("num_articles"));
        // termDF = new Each(termDF, DebugLevel.VERBOSE, new Debug("docs per term", true));
        
        termDF = new GroupBy(termDF, Fields.NONE, new Fields("num_articles"), true);
        termDF = new Each(termDF, new Fields("num_articles"), new ExpressionFunction(new Fields("df"), "(float)num_articles / " + options.getTotalArticles(), Float.class), Fields.SWAP);
        Tap termDFSink = platform.makeTap(platform.makeTextScheme(), options.getWorkingSubdirPath(WorkingConfig.TERMDF_SUBDIR_NAME), SinkMode.REPLACE);
        flowDef.addTailSink(termDF, termDFSink);
        */
        
        // Calculate the TF*IDF value for term/article ref pairs.
        Pipe termTFIDF = new Pipe("term TF*IDF pipe", terms);
        termTFIDF = new Retain(termTFIDF, new Fields(WikiTermDatum.TERM_FN, WikiTermDatum.ARTICLE_REF_FN, WikiTermDatum.TERM_DISTANCE_FN));
        // Our "term count" could be based on distance (e.g. Math.max(1, 10-$0)), or a step function (e.g. ($0 > 10 ? 0 : 1))
        termTFIDF = new Each(termTFIDF, new Fields(WikiTermDatum.TERM_DISTANCE_FN), new ExpressionFunction(new Fields(TfIdfAssembly.TERM_COUNT_FN), "($0 > 10 ? 0 : 1)", Integer.class), Fields.SWAP);
        termTFIDF = new Each(termTFIDF, new Fields(TfIdfAssembly.TERM_COUNT_FN), new ExpressionFilter("$0 == 0", Integer.class));
        
        termTFIDF = new Rename( termTFIDF,
                                new Fields(WikiTermDatum.TERM_FN, WikiTermDatum.ARTICLE_REF_FN),
                                new Fields(TfIdfAssembly.TERM_FN, TfIdfAssembly.DOC_FN));
        
        termTFIDF = new TfIdfAssembly(termTFIDF, options.getMinArticleRefs());

        // See if the score is below a threshold
        if (options.getMinScore() > 0.0) {
            termTFIDF = new Each(   termTFIDF,
                                    new Fields(TfIdfAssembly.TF_IDF_FN),
                                    new ExpressionFilter(String.format("$0 < %f", options.getMinScore()), Double.class));
        }
        
        // Group by term, sort by score, take the top N, and reorder so terms are first
        Pipe topTerms = new GroupBy("top terms", termTFIDF, new Fields(TfIdfAssembly.TERM_FN), new Fields(TfIdfAssembly.TF_IDF_FN), true);
        topTerms = new Every(topTerms, new First(options.getTopArticleLimit()), Fields.RESULTS);
        topTerms = new Retain(topTerms, new Fields(TfIdfAssembly.TERM_FN, TfIdfAssembly.DOC_FN, TfIdfAssembly.TF_IDF_FN, TfIdfAssembly.TERM_COUNT_PER_DOC_FN));
        
        BasePath topTermsPath = options.getWorkingSubdirPath(WorkingConfig.TERM_SCORES_SUBDIR_NAME);
        Tap topTermsSink = platform.makeTap(platform.makeTextScheme(), topTermsPath, SinkMode.REPLACE);
        flowDef.addTailSink(topTerms, topTermsSink);

        // We're going to also join the term/score data with the term/category data, so we get term/category
        Pipe termCategoryPipe = new CoGroup("term to category",
                                            categories, new Fields(WikiCategoryDatum.ARTICLE_NAME_FN),
                                            termTFIDF, new Fields(TfIdfAssembly.DOC_FN));
        // We've got WikiCategoryDatum.ARTICLE_NAME_FN, WikiCategoryDatum.CATEGORY_FN, TfIdfAssembly.DOC_FN, TfIdfAssembly.TERM_FN, TfIdfAssembly.TF_IDF_FN, TfIdfAssembly.TERM_COUNT_PER_DOC_FN
        termCategoryPipe = new Retain(termCategoryPipe, new Fields(TfIdfAssembly.TERM_FN, WikiCategoryDatum.CATEGORY_FN, TfIdfAssembly.TF_IDF_FN));
        termCategoryPipe = new SumBy(termCategoryPipe, new Fields(TfIdfAssembly.TERM_FN, WikiCategoryDatum.CATEGORY_FN), new Fields(TfIdfAssembly.TF_IDF_FN), new Fields("category_score"), Float.class);
        
        BasePath termCategoryPath = options.getWorkingSubdirPath(WorkingConfig.TERM_CATEGORIES_SUBDIR_NAME);
        Tap termCategorySink = platform.makeTap(platform.makeTextScheme(), termCategoryPath, SinkMode.REPLACE);
        flowDef.addTailSink(termCategoryPipe, termCategorySink);
        
        return platform.makeFlowConnector().connect(flowDef);
    }

}

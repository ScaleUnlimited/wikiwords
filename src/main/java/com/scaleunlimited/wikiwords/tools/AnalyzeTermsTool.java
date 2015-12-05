package com.scaleunlimited.wikiwords.tools;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import cascading.flow.Flow;
import cascading.flow.planner.PlannerException;
import cascading.tuple.Fields;

import com.scaleunlimited.cascading.BaseTool;
import com.scaleunlimited.cascading.FlowResult;
import com.scaleunlimited.cascading.FlowRunner;
import com.scaleunlimited.cascading.FlowUtils;
import com.scaleunlimited.wikiwords.WorkflowOptions;
import com.scaleunlimited.wikiwords.flow.AnalyzeTermsFlow;
import com.scaleunlimited.wikiwords.flow.GenerateTermsFlow;
import com.scaleunlimited.wikiwords.tools.GenerateTermsTool.GenerateTermsOptions;

public class AnalyzeTermsTool extends BaseTool {

    private void run(AnalyzeTermsOptions options) throws Exception {
        Flow flow = AnalyzeTermsFlow.createFlow(options);

        if (options.getDOTFile() != null) {
            flow.writeDOT(getDotFileName(options, "generateterms"));
            flow.writeStepsDOT(getStepDotFileName(options, "generateterms"));
        }
        
        FlowUtils.nameFlowSteps(flow);
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(AnalyzeTermsFlow.class, fr.getCounters());
    }

    public static void main(String[] args) {
        AnalyzeTermsOptions options = new AnalyzeTermsOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        AnalyzeTermsTool tool = new AnalyzeTermsTool();
        
        try {
            tool.run(options);
        } catch (PlannerException e) {
            e.writeDOT("build/failed-flow.dot");
            System.err.println("PlannerException: " + e.getMessage());
            e.printStackTrace(System.err);
            System.exit(-1);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        } catch (Throwable t) {
            System.err.println("Exception running tool: " + t.getMessage());
            t.printStackTrace(System.err);
            System.exit(-1);
        }
    }
    
    public static class AnalyzeTermsOptions extends WorkflowOptions {
        
        private int _topArticleLimit = 20;
        private int _minArticleRefs = 1;
        private double _minScore = 0.0;
        
        public AnalyzeTermsOptions() {
            super();
        }
        
        public AnalyzeTermsOptions(WorkflowOptions baseOptions) {
            super(baseOptions);
        }
        
        @Option(name = "-toparticles", usage = "number of top articles per term to report", required = false)
        public void setTopArticleLimit(int topArticleLimit) {
            _topArticleLimit = topArticleLimit;
        }

        public int getTopArticleLimit() {
            return _topArticleLimit;
        }
        
        @Option(name = "-minrefs", usage = "minimum number of article refs for a term to be considered valid", required = false)
        public void setMinArticleRefs(int minArticleRefs) {
            _minArticleRefs = minArticleRefs;
        }

        public int getMinArticleRefs() {
            return _minArticleRefs;
        }

        @Option(name = "-minscore", usage = "minimum score for term<->article relationship to be valid", required = false)
        public void setMinScore(double minScore) {
            _minScore = minScore;
        }
        
        public double getMinScore() {
            return _minScore;
        }
        
    }
}

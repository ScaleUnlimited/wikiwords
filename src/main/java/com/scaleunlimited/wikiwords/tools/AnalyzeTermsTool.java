package com.scaleunlimited.wikiwords.tools;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import cascading.flow.Flow;
import cascading.flow.planner.PlannerException;

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
        
        private int _totalArticles;
        
        public AnalyzeTermsOptions() {
            super();
        }
        
        // TODO support options for how to convert distance to "term count" (weight)
        // Value at 0, Min value
        
        public AnalyzeTermsOptions(WorkflowOptions baseOptions) {
            super(baseOptions);
        }
        
        @Option(name = "-numarticles", usage = "number of unique Wikipedia articles referenced by links", required = true)
        public void setTotalArticles(int totalArticles) {
            _totalArticles = totalArticles;
        }

        public int getTotalArticles() {
            return _totalArticles;
        }
        
    }
}

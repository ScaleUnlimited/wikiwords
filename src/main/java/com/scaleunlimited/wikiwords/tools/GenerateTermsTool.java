package com.scaleunlimited.wikiwords.tools;

import org.apache.log4j.Logger;
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
import com.scaleunlimited.wikiwords.flow.GenerateTermsFlow;

public class GenerateTermsTool extends BaseTool {
    private static final Logger LOGGER = Logger.getLogger(GenerateTermsTool.class);

    private void run(GenerateTermsOptions options) throws Exception {
        Flow flow = GenerateTermsFlow.createFlow(options);

        if (options.getDOTFile() != null) {
            flow.writeDOT(getDotFileName(options, "generateterms"));
            flow.writeStepsDOT(getStepDotFileName(options, "generateterms"));
        }
        
        FlowUtils.nameFlowSteps(flow);
        FlowResult fr = FlowRunner.run(flow);
        options.saveCounters(GenerateTermsFlow.class, fr.getCounters());
    }

    public static void main(String[] args) {
        GenerateTermsOptions options = new GenerateTermsOptions();
        CmdLineParser parser = new CmdLineParser(options);

        try {
            parser.parseArgument(args);
        } catch (CmdLineException e) {
            System.err.println(e.getMessage());
            printUsageAndExit(parser);
        }

        GenerateTermsTool tool = new GenerateTermsTool();
        
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
    
    public static class GenerateTermsOptions extends WorkflowOptions {
        private String _inputDirname;
        private int _maxDistanceToLink = 100;
        
        public GenerateTermsOptions() {
            super();
        }
        
        @Option(name = "-inputdir", usage = "path to directory containing part-xxx input files", required = true)
        public void setInputDirname(String inputDirname) {
            _inputDirname = inputDirname;
        }

        public String getInputDirname() {
            return _inputDirname;
        }

        @Option(name = "-maxdistance", usage = "maximum # of terms before/after Wiki article link", required = false)
        public void setMaxDistance(int maxDistanceToLink) {
            _maxDistanceToLink = maxDistanceToLink;
        }

        public int getMaxDistanceToLink() {
            return _maxDistanceToLink;
        }

    }
}

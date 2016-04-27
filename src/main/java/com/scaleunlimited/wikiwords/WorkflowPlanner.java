package com.scaleunlimited.wikiwords;

/**
 * Cascading planner used when creating/executing the workflow
 *
 */
public enum WorkflowPlanner {

    LOCAL,
    HADOOP,
    TEZ,
    FLINK
}

package com.scaleunlimited.wikiwords;

import java.io.BufferedWriter;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.kohsuke.args4j.Option;

import com.scaleunlimited.cascading.BaseOptions;
import com.scaleunlimited.cascading.BasePath;
import com.scaleunlimited.cascading.BasePlatform;
import com.scaleunlimited.cascading.hadoop.HadoopPlatform;
import com.scaleunlimited.cascading.local.LocalPlatform;

public abstract class WorkflowOptions extends BaseOptions {

    private String _workingDirname;
    
    private transient BasePlatform _platform;
    
    public WorkflowOptions() {
        super();
    }
    
    public WorkflowOptions(WorkflowOptions baseOptions) {
        // TODO add constructor to BaseOptions that takes BaseOptions
        super();
        
        setDebugLogging(baseOptions.isDebugLogging());

        _workingDirname = baseOptions._workingDirname;
    }
    
    public void setDebug(boolean debug) {
        setDebugLogging(debug);
    }
    
    public boolean isDebug() {
        return isDebugLogging();
    }

    @Option(name = "-workingdir", usage = "working directory path", required = true)
    public void setWorkingDirname(String workingDirname) {
        _workingDirname = workingDirname;
    }

    public String getWorkingDirname() {
        return _workingDirname;
    }

    public BasePlatform getPlatform(Class<?> applicationJarClass) {
        if (_platform == null) {
            if (isDebug()) {
                _platform = new LocalPlatform(applicationJarClass);
            } else {
                _platform = new HadoopPlatform(applicationJarClass);
            }
        }
        
        return _platform;
    }

    public BasePath getWorkingPath() throws Exception {
        BasePlatform platform = getPlatform(WorkflowOptions.class);
        return platform.makePath(getWorkingDirname());
    }
    
    public BasePath getWorkingSubdirPath(String subdirName) throws Exception {
        BasePlatform platform = getPlatform(WorkflowOptions.class);
        BasePath workingPath = platform.makePath(getWorkingDirname());
        return platform.makePath(workingPath, subdirName);
    }
    
    public BasePath saveCounters(Class<?> workflowClass, Map<String, Long> counters) throws Exception {
        BasePlatform platform = getPlatform(workflowClass);
        BasePath workingPath = platform.makePath(getWorkingDirname());
        BasePath countersPath = platform.makePath(workingPath, WorkingConfig.COUNTERS_SUBDIR_NAME);
        countersPath.mkdirs();
        
        Properties props = new Properties();
        for (String counterName : counters.keySet()) {
            props.setProperty(counterName, "" + counters.get(counterName));
        }

        BasePath countersFile = platform.makePath(countersPath, workflowClass.getSimpleName() + ".properties");
        try (OutputStream os = countersFile.openOutputStream()) {
            props.store(os, "Counters from worklow " + workflowClass.getSimpleName());
        }
        
        return countersFile;
    }
    
    public Map<String, Long> getCounters(Class<?> workflowClass) throws Exception {
        BasePlatform platform = getPlatform(workflowClass);
        BasePath workingPath = platform.makePath(getWorkingDirname());
        BasePath countersPath = platform.makePath(workingPath, WorkingConfig.COUNTERS_SUBDIR_NAME);
        BasePath countersFile = platform.makePath(countersPath, workflowClass.getSimpleName() + ".properties");
        
        Properties props = new Properties();
        try (InputStream is = countersFile.openInputStream()) {
            props.load(is);
        }
        
        Map<String, Long> result = new HashMap<>();
        for (String counterName : props.stringPropertyNames()) {
            result.put(counterName, Long.parseLong(props.getProperty(counterName)));
        }
        
        return result;
    }
    
    public long getCounter(Class<?> workflowClass, Enum<WikiwordsCounters> counter) throws Exception {
        Long count = getCounters(workflowClass).get(getFlowCounterName(counter));
        return (count == null ? 0 : count);
    }

    // TODO this should be a static method in FlowResult, since that is where we get a map
    // of counter names to counts.
    public static String getFlowCounterName(Enum<?> counter) {
        return String.format("%s.%s", counter.getClass().getName(), counter.name());
    }
}

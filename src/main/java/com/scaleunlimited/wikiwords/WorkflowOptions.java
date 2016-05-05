package com.scaleunlimited.wikiwords;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.kohsuke.args4j.Option;

import com.scaleunlimited.cascading.BaseOptions;
import com.scaleunlimited.cascading.BasePath;
import com.scaleunlimited.cascading.BasePlatform;
import com.scaleunlimited.cascading.flink.FlinkPlatform;
import com.scaleunlimited.cascading.hadoop.HadoopPlatform;
import com.scaleunlimited.cascading.local.LocalPlatform;

public abstract class WorkflowOptions extends BaseOptions {

    private String _workingDirname = "/working1";
    private WorkflowPlanner _planner = WorkflowPlanner.FLINK;
    
    private int _sourceParallelism = -1;
    private int _groupParallelism = -1;
    
    private transient BasePlatform _platform;
    
    public WorkflowOptions() {
        super();
    }
    
    public WorkflowOptions(WorkflowOptions baseOptions) {
        super(baseOptions);
        
        setPlanner(baseOptions.getPlanner());
        setWorkingDirname(baseOptions.getWorkingDirname());
    }
    
    @Option(name = "--workingdir", usage = "working directory path", required = false)
    public void setWorkingDirname(String workingDirname) {
        _workingDirname = workingDirname;
    }

    public String getWorkingDirname() {
        return _workingDirname;
    }

    @Option(name = "--planner", usage = "Cascading planner (local, hadoop, tez, flink)", required = false)
    public void setPlanner(WorkflowPlanner planner) {
        _planner = planner;
    }

    public WorkflowPlanner getPlanner() {
        return _planner;
    }

    @Option(name = "--parsource", usage = "Parallelism for source tasks, only appliable for Flink", required = false)
    public void setSourceParallelism(int parallelism) {
        _sourceParallelism = parallelism;
    }

    public int getSourceParallelism() {
        return _sourceParallelism;
    }

    @Option(name = "--pargroup", usage = "Parallelism for group tasks, only appliable for Flink", required = false)
    public void setGroupParallelism(int parallelism) {
        _groupParallelism = parallelism;
    }

    public int getGroupParallelism() {
        return _groupParallelism;
    }
    
    public BasePlatform getPlatform(Class<?> applicationJarClass) {
        if (_platform == null) {
            switch (_planner) {
                case LOCAL:
                    _platform = new LocalPlatform(applicationJarClass);
                    break;
                    
                case HADOOP:
                    _platform = new HadoopPlatform(applicationJarClass);
                    break;
                    
                case FLINK:
                    _platform = new FlinkPlatform(applicationJarClass);
                    break;
                    
                case TEZ:
                    throw new IllegalArgumentException("Tez is not yet a supported platform");
                    
                default:
                    throw new IllegalArgumentException("" + _planner + " is not a supported platform");
            }
        }
        
        if (getSourceParallelism() != -1) {
            if (_planner == WorkflowPlanner.FLINK) {
                FlinkPlatform platform = (FlinkPlatform)_platform;
                platform.setSourceParallelism(getSourceParallelism());
            } else {
                throw new IllegalArgumentException("Can't set source parallelism for non-Flink planners");
            }
        }

        if (getGroupParallelism() != -1) {
            if (_planner == WorkflowPlanner.FLINK) {
                FlinkPlatform platform = (FlinkPlatform)_platform;
                platform.setGroupParallelism(getSourceParallelism());
            } else {
                throw new IllegalArgumentException("Can't set group parallelism for non-Flink planners");
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

# cascading.classify
Linear SVM for Cascading-based workflows

This is based on [LibLinear](https://www.csie.ntu.edu.tw/~cjlin/liblinear/). We're using the [liblinear-java](http://liblinear.bwaldvogel.de/) project, which is a Java port of the original C++ code (created and maintained by [Benedikt Waldvogel](https://github.com/bwaldvogel), thanks!). We've forked Benedikt's code into the [liblinear-java](https://github.com/ScaleUnlimited/liblinear-java) GitHub repo. This has a few improvements, specifically:

 * Make the models Hadoop Writable, for (de)serialization in workflows.
 * Make Train.constructProblem public, so we can use it when cross-validating results.
 * Minor fix to pom.xml for test scope dependency.
 
 

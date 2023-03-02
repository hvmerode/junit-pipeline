# junit-pipeline
Perform unit/integration testing for pipelines (Azure DevOps)

## Overview ##
This library is used to perform unit- and integration tests on (YAML) pipelines. At the moment, only Azure DevOps pipelines are supported.
Development is still in an experimental phase and it may cause some issues when used but in genera, it works.

### How it works ###
Assume that your application and pipeline code reside in a repository called "myrepo" in the Azure DevOps project "MyApp".
Development on the (Java) app is straightforward. With the junit_pipeline.jar it becomes also possible to test the
pipeline code (in this case an Azure DevOps pipeline in YAML).
Testing the pipeline code is performed by JUnit tests. In these tests, the original pipeline code is manipulated according to your needs.
Assume, you want to mock your deployment task to prevent something to be deployed to your AWS account. With a few lines, the "AWSShellScript@1"
task is replaced by a script. Example code:
```java
String inlineScript = "echo \"This is a mock script\"\n" +
"echo \"Mock-and-roll\"";
pipeline.mockStep("AWSShellScript@1", inlineScript);
```
To test the manipulated script, execute it using
```java
pipeline.startPipeline();
```
The junit_pipeline library connects with the Azure DevOps test project (for example project "UnitTest"), pushes the code of your "myrepo" to it
(with the changed pipeline code) and executes the pipeline. If the repository and/or the pipeline in project "InitTest" does not exists, it
is automatically created for you. The illustration below shows how it works in concept.

![no picture](https://github.com/hvmerode/junit-pipeline/blob/main/junit_pipeline.png "how it work")

### How to start ##
TODO
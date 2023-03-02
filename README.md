# junit-pipeline
Perform unit/integration testing for pipelines (Azure DevOps)

## Overview ##
This library is used to perform unit- and integration tests on (YAML) pipelines. At the moment, only Azure DevOps pipelines are supported.
Development is still in an experimental phase and it may cause some issues when used but in general, it works.

### How it works ###
Assume that your application and pipeline code reside in a repository called "myrepo" in the Azure DevOps project "MyApp".
Development on the (Java) app is straightforward. With the ___junit_pipeline___ libray it becomes also possible to test the
pipeline code (in this case an Azure DevOps pipeline in YAML).
Testing the pipeline code is performed by JUnit tests. In these tests, the original pipeline code is manipulated according to your needs.
Assume, you want to mock your deployment task to prevent something to be deployed to your AWS account. With a few lines, the "AWSShellScript@1"
task is replaced by a script. Example code:
```java
String inlineScript = "echo \"This is a mock script\"\n" +
"echo \"Mock-and-roll\"";
pipeline.mockStep("AWSShellScript@1", inlineScript);
```
To test the manipulated script, execute it like this:
```java
pipeline.startPipeline();
```
The ___junit_pipeline___ library connects with the Azure DevOps test project (for example project "UnitTest"), pushes the code of your "myrepo" to it
(with the changed pipeline code) and executes the pipeline. If the repository and/or the pipeline in project "UnitTest" does not exists, it
is automatically created for you. The illustration below shows how it works in concept.

![no picture](https://github.com/hvmerode/junit-pipeline/blob/main/junit_pipeline.png "how it works")

### How to start ##
TODO

### Known limitations ##
* The updated pipeline code is pushed to the _default branch_ in the test project (master); pushing to other branches is not possible.
* Only YAML templates in the same repository are taken into account. Templates in other repositories (identified with a @ behind the template name) are ignored.
* If the pipeline makes use of a resource in the test project for the first time, it needs manual approval first; for example, a variable group or an Environment.
* If unknown service connections are used or the updated pipeline code is not valid YAML anymore, the AzDo API returns an HTTP status code 400.
* No methods yet to add, update or remove conditions in stages or jobs. Use the _overrideLiteral_ method, if possible.
* At the start, the local target repository and the remote target repository (of the test project) can become out-of-sync. Delete both the local and the remote repo and start again.

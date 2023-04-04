# junit-pipeline
Perform unit/integration testing for pipelines (Azure DevOps)

## Overview ##
This library is used to perform unit- and integration tests on (YAML) pipelines. At the moment, only Azure DevOps pipelines are supported.
Development is still in an experimental phase and it may cause some issues when used but in general, it works.

***
### How it works ###
Assume that your application and pipeline code reside in a repository called "myrepo" in the Azure DevOps project "MyApp".
Development on the (Java) app is straightforward. With the ___junit_pipeline___ libray, it becomes also possible to test the
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
The ___junit_pipeline___ library connects with the Azure DevOps test project (for example project "UnitTest"), and pushes the code to your test 
repository (which can also be called "myrepo", if you want). It then executes the pipeline. If the repository and/or the pipeline in project "UnitTest" 
do not exists, they are automatically created for you. The illustration below shows how it works in concept.

![no picture](https://github.com/hvmerode/junit-pipeline/blob/main/junit_pipeline.png "how it works")

### How to start
***
#### Create Azure DevOps test project ####
TODO

****
#### Configure junit_pipeline.properties ####
This file is located in src/main/resources. It contains the properties for your project. Some important properties:
* __source.path__ - Contains the location (directory) of the main local Git repository on your computer. This is the repository in which you develop your 
  app and the associated pipeline. In the example case, this repository is called "myrepo".
* __target.path__ - Contains the location (directory) of the local Git repository used to test the pipeline. You are not actively working in this repo.\
  It is only used for the junit_pipeline framework to communicate with the Azure DevOps test project. Before you start, this directory must not exist.
* __target.organization__ - The name of your organization as defined in Azure DevOps. This will be included in the Azure DevOps API calls.
* __target.project__ - The name of the test project. In the example case it is called "UnitTest".
* __target.repository.name__ - The name of the repository used in the Git repository used for testing. Best is to keep the source and target repository names identical.
* __target.repository.user__ - User used in the Azure DevOps API calls to the test project. Can be the default name 'UserWithToken'.
* __target.repository.password__ - The PAT (Personal Access Token) used in the Azure DevOps API calls to the test project.\
  See https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Windows how to create a PAT.\
  Make sure this PAT is authorized for all pipelines used in the Azure DevOps test project.
* __repository.pipeline.path__ - The location of the main pipeline file in the repository (both in source and target).
* __git.commit.pattern__ - Defines the type of files pushed to the local- and remote test repo (this is a subset of the files from the main repo)
* __pipelines.api__ - Name of the Azure DevOps base Pipeline API; do not change this value. 
* __pipelines.api.runs__ - Name of a specific Azure DevOps Pipeline API; do not change this value.
* __pipelines.api.version__ - Version of the Azure DevOps Pipeline API; only change if it is really needed (e.g., if a new version of the API is released).
* __git.api__ - Name of the Azure DevOps base Git API; do not change this value.
* __git.api.repositories__ - Name of a specific Azure DevOps Git API; do not change this value.
* __git.api.version__ - Version of the Azure DevOps Git API; only change if it is really needed (e.g., if a new version of the API is released).
* __build.api__ - Name of the Azure DevOps base Build API; do not change this value.
* __build.api.version__ - Version of the Azure DevOps Build API; only change if it is really needed (e.g., if a new version of the API is released).
* __build.api.poll.frequency__ - The result of a pipeline run is retrieved, using an Azure DevOps API. This API is called with a frequency determined by __build.api.poll.frequency__ (in seconds).  
* __build.api.poll.timeout__ - The timeout value of polling the result of the pipeline run. If the final result is not retrieved yet, the polling stops after a number of seconds, defined by  __build.api.poll.timeout__.
* __project.api__ - Name of the Azure DevOps base Project API; do not change this value.
* __project.api.version__ - Version of the Azure DevOps Project API; only change if it is really needed (e.g., if a new version of the API is released).
> The property file is stored in the _resources_ folder of the code repository.

***
#### pom.xml ####
After the properties file has been created, the junit_pipeline library must be added to the _pom.xml_ of your project.
Example:
```xml
<dependency>
  <groupId>org.pipeline</groupId>
  <artifactId>junit-pipeline</artifactId>
  <version>1.0.0</version>
</dependency>
```

### How to use it ##
This repository already contains a sample unittest file called _PipelineUnit_. We take this file as an example.  

***
#### Create  ___AzDoPipeline___ object ####
Pipeline unit tests are defined in a unit test Java class. Before tests are executed, a new ___AzDoPipeline___ Java object must
be instantiated. Its constructor requires two arguments, a property file and the main pipeline YAML file. Example:
```java
AzDoPipeline pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline_test.yml");
```
The _junit_pipeline_my.properties_ file in this example contains my personal properties.
The file _./pipeline/pipeline_test.yml_ is the main pipeline file. It can be stored in any folder of the code repository.
It's path is relative to the root of the repository. The main pipeline file may contain references to other template files
in the repository. These files don't have to be listed. The junit_pipeline frameworks sorts out, which templates are included.
> Note, that templates in other repositories (identified with an @ behind the template name) are ignored.

#### Define a command bundle ####
It is perfectly possible to repeat a certain command in every unit test, but if you, for example, never want to
execute a certain task, it is also possible to add an action to a command bundle that skips the tasks or
replaces it with a mock script. In the example below, the template _template-steps_1.yml_ is replaced by
_template-mock.yml_ for every unit test.
```java
pipeline.commandBundle.overrideLiteral("templates/steps/template-steps_1.yml", "templates/steps/template-mock.yml");
```

***
#### Define unit test ####
The junit_library contains a set of commands - used in the unit tests - to manipulate the pipeline. Let's 
go over them:
```java
public void mockStep(String stepValue, String inlineScript)
Bla, bla
```
```java
public void skipJob(String jobName)
Bla, bla
```
```java
public void overrideVariable(String variableName, String value)
Bla, bla
```
```java
public void overrideTemplateParameter(String parameterName, String value)
Bla, bla
```
```java
public void overrideParameterDefault(String parameterName, String value)
Bla, bla
```
```java
public void overrideLiteral(String findLiteral, String replaceLiteral)
Bla, bla
```
```java
public void skipJob(String jobName)
Bla, bla
```
```java
public void skipStage(String stageName)
Bla, bla
```
```java
public void overrideCurrentBranch(String newBranchName)
Bla, bla
```

***
#### Start unit tests and retrieve the result ####
TODO

***
### Known limitations ##
* Tests cannot be executed in parallel. Because the target repository is updated for each test, the next
  test must wait before the previous one is completed.
* Only YAML templates in the same repository are taken into account. Templates in other repositories (identified with a @ behind the template name) are ignored.
* If the pipeline makes use of a resource in the test project for the first time, it needs manual approval first; for example, a variable group or an Environment.
* If unknown service connections are used or the updated pipeline code is not valid YAML anymore, the AzDo API returns an HTTP status code 400.
* No methods yet to add, update or remove conditions in stages or jobs. Use the _overrideLiteral_ method, if possible.
* At the start, the local target repository and the remote target repository (of the test project) can become out-of-sync. Delete both the local and the remote repo and start again.
* Copying files from the main local repo to the test local repo involves exclusion of files, using an exclusion list. This list is currently hardcoded\
  and contains "idea, target, .git and class". This should be made configurable in the junit_pipeline.properties file.
  

* ~~With the introduction of tests running in multiple branches, it is not possible to run multiple tests in one go. Second test fails
because cloning/checkout is not possible somehow~~
* ~~The updated pipeline code is pushed to the _default branch_ in the test project (master); pushing to other branches is not possible.~~
* ~~The project id of the Azure DevOps test project must be configured manually and is not (yet) derived automatically.~~

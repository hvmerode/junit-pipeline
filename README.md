# junit-pipeline
Perform unit/integration testing for pipelines (Azure DevOps)

## Overview ##
This library is used to perform unit- and integration tests on (YAML) pipelines. At the moment, only Azure DevOps pipelines are supported.
Development is still in an experimental phase and it may cause some issues when used but in general, it works.

<br>

### How it works ###
***
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

<br>

### How to start
***
#### Create Azure DevOps test project ####
Unfortunately, testing a pipeline within the IDE is not possible. You need an Azure DevOps unit test project for this. Create a project
using this link: [Create a project in Azure DevOps](https://learn.microsoft.com/en-us/azure/devops/organizations/projects/create-project)

<br>

#### Configure junit_pipeline.properties ####
This file is located in src/main/resources. It contains the properties for your project. Some important properties:
* __source.path__ - Contains the location (directory) of the main local Git repository on your computer. This is the repository in which you develop your 
  app and the associated pipeline. In the example case, this repository is called "myrepo".
* __target.path__ - Contains the location (directory) of the local Git repository used to test the pipeline. You are not actively working in this repo.\
  It is only used for the __junit_pipeline__ framework to communicate with the Azure DevOps test project. Before you start, this directory must not exist.
* __target.organization__ - The name of your organization as defined in Azure DevOps. This will be included in the Azure DevOps API calls.
* __target.project__ - The name of the test project. In the example case it is called "UnitTest".
* __target.repository.name__ - The name of the repository used in the Git repository used for testing. Best is to keep the source and target repository names identical.
* __target.repository.user__ - User used in the Azure DevOps API calls to the test project. Can be the default name 'UserWithToken'.
* __target.repository.password__ - The PAT (Personal Access Token) used in the Azure DevOps API calls to the test project.\
  See [Use personal access tokens](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Windows) how to create a PAT.\
  Make sure this PAT is authorized for all pipelines used in the Azure DevOps test project.
* __repository.pipeline.path__ - The location of the main pipeline file in the repository (both in source and target).
  It is used to assign the pipeline file when creating a new pipeline in Azure DevOps.
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

<br>

#### Update pom.xml ####
After the properties file has been created, the __junit_pipeline__ library must be added to the _pom.xml_ of your project.
Example:
```xml
<dependency>
  <groupId>org.pipeline</groupId>
  <artifactId>junit-pipeline</artifactId>
  <version>1.0.0</version>
</dependency>
```
<br>

### How to use it ##
***
This repository already contains a sample unittest file called _PipelineUnit_. We take this file as an example.  

<br>

#### Create  ___AzDoPipeline___ object ####
Pipeline unit tests are defined in a unit test Java class. Before tests are executed, a new ___AzDoPipeline___ Java object must
be instantiated. Its constructor requires two arguments, a property file and the main pipeline YAML file. Example:
```java
AzDoPipeline pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline_test.yml");
```
The _junit_pipeline_my.properties_ file in this example contains my personal properties.
The file _./pipeline/pipeline_test.yml_ is the main pipeline file. It can be stored in any folder of the code repository.
Its path is relative to the root of the repository. The main pipeline file may contain references to other template files
in the repository. The __junit_pipeline__ frameworks takes these templates into account in pipeline manipulation.
> Note, that templates in other repositories (identified with an @ behind the template name) are used just as-is. 
> The __junit_pipeline__ framework leaves these templates untouched.

<br>

#### Define a command bundle ####
It is perfectly possible to repeat a certain command in every unit test, but if you, for example, want to
execute a certain task in all tests, it is also possible to add an action to a command bundle. For example, add
a command that skips a task or replaces it with a mock script. 
In the example below, the template _template-steps_1.yml_ is replaced by
_template-mock.yml_ for every unit test.
```java
pipeline.commandBundle.overrideLiteral("templates/steps/template-steps_1.yml", "templates/steps/template-mock.yml");
```

<br>

#### Hooks ####
Before the pipeline code is pushed to the Azure DevOps unit test project, and started, it is possible to execute
custom code. This code is provided as a list of 'hooks'. The unit test file _PipelineUnit.java_ show an example, _test 3_.\
This package also contains a few custom hooks:
* _DeleteJUnitPipelineDependency_ - Deletes the __junit-pipeline__ dependency from the _pom.xml_, before it is pushed to the
Azure DevOps unit test project.
* _DeleteTargetFile_ - Deletes a single file before it is pushed to the Azure DevOps unit test project.

<br>

#### Define unit test ####
The __junit_library__ contains a set of commands - used in unit tests - to manipulate the pipeline. Let's 
go over them:

<br>

```java
public void mockStep(String stepValue, String inlineScript)
```
<i>
The original step is replaced by a mock step. This is a step of the type 'script'. The argument 'inlineScript' is added to the mock.
Depending on the job pool this can be a Powershell script (Windows) or a bash script (Linux).
</i>
<br>
<br>

***
```java
public void skipStage(String stageName)
```
<i>Skip a stage.        
The result is, that the stage is completely removed from the output pipeline yaml file, which basically is
the same as skipping it.

<u>Example</u>:
<pre>
- stage: my_stage
  displayName: 'This is my stage'
</pre>

Call pipeline.skipStage("my_stage")
==> The stage with name "my_stage" is skipped
</i>
<br>
<br>

***
```java
public void skipJob(String jobName)
```
<i>
Skip a job.
The result is, that the job is completely removed from the output pipeline yaml file, which basically is
the same as skipping it.

<u>Example</u>:
<pre>
- job: my_job
  displayName: 'This is my job'
</pre>

Call pipeline.skipJob("my_job")
==> The job with name "my_job" is skipped
</i>
<br>
<br>

***
```java
public void overrideVariable(String variableName, String value)
```
<i>
Replace the value of a variable in the 'variables' section. Two constructions are possible:

<u>Construction 1</u>:
<pre>
variables:
myVar : myValue
</pre>

<u>Construction 2</u>:
<pre>
variables:
- name: myVar
  value: myValue
</pre>

pipeline.overrideVariable("myVar", "myNewValue") results in resp.
<pre>
variables:
myVar : myNewValue
</pre>

<pre>
variables:
- name: myVar
  value: myNewValue
</pre>
This method does not replace variables defined in a Library.
</i>
<br>
<br>

***
```java
public void overrideTemplateParameter(String parameterName, String value)
```
<i>
Replace the value of a parameter in a 'template' section. Example:
<pre>
- template: step/mytemplate.yml
  parameters:
    tag: $(version)
</pre>

To replace the version with a fixed value (2.1.0), call:
pipeline.overrideTemplateParameter("tag", "2.1.0"). This results in:
<pre>
- template: step/mytemplate.yml
  parameters:
    tag: 2.1.0
</pre>
</i>
<br>

***
```java
public void overrideParameterDefault(String parameterName, String value)
```
<i>
Replace the default value of a parameter in the 'parameters' section. Example:
<pre>
- name: myNumber
  type: number
  default: 2
  values:
  - 1
  - 2
  - 4
</pre>

pipeline.overrideParameterDefault("myNumber", "4") result in:
<pre>
- name: myNumber
  type: number
  default: 4
  values:
  - 1
  - 2
  - 4
</pre>
</i>
<br>

***
```java
public void overrideLiteral(String findLiteral, String replaceLiteral, boolean replaceAll)
```
<i>
Override (or overwrite) any arbitrary string in the yaml file.
<pre>
- task: AzureWebApp@1
  displayName: Azure Web App Deploy
  inputs:
    azureSubscription: $(azureSubscription)
    appName: samplewebapp
</pre>

Calling pipeline.overrideLiteral ("$(azureSubscription)", "1234567890") results in
<pre>
- task: AzureWebApp@1
  displayName: Azure Web App Deploy
  inputs:
  azureSubscription: 1234567890
  appName: samplewebapp
</pre>

If _replaceAll_ is 'true' all occurences of literal in both the main YAML and the templates are replaced.\
If _replaceAll_ is 'false' the first occurence of literal in both the main YAML and the templates are replaced.
</i>
<br>
<br>

***
```java
public void overrideCurrentBranch(String newBranchName, boolean replaceAll)
```
<i>
Replace the current branch with a given branch name.
Example: Assume the following condition:
<pre>
and(succeeded(), eq(variables['Build.SourceBranchName'], 'main'))
</pre>

After applying pipeline.overrideCurrentBranch("myFeature") it becomes
<pre>
and(succeeded(), eq('myFeature', 'main'))
</pre>

If _replaceAll_ is 'true', all occurences in both the main YAML and the templates are replaced.\
If _replaceAll_ is 'false', the first occurence in both the main YAML and the templates are replaced.
</i>
<br>
<br>

***
#### Start unit tests and retrieve the result ####
The startPipeline method has a few representations:
* _startPipeline()_ - Starts the pipeline with the default branch (in most cases, this is the _master_ branch).
* _startPipeline(String branchName)_ - Starts the pipeline with a given branch, for example a _feature_ branch.
* _startPipeline(String branchName, List<Hook> hooks)_ - Starts the pipeline with a given branch but
  before the pipeline starts, the list with 'hooks' is executed.
  
The result of a pipeline run is retrieved with
```java
pipeline.getRunResult()
```
<br>

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
  and contains "idea, target, .git and class". This should be made configurable in the _junit_pipeline.properties_ file.


* ~~Sometimes you get the error "org.eclipse.jgit.api.errors.RefAlreadyExistsException: Ref myFeature already exists". This
  happens if a branch already exists (the checkout wants to create it again). Just ignore this error.~~
* ~~With the introduction of tests running in multiple branches, it is not possible to run multiple tests in one go. Second test fails
because cloning/checkout is not possible somehow~~
* ~~The updated pipeline code is pushed to the _default branch_ in the test project (master); pushing to other branches is not possible.~~
* ~~The project id of the Azure DevOps test project must be configured manually and is not (yet) derived automatically.~~

Copyright (c) Henry van Merode.\
Licensed under the MIT License.

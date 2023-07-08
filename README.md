# junit-pipeline
Perform unit/integration test for pipelines (Azure DevOps)

## Introduction ##
Unit testing CI/CD pipelines is a challenge. Test frameworks for pipelines are almost non-existent or at least 
very scarce. Teams often develop pipelines using trial-and-error and they test along the way. 
A lot of things can go wrong:
* During testing, a wrong version of an app was deployed by accident.
* Code from a feature branch was accidentally tagged with a release version tag.
* The number of commits is very high because of the trial-and-error nature of developing and testing pipelines.
* Temporary code, added to the pipeline specifically for testing is not removed.
* Temporary disabled or commented code is not enabled / uncommented anymore.
* The pipeline code contains switches or conditions specifically for testing the pipeline.
* The overview with regular application pipeline runs is cluttered with a zillion test runs.
 
This library is used to perform unit- and integration tests on (YAML) pipelines. At the moment, only 
Azure DevOps pipelines are supported.
<br></br>

## How it works ##
***
Assume that your application and pipeline code reside in a repository called "__myrepo__" in the Azure DevOps project "__MyApp__".
Development on the (Java) app is straightforward. \
With the ___junit-pipeline___ libray, it becomes possible to test the YAML pipeline code.
Testing the pipeline code is performed by JUnit tests. In these tests, the original pipeline code is manipulated according to your needs.
Assume, you want to mock your deployment task to prevent something to be deployed to your AWS account. With a few lines, the "AWSShellScript@1"
task is replaced by a script. Example code:
```java
String inlineScript = "echo \"This is a mock script\"\n" +
"echo \"Mock-and-roll\"";
pipeline.mockStepSearchByIdentifier("AWSShellScript@1", inlineScript);
```
To test the manipulated script, execute it like this:
```java
pipeline.startPipeline();
```
The ___junit-pipeline___ library connects with the Azure DevOps test project (in the figure below, represented by "__UnitTest__"), and pushes the code to your test 
repository ("__myrepo-test__", in this example), after which the pipeline is executed. If the repository and/or the pipeline in the test project
do not exists, they are automatically created for you. The illustration below shows how it works in concept.

![no picture](https://github.com/hvmerode/junit-pipeline/blob/main/junit_pipeline.png "how it works")

In addition, all external repositories defined in the pipeline are cloned and also pushed to the Azure DevOps test project. External repositories are
used to define pipeline templates that are included in your pipeline, but reside in a different repository than the pipeline itself.
The ___junit-pipeline___ library takes care that the main pipeline refers to the 
cloned copies of these repositories instead to the original ones, so external templates can also be manipulated.
<br></br>

## How to start
***
### Create Azure DevOps test project ###
Unfortunately, testing a pipeline within the IDE is not possible. You need an Azure DevOps unit test project for this. Create a test project
using this link: [Create a project in Azure DevOps](https://learn.microsoft.com/en-us/azure/devops/organizations/projects/create-project)
<br></br>

### Configure junit_pipeline.properties ###
The properties file is located in src/main/resources. It contains the properties for your project. Some important properties:
* __source.path__ - Contains the location (directory) of the main Git repository on your computer. This is the repository in which you develop your 
  app and the associated pipeline. In the example case, this repository is called "__myrepo__".
* __target.path__ - Contains the location (directory) of the Git repository used to test the pipeline. You are not actively working in this repo.
  It is only used for the __junit-pipeline__ framework to communicate with the Azure DevOps test project. Before you start, this directory must not exist.
* __target.organization__ - The name of your organization as defined in Azure DevOps. This will be included in the Azure DevOps API calls.
* __target.project__ - The name of the test project. In the example case it is called "__UnitTest__".
* __source.base.path.external__ - The local directory of the external repositories on the workstation, defined in the "__repositories__" section of the main pipeline YAML file.
  You are not actively working in this repo.
* __target.base.path.external__ - The location (local directory) containing external Git repositories; this location
  is used communicate with the Azure DevOps test project.
* __source.repository.name__ - The name of the main repository
* __target.repository.name__ - The name of the repository used in the Git repository used for testing. Example: If a source repository 
 with the name "__myrepo__" is used, the __target.repository.name__ used for testing the pipeline can be called "__myrepo-test__".
  _target.repository.name_ should preferably not be equal to _source.repository.name_. 
* __azdo.user__ - User used in the Azure DevOps API calls. Can be the default name 'UserWithToken'.
* __azdo.pat__ - The PAT (Personal Access Token) used in the Azure DevOps API calls.\
  See [Use personal access tokens](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Windows) how to create a PAT.\
  Make sure this PAT is authorized to clone repositories in other Azure DevOps projects in the organization (other than the test project). 
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
* __error.continue__ - If _true_, the junit-.pipeline framework continues after an error is detected 
  (e.g., if the pipeline YAML file or a template file is incorrect). Note, that this can result in unpredictable results.
  If _false_, the framework stops with the test as soon as an error is detected.
> The property file is stored in the _resources_ folder.

<br></br>
### Update pom.xml ###
After the properties file has been created, the __junit-pipeline__ library must be added to the _pom.xml_ of your project.
Example:
```xml
<dependency>
  <groupId>io.github.hvmerode</groupId>
  <artifactId>junit-pipeline</artifactId>
  <version>1.1.2</version>
</dependency>
```

<br></br>
## How to use it ##
***
This repository already contains a sample unit test file called _PipelineUnit.java_. We take this file as an example.
If you want to check out a demo project, please take a look at '[hello-pipeline](https://github.com/hvmerode/hello-pipeline)'.
<br></br>

### Create  ___AzDoPipeline___ object ###
Pipeline unit tests are defined in a unit test Java class. Before tests are executed, a new ___AzDoPipeline___ Java object must
be instantiated. Its constructor requires two arguments, a property file and the main pipeline YAML file. Example:
```java
AzDoPipeline pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline_test.yml");
```
The _junit_pipeline_my.properties_ file in this example contains my personal properties, but you can use 
the _junit_pipeline.properties_ file in the _resources_ folder and customize it to your needs.\
The file _./pipeline/pipeline_test.yml_ is the main pipeline file. It can be stored in any folder of the code repository.
Its path is relative to the root of the repository. The main pipeline file may contain references to other yamlTemplate files
in the repository. The __junit-pipeline__ frameworks takes these templates into account in pipeline manipulation.

<br></br>

### Hooks ###
Before the pipeline code is pushed to the Azure DevOps unit test project, and started, it is possible to execute
custom code. This code is provided as a list of 'hooks'. The unit test file _PipelineUnit.java_ shows an example; _test 3_.\
This repository also contains a few standard hooks:
* _DeleteJUnitPipelineDependency_ - Deletes the __junit-pipeline__ dependency from the _pom.xml_, before it is pushed to the
Azure DevOps unit test project.
* _DeleteTargetFile_ - Deletes a single file before it is pushed to the Azure DevOps unit test project. It can be used to
remove the file that includes the pipeline unit tests, if you don't want it to run it in the test project.
* _FindReplaceInFile_ - Find and replace a string in a given file; either replaces the first occurence or all occurences.
  This hook can be used to fix some inconveniences in the target yaml files in the Azure DevOps test project. 
<br></br>

### Define unit test ###
The __junit-pipeline__ library contains a set of methods - used in unit tests - to manipulate the pipeline. Let's 
go over a few of them:
> Note, that this is only a subset of the methods available.

<br></br>

***
***
```java
public void mockStepSearchByIdentifier (String stepIdentifier, String inlineScript)
```
<i>
The original step is replaced by a mock step. This is a step of the type 'script'. The argument 'inlineScript' '
is added to the mock. Depending on the job pool this can be a Powershell script (Windows) or a bash script (Linux).

<u>Example</u>:
<pre>
- task: AWSShellScript@1
  inputs:
    awsCredentials: $(aws_connection)
    regionName: $(aws_region)
    scriptType: 'inline'
    inlineScript: |
      #!/bin/bash
      set -ex
      export cdk=`find $(Pipeline.Workspace)/. -name 'cdk*.jar'`
      export app=`find $(Pipeline.Workspace)/. -name 'app*.jar'`

      echo "Deploying stack"
      cdk deploy --app '${JAVA_HOME_11_X64}/bin/java -cp $cdk com.org.app.Stack' \
          -c env=${{ parameters.environment }} \
          -c app=$app
          --all \
          --ci \
          --require-approval never
  displayName: 'Deploy to AWS'
</pre>

Calling in Java:
```java
String inlineScript = "echo \"This is a mock script\"\n" + "echo \"Mock-and-roll\"";
pipeline.mockStepSearchByIdentifier ("AWSShellScript@1", inlineScript);
```
results in:
<pre>
- script: |-
    echo "This is a mock script"
    echo "This is line 2"
</pre>

</i>
<br>
<br>

***
***
```java
public void skipStageSearchByIdentifier (String stageIdentifier)
```
<i>Skip a stage.
The result is, that the stage is completely removed from the output pipeline yaml file, which basically is
the same as skipping it.

<u>Example</u>:
<pre>
- stage: my_stage
  displayName: 'This is my stage'
</pre>

Calling in Java:
```java
pipeline.skipStageSearchByIdentifier ("my_stage")
```
==> The stage with identifier "my_stage" is skipped
</i>
<br>
<br>

***
***
```java
public void skipJobSearchByIdentifier (String jobIdentifier)
```
<i>
Skip a job. This is similar to the skipStageSearchByIdentifier() method but for jobs.

<u>Example</u>:
<pre>
- job: my_job
  displayName: 'This is my job'
</pre>

Calling in Java:
```java
pipeline.skipJobSearchByIdentifier ("my_job")
```
==> The job with identifier "my_job" is skipped
</i>
<br>
<br>

***
***
```java
public void skipTemplateSearchByIdentifier (String templateIdentifier)
```
<i>
Skip a template. This is similar to the skipStageSearchByIdentifier() method but for templates.

<u>Example</u>:
<pre>
  - template: templates/stages/template-stages.yml
    parameters:
      parameter_1: value_1
      parameter_2: value_2
</pre>

Calling in Java:
```java
pipeline.skipTemplateSearchByIdentifier ("templates/stages/template-stages.yml")
```
==> The template with identifier "templates/stages/template-stages.yml" is skipped
</i>
<br>
<br>

***
***
```java
public void overrideVariable (String variableName, String value)
```
<i>
Replace the value of a variable in the 'variables' section:

<pre>
variables:
- name: myVar
  value: myValue
</pre>

Calling in Java:
```java
pipeline.overrideVariable ("myVar", "myNewValue")
```
results in:
<pre>
variables:
- name: myVar
  value: myNewValue
</pre>
This method does not replace variables defined in a Library (variable group).
</i>
<br>
<br>

***
***
```java
public void overrideTemplateParameter (String parameterName, String value)
```
<i>
Replace the value of a parameter in a 'yamlTemplate' section.

<u>Example</u>:
<pre>
- yamlTemplate: step/mytemplate.yml
  parameters:
    tag: $(version)
</pre>

To replace the version with a fixed value (2.1.0), call:
```java
pipeline.overrideTemplateParameter ("tag", "2.1.0"). 
```        
This results in:
<pre>
- yamlTemplate: step/mytemplate.yml
  parameters:
    tag: 2.1.0
</pre>
</i>
<br>

***
***
```java
public void overrideParameterDefault (String parameterName, String defaultValue)
```
<i>
Replace the default value of a parameter in the 'parameters' section.

<u>Example</u>:
<pre>
- name: myNumber
  type: number
  default: 2
  values:
  - 1
  - 2
  - 4
</pre>

Calling in Java:
```java
pipeline.overrideParameterDefault ("myNumber", "4") 
```
results in:
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
<br>

***
***
```java
public void overrideLiteral (String literalToReplace, String newValue, boolean replaceAll)
```
<i>
Override (or overwrite) any arbitrary string in the yaml file.

<u>Example</u>:
<pre>
- task: AzureWebApp@1
  displayName: Azure Web App Deploy
  inputs:
    azureSubscription: $(azureSubscription)
    appName: samplewebapp
</pre>

Calling in Java:
```java
pipeline.overrideLiteral ("$(azureSubscription)", "1234567890") 
```
results in
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
***
```java
public void overrideCurrentBranch (String newBranchName, boolean replaceAll)
```
<i>
Replace the current branch with a given branch name.

<u>Example</u>:\
Assume the following condition:
<pre>
and(succeeded(), eq(variables['Build.SourceBranchName'], 'main'))
</pre>

After applying
```java
pipeline.overrideCurrentBranch ("myFeature")
```
it becomes:
<pre>
and(succeeded(), eq('myFeature', 'main'))
</pre>

If _replaceAll_ is 'true', all occurences in both the main YAML and the templates are replaced.\
If _replaceAll_ is 'false', the first occurence in both the main YAML and the templates are replaced.
</i>
<br>
<br>

***
***
```java
public void setVariableSearchStepByDisplayName (String displayValue, 
        String variableName, 
        String value, 
        boolean insertBefore)
```
<i>
This is method is used to manipulate variables at runtime. Just before or after a certain step - identified by its 
displayName - is executed, the provided (new) value of the variable is set. Argument 'insertBefore' determines whether 
the value is set just before execution of a step, or just after execution of a step.

<u>Example</u>:
<pre>
- task: AzureRMWebAppDeployment@4
  displayName: Azure App Service Deploy
  inputs:
    appType: webAppContainer
    ConnectedServiceName: $(azureSubscriptionEndpoint)
    WebAppName: $(WebAppName)
    DockerNamespace: $(DockerNamespace)
    DockerRepository: $(DockerRepository)
    DockerImageTag: $(Build.BuildId)
</pre>

After applying
```java
pipeline.setVariableSearchStepByDisplayName ("Azure App Service Deploy", "WebAppName", "newName")
```
a script is inserted just before the AzureRMWebAppDeployment@4 (note, that 'insertBefore' is omitted; default is 'true').
When running the pipeline, the value of "WebAppName" is set with the value "newName"
<pre>
script: echo '##vso[task.setvariable variable=WebAppName]newName';
</pre>
</i>
<br>

***
***
```java
public void assertEqualsSearchStepByDisplayName (String displayValue, 
        String variableName, 
        String compareValue, 
        boolean insertBefore)
```
<i>
The assertEqualsSearchStepByDisplayName() method validates a variable during runtime of the pipeline. If the 
value of the variable - with 'variableName' - is equal to 'compareValue', the pipeline aborts. 
The assertion is performed just before or after the execution of the step, identified by the 'displayName'.

<u>Example</u>:\
After calling 
```java
pipeline.assertEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "123")
```
the value of variable 'myVar' is compared with '123', just before the step with displayName 
"Deploy the app" is executed. If you want to validate just after execution of the step, call
assertEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "123", false). 
</i>
<br>
<br>

***
***
```java
public void assertFileNotExistsSearchStepByDisplayName (String displayValue,
        String fileName,
        boolean insertBefore)
```
<i>
The assertFileNotExistsSearchStepByDisplayName() method validates the existence of a file on the Azure DevOps agent, 
during runtime of the pipeline. If the file is not present or empty, the pipeline aborts. The assertion is performed just 
before or after the execution of the step, identified by the 'displayName'.
</i>
<br>
<br>

***
***
### Start unit tests and retrieve the result ###
The startPipeline method has a few representations:
* _startPipeline()_ - Starts the pipeline with the default branch (in most cases, this is the _master_ branch).
* _startPipeline(String branchName)_ - Starts the pipeline with a given branch, for example a _feature_ branch.
* _startPipeline(String branchName, List<Hook> hooks)_ - Starts the pipeline with a given branch but
  before the pipeline starts, the list with 'hooks' is executed.
* _startPipeline(String branchName, List<Hook> hooks, boolean dryRun)_ - Performs all actions but does not start the 
  pipeline in Azure DevOps.  Use this boolean to minimize the exexution time (A free Azure DevOps account includes 
  1 Microsoft-hosted job with 1,800 minutes per month).

The result of a pipeline run is retrieved using:
```java
pipeline.getRunResult()
```
<br></br>

## Known limitations ##
***
* Tests cannot be executed in parallel. Because the target repository is updated for each test, the next
  test must wait before the previous one is completed.

* Templates residing in external repositories (GitHub and other Azure DevOps projects) are taken into account, but:
  * The _ref_ parameter is not (yet) fully implemented. Only the format "refs/heads/branch" is supported; the pattern 
    "refs/tags/tag" is not yet supported .
  * If a remote external repository is updated, the update is not automatically included in the test; first delete the 
    corresponding local directory; this enables the creation of q new clone of the external repository. For example, 
    if an external repository is called 'Templates', 2 local directories are created, 'Templates' and 'Templates-source'; 
    delete them both.
  * An external GitHub repository is assumed to be public; no credentials are used to access the GitHub repository.
* If the pipeline makes use of a resource in the test project for the first time, it needs manual approval first; for example, 
  a variable group or an Environment. The Azure DevOps API returns an HTTP status 400.
* If unknown service connections are used, if the updated pipeline code is not valid YAML anymore, or if a manual approval 
  of a resource is required, the AzDo API returns an HTTP status code 400.
<br></br>

## Known bugs ##
***
* An Azure DevOps "on..failure" / "on..success" construction is translated to "true..failure" / "true..success". It may be an issue in snakeyaml.
  * Temporary fix is by adding a FindReplaceInFile hook that replaces the "true:" string with an "on:" string.
* A task with an input parameter 'template:' is handled as if it is a yamlTemplate (although it isn't); processing 
  is still fine though (gives a warning), but it should not be treated as a yamlTemplate. Alternative is to change the 
  warning and give the recommendation that, although it is correct, it may lead to confusion.
<br></br>

## New features ##
***
* Test on Linux; some filesystem methods in Utils may not work properly.
* Support "refs/tags/tag" and "refs/refname" for external repositories with templates.
* Log YAML line numbers in method _Utils.validatePipelineFile()_ according to [yaml-line-numbers.md](https://github.com/networknt/json-schema-validator/blob/master/doc/yaml-line-numbers.md)
* Add option to pipeline.mockStep to display a name (the inline script shows as CmdLine in Azure DevOps).
* Add option to continue on error for all steps.
* Possibility to replace a step with a yamlTemplate file (the yamlTemplate file could serve as a mock file).
* Possibility to replace a step with another step.
* Add unit tests to the junit-pipeline code itself.
* Add methods to add, update or remove conditions in stages or jobs. Use the _overrideLiteral_ method, if possible.
* Check whether the output pipeline is a valid pipeline (valid yaml and valid Azure DevOps pipeline).
  This is a 'nice-to-have'.
* ~~Add an assert step; check a variable on a certain value using a condition. Exit with 1 if the condition is not met.~~
  * ~~This step can be added before or after a certain step using a pipeline method.~~
* ~~Check/assert output variables of a step.~~

## Solved ##
***
* ~~Some of the methods add a script task to the yaml. Currently this is a bash type of script, so it is assumed that the
  Azure DevOps agent is a Linux agent.~~
* ~~If the first run of a pipeline is not 'master' but another branch, the pipeline does not run.
  The first runs must be 'master'.~~
* ~~Scripts added in some methods must be Azure DevOps agent agnostic; this means that inserted tasks must either be
  Linux or Windows scripts. Currently, Linux agents are assumed.~~
* ~~If a new AzDoPipeline object is created with a different .yml file, the pipeline in Azure DevOps still uses
  the original .yml file; the pipeline must use the provided file.~~
* ~~Check whether the input pipeline and templates are a valid Azure DevOps pipeline YAML files~~
* ~~Only YAML templates in the same repository are taken into account. Templates in other repositories (identified with a @ behind the yamlTemplate name) are ignored.\
  TODO: Option to incorporate other resources (repositories) and manipulate the templates in these repos also.~~
* ~~Copying files from the main local repo to the test local repo involves exclusion of files, using an exclusion list. This list is currently hardcoded\
  and contains "idea, target, .git and class". This should be made configurable in the _junit_pipeline.properties_ file.~~
* ~~Sometimes you get the error "org.eclipse.jgit.api.errors.RefAlreadyExistsException: Ref myFeature already exists". This
  happens if a branch already exists (the checkout wants to create it again). Just ignore this error.~~
* ~~With the introduction of tests running in multiple branches, it is not possible to run multiple tests in one go. Second test fails
because cloning/checkout is not possible somehow~~
* ~~The updated pipeline code is pushed to the _default branch_ in the test project (master); pushing to other branches is not possible.~~
* ~~The project id of the Azure DevOps test project must be configured manually and is not (yet) derived automatically.~~

<br></br>
Copyright (c) Henry van Merode.\
Licensed under the MIT License.

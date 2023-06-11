# junit-pipeline
Perform unit/integration test for pipelines (Azure DevOps)

## Overview ##
This library is used to perform unit- and integration tests on (YAML) pipelines. At the moment, only Azure DevOps pipelines are supported.
<br></br>

### How it works ###
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
pipeline.mockStep("AWSShellScript@1", inlineScript);
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

### How to start
***
#### Create Azure DevOps test project ####
Unfortunately, testing a pipeline within the IDE is not possible. You need an Azure DevOps unit test project for this. Create a test project
using this link: [Create a project in Azure DevOps](https://learn.microsoft.com/en-us/azure/devops/organizations/projects/create-project)
<br></br>

#### Configure junit_pipeline.properties ####
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
* __target.repository.name__ - The name of the repository used in the Git repository used for testing. Example: If a repository is used 
  with the name "__myrepo__", the __target.repository.name__ used for testing the pipeline can be called "__myrepo-test__".
* __azdo.user__ - User used in the Azure DevOps API calls. Can be the default name 'UserWithToken'.
* __azdo.pat__ - The PAT (Personal Access Token) used in the Azure DevOps API calls.\
  See [Use personal access tokens](https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Windows) how to create a PAT.\
  Make sure this PAT is authorized to clone repositories in other Azure DevOps projects in the organization (other than the test project). 
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
> The property file is stored in the _resources_ folder.

<br></br>
#### Update pom.xml ####
After the properties file has been created, the __junit-pipeline__ library must be added to the _pom.xml_ of your project.
Example:
```xml
<dependency>
  <groupId>io.github.hvmerode</groupId>
  <artifactId>junit-pipeline</artifactId>
  <version>1.0.1</version>
</dependency>
```

<br></br>
### How to use it ##
***
This repository already contains a sample unit test file called _PipelineUnit.java_. We take this file as an example.  
<br></br>

#### Create  ___AzDoPipeline___ object ####
Pipeline unit tests are defined in a unit test Java class. Before tests are executed, a new ___AzDoPipeline___ Java object must
be instantiated. Its constructor requires two arguments, a property file and the main pipeline YAML file. Example:
```java
AzDoPipeline pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline_test.yml");
```
The _junit_pipeline_my.properties_ file in this example contains my personal properties.
The file _./pipeline/pipeline_test.yml_ is the main pipeline file. It can be stored in any folder of the code repository.
Its path is relative to the root of the repository. The main pipeline file may contain references to other template files
in the repository. The __junit-pipeline__ frameworks takes these templates into account in pipeline manipulation.
> Note, that templates in other repositories (identified with an @ behind the template name) are used just as-is. 
> The __junit-pipeline__ framework leaves these templates untouched.

<br></br>
#### Define a command bundle ####
It is perfectly possible to repeat a certain command in every unit test, but if you, for example, want to
execute a certain task in all tests, it is also possible to add it to a command bundle. You only define it
once and it is executed in all unit tests. For example, adding a command that skips a task or replaces it with a mock script. 
In the example below, the template _template-steps_1.yml_ is replaced by
_template-mock.yml_ for every unit test.
```java
pipeline.commandBundle.overrideLiteral("templates/steps/template-steps_1.yml", "templates/steps/template-mock.yml");
```

<br></br>
#### Hooks ####
Before the pipeline code is pushed to the Azure DevOps unit test project, and started, it is possible to execute
custom code. This code is provided as a list of 'hooks'. The unit test file _PipelineUnit.java_ shows an example; _test 3_.\
This repository also contains a few custom hooks:
* _DeleteJUnitPipelineDependency_ - Deletes the __junit-pipeline__ dependency from the _pom.xml_, before it is pushed to the
Azure DevOps unit test project.
* _DeleteTargetFile_ - Deletes a single file before it is pushed to the Azure DevOps unit test project. It can be used to
remove the file that includes the pipeline unit tests, if you don't want it to run it in the test project.
<br></br>

#### Define unit test ####
The __junit-pipeline__ library contains a set of commands - used in unit tests - to manipulate the pipeline. Let's 
go over them:
<br></br>

```java
public void mockStep(String stepValue, String inlineScript)
```
<i>
The original step is replaced by a mock step. This is a step of the type 'script'. The argument 'inlineScript' is added to the mock.
Depending on the job pool this can be a Powershell script (Windows) or a bash script (Linux).

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
pipeline.mockStep("AWSShellScript@1", inlineScript);
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

Calling in Java:
```java
pipeline.skipStage("my_stage")
```
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

Calling in Java:
```java
pipeline.skipJob("my_job")
```
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

Calling in Java:
```java
pipeline.overrideVariable("myVar", "myNewValue")
```
results in resp.
<pre>
variables:
myVar : myNewValue
</pre>

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
```java
public void overrideTemplateParameter(String parameterName, String value)
```
<i>
Replace the value of a parameter in a 'template' section.

<u>Example</u>:
<pre>
- template: step/mytemplate.yml
  parameters:
    tag: $(version)
</pre>

To replace the version with a fixed value (2.1.0), call:
```java
pipeline.overrideTemplateParameter("tag", "2.1.0"). 
```        
This results in:
<pre>
- template: step/mytemplate.yml
  parameters:
    tag: 2.1.0
</pre>
</i>
<br>
<br>

***
```java
public void overrideParameterDefault(String parameterName, String value)
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
pipeline.overrideParameterDefault("myNumber", "4") 
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
```java
public void overrideLiteral(String findLiteral, String replaceLiteral, boolean replaceAll)
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
```java
public void overrideCurrentBranch(String newBranchName, boolean replaceAll)
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
pipeline.overrideCurrentBranch("myFeature")
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
#### Start unit tests and retrieve the result ####
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

### Known limitations ##
***
* Not tested on Linux; some filesystem methods in Utils may not work properly
* Tests cannot be executed in parallel. Because the target repository is updated for each test, the next
  test must wait before the previous one is completed.
* Templates residing in external repositories (GitHub and other Azure DevOps projects) are taken into account, but:
  * The _ref_ parameter is not (yet) fully implemented. Only the format "refs/heads/branch" is supported; the pattern 
  "refs/tags/tag" is not yet supported 
  * If a remote external repository is updated, the update is not automatically included in the test; first delete the 
    corresponding local directory; this enables the creation of q new clone of the external repository. For example, 
    if an external repository is called 'Templates', 2 local directories are created, 'Templates' and 'Templates-source'; 
    delete them both.
* Output variables of a step cannot be checked/asserted; TODO: Add option to validate output variables of a certain step
* If the pipeline makes use of a resource in the test project for the first time, it needs manual approval first; for example, 
  a variable group or an Environment. The Azure DevOps API returns an HTTP status 400. 
* If unknown service connections are used, the updated pipeline code is not valid YAML anymore, or a manual approval on a resource
is required, the AzDo API returns an HTTP status code 400. TODO: Check whether the outputed pipeline is valid yaml.
* The junit-pipeline code itself does not have any unit tests yet. 
* No methods yet to add, update or remove conditions in stages or jobs. Use the _overrideLiteral_ method, if possible.
* No methods yet to replace a step with another step.
* No method to replace a step with a template file (the template file could serve as a mock file)
* There is no option (yet) to continue on error for all steps.
<br></br>

### Known bugs ##
***
* An Azure DevOps "on..failure" / "on..success" construction is translated to "true..failure" / "true..success". It may be an issue in snakeyaml, 
  but that's not sure atm
* 
<br></br>

### Solved ##
***
* ~~Only YAML templates in the same repository are taken into account. Templates in other repositories (identified with a @ behind the template name) are ignored.\
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

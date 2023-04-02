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

### How to start - configure junit_pipeline.properties ##
This file is located in src/main/resources. It contains the properties for your project. Some important properties:
* __source.path__ - Contains the location (directory) of the main local Git repository on your computer. This is you main project in which you develop your app and the associated pipeline.
* __target.path__ - Contains the location (directory) of the local Git repository used to test the pipeline. You are not actively working in this repo.\
  It is only used for the junit_pipeline framework to communicate with the Azure DevOps test project. Before you start, this directory must not exist.
* __repository.pipeline.path__ - The location of the main pipeline file in the repository (both in source and target).
* __pipeline.yaml__ - The location of the main pipeline file. __repository.pipeline.path__ and __pipeline.yaml__ are the same (TODO: to be changed). 
* __target.repository.name__ - The name of the repository used in the Git repository used for testing. Best is to keep the source and target repository names identical
* __target.repository.uri__ - The URL of the git repos in the target Azure DevOps project. Must be something like https://dev.azure.com/my-organization-name/my-azure-devops-project/_git
* __endpoint__ - The URL of the apis in the target Azure DevOps project. Must be something like https://dev.azure.com/my-organization-name/my-azure-devops-project/_apis\
  (TODO: must be combined).
* __target.repository.user__ - User used in the Azure DevOps API calls to the test project. Can be the default name 'UserWithToken'.
* __target.repository.password__ - The PAT (Personal Access Token) used in the Azure DevOps API calls to the test project.\
  See https://learn.microsoft.com/en-us/azure/devops/organizations/accounts/use-personal-access-tokens-to-authenticate?view=azure-devops&tabs=Windows how to create a PAT.\
  Make sure this PAT is authorized for all pipelines used in the Azure DevOps test project.
* __git.commit.pattern__ - Defines the type of files pushed to the local- and remote test repo (this is a subset of the files from the main repo)
* __project.id__ - Defines the test project in Azure DevOps in which the pipelines are tested. Enter https://dev.azure.com/my-organization-name/_apis/projects in the browser to retrieve this information.
* __build.api.poll.frequency__ - The result of a pipeline run is retrieved using an Azure DevOps API. This API is called with a frequency determined by __build.api.poll.frequency__ (in seconds).  
* __build.api.poll.timeout__ - The timeout value of polling the result of the pipeline run. If the final result is not retrieved yet, the polling stops after a number of seconds, defined by  __build.api.poll.timeout__.

### Known limitations ##
* ~~The updated pipeline code is pushed to the _default branch_ in the test project (master); pushing to other branches is not possible.~~
* Only YAML templates in the same repository are taken into account. Templates in other repositories (identified with a @ behind the template name) are ignored.
* If the pipeline makes use of a resource in the test project for the first time, it needs manual approval first; for example, a variable group or an Environment.
* If unknown service connections are used or the updated pipeline code is not valid YAML anymore, the AzDo API returns an HTTP status code 400.
* No methods yet to add, update or remove conditions in stages or jobs. Use the _overrideLiteral_ method, if possible.
* At the start, the local target repository and the remote target repository (of the test project) can become out-of-sync. Delete both the local and the remote repo and start again.
* Copying files from the main local repo to the test local repo involves exclusion of files, using an exclusion list. This list is currently hardcoded\
  and contains "idea, target, .git and class". This should be made configurable in the junit_pipeline.properties file.
* The project id of the Azure DevOps test project must be configured manually and is not (yet) derived automatically.

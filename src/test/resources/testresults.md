# PipelineUnit test results
The expected results of each test is described in this document. Validating the test results is not automated (yet).

### test1 ###
***
* The test is executed in the Azure DevOps test project
* The pipeline is called _junit-pipeline-test_
* The test is executed in the _master_ branch
* Three stages are executed:
  * __template-stages.yml__; this stage is defined in __templates/stages/template-stages.yml__
  * __ExecuteScriptStage__
  * __DeployStage__
<br></br>

#### Stage: template-stages.yml ####
* This stage executes:
  * __template-stages.yml job__
    * This job executes a script:
      * _This is job: Job_B with parameter aNiceValue_
      * _aNiceDefault_ is replaced with _aNiceValue_
  * __template-jobs.yml job__
    * 
<br></br>

#### Stage: ExecuteScriptStage ####
<br></br>

#### Stage: DeployStage ####
<br></br>

### test2 ###
***
* The test is executed in the Azure DevOps test project
* The pipeline is called _junit-pipeline-test_
* The test is executed in the _myFeature_ branch
* Two stages are executed

### test3 ###
***
* The test is not executed in Azure DevOps because it used the _dryRun=true_ argument in the startPipeline() method


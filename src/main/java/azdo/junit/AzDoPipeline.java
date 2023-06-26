// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.action.*;
import azdo.hook.Hook;
import azdo.utils.*;
import azdo.yaml.YamlDocumentEntryPoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static azdo.utils.Constants.*;

/*
   AzDoPipeline is used in JUnit tests; it acts as a Java representation of an Azure DeVOps pipeline.
   It encapsulates classes that represents the Azure DevOps pipeline and template files (YAML).
   All AzDoPipeline methods used by the JUnit tests are forwarded to the encapsulated objects.
*/
public class AzDoPipeline {
    private static Log logger = Log.getLogger();
    private PropertyUtils properties;
    private Git git = null;
    private CredentialsProvider credentialsProvider;
    String yamlFile;
    Map<String, Object> yamlMap = null;
    String repositoryId = null;
    String pipelineId = null;
    RunResult runResult = new RunResult();
    private YamlDocumentEntryPoint yamlDocumentEntryPoint;
    private static final String EXCLUDEFILESLIST = "\\excludedfileslist.txt";

    @SuppressWarnings("java:S1192")
    public AzDoPipeline(String propertyFile, String pipelineFile) {
        logger.debug("==> Object: AzDoPipeline");
        logger.debug("propertyFile {}:", propertyFile);
        logger.debug("pipelineFile {}:", pipelineFile);

        // Validate the main pipeline file before any other action
        // If it is not valid, the test may fail
        properties = new PropertyUtils(propertyFile);
        Utils.validatePipelineFile(pipelineFile, properties.isContinueOnError());

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("Start AzDoPipeline: Initializing repository and pipeline");
        logger.debug(DEMARCATION);

        // Read the properties file and create the entry point
        yamlDocumentEntryPoint = new YamlDocumentEntryPoint(properties.getSourcePath(),
                properties.getTargetPath(),
                properties.getSourceBasePathExternal(),
                properties.getTargetBasePathExternal(),
                properties.getSourceRepositoryName(),
                properties.getTargetRepositoryName());

        // Read the main pipeline file; this is the YAML file used in the Azure DevOps pipeline (in the Azure DeVOps test project)
        yamlMap = yamlDocumentEntryPoint.read(pipelineFile, properties.isContinueOnError());

        // The external resources section in the main YAML file is parsed and a list of repositories are stored in
        // the yamlDocumentEntryPoint. These repositories contain template YAML files, referred to in the pipeline.
        // In addition, these repositories are cloned and the source files are stored locally
        // (on the filesystem of the workstation). Because these repositories are also used in the Azure DevOps
        // test project, they are pushed to to this project. Any link in the pipeline YAML file with the original
        // repository location is removed.
        yamlDocumentEntryPoint.initExternalResources(yamlMap, properties);

        yamlFile = pipelineFile;
        credentialsProvider = new UsernamePasswordCredentialsProvider(
                properties.getAzDoUser(),
                properties.getAzdoPat());

        // If no repository exists, create a new repo in Azure DevOps. Otherwise, make use of the existing repository
        // This concerns the repository containing the main YAML pipeline file.
        // Similar to the repositories defined in the resources section of the main pipeline file, this repository
        // is cloned from its original location and pushed to the Azure DevOps test project.
        repositoryId = AzDoUtils.createRepositoryIfNotExists (properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getTargetPath(),
                properties.getTargetRepositoryName(),
                properties.getTargetOrganization(),
                properties.getTargetProject(),
                properties.getAzdoBaseUrl(),
                properties.getAzdoEndpoint(),
                properties.getGitApi(),
                properties.getGitApiVersion(),
                properties.getProjectApi(),
                properties.getProjectApiVersion(),
                properties.getGitApiRepositories());

        // Create a new pipeline is needed; the name of the pipeline is the same as the name of the target repository
        pipelineId = AzDoUtils.createPipelineIfNotExists (properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getPipelinePathRepository(),
                properties.getTargetRepositoryName(),
                properties.getAzdoEndpoint(),
                properties.getPipelinesApi(),
                properties.getPipelinesApiVersion(),
                repositoryId);

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("End AzDoPipeline: Initializing repository and pipeline");
        logger.debug(DEMARCATION);
        logger.debug("");
    }

    /* After the YAML files have been manipulated in the JUnit tests, the startPipeline method is called.
       This method creates a new (target) YAML file, containing the manipulated settings in the local
       target repository (associated with the Azure DeVOps test project).
       The local repositories are committed and pushed to the remote repositories in the Azure DeVOps test project.
       After all files are pushed, the pipeline in Azure Devops is called by means of an API.
       The last step is to reload the original yaml file, so it can be used for the next test.
       The startPipeline() method has different flavors, that allow to pass hooks or perform a dryrun (not starting the pipeline).
     */
    public void startPipeline() throws IOException {
        startPipeline(GitUtils.BRANCH_MASTER, null, false);
    }
    public void startPipeline(boolean dryRun) throws IOException {
        startPipeline(GitUtils.BRANCH_MASTER, null, dryRun);
    }
    public void startPipeline(String branchName) throws IOException {
        startPipeline(branchName, null, false);
    }
    public void startPipeline(String branchName, boolean dryRun) throws IOException {
        startPipeline(branchName, null, dryRun);
    }
    public void startPipeline(String branchName, List<Hook> hooks) throws IOException {
        startPipeline(branchName, hooks, false);
    }
    public void startPipeline(String branchName, List<Hook> hooks, boolean dryRun) throws IOException {
        logger.debug("==> Method: AzDoPipeline.startPipeline");
        logger.debug("branchName: {}", branchName);
        logger.debug("dryRun {}:", dryRun);

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("Start pipeline {} for branch {}", properties.getTargetRepositoryName(), branchName);
        logger.debug(DEMARCATION);

        /*******************************************************************************************
                                  Doing stuff for the main repository
         *******************************************************************************************/

        // Clone the repository to local if not done earlier
        // Keep the reference to the git object
        try {
            // Clone the main repository to local and initialize
            git = GitUtils.cloneAzdoToLocal(properties.getTargetPath(),
                    properties.getTargetRepositoryName(),
                    properties.getAzDoUser(),
                    properties.getAzdoPat(),
                    properties.getTargetOrganization(),
                    properties.getTargetProject());
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot clone repository to local: {}", e.getMessage());
            return;
        }

        // If git object is invalid after the clone or if the repository was not cloned, recreate the git object again
        if (git == null) {
            logger.debug("Recreate git object");
            git = GitUtils.createGit(properties.getTargetPath());
        }

        // Check whether there is a remote branch; pipelines can be started using files from any branch
        boolean isRemote = GitUtils.containsBranch(git, branchName);

        // Perform the checkout. This may fail, but that's not a problem. The main concern is that
        // the remote branch is created in the repository in the Azure DevOps test project
        GitUtils.checkout(git, properties.getTargetPath(), branchName, !isRemote);

        // Copy local resources from main source to target directory
        try {
            // Copy all sources from the source local repo to the target local repo
            Utils.copyAll(properties.getSourcePath(), properties.getTargetPath(), properties.getTargetExludeList());
        }
        catch (Exception e) {
            logger.debug("Exception occurred.Cannot copy local files to target: {}", e.getMessage());
        }

        /*******************************************************************************************
                              Doing stuff for the external repositories
         *******************************************************************************************/

        // Copy all resources from a local version of the external repositories.
        // This cleans up the local 'external resources' repositories after it was poluted by the previous testrun
        yamlDocumentEntryPoint.copyAllSourceFiles(properties.getTargetExludeList());

        // Repositories in the resources section of the yaml pipeline are copied to the Azure DevOps
        // test project. This makes them git repositories, all with type = git (which means Azure DevOps),
        // and all in the same Azure DevOps project. This is independent whether the repositories are
        // originally from another Azure DeVOps project or from GithHub.
        yamlDocumentEntryPoint.makeResourcesLocal();


        /*******************************************************************************************
                                      Prepare for running the pipeline
         *******************************************************************************************/

        // Save the manipulated main YAML (incl. template files) to the target location.
        // The manipulated YAML files are stored in memory (in a YamlDocument or YamlTemplate object). The target
        // location is a local repository, with a remote repository residing in the Azure DevOps test project.
        // Manipulation is performed in JUnit tests by calling the pipeline actions (overrideVariable, overrideLiteral. etc...)
        yamlDocumentEntryPoint.dumpYaml();

        // Perform all (pre)hooks
        if (hooks != null) {
            logger.debug("Execute hooks");
            int size = hooks.size();
            for (int i = 0; i < size; i++) {
                hooks.get(i).executeHook();
            }
        }

        /*******************************************************************************************
           Push everything to the main and external repositories in the Azure DevOps test project
         *******************************************************************************************/

        // Push the local (main) repo to remote; this is the repository containing the main pipeline YAML file.
        GitUtils.commitAndPush(git,
                properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getCommitPatternList());

        if (git != null)
            git.close();

        // Commit and Push all external repositories to remote.
        // The repositoryList is maintained by the YamlDocumentEntryPoint, so delegate to the YamlDocumentEntryPoint.
        // This results in pushing all manipulated template files to the remote repositories in the Azure DevOps test project.
        yamlDocumentEntryPoint.commitAndPushTemplates (properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getCommitPatternList());

        /*******************************************************************************************
                         Run the pipeline and retrieve the pipeline run result
         *******************************************************************************************/
        // Call Azure Devops API to start the pipeline and retrieve the result.
        // If dryRun is true, the pipeline does not start.
        if (!dryRun) {
            AzDoUtils.callPipelineRunApi (properties.getAzDoUser(),
                    properties.getAzdoPat(),
                    properties.getAzdoEndpoint(),
                    properties.getBuildApi(),
                    properties.getBuildApiVersion(),
                    pipelineId,
                    branchName);
            runResult = AzDoUtils.callRunResult (properties.getAzDoUser(),
                    properties.getAzdoPat(),
                    properties.getBuildApiPollFrequency(),
                    properties.getBuildApiPollTimeout(),
                    properties.getAzdoEndpoint(),
                    properties.getBuildApi(),
                    properties.getBuildApiVersion(),
                    pipelineId);
        }
        else {
            logger.debug("dryRun is true; skip executing the pipeline");
        }

        // Re-read the original pipeline for the next test (for a clean start of the next test)
        // The manipulated, in-memory stored YAML files are refreshed with the content of the original (source) files.
        yamlMap = yamlDocumentEntryPoint.read(yamlFile, properties.isContinueOnError());

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("End pipeline {} for branch {}", properties.getTargetRepositoryName(), branchName);
        logger.debug(DEMARCATION);
    }

    /* Skip a job.
       The result is, that the job is completely removed from the output pipeline yaml file, which basically is
       the same as skipping it.

       Example:
       =========
       - job: my_job
         displayName: 'This is my job'

       Call skipJob("my_job")
       ==> The job with name "my_job" is skipped
     */

//    @Deprecated
//    public void skipJob(String jobName) {
//        logger.debug("==> Method: AzDoPipeline.skipJob: {}", jobName);
//        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
//                SECTION_JOBS,
//                "",
//                "",
//                "",
//                SECTION_JOB,
//                jobName,
//                false);
//    }

    /* Skip a step. This can be of stepType task, script or checkout
       If only 'stepType' is provided as argument, all steps of this type are skipped (e.g. all scripts are skipped)
       If the value of 'identifier' is the displayName of the step, all steps of type 'stepType' with that given
       displayName are skipped.
       If no step with this given displayName can be found, the steps of type 'stepType' with an 'id' in the yaml
       are searched and if found, these steps are skipped.

       Example 1:
       =========
       - task: Maven@3
         displayName: 'Maven Package'

       Call skipStep("task", "Maven@3")
       ==> All tasks with id "Maven@3" are skipped

       Example 2:
       =========
       - task: Maven@3
         displayName: 'Maven Package'

       Call skipStep("task", "Maven Package")
       ==> All tasks with displayName 'Maven Package' are skipped
     */
    // TODO: Look into this; does not seem right
    @Deprecated
//    public void skipStep(String stepName) {
//        logger.debug("==> Method: AzDoPipeline.skipStep: {}", stepName);
//        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
//                SECTION_STEPS,
//                "",
//                "",
//                "",
//                SECTION_STEP,
//                stepName,
//                false);
//        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
//                SECTION_STEPS,
//                "",
//                "",
//                "",
//                SECTION_TASK,
//                stepName,
//                false);
//    }

    /* The original step is replaced by a mock step. This is a step of type script. The argument 'inlineScript' is
       added to the mock. Depending on the job pool this can be a Powershell script (Windows) or a bash script (Linux)
     */

//    @Deprecated
//    public void mockStep(String stepValue, String inlineScript){
//        logger.debug("==> Method: AzDoPipeline.mockStep: {}", stepValue);
//        yamlDocumentEntryPoint.executeCommand(ActionEnum.mock,
//                SECTION_STEPS,
//                "",
//                "",
//                "",
//                stepValue,
//                inlineScript,
//                false);
//    }

    public RunResult getRunResult() {
        return runResult;
    }

    public void setRunResult(RunResult runResult) {
        this.runResult = runResult;
    }

    public PropertyUtils getProperties() {
        return properties;
    }

    /******************************************************************************************
     Skip a stage.
     The result is, that the stage is completely removed from the output pipeline yaml file,
     which basically is the same as skipping it.

     Example:
     =========
     - stage: my_stage
       displayName: 'This is my stage'

     Call skipStage("my_stage")
     ==> The stage with name "my_stage" is skipped
     ******************************************************************************************/
        public void skipStageSearchByIdentifier (String stageIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByIdentifier");
        logger.debug("stageIdentifier: {}", stageIdentifier);

        // Call the performAction method; find "stage" section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection("stage", stageIdentifier),
                "stage",
                stageIdentifier);
    }

    /******************************************************************************************
     Skip a stage, but search it using the displayName
     ******************************************************************************************/
    public void skipStageSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a "stage" section
        // If a stage is found (can be any stage), determine whether its property name (in this case "displayName"), has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty("stage", "displayName", displayValue),
                "stage",
                "");
    }

    // Same as the previous one, but instead of a fixed property (displayName), another property can be used to skip the stage

    /******************************************************************************************
     Same as the previous one, but instead of a fixed property (displayName), another property
     can be used to skip the stage ('pool', if you want).
     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public void skipStageSearchByProperty (String property, String propertyValue) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByProperty");
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        // Call the performAction method; find "stage" section with the identifier
        // If a stage is found, determine whether the given property has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty("stage", property, propertyValue),
                "stage",
                "");
    }

    /******************************************************************************************
     Skip a job.
     The result is, that the job is completely removed from the output pipeline yaml file,
     which basically is the same as skipping it. This is similar to the 'skipJobSearchByIdentifier()'
     method.
     ******************************************************************************************/
    public void skipJobSearchByIdentifier (String jobIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipJobSearchByIdentifier");
        logger.debug("jobIdentifier: {}", jobIdentifier);

        // Call the performAction method; find "stage" section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection("job", jobIdentifier),
                "job",
                jobIdentifier);
    }


    /******************************************************************************************
     Same as skipStage(), but now any type of section can be skipped (for example "job" or
     "task"). The section is searched using the 'sectionIdentifier'.
     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public void skipSectionSearchByTypeAndIdentifier (String sectionType, String sectionIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipSectionSearchByTypeAndIdentifier");
        logger.debug("sectionType: {}", sectionType); // "stage", "task", "job"
        logger.debug("sectionIdentifier: {}", sectionIdentifier);

        // Call the performAction method; find the section defined by sectionType, with the sectionIdentifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(sectionType, sectionIdentifier),
                sectionType,
                sectionIdentifier);
    }

    /******************************************************************************************
     Same as the previous one, but instead of a "stage", any section can be defined (for
     example "job" or "task"). Searching can be done using any property of the section.
     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public void skipSectionSearchByProperty (String sectionType, String property, String propertyValue) {
        logger.debug("==> Method: AzDoPipeline.skipSectionSearchByProperty");
        logger.debug("sectionType: {}", sectionType);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        // Call the performAction method; find "stage" section with the identifier
        // If a stage is found, determine whether the given property has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(sectionType, property, propertyValue),
                sectionType,
                "");
    }

    /******************************************************************************************
     Inserts a yaml section (step) bfore a given step.
     @see azdo.action.ActionInsertSection
     ******************************************************************************************/
    public void insertBeforeStepSearchByIdentifier (String stepIdentifier, Map<String, Object> stepToInsert) {
        logger.debug("==> Method: AzDoPipeline.insertBeforeStepSearchByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // Can be a Maven@3 task
        logger.debug("stepToInsert: {}", stepToInsert);

        // Call the performAction method; find the "step" section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionInsertSection("task", stepIdentifier, stepToInsert, true),
                "task",
                stepIdentifier);

        // It can also be a "task", so try that one also
        yamlDocumentEntryPoint.performAction (new ActionInsertSection("step", stepIdentifier, stepToInsert, true),
                "step",
                stepIdentifier);
    }

    /******************************************************************************************
     Inserts a yaml section (step) after a given step.
     @see azdo.action.ActionInsertSection
     ******************************************************************************************/
    public void insertAfterStepSearchByIdentifier (String stepIdentifier, Map<String, Object> stepToInsert) {
        logger.debug("==> Method: AzDoPipeline.insertAfterStepSearchByIdentifier");
        logger.debug("stepType: {}", stepIdentifier); // Can be a Maven@3 task
        logger.debug("stepToInsert: {}", stepToInsert);

        // Call the performAction method; find the "step" section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionInsertSection("task", stepIdentifier, stepToInsert, false),
                "task",
                stepIdentifier);

        // It can also be a "task", so try that one also
        yamlDocumentEntryPoint.performAction (new ActionInsertSection("step", stepIdentifier, stepToInsert, false),
                "step",
                stepIdentifier);
    }

    /******************************************************************************************
     Replaces the value of a variable in the 'variables' section. Example:

     variables:
     - name: myVar
       value: myValue

     overrideVariable("myVar", "myNewValue") results in:
     variables:
     - name: myVar
       value: myNewValue

     This method does not replace variables defined in a Library.
     ******************************************************************************************/
    public void overrideVariable (String variableName, String value) {
        logger.debug("==> Method: AzDoPipeline.overrideVariable");
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);

        // Call the performAction method; find the "variables" section, and replace the old value of the variable (with name
        // 'variableName') with the new value
        yamlDocumentEntryPoint.performAction (new ActionOverrideElement(variableName, value, false),
                "variables",
                null);
    }

    /******************************************************************************************
     Sets (changes) the value of a variable (identified by "variableName") just before a certain
     step is executed. This means that the variable value is changed at runtime (while running
     the pipeline), unlike the overrideVariable() method, which replaces the value during
     pre-processing the pipelines.
     This step is found using the "stepIdentifier". The value of "stepType" is
     for example, "Maven@03". The methods searches for the first instance of a "Maven@03" task.
     ******************************************************************************************/
    public void setVariableBeforeStepSearchByIdentifier (String stepIdentifier, String variableName, String value) {
        logger.debug("==> Method: AzDoPipeline.setVariableBeforeStepSearchByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // Type can be a Maven@03 task, for example
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);

        // Create a script task that sets the value of a variable
        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        String s = "echo '##vso[task.setvariable variable=" + variableName + "]" + value .toString() + "'";
        stepToInsert.put("script", s);

        // Call the performAction method; find the "step" section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionInsertSection("task", stepIdentifier, stepToInsert, true),
                "task",
                stepIdentifier);

        // It can also be a "task", so try that one also
        yamlDocumentEntryPoint.performAction (new ActionInsertSection("step", stepIdentifier, stepToInsert, true),
                "step",
                stepIdentifier);
    }

    /******************************************************************************************
     Set the variable at runtime, just as the previous method, but search the step using the
     displayName. The step can be of any type "step", "tasks", or "script".
     ******************************************************************************************/
    public void setVariableBeforeStepSearchByDisplayName (String displayValue, String variableName, String value) {
        logger.debug("==> Method: AzDoPipeline.setVariableBeforeStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);

        // Create a script task that sets the value of a variable
        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        String s = "echo '##vso[task.setvariable variable=" + variableName + "]" + value .toString() + "'";
        stepToInsert.put("script", s);

        // Call the performAction method; find the "step" section with the displayName
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty("task", "displayName", displayValue, stepToInsert, true);
        yamlDocumentEntryPoint.performAction (actionTask, "task", "");

        // It can also be a "task", so try that one also
        //ActionInsertSectionByProperty actionStep = new ActionInsertSectionByProperty("step", "displayName", displayValue, stepToInsert, true);
        //yamlDocumentEntryPoint.performAction (actionStep, "step", "");

        // It can even be a "script" with that displayName
        //ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty("script", "displayName", displayValue, stepToInsert, true);
        //yamlDocumentEntryPoint.performAction (actionScript, "script", "");
    }

    /******************************************************************************************
     Replaces the value of a parameter in the 'template' section. Example:
     - template: step/mytemplate.yml
       parameters:
         tag: $(version)

     To replace the version to a fixed value (2.1.0), use:
     overrideTemplateParameter("tag", "2.1.0"). This results in:
     - template: step/mytemplate.yml@templates
       parameters:
         tag: 2.1.0
     ******************************************************************************************/
    public void overrideTemplateParameter(String parameterName, String value) {
        logger.debug("==> Method: AzDoPipeline.overrideTemplateParameter");
        logger.debug("parameterName: {}", parameterName);
        logger.debug("value: {}", value);

        // Call the performAction method; find the "templates" section, and replace the old value of the parameter (with name
        // 'parameterName') with the new value. The value of overrideFirstOccurrence must be 'true' because the
        // 'parameters' section of a template differs from the global parameters section.
        yamlDocumentEntryPoint.performAction (new ActionOverrideElement(parameterName, value, true),
                "parameters",
                null);
    }

    /******************************************************************************************
     Replaces the default value of a parameter in the 'parameters' section. Example:

     parameters:
     - name: myNumber
       type: number
       default: 2
       values:
       - 1
       - 2
       - 4
       - 8
       - 16

     overrideParameterDefault("myNumber", "8") result in:

     parameters:
     - name: myNumber
       type: number
       default: 8
       values:
       - 1
       - 2
       - 4
       - 8
       - 16
     ******************************************************************************************/
    public void overrideParameterDefault (String parameterName, String defaultValue) {
        logger.debug("==> Method: AzDoPipeline.overrideParameterDefault");
        logger.debug("parameterName: {}", parameterName);
        logger.debug("defaultValue: {}", defaultValue);

        // Call the performAction method; find the "parameters" section, and replace the old value of the parameter (with name
        // 'parameterName') with the new value
        ActionOverrideElement overrideParameterDefault = new ActionOverrideElement(parameterName,
                defaultValue,
                "name",
                "default",
                false);
        yamlDocumentEntryPoint.performAction (overrideParameterDefault,
                "parameters",
                null);
    }

    /******************************************************************************************
     Override (or overwrite) any arbitrary string in the yaml file.
     - task: AzureWebApp@1
       displayName: Azure Web App Deploy
       inputs:
         azureSubscription: $(azureSubscription)
         appName: samplewebapp

     Calling pipeline.overrideLiteral ("$(azureSubscription)", "1234567890") results in
     - task: AzureWebApp@1
       displayName: Azure Web App Deploy
       inputs:
         azureSubscription: 1234567890
         appName: samplewebapp
     ******************************************************************************************/

    public void overrideLiteral (String literalToReplace, String newValue, boolean replaceAll) {
        logger.debug("==> Method: AzDoPipeline.overrideLiteral");
        logger.debug("literalToReplace: {}", literalToReplace);
        logger.debug("value: {}", newValue);
        logger.debug("replaceAll: {}", replaceAll);

        // Find every instance of 'literalToReplace' and replace it with 'newValue'
        yamlDocumentEntryPoint.overrideLiteral(literalToReplace, newValue, replaceAll);
    }

    public void overrideLiteral (String literalToReplace, String newValue) {
        overrideLiteral(literalToReplace, newValue, true);
    }

    /******************************************************************************************
     Replace the current branch with a given branch name.
     Example: Assume the following condition:
     and(succeeded(), eq(variables['Build.SourceBranchName'], 'main'))

     After applying public void overrideCurrentBranch("myFeature") it becomes
     and(succeeded(), eq('myFeature', 'main'))

     if replaceAll is true, it replaces all occurences.
     If replaceAll is false, it only replaces the first occurence.
     ******************************************************************************************/
    public void overrideCurrentBranch (String newBranchName, boolean replaceAll){
        logger.debug("==> Method: AzDoPipeline.overrideCurrentBranch");
        logger.debug("newBranchName: {}", newBranchName);
        logger.debug("replaceAll: {}", replaceAll);

        overrideLiteral("variables[\'Build.SourceBranch\']", "\'refs/heads/" + newBranchName + "\'", replaceAll);
        overrideLiteral("$(Build.SourceBranch)", "refs/heads/" + newBranchName, replaceAll);
        overrideLiteral("variables[\'Build.SourceBranchName\']", "\'" + newBranchName + "\'", replaceAll);
        overrideLiteral("$(Build.SourceBranchName)", newBranchName, replaceAll);
        overrideLiteral("BUILD_SOURCEBRANCH", "refs/heads/" + newBranchName, replaceAll);
        overrideLiteral("BUILD_SOURCEBRANCHNAME", newBranchName, replaceAll);
    }

    public void overrideCurrentBranch (String newBranchName) {
        overrideCurrentBranch(newBranchName, true);
    }

    /******************************************************************************************
     // TODO
     ******************************************************************************************/
    public void mockStep (String stepIdentifier, String inlineScript){
        logger.debug("==> Method: YamlDocument.mockSection");
        logger.debug("stepIdentifier: {}", stepIdentifier); // For example AWSShellScript@1
        logger.debug("inlineScript: {}", inlineScript);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put("script", inlineScript);

        // Call the performAction method; find the step section with the stepIdentifier
        ActionUpdateSection action = new ActionUpdateSection("task", stepIdentifier, script);
        yamlDocumentEntryPoint.performAction (action, "task", stepIdentifier);
    }

    /******************************************************************************************
     TODO; Add methods:
     'Insert new steps with a condition and an exit 1'
     assertEqualsSearchStepByDisplayName (String displayValue, String variableName, String compareValue, boolean beforeStep)
     assertNotEqualsSearchStepByDisplayName (String displayValue, String variableName, String compareValue, boolean beforeStep)
     assertEmptySearchStepByDisplayName (String displayValue, String variableName, boolean beforeStep)

     assertEqualsSearchStepByIdentifier (String stepIdentifier, String variableName, String compareValue, boolean beforeStep)
     assertNotEqualsSearchStepByIdentifier (String stepIdentifier, String variableName, String compareValue, boolean beforeStep)
     assertEmptySearchStepByIdentifier (String stepIdentifier, String variableName, boolean beforeStep)

     TODO; Rename and expand methods:
     insertBeforeStepSearchByIdentifier --> insertStepSearchByIdentifier (requires boolean beforeStep)
     setVariableBeforeStepSearchByIdentifier --> setVariableSearchStepByIdentifier (requires boolean beforeStep)
     setVariableBeforeStepSearchByDisplayName --> setVariableSearchStepByDisplayName (requires boolean beforeStep)
    ******************************************************************************************/
}

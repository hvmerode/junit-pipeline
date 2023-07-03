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

    public enum AgentOSEnum {LINUX, WINDOWS};

    private AgentOSEnum agentOS = AgentOSEnum.LINUX; // Needed for OS-specific tasks

    private static final String EXCLUDEFILESLIST = "\\excludedfileslist.txt";

    @SuppressWarnings("java:S1192")
    public AzDoPipeline(String propertyFile,
                        String pipelineFile) {
        this (propertyFile, pipelineFile, AgentOSEnum.LINUX);
    }

    public AzDoPipeline(String propertyFile,
                        String pipelineFile,
                        AgentOSEnum agentOS) {
        logger.debug("==> Object: AzDoPipeline");
        logger.debug("propertyFile {}:", propertyFile);
        logger.debug("pipelineFile {}:", pipelineFile);
        logger.debug("agentOS {}:", agentOS);
        this.agentOS = agentOS;

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

        // If no repository exists, create a new repo in Azure DevOps. Otherwise, make use of the existing repository.
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

        // Create a new pipeline if needed; the name of the pipeline is the same as the name of the pipeline file
        // with a prefixed repository name and without extension(s).
        // Example pipeline name: my-repository.my-pipeline-yaml
        String pipelinePath = Utils.relativize(properties.getSourcePath(), pipelineFile);
        String pipelineName = properties.getTargetRepositoryName() + "#" + Utils.getFileNameWithoutPathAndExtension(pipelineFile, true);

        pipelineId = AzDoUtils.createPipelineIfNotExists (properties.getAzDoUser(),
                properties.getAzdoPat(),
                pipelinePath,
                pipelineName,
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

    /******************************************************************************************
    After the YAML files have been manipulated in the JUnit tests, the startPipeline method is called.
    This method creates a new (target) YAML file, containing the manipulated settings in the local
    target repository (associated with the Azure DeVOps test project).
    The local repositories are committed and pushed to the remote repositories in the Azure DeVOps test project.
    After all files are pushed, the pipeline in Azure Devops is called by means of an API.
    The last step is to reload the original yaml file, so it can be used for the next test.
    The startPipeline() method has different flavors, that allow to pass hooks or perform a dryrun (not starting the pipeline).
    *******************************************************************************************/
    public void startPipeline() throws IOException {
        startPipeline (GitUtils.BRANCH_MASTER, null, false);
    }

    /******************************************************************************************
     @param dryRun Does not start the pipeline in Azure DevOps
     *******************************************************************************************/
    public void startPipeline(boolean dryRun) throws IOException {
        startPipeline (GitUtils.BRANCH_MASTER, null, dryRun);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     *******************************************************************************************/
    public void startPipeline (String branchName) throws IOException {
        startPipeline (branchName, null, false);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     @param dryRun Does not start the pipeline in Azure DevOps
     *******************************************************************************************/
    public void startPipeline (String branchName,
                              boolean dryRun) throws IOException {
        startPipeline (branchName, null, dryRun);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     @param hooks List of hooks to be executed locally before the pipeline starts
     *******************************************************************************************/
    public void startPipeline (String branchName,
                              List<Hook> hooks) throws IOException {
        startPipeline (branchName, hooks, false);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     @param hooks List of hooks to be executed locally before the pipeline starts
     @param dryRun Does not start the pipeline in Azure DevOps
     *******************************************************************************************/
    public void startPipeline(String branchName,
                              List<Hook> hooks,
                              boolean dryRun) throws IOException {
        logger.debug("==> Method: AzDoPipeline.startPipeline");
        logger.debug("branchName: {}", branchName);
        logger.debug("dryRun: {}", dryRun);

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
            logger.info("Execute the pipeline remotely in Azure DevOps project \'{}\'", properties.getTargetProject());
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
                    pipelineId,
                    properties.isContinueOnError());

            // Runresult may be null; handle it gracefully
            // Return a new object with result and status are "undetermined"
            if (runResult == null)
                runResult = new RunResult();
        }
        else {
            logger.info("dryRun is true; skip executing the pipeline");
        }

        // Re-read the original pipeline for the next test (for a clean start of the next test)
        // The manipulated, in-memory stored YAML files are refreshed with the content of the original (source) files.
        yamlMap = yamlDocumentEntryPoint.read(yamlFile, properties.isContinueOnError());

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("End pipeline {} for branch {}", properties.getTargetRepositoryName(), branchName);
        logger.debug(DEMARCATION);
    }

    /******************************************************************************************
     @return Returns the result from Azure DevOps of the pipeline run
     *******************************************************************************************/
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
     @param stageIdentifier The identification of a stage

     Example:
     =========
     - stage: my_stage
       displayName: 'This is my stage'

     Call skipStageSearchByIdentifier("my_stage")
     Result: The stage with identifier "my_stage" is skipped
     ******************************************************************************************/
        public void skipStageSearchByIdentifier (String stageIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByIdentifier");
        logger.debug("stageIdentifier: {}", stageIdentifier);

        // Call the performAction method; find SECTION_STAGE with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_STAGE, stageIdentifier),
                SECTION_STAGE,
                stageIdentifier);
    }

    /******************************************************************************************
     Skip a stage, but search it using the displayName
     @param displayValue The displayName of the stage in the pipeline
     ******************************************************************************************/
    public void skipStageSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a SECTION_STAGE
        // If a stage is found (can be any stage), determine whether its property name (in this case DISPLAY_NAME), has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_STAGE, DISPLAY_NAME, displayValue),
                SECTION_STAGE,
                "");
    }

    /******************************************************************************************
     Same as the previous one, but instead of a fixed property (displayName), another property
     can be used to skip the stage ('pool', if you want).
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The value of this property

     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public void skipStageSearchByProperty (String property,
                                           String propertyValue) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByProperty");
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        // Call the performAction method; find SECTION_STAGE with the identifier
        // If a stage is found, determine whether the given property has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_STAGE, property, propertyValue),
                SECTION_STAGE,
                "");
    }

    /******************************************************************************************
     Skip a job.
     The result is, that the job is completely removed from the output pipeline yaml file,
     which basically is the same as skipping it. This is similar to the 'skipJobSearchByIdentifier()'
     method.
     @param jobIdentifier The identifier of a job
     ******************************************************************************************/
    public void skipJobSearchByIdentifier (String jobIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipJobSearchByIdentifier");
        logger.debug("jobIdentifier: {}", jobIdentifier);

        // Call the performAction method; find SECTION_JOB with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_JOB, jobIdentifier),
                SECTION_JOB,
                jobIdentifier);
    }

    /******************************************************************************************
     Skip a job, but search it using the displayName
     @param displayValue The value of the displayName property of a job
     ******************************************************************************************/
    public void skipJobSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipJobSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a SECTION_JOB
        // If it is found, determine whether its property name (in this case DISPLAY_NAME), has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_JOB, DISPLAY_NAME, displayValue),
                SECTION_JOB,
                "");
    }

    /******************************************************************************************
     Skip a step or task.
     @param stepIdentifier The identification of a step

     Example:
     =========
     - task: Maven@3
       displayName: 'Maven Package'

     Call skipStepSearchByIdentifier(SECTION_TASK, "Maven@3")
     Result: The "Maven@3" task is skipped

     Note, that finding a step using the stepIdentifier often does not provide a unique instance.
     If there are more instance of a step with the same identifier, use
     skipStepSearchByDisplayName() instead.
     ******************************************************************************************/
    public void skipStepSearchByIdentifier (String stepIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipStepSearchByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // For example AWSShellScript@1

        // Call the performAction method; find SECTION_TASK and SECTION_SCRIPT with the identifier
        // Other arguments besides SECTION_TASK are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_TASK, stepIdentifier),
                SECTION_TASK,
                stepIdentifier);
    }

    /******************************************************************************************
     Skip a step, but search it using the displayName
     @param displayValue The value of the displayName property of a step
     ******************************************************************************************/
    public void skipStepSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipStepSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a SECTION_TASK, or SECTION_SCRIPT
        // If it is found, determine whether its property name (in this case DISPLAY_NAME), has a certain value
        // Other arguments besides SECTION_TASK and SECTION_SCRIPT are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented

        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue),
                SECTION_TASK,
                "");
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_SCRIPT, DISPLAY_NAME, displayValue),
                SECTION_SCRIPT,
                "");
    }

    /******************************************************************************************
     Skip a template with a specific identifier. This is similar to the
     skipStageSearchByIdentifier() method but for templates.
     @param templateIdentifier The identification of a template

     It is identical to skipSectionSearchByTypeAndIdentifier ("template", "template-identifier");
     The skipTemplateSearchByIdentifier() method is just for convenience.
     ******************************************************************************************/
    public void skipTemplateSearchByIdentifier (String templateIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipTemplateSearchByIdentifier");
        logger.debug("stageIdentifier: {}", templateIdentifier);

        // Call the performAction method; find SECTION_TEMPLATE section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_TEMPLATE, templateIdentifier),
                SECTION_TEMPLATE,
                templateIdentifier);
    }


    /******************************************************************************************
     Same as SearchByIdentifier(), but now any type of section can be skipped (for example
     SECTION_JOB or SECTION_TASK). The section is searched using the 'sectionIdentifier'.
     This is the generalized version of all other skip-methods.
     @param sectionType Possible values ["stage", "job", "template", "task", ...]
     @param sectionIdentifier The identification of a section

     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public void skipSectionSearchByTypeAndIdentifier (String sectionType,
                                                      String sectionIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipSectionSearchByTypeAndIdentifier");
        logger.debug("sectionType: {}", sectionType); // SECTION_STAGE, SECTION_JOB, SECTION_TASK
        logger.debug("sectionIdentifier: {}", sectionIdentifier);

        // Call the performAction method; find the section defined by sectionType, with the sectionIdentifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(sectionType, sectionIdentifier),
                sectionType,
                sectionIdentifier);
    }

    /******************************************************************************************
     Same as the previous one, but instead of a SECTION_STAGE, any section can be defined (for
     example SECTION_JOB or SECTION_TASK). Searching can be done using any property of the section.
     @param sectionType Possible values ["stage", "job", "template", "task", ...]
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The value of this property

     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public void skipSectionSearchByProperty (String sectionType,
                                             String property,
                                             String propertyValue) {
        logger.debug("==> Method: AzDoPipeline.skipSectionSearchByProperty");
        logger.debug("sectionType: {}", sectionType);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        // If a section is found, determine whether the given property has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(sectionType, property, propertyValue),
                sectionType,
                "");
    }

    /******************************************************************************************
     Inserts a yaml section (step) before or after a given step.
     @param stepIdentifier The identification of a step
     @param stepToInsert The actual step to insert. Representation is a Map

     @see azdo.action.ActionInsertSection
     ******************************************************************************************/
    public void insertSectionSearchStepByIdentifier (String stepIdentifier,
                                                     Map<String, Object> stepToInsert) {
        insertSectionSearchStepByIdentifier (stepIdentifier, stepToInsert, true); // Default is to insert before a step
    }
    public void insertSectionSearchStepByIdentifier (String stepIdentifier,
                                                     Map<String, Object> stepToInsert,
                                                     boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.insertSectionSearchStepByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // Can be a Maven@3 task
        logger.debug("stepToInsert: {}", stepToInsert);
        logger.debug("insertBefore: {}", insertBefore);

        // Call the performAction method; find the SECTION_TASK section with the identifier
        // Other arguments besides SECTION_TASK are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        yamlDocumentEntryPoint.performAction (new ActionInsertSection(SECTION_TASK, stepIdentifier, stepToInsert, insertBefore),
                SECTION_TASK,
                stepIdentifier);
    }

    /******************************************************************************************
     Replaces the value of a variable in the 'variables' section.
     @param variableName The name of the variable as declared in the 'variables' section
     @param value The new value of the variable

     Example

     variables:
     - name: myVar
       value: myValue

     overrideVariable("myVar", "myNewValue") results in:
     variables:
     - name: myVar
       value: myNewValue

     This method does not replace variables defined in a Library.
     ******************************************************************************************/
    public void overrideVariable (String variableName,
                                  String value) {
        logger.debug("==> Method: AzDoPipeline.overrideVariable");
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);

        // Call the performAction method; find the SECTION_VARIABLES, and replace the old value of the variable (with name
        // 'variableName') with the new value
        yamlDocumentEntryPoint.performAction (new ActionOverrideElement(variableName, value, false),
                SECTION_VARIABLES,
                null);
    }

    /******************************************************************************************
     Sets (changes) the value of a variable (identified by "variableName") just before a certain
     step is executed. This means that the variable value is changed at runtime (while running
     the pipeline), unlike the overrideVariable() method, which replaces the value during
     pre-processing the pipelines.
     This step is found using the "stepIdentifier". The value of "stepIdentifier" is
     for example, "Maven@03". The methods searches for the first instance of a "Maven@03" task.
     @param stepIdentifier The identification of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param value The new value of the variable
     ******************************************************************************************/
    public void setVariableSearchStepByIdentifier (String stepIdentifier,
                                                   String variableName,
                                                   String value) {
        setVariableSearchStepByIdentifier (stepIdentifier, variableName, value, true); // Default is to set the value before a step
    }

    public void setVariableSearchStepByIdentifier (String stepIdentifier, String variableName, String value, boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.setVariableSearchStepByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // Type can be a Maven@03 task, for example
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);
        logger.debug("insertBefore: {}", insertBefore);

        // Create a script that sets the value of a variable
        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        String s;
        if (agentOS == AgentOSEnum.LINUX) {
            logger.debug("OS is Linux");
            s = "\"echo ##vso[task.setvariable variable=" + variableName + "]" + value.toString() + "\"";
        }
        else {
            logger.debug("OS is Windows");
            s = "echo ##vso[task.setvariable variable=" + variableName + "]" + value.toString();
        }
        stepToInsert.put(SECTION_SCRIPT, s);

        // Call the performAction method; find the SECTION_TASK section with the identifier
        // Other arguments besides SECTION_TASK are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        yamlDocumentEntryPoint.performAction (new ActionInsertSection(SECTION_TASK, stepIdentifier, stepToInsert, insertBefore),
                SECTION_TASK,
                stepIdentifier);
    }

    /******************************************************************************************
     Set the variable at runtime, just as the previous method, but search the step using the
     displayName. The step can be of any type "step", SECTION_TASK, or SECTION_SCRIPT.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param value The new value of the variable
     ******************************************************************************************/
    public void setVariableSearchStepByDisplayName (String displayValue,
                                                    String variableName,
                                                    String value) {
        setVariableSearchStepByDisplayName (displayValue, variableName, value, true); // Default is to set the value before a step
    }
    public void setVariableSearchStepByDisplayName (String displayValue,
                                                    String variableName,
                                                    String value,
                                                    boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.setVariableSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);
        logger.debug("insertBefore: {}", insertBefore);

        // Create a script that sets the value of a variable
        // Other arguments besides SECTION_TASK and SECTION_SCRIPT are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        String s;
        if (agentOS == AgentOSEnum.LINUX) {
            logger.debug("OS is Linux");
            s = "echo \"##vso[task.setvariable variable=" + variableName + "]" + value.toString() + "\"";
        }
        else {
            logger.debug("OS is Windows");
            s = "echo ##vso[task.setvariable variable=" + variableName + "]" + value.toString();
        }
        stepToInsert.put(SECTION_SCRIPT, s);

        // Call the performAction method; find the SECTION_TASK section with the DISPLAY_NAME
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
        yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // It can even be a SECTION_SCRIPT with that displayName
        ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty(SECTION_SCRIPT, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
        yamlDocumentEntryPoint.performAction (actionScript, SECTION_SCRIPT, "");
    }

    /******************************************************************************************
     Replaces the value of a parameter in the 'template' section.
     @param parameterName The name of the paramter as declared in the 'parameters' section of a template declaration
     @param value The new value of the parameter

     Example:
     - template: step/mytemplate.yml
       parameters:
         tag: $(version)

     To replace the version to a fixed value (2.1.0), use:
     overrideTemplateParameter("tag", "2.1.0"). This results in:
     - template: step/mytemplate.yml@templates
       parameters:
         tag: 2.1.0
     ******************************************************************************************/
    public void overrideTemplateParameter(String parameterName,
                                          String value) {
        logger.debug("==> Method: AzDoPipeline.overrideTemplateParameter");
        logger.debug("parameterName: {}", parameterName);
        logger.debug("value: {}", value);

        // Call the performAction method; find the "templates" section, and replace the old value of the parameter (with name
        // 'parameterName') with the new value. The value of overrideFirstOccurrence must be 'true' because the
        // 'parameters' section of a template differs from the global parameters section.
        yamlDocumentEntryPoint.performAction (new ActionOverrideElement(parameterName, value, true),
                SECTION_PARAMETERS,
                null);
    }

    /******************************************************************************************
     Replaces the default value of a parameter in the 'parameters' section. Example:
     @param parameterName The name of the paramter as declared in the 'parameters' section
     @param defaultValue The new default value of the parameter

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
    public void overrideParameterDefault (String parameterName,
                                          String defaultValue) {
        logger.debug("==> Method: AzDoPipeline.overrideParameterDefault");
        logger.debug("parameterName: {}", parameterName);
        logger.debug("defaultValue: {}", defaultValue);

        // Call the performAction method; find the SECTION_PARAMETERS section, and replace the old value of the parameter (with name
        // 'parameterName') with the new value
        ActionOverrideElement overrideParameterDefault = new ActionOverrideElement(parameterName,
                defaultValue,
                "name",
                "default",
                false);
        yamlDocumentEntryPoint.performAction (overrideParameterDefault,
                SECTION_PARAMETERS,
                null);
    }

    /******************************************************************************************
     Override (or overwrite) any arbitrary string in the yaml file.
     @param literalToReplace A substring of a yaml pipeline- or template definition
     @param newValue The substring is replaced by 'newValue'
     @param replaceAll Replace all occurences in all yaml files, including the templates

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

    public void overrideLiteral (String literalToReplace,
                                 String newValue,
                                 boolean replaceAll) {
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
     @param newBranchName New branch name that overrides an occurence of the current branch
     @param replaceAll Replace all occurences in all yaml files, including the templates

     Example: Assume the following condition:
     and(succeeded(), eq(variables['Build.SourceBranchName'], 'main'))

     After applying public void overrideCurrentBranch("myFeature") it becomes
     and(succeeded(), eq('myFeature', 'main'))

     if replaceAll is true, it replaces all occurences.
     If replaceAll is false, it only replaces the first occurence.
     ******************************************************************************************/
    public void overrideCurrentBranch (String newBranchName,
                                       boolean replaceAll) {
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
     Replace the identifier of a section (stage, job, vmImage, ...) with a new identifier value
     @param sectionType Possible values ["stage", "job", "template", "task", ...]
     @param sectionIdentifier The identification of a section
     @param newSectionIdentifier The new identification of a section
     ******************************************************************************************/
    // TODO: Still to test
    public void overrideSectionIdentifier (String sectionType,
                                           String sectionIdentifier,
                                           String newSectionIdentifier) {
        logger.debug("==> Method: AzDoPipeline.overrideSectionIdentifier");
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        logger.debug("newSectionIdentifier: {}", newSectionIdentifier);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put(sectionType, newSectionIdentifier);

        // Call the performAction method; find the section with the Identifier and replace it
        ActionUpdateSection action = new ActionUpdateSection(sectionType, sectionIdentifier, script);
        yamlDocumentEntryPoint.performAction (action, SECTION_TASK, sectionIdentifier);
    }

    /******************************************************************************************
     Find a section in the yaml, identified by a certain type (e.g. "pool", "stage", "task"),
     and an identifier (can have a value or empty). If the section is found, the value of the
     property is replaced by a new value ('propertyValue').
     @param sectionType Possible values ["stage", "job", "template", "task", ...]
     @param sectionIdentifier The identification of a section
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The new value of this property
     ******************************************************************************************/
    public void overrideSectionPropertySearchByTypeAndIdentifier (String sectionType,
                                                                  String sectionIdentifier,
                                                                  String property,
                                                                  String propertyValue) {
        logger.debug("==> Method: AzDoPipeline.overrideSectionPropertySearchByTypeAndIdentifier");
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        ActionOverrideElement action = new ActionOverrideElement (property,
                propertyValue,
                sectionType,
                sectionIdentifier,
        true);
        yamlDocumentEntryPoint.performAction (action, sectionType, sectionIdentifier);
    }

    /******************************************************************************************
     Replace the content of a step with an inline script. The step is searched using the
     'stepIdentifier', for example "AWSShellScript@1"
     @param stepIdentifier The identification of a step
     @param inlineScript The script
     ******************************************************************************************/
    public void  mockStepSearchByIdentifier (String stepIdentifier,
                                             String inlineScript){
        logger.debug("==> Method: YamlDocument. mockStepSearchByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // For example AWSShellScript@1
        logger.debug("inlineScript: {}", inlineScript);

        Map<String, Object> script = new LinkedHashMap<>();
        script.put(SECTION_SCRIPT, inlineScript);

        // Call the performAction method; find the step section with the stepIdentifier
        ActionUpdateSection action = new ActionUpdateSection(SECTION_TASK, stepIdentifier, script);
        yamlDocumentEntryPoint.performAction (action, SECTION_TASK, stepIdentifier);
    }

    /******************************************************************************************
     The assertEqualsSearchStepByDisplayName() method validates a variable during runtime of
     the pipeline. If the variable - with 'variableName' - is equal to 'compareValue', the
     pipeline aborts.
     The assertion is performed just before the execution of the step, identifier by the
     'displayName'.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param compareValue The value with which the variable or parameter is compared

     Example:
     Calling assertEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "myValue") means
     that just before the step with displayName "Deploy the app" is executed, the variable
     'myVar' value is compared with "myValue".
     If you want to validate just after execution of the step, call
     assertEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "myValue", false)
     ******************************************************************************************/
    public void assertEqualsSearchStepByDisplayName (String displayValue,
                                                     String variableName,
                                                     String compareValue) {
        assertEqualsSearchStepByDisplayName (displayValue, variableName, compareValue, true); // Default is to insert before a step
    }

    public void assertEqualsSearchStepByDisplayName (String displayValue,
                                                     String variableName,
                                                     String compareValue,
                                                     boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertVariableSearchStepByDisplayName (displayValue, variableName,compareValue, true, insertBefore);
    }

    /******************************************************************************************
     The assertNotEqualsSearchStepByDisplayName() method validates a variable during runtime of
     the pipeline. If the variable - with 'variableName' - is not equal to 'compareValue', the
     pipeline aborts.
     The assertion is performed just before the execution of the step, identifier by the
     'displayName'.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param compareValue The value with which the variable or parameter is compared
     ******************************************************************************************/
    public void assertNotEqualsSearchStepByDisplayName (String displayValue,
                                                        String variableName,
                                                        String compareValue) {
        assertNotEqualsSearchStepByDisplayName (displayValue, variableName, compareValue, true); // Default is to insert before a step
    }

    public void assertNotEqualsSearchStepByDisplayName (String displayValue,
                                                        String variableName,
                                                        String compareValue,
                                                        boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertNotEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertVariableSearchStepByDisplayName (displayValue, variableName,compareValue, false, insertBefore);
    }

    /******************************************************************************************
     Same as assertEqualsSearchStepByDisplayName() but it is compared to an empty value
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     ******************************************************************************************/
    public void assertEmptySearchStepByDisplayName (String displayValue,
                                                    String variableName) {
        assertEmptySearchStepByDisplayName (displayValue, variableName, true); // Default is to insert before a step
    }
    public void assertEmptySearchStepByDisplayName (String displayValue,
                                                    String variableName,
                                                    boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertEmptySearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("insertBefore: {}", insertBefore);

        assertVariableSearchStepByDisplayName (displayValue, variableName,"", true, insertBefore);
    }

    /******************************************************************************************
     Private method used for the previous assert-if-variable-has-value methods.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param compareValue The value with which the variable or parameter is compared
     ******************************************************************************************/
    private void assertVariableSearchStepByDisplayName (String displayValue,
                                                       String variableName,
                                                       String compareValue,
                                                       boolean equals,
                                                       boolean insertBefore) {

        // Create a script that compares the value of a variable
        Map<String, Object> stepToInsert;
        if (equals)
            stepToInsert = constructAssertStep(SECTION_VARIABLES, variableName, compareValue, OPERATOR_EQUALS);
        else
            stepToInsert = constructAssertStep(SECTION_VARIABLES, variableName, compareValue, OPERATOR_NOT_EQUALS);

        // Call the performAction method; find the SECTION_TASK with the displayName
        // Other arguments besides SECTION_TASK and SECTION_SCRIPT are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
        yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // It can even be a SECTION_SCRIPT with that displayName
        ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty(SECTION_SCRIPT, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
        yamlDocumentEntryPoint.performAction (actionScript, SECTION_SCRIPT, "");
    }

    /******************************************************************************************
     The assertFileNotExistsSearchStepByDisplayName() method validates at runtime the presence
     of a certain file on the Azure DevOps agent. If the file does not exists, the pipeline
     exists with an error.
     @param displayValue The value of the displayName property of a step
     @param fileName The file name of the file of which its existence is checked
     ******************************************************************************************/
    public void assertFileNotExistsSearchStepByDisplayName (String displayValue,
                                                            String fileName) {
        assertFileNotExistsSearchStepByDisplayName (displayValue, fileName, true); // Default is to insert before a step
    }

    public void assertFileNotExistsSearchStepByDisplayName (String displayValue,
                                                            String fileName,
                                                            boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertFileNotExistsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("fileName: {}", fileName);
        logger.debug("insertBefore: {}", insertBefore);

        // Create a Bash script or Powershell task that checks on the existence of a file
        Map<String, Object> assertStep = new LinkedHashMap<>();
        String s;
        if (agentOS == AgentOSEnum.LINUX) {
            // Linux
            logger.debug("OS is Linux");
            String echo = String.format("echo \"AssertFileNotExists: file '%s' is not present (or empty) on the Azure DevOps Agent\"\n", fileName);
            s = "if [ ! -f " + fileName + " ]; then\n" +
                    "    " + echo +
                    "    exit 1\n" +
                    "fi\n" +
                    "if [ ! -s " + fileName + " ]; then\n" +
                    "    " + echo +
                    "    exit 1\n" +
                    "fi\n";
            assertStep.put(SECTION_SCRIPT, s);
        }
        else {
            // Windows
            logger.debug("OS is Windows");
            Map<String, Object> inputs = new LinkedHashMap<>();
            assertStep.put(SECTION_TASK, "PowerShell@2");
            inputs.put("targetType", "inline");
            s = "$FilePath = \"" + fileName + "\"\n" +
            "if (-not(Test-path $FilePath -PathType leaf)) {\n" +
                    "    Write-Host \"AssertFileNotExists: file \'" + fileName + "\' is not present (or empty) on the Azure DevOps Agent\"\n" +
                    "    exit 1\n" +
                    "}";
            inputs.put("script", s);
            assertStep.put ("inputs", inputs);
        }

        // displayName
        s = "AssertFileNotExists: " + fileName;
        assertStep.put(DISPLAY_NAME, s);

        // Call the performAction method; find the SECTION_TASK section with the displayName
        // Other arguments besides SECTION_TASK and SECTION_SCRIPT are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, assertStep, insertBefore);
        yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // It can even be a SECTION_SCRIPT with that displayName
        ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty(SECTION_SCRIPT, DISPLAY_NAME, displayValue, assertStep, insertBefore);
        yamlDocumentEntryPoint.performAction (actionScript, SECTION_SCRIPT, "");
    }

    /******************************************************************************************
     Construct a step with a condition that validates a variable
     @param identifierType Possible values ["variables", "parameters"]
     @param identifier The value of the identification
     @param compareValue The value with which the variable or parameter is compared
     @param operator Possible values ["eq", "ne"]
     ******************************************************************************************/
    private Map<String, Object> constructAssertStep (String identifierType,
                                                     String identifier,
                                                     String compareValue,
                                                     String operator) {
        Map<String, Object> assertStep = new LinkedHashMap<>();

        String operatorRepresentation = "";
        if (OPERATOR_EQUALS.equals(operator))
            operatorRepresentation = "equal";
        if (OPERATOR_NOT_EQUALS.equals(operator))
            operatorRepresentation = "not equal";

        String s = "";

        // script
        if (SECTION_VARIABLES.equals(identifierType))
            s = String.format("echo \"AssertEquals: variable '%s' with value '$(%s)' is %s to '%s'\"\n", identifier, identifier, operatorRepresentation, compareValue);
        if (SECTION_PARAMETERS.equals(identifierType))
            s = String.format("echo \"AssertEquals: parameter '%s' with value '${{ parameters.%s }}' is %s to '%s'\"\n", identifier, identifier, operatorRepresentation, compareValue);
        s = s + "exit 1";
        assertStep.put(SECTION_SCRIPT, s);

        // displayName
        if (SECTION_VARIABLES.equals(identifierType))
            s = "AssertEquals variable " + identifier;
        if (SECTION_PARAMETERS.equals(identifierType))
            s = "AssertEquals parameter " + identifier;
        assertStep.put(DISPLAY_NAME, s);

        // condition
        if (SECTION_VARIABLES.equals(identifierType))
            s = operator + "(variables['" + identifier + "'], '" + compareValue + "')";
        if (SECTION_PARAMETERS.equals(identifierType))
            s = operator + "(parameters['" + identifier + "'], '" + identifier + "')";

        assertStep.put(CONDITION, s);

        return assertStep;
    }

    /******************************************************************************************
     TODO; Add methods:
     setVariableSearchTemplateByIdentifier (String templateIdentifier, String variableName, String value, boolean insertBefore)
    ******************************************************************************************/
}

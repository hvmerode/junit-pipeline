// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.action.*;
import azdo.hook.Hook;
import azdo.utils.*;
import azdo.yaml.ActionResult;
import azdo.yaml.YamlDocumentEntryPoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import java.io.IOException;
import java.util.*;

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

    // TODO: git, rm, ssh, cp, scp, rcp, sftp, rsync, mv, mkdir, touch, cat
    public String supportedBashCommands[] = { "curl", "wget", "ftp" }; // Valid commands for method mockBashCommandSearchStepByDisplayName()

    // TODO: Invoke-WebRequest
    public String supportedPowerShellCommands[] = { "Invoke-RestMethod" }; // Valid commands for method mockPowerShellCommandSearchStepByDisplayName()

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
    @throws IOException
    *******************************************************************************************/
    public void startPipeline() throws IOException {
        startPipeline (GitUtils.BRANCH_MASTER, null, false);
    }

    /******************************************************************************************
     @param dryRun Does not start the pipeline in Azure DevOps
     @throws IOException
     *******************************************************************************************/
    public void startPipeline(boolean dryRun) throws IOException {
        startPipeline (GitUtils.BRANCH_MASTER, null, dryRun);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     @throws IOException
     *******************************************************************************************/
    public void startPipeline (String branchName) throws IOException {
        startPipeline (branchName, null, false);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     @param dryRun Does not start the pipeline in Azure DevOps
     @throws IOException
     *******************************************************************************************/
    public void startPipeline (String branchName,
                              boolean dryRun) throws IOException {
        startPipeline (branchName, null, dryRun);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     @param hooks List of hooks to be executed locally before the pipeline starts
     @throws IOException
     *******************************************************************************************/
    public void startPipeline (String branchName,
                              List<Hook> hooks) throws IOException {
        startPipeline (branchName, hooks, false);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts
     @param hooks List of hooks to be executed locally before the pipeline starts
     @param dryRun Does not start the pipeline in Azure DevOps
     @throws IOException
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
        public AzDoPipeline skipStageSearchByIdentifier (String stageIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByIdentifier");
        logger.debug("stageIdentifier: {}", stageIdentifier);

        // Call the performAction method; find SECTION_STAGE with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_STAGE, stageIdentifier),
                SECTION_STAGE,
                stageIdentifier);

        return this;
    }

    /******************************************************************************************
     Skip a stage, but search it using the displayName
     @param displayValue The displayName of the stage in the pipeline
     ******************************************************************************************/
    public AzDoPipeline skipStageSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a SECTION_STAGE
        // If a stage is found (can be any stage), determine whether its property name (in this case DISPLAY_NAME), has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_STAGE, DISPLAY_NAME, displayValue),
                SECTION_STAGE,
                "");

        return this;
    }

    /******************************************************************************************
     Same as the previous one, but instead of a fixed property (displayName), another property
     can be used to skip the stage ('pool', if you want).
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The value of this property

     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public AzDoPipeline skipStageSearchByProperty (String property,
                                           String propertyValue) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByProperty");
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        // Call the performAction method; find SECTION_STAGE with the identifier
        // If a stage is found, determine whether the given property has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_STAGE, property, propertyValue),
                SECTION_STAGE,
                "");

        return this;
    }

    /******************************************************************************************
     Skip a job.
     The result is, that the job is completely removed from the output pipeline yaml file,
     which basically is the same as skipping it. This is similar to the 'skipJobSearchByIdentifier()'
     method.
     @param jobIdentifier The identifier of a job
     ******************************************************************************************/
    public AzDoPipeline skipJobSearchByIdentifier (String jobIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipJobSearchByIdentifier");
        logger.debug("jobIdentifier: {}", jobIdentifier);

        // Call the performAction method; find SECTION_JOB with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_JOB, jobIdentifier),
                SECTION_JOB,
                jobIdentifier);

        return this;
    }

    /******************************************************************************************
     Skip a job, but search it using the displayName
     @param displayValue The value of the displayName property of a job
     ******************************************************************************************/
    public AzDoPipeline skipJobSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipJobSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a SECTION_JOB
        // If it is found, determine whether its property name (in this case DISPLAY_NAME), has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_JOB, DISPLAY_NAME, displayValue),
                SECTION_JOB,
                "");

        return this;
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
    public AzDoPipeline skipStepSearchByIdentifier (String stepIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipStepSearchByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // For example AWSShellScript@1

        // Call the performAction method; find SECTION_TASK and SECTION_SCRIPT with the identifier
        // Other arguments besides SECTION_TASK are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_TASK, stepIdentifier),
                SECTION_TASK,
                stepIdentifier);

        return this;
    }

    /******************************************************************************************
     Skip a step, but search it using the displayName
     @param displayValue The value of the displayName property of a step
     ******************************************************************************************/
    public AzDoPipeline skipStepSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipStepSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method
        // If it is found, determine whether its property name (in this case DISPLAY_NAME), has a certain value
        ActionResult ar;
        ar = yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue),
                SECTION_TASK,
                "");
        if (ar == null || !ar.actionExecuted) {
            ar = yamlDocumentEntryPoint.performAction(new ActionDeleteSectionByProperty(STEP_SCRIPT, DISPLAY_NAME, displayValue),
                    STEP_SCRIPT,
                    "");
        }
        if (ar == null || !ar.actionExecuted) {
            ar = yamlDocumentEntryPoint.performAction(new ActionDeleteSectionByProperty(STEP_SCRIPT_BASH, DISPLAY_NAME, displayValue),
                    STEP_SCRIPT_BASH,
                    "");
        }
        if (ar == null || !ar.actionExecuted) {
            yamlDocumentEntryPoint.performAction(new ActionDeleteSectionByProperty(STEP_SCRIPT_PWSH, DISPLAY_NAME, displayValue),
                    STEP_SCRIPT_PWSH,
                    "");
        }

        return this;
    }

    /******************************************************************************************
     Skip a template with a specific identifier. This is similar to the
     skipStageSearchByIdentifier() method but for templates.
     @param templateIdentifier The identification of a template

     It is identical to skipSectionSearchByTypeAndIdentifier ("template", "template-identifier");
     The skipTemplateSearchByIdentifier() method is just for convenience.
     ******************************************************************************************/
    public AzDoPipeline skipTemplateSearchByIdentifier (String templateIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipTemplateSearchByIdentifier");
        logger.debug("stageIdentifier: {}", templateIdentifier);

        // Call the performAction method; find SECTION_TEMPLATE section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(SECTION_TEMPLATE, templateIdentifier),
                SECTION_TEMPLATE,
                templateIdentifier);

        return this;
    }

    /******************************************************************************************
     Same as SearchByIdentifier(), but now any type of section can be skipped (for example
     SECTION_JOB or SECTION_TASK). The section is searched using the 'sectionIdentifier'.
     This is the generalized version of all other skip-methods.
     @param sectionType Possible values ["stage", "job", "template", "task", ...]
     @param sectionIdentifier The identification of a section

     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public AzDoPipeline skipSectionSearchByTypeAndIdentifier (String sectionType,
                                                              String sectionIdentifier) {
        logger.debug("==> Method: AzDoPipeline.skipSectionSearchByTypeAndIdentifier");
        logger.debug("sectionType: {}", sectionType); // SECTION_STAGE, SECTION_JOB, SECTION_TASK
        logger.debug("sectionIdentifier: {}", sectionIdentifier);

        // Call the performAction method; find the section defined by sectionType, with the sectionIdentifier
        yamlDocumentEntryPoint.performAction (new ActionDeleteSection(sectionType, sectionIdentifier),
                sectionType,
                sectionIdentifier);

        return this;
    }

    /******************************************************************************************
     Same as the previous one, but instead of a SECTION_STAGE, any section can be defined (for
     example SECTION_JOB or SECTION_TASK). Searching can be done using any property of the section.
     @param sectionType Possible values ["stage", "job", "template", "task", ...]
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The value of this property

     @see  azdo.action.ActionDeleteSectionByProperty
     ******************************************************************************************/
    public AzDoPipeline skipSectionSearchByProperty (String sectionType,
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

        return this;
    }

    /******************************************************************************************
     Inserts a yaml section (step) before or after a given step.
     @param stepIdentifier The identification of a step
     @param stepToInsert The actual step to insert. Representation is a Map

     @see azdo.action.ActionInsertSection
     ******************************************************************************************/
    public AzDoPipeline insertSectionSearchStepByIdentifier (String stepIdentifier,
                                                             Map<String, Object> stepToInsert) {
        insertSectionSearchStepByIdentifier (stepIdentifier, stepToInsert, true); // Default is to insert before a step

        return this;
    }
    public AzDoPipeline insertSectionSearchStepByIdentifier (String stepIdentifier,
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

        return this;
    }

    /******************************************************************************************
     Inserts a script before or after a given step.
     @param displayValue The displayName of a step
     @param inlineScript The script to insert, before or after the step
     @param insertBefore Determines whether the script is inserted before (true) or after (false)
     the given step
     ******************************************************************************************/
    public AzDoPipeline insertScriptSearchStepByDisplayName (String displayValue,
                                                             String inlineScript,
                                                             boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.insertScriptSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue);
        logger.debug("inlineScript: {}", inlineScript);
        logger.debug("insertBefore: {}", insertBefore);

        Map<String, Object> scriptToInsert = new LinkedHashMap<>();
        scriptToInsert.put(STEP_SCRIPT, inlineScript);
        scriptToInsert.put(DISPLAY_NAME, "<Inserted> Script");

        // Call the performAction method; find the SECTION_TASK section with the DISPLAY_NAME
        ActionResult ar;
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, scriptToInsert, insertBefore);
        ar = yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // It can even be a SECTION_SCRIPT with that displayName
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty(STEP_SCRIPT, DISPLAY_NAME, displayValue, scriptToInsert, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionScript, STEP_SCRIPT, "");
        }

        // Or a SECTION_BASH
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionBash = new ActionInsertSectionByProperty(STEP_SCRIPT_BASH, DISPLAY_NAME, displayValue, scriptToInsert, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionBash, STEP_SCRIPT_BASH, "");
        }

        // It can even be a SECTION_POWERSHELL
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionPSScript = new ActionInsertSectionByProperty(STEP_SCRIPT_PWSH, DISPLAY_NAME, displayValue, scriptToInsert, insertBefore);
            yamlDocumentEntryPoint.performAction(actionPSScript, STEP_SCRIPT_PWSH, "");
        }

        return this;
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
    public AzDoPipeline overrideVariable (String variableName,
                                          String value) {
        logger.debug("==> Method: AzDoPipeline.overrideVariable");
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);

        // Call the performAction method; find the SECTION_VARIABLES, and replace the old value of the variable (with name
        // 'variableName') with the new value
        yamlDocumentEntryPoint.performAction (new ActionOverrideElement(variableName, value, false),
                SECTION_VARIABLES,
                null);

        return this;
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
    public AzDoPipeline setVariableSearchStepByIdentifier (String stepIdentifier,
                                                           String variableName,
                                                           String value) {
        setVariableSearchStepByIdentifier (stepIdentifier, variableName, value, true); // Default is to set the value before a step

        return this;
    }

    public AzDoPipeline setVariableSearchStepByIdentifier (String stepIdentifier,
                                                           String variableName,
                                                           String value,
                                                           boolean insertBefore) {
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
        stepToInsert.put(STEP_SCRIPT, s);

        s = "<Inserted> Set variable";
        stepToInsert.put(DISPLAY_NAME, s);

        // Call the performAction method; find the SECTION_TASK section with the identifier
        // Other arguments besides SECTION_TASK are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        yamlDocumentEntryPoint.performAction (new ActionInsertSection(SECTION_TASK, stepIdentifier, stepToInsert, insertBefore),
                SECTION_TASK,
                stepIdentifier);

        return this;
    }

    /******************************************************************************************
     Set the variable at runtime, just as the previous method, but search the step using the
     displayName. The step can be of any type "step", SECTION_TASK, or SECTION_SCRIPT.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param value The new value of the variable
     ******************************************************************************************/
    public AzDoPipeline setVariableSearchStepByDisplayName (String displayValue,
                                                    String variableName,
                                                    String value) {
        setVariableSearchStepByDisplayName (displayValue, variableName, value, true); // Default is to set the value before a step

        return this;
    }
    public AzDoPipeline setVariableSearchStepByDisplayName (String displayValue,
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
            // Add Window cmd script
            s = "echo ##vso[task.setvariable variable=" + variableName + "]" + value.toString();
        }
        stepToInsert.put(STEP_SCRIPT, s);

        s = "<Inserted> Set variable";
        stepToInsert.put(DISPLAY_NAME, s);

        // Call the performAction method; find the SECTION_TASK section with the DISPLAY_NAME
        ActionResult ar;
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
        ar = yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // It can even be a SECTION_SCRIPT with that displayName
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty(STEP_SCRIPT, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionScript, STEP_SCRIPT, "");
        }

        // Or a SECTION_BASH
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionBash = new ActionInsertSectionByProperty(STEP_SCRIPT_BASH, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionBash, STEP_SCRIPT_BASH, "");
        }

        // It can even be a SECTION_POWERSHELL
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionPSScript = new ActionInsertSectionByProperty(STEP_SCRIPT_PWSH, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
            yamlDocumentEntryPoint.performAction(actionPSScript, STEP_SCRIPT_PWSH, "");
        }

        return this;
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
    public AzDoPipeline overrideTemplateParameter(String parameterName,
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

        return this;
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
    public AzDoPipeline overrideParameterDefault (String parameterName,
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

        return this;
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

    public AzDoPipeline overrideLiteral (String literalToReplace,
                                         String newValue,
                                         boolean replaceAll) {
        logger.debug("==> Method: AzDoPipeline.overrideLiteral");
        logger.debug("literalToReplace: {}", literalToReplace);
        logger.debug("value: {}", newValue);
        logger.debug("replaceAll: {}", replaceAll);

        // Find every instance of 'literalToReplace' and replace it with 'newValue'
        yamlDocumentEntryPoint.overrideLiteral(literalToReplace, newValue, replaceAll);

        return this;
    }

    public AzDoPipeline overrideLiteral (String literalToReplace, String newValue) {
        overrideLiteral(literalToReplace, newValue, true);

        return this;
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
    public AzDoPipeline overrideCurrentBranch (String newBranchName,
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

        return this;
    }

    public AzDoPipeline overrideCurrentBranch (String newBranchName) {
        overrideCurrentBranch(newBranchName, true);

        return this;
    }

    /******************************************************************************************
     Replace the identifier of a section (stage, job, vmImage, ...) with a new identifier value
     @param sectionType Possible values ["stage", "job", "template", "task", ...]
     @param sectionIdentifier The identification of a section
     @param newSectionIdentifier The new identification of a section
     ******************************************************************************************/
    // TODO: Still to test
    public AzDoPipeline overrideSectionIdentifier (String sectionType,
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

        return this;
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
    public AzDoPipeline overrideSectionPropertySearchByTypeAndIdentifier (String sectionType,
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

        return this;
    }

    /******************************************************************************************
     Replace the content of a step with an inline script. The step is searched using the
     'stepIdentifier', for example "AWSShellScript@1"
     @param stepIdentifier The identification of a step
     @param inlineScript The inline script
     ******************************************************************************************/
    public AzDoPipeline mockStepSearchByIdentifier (String stepIdentifier,
                                                    String inlineScript){
        logger.debug("==> Method: AzDoPipeline.mockStepSearchByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // For example AWSShellScript@1
        logger.debug("inlineScript: {}", inlineScript);

        Map<String, Object> mockScript = new LinkedHashMap<>();
        mockScript.put(STEP_SCRIPT, inlineScript);
        mockScript.put(DISPLAY_NAME, "<Replaced> Mock script");

        // Call the performAction method; find the step section with the stepIdentifier
        ActionUpdateSection action = new ActionUpdateSection(SECTION_TASK, stepIdentifier, mockScript);
        yamlDocumentEntryPoint.performAction (action, SECTION_TASK, stepIdentifier);

        return this;
    }

    /******************************************************************************************
     Replace the content of a step with an inline script. The step is searched using the
     'displayName', for example "Deploy step"
     @param displayValue The value of the displayName property of a step
     @param inlineScript The inline script
     ******************************************************************************************/
    public AzDoPipeline mockStepSearchStepByDisplayName (String displayValue,
                                                         String inlineScript){
        logger.debug("==> Method: AzDoPipeline.mockStepSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue);
        logger.debug("inlineScript: {}", inlineScript);

        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        stepToInsert.put(STEP_SCRIPT, inlineScript);

        // Call the performAction method; find the task section with the displayName
        ActionResult ar;
        ActionUpdateSectionByProperty actionTask = new ActionUpdateSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, stepToInsert);
        ar = yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // Also check whether a script must be updated, instead of a task
        if (ar == null || !ar.actionExecuted) {
            ActionUpdateSectionByProperty actionScript = new ActionUpdateSectionByProperty(STEP_SCRIPT, DISPLAY_NAME, displayValue, stepToInsert);
            yamlDocumentEntryPoint.performAction(actionScript, STEP_SCRIPT, "");
        }

        return this;
    }

    /******************************************************************************************
     Mock a bash command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step
     @param command Bash command; for example "curl", "wget", "ftp"
     @param commandOutput The return value of the bash command

     Note: This method supports the following step types:
     - script
     - bash
     - Bash@3
     ******************************************************************************************/
    public AzDoPipeline mockBashCommandSearchStepByDisplayName (String displayValue,
                                                                String command,
                                                                String commandOutput){
        String[] commandOutputArray = new String[1];
        commandOutputArray[0] = commandOutput;
        return mockBashCommandSearchStepByDisplayName (displayValue, command, commandOutputArray);
    }

    /******************************************************************************************
     Mock a bash command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step
     @param command Bash command; for example "curl", "wget", "ftp"
     @param commandOutputArray The return value of the Bash command. This method signature takes
     an array of Strings. Reason is, that the step may contain multiple instances of the same command.
     The order of the String array is also the order in which the commands are located in the
     Bash script.

     Note: This method supports the following step types:
     - script
     - bash
     - Bash@3
     ******************************************************************************************/
    public AzDoPipeline mockBashCommandSearchStepByDisplayName (String displayValue,
                                                                String command,
                                                                String[] commandOutputArray){
        logger.debug("==> Method: AzDoPipeline.mockBashCommandSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue);
        logger.debug("command: {}", command);
        logger.debug("commandOutputArray: {}", commandOutputArray);

        // First, insert a section before the script, to override the Bash command
        String newLine = null;
        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        String s;
        String functionFileName;
        boolean retval = Arrays.asList(supportedBashCommands).contains(command);
        if (retval) {
            // Mock the command; create a bash script to override the command with a function
            functionFileName = "./" + command + "-mock.sh";
            s = getMockedBashCommandScript (command, "./" + command + "-mock.sh", commandOutputArray);
            stepToInsert.put(STEP_SCRIPT_BASH, s);

            // displayName
            s = "<Inserted> Mock " + command;
            stepToInsert.put(DISPLAY_NAME, s);
            newLine = ". " + functionFileName + "\n";
        }
        else {
            logger.warn("Mocking command \'{}\' is not yet supported", command);
        }

        if (newLine != null) {
            // Insert the bash step before a searched step of type 'script' (if found)
            ActionResult ar;
            ActionInsertSectionByProperty scriptAction = new ActionInsertSectionByProperty(STEP_SCRIPT, DISPLAY_NAME, displayValue, stepToInsert, true);
            ar = yamlDocumentEntryPoint.performAction (scriptAction, STEP_SCRIPT, "");

            // Add a new line to the subsequent script step
            ActionInsertLineInSection scriptActionInsertLineInSection = new ActionInsertLineInSection(STEP_SCRIPT, DISPLAY_NAME, displayValue, newLine);
            yamlDocumentEntryPoint.performAction(scriptActionInsertLineInSection, STEP_SCRIPT, "");

            if (ar == null || !ar.actionExecuted) {
                // If the script was not found, try to insert the bash step before a searched step of type 'bash'
                ActionInsertSectionByProperty bashAction = new ActionInsertSectionByProperty(STEP_SCRIPT_BASH, DISPLAY_NAME, displayValue, stepToInsert, true);
                ar = yamlDocumentEntryPoint.performAction(bashAction, STEP_SCRIPT_BASH, "");

                // Add a new line to the subsequent bash step
                ActionInsertLineInSection bashActionInsertLineInSection = new ActionInsertLineInSection(STEP_SCRIPT_BASH, DISPLAY_NAME, displayValue, newLine);
                yamlDocumentEntryPoint.performAction(bashActionInsertLineInSection, STEP_SCRIPT_BASH, "");
            }

            if (ar == null || !ar.actionExecuted) {
                // If the searched step is not a "bash" or "script" type, it may be a "Bash@3" task. Btw, use the SECTION_TASK instead of the TASK_BASH_3
                ActionInsertSectionByProperty actionBashTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, stepToInsert, true);
                yamlDocumentEntryPoint.performAction(actionBashTask, SECTION_TASK, "");

                // Insert a new line to the "Bash@3" script; the line contains a declaration of the function file created in the inserted step
                // The inline script in a "Bash@3" task is > inputs > script
                ActionInsertLineInInnerSection actionInsertLineInInnerSection = new ActionInsertLineInInnerSection(SECTION_TASK,
                        DISPLAY_NAME,
                        displayValue,
                        INPUTS,
                        SCRIPT,
                        newLine);
                yamlDocumentEntryPoint.performAction(actionInsertLineInInnerSection, TASK_BASH_3, "");
            }
        }

        return this;
    }


    /******************************************************************************************
     Mock a PowerShell command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step
     @param command PowerShell command
     @param commandOutput The return value of the PowerShell command

     Note: This method supports the following step types:
     - pwsh
     - PowerShell@2
     ******************************************************************************************/
    public AzDoPipeline mockPowerShellCommandSearchStepByDisplayName(String displayValue,
                                                                     String command,
                                                                     String commandOutput){
        String[] commandOutputArray = new String[1];
        commandOutputArray[0] = commandOutput;
        return mockPowerShellCommandSearchStepByDisplayName(displayValue, command, commandOutputArray);
    }

    /******************************************************************************************
     Mock a PowerShell command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step
     @param command PowerShell command
     @param commandOutputArray The return value of the PowerShell command. This method signature takes
     an array of Strings. Reason is, that the step may contain multiple instances of the same command.
     The order of the String array is also the order in which the commands are located in the
     PowerShell script.

     Note: This method supports the following step types:
     - pwsh
     - PowerShell@2
     ******************************************************************************************/
    public AzDoPipeline mockPowerShellCommandSearchStepByDisplayName(String displayValue,
                                                                     String command,
                                                                     String[] commandOutputArray){
        logger.debug("==> Method: AzDoPipeline.mockPowerShellCommandSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue);
        logger.debug("command: {}", command);
        logger.debug("commandOutputArray: {}", commandOutputArray);

        // First, insert a section before the script, to override the PS command
        String newLine = null;
        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        String s;
        switch (command) {
            case "Invoke-RestMethod": {
                // Construct the mock PowerShell function for the Invoke-RestMethod
                s = "{function Invoke-RestMethod {\n" +
                        "$global:InvokeRestMethodCounter++\n" +
                        "$strarry = @('{\"dummy\": \"dummy\"}', ";
                int size = commandOutputArray.length;
                int sizeMinusOne = size - 1;
                for (int i = 0; i < size; i++) {
                    s += "'" + commandOutputArray[i] + "'";
                    if (i < sizeMinusOne)
                        s += ", ";
                }
                s += ")\n";
                s += "$output = $($strarry[$InvokeRestMethodCounter]) | ConvertFrom-Json\n";
                s += "return $output\n";
                s += "}} > ./Invoke-RestMethod-mock.ps1";

                stepToInsert.put(STEP_SCRIPT_PWSH, s);

                // displayName
                s = "<Inserted> Mock Invoke-RestMethod";
                stepToInsert.put(DISPLAY_NAME, s);
                newLine = ". ./Invoke-RestMethod-mock.ps1\n";
                break;
            }
            default: {
                logger.warn("Mocking command \'{}\' is not yet supported", command);
                break;
            }
        }

        if (newLine != null) {
            // Insert a PowerShell (pwsh) script before the PowerShell (pwsh) script that is searched for
            ActionResult ar;
            ActionInsertSectionByProperty actionPSScript = new ActionInsertSectionByProperty(STEP_SCRIPT_PWSH, DISPLAY_NAME, displayValue, stepToInsert, true);
            yamlDocumentEntryPoint.performAction (actionPSScript, STEP_SCRIPT_PWSH, "");

            // Insert a new line to the "pwsh" script; the line contains a declaration of the function file created in the inserted step
            ActionInsertLineInSection actionInsertLineInSection = new ActionInsertLineInSection(STEP_SCRIPT_PWSH, DISPLAY_NAME, displayValue, newLine);
            ar = yamlDocumentEntryPoint.performAction(actionInsertLineInSection, STEP_SCRIPT_PWSH, "");

            if (ar == null || !ar.actionExecuted) {
                // If it is not a "pwsh" script, it may be a "PowerShel@2" task. Btw, use the SECTION_TASK instead of the TASK_POWERSHELL_2
                ActionInsertSectionByProperty actionPSTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, stepToInsert, true);
                yamlDocumentEntryPoint.performAction (actionPSTask, SECTION_TASK, "");

                // Insert a new line to the "PowerShell@2" script; the line contains a declaration of the function file created in the inserted step
                // The inline script in a "PowerShell@2" task is > inputs > script
                ActionInsertLineInInnerSection actionInsertLineInInnerSection = new ActionInsertLineInInnerSection(SECTION_TASK,
                        DISPLAY_NAME,
                        displayValue,
                        INPUTS,
                        SCRIPT,
                        newLine);
                yamlDocumentEntryPoint.performAction(actionInsertLineInInnerSection, TASK_POWERSHELL_2, "");
            }
        }

        return this;
    }

    private String getMockedBashCommandScript (String functionName, String functionFileName, String[] commandOutputArray) {
        // Because a global variable cannot be updated in a bash function, the value of commandCounter is stored in a file
        String s = "cat > " + functionFileName + " <<\'EOF\'\n" +
                "function " + functionName + " {\n" +
                "  rfile=\"./\"\n" +
                "  rfile+=$(echo $$)\n" +
                "  if [ ! -f \"$rfile\" ]; then\n" +
                "    echo \"-1\" > \"$rfile\"\n" +
                "  fi\n" +
                "  commandCounter=$(cat \"$rfile\")\n" +
                "  commandCounter=$((commandCounter + 1))\n" +
                "  echo $commandCounter > \"$rfile\"\n" +
                "  myArray=(";
                int size = commandOutputArray.length;
                int sizeMinusOne = size - 1;
                for (int i = 0; i < size; i++) {
                    s += "'" + commandOutputArray[i] + "'";
                    if (i < sizeMinusOne)
                        s += " ";
                }
                s += ")\n";
                s += "  local result=${myArray[$((commandCounter))]}\n";
                s += "  echo \"$result\"\n";
                s += "}\n";
                s += "EOF";
        return s;
    }

//    private String getMockedPSCommandScript (String functionName, String functionFileName, String commandOutput) {
//        String s = "{function " + functionName + " {\n" +
//                "return '" + commandOutput + "' | ConvertFrom-Json\n " +
//                "}} > " + functionFileName;
//        return s;
//    }

    /******************************************************************************************
     The assertVariableEqualsSearchStepByDisplayName() method validates a variable during
     runtime of the pipeline. If the variable - with 'variableName' - is not equal to
     'compareValue', the pipeline aborts.
     The assertion is performed just before the execution of the step, identifier by the
     'displayName'.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param compareValue The value with which the variable or parameter is compared

     Example:
     Calling assertVariableEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "myValue") means
     that just before the step with displayName "Deploy the app" is executed, the variable
     'myVar' value is compared with "myValue".
     If you want to validate just after execution of the step, call
     assertVariableEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "myValue", false)
     ******************************************************************************************/
    public AzDoPipeline assertVariableEqualsSearchStepByDisplayName (String displayValue,
                                                                     String variableName,
                                                                     String compareValue) {
        assertVariableEqualsSearchStepByDisplayName (displayValue, variableName, compareValue, true); // Default is to insert before a step

        return this;
    }

    public AzDoPipeline assertVariableEqualsSearchStepByDisplayName (String displayValue,
                                                                     String variableName,
                                                                     String compareValue,
                                                                     boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertVariableSearchStepByDisplayName (displayValue, variableName,compareValue, true, insertBefore);

        return this;
    }

    /******************************************************************************************
     The assertVariableNotEqualsSearchStepByDisplayName() method validates a variable during
     runtime of the pipeline. If the variable - with 'variableName' - is equal to
     'compareValue', the pipeline aborts.
     The assertion is performed just before the execution of the step, identifier by the
     'displayName'.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param compareValue The value with which the variable or parameter is compared
     ******************************************************************************************/
    public AzDoPipeline assertVariableNotEqualsSearchStepByDisplayName (String displayValue,
                                                                        String variableName,
                                                                        String compareValue) {
        assertVariableNotEqualsSearchStepByDisplayName (displayValue, variableName, compareValue, true); // Default is to insert before a step

        return this;
    }

    public AzDoPipeline assertVariableNotEqualsSearchStepByDisplayName (String displayValue,
                                                                        String variableName,
                                                                        String compareValue,
                                                                        boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertVariableNotEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertVariableSearchStepByDisplayName (displayValue, variableName,compareValue, false, insertBefore);

        return this;
    }

    /******************************************************************************************
     Same as assertVariableNotEqualsSearchStepByDisplayName() but it is compared to an empty value
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     ******************************************************************************************/
    public AzDoPipeline assertVariableNotEmptySearchStepByDisplayName (String displayValue,
                                                                       String variableName) {
        assertVariableNotEmptySearchStepByDisplayName (displayValue, variableName, true); // Default is to insert before a step

        return this;
    }

    public AzDoPipeline assertVariableNotEmptySearchStepByDisplayName (String displayValue,
                                                                       String variableName,
                                                                       boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertVariableNotEmptySearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("insertBefore: {}", insertBefore);

        assertVariableSearchStepByDisplayName (displayValue, variableName,"", false, insertBefore);

        return this;
    }

    /******************************************************************************************
     Same as assertVariableEqualsSearchStepByDisplayName() but it is compared to an empty value
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     ******************************************************************************************/
    public AzDoPipeline assertVariableEmptySearchStepByDisplayName (String displayValue,
                                                                    String variableName) {
        assertVariableEmptySearchStepByDisplayName (displayValue, variableName, true); // Default is to insert before a step

        return this;
    }
    public AzDoPipeline assertVariableEmptySearchStepByDisplayName (String displayValue,
                                                                    String variableName,
                                                                    boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertVariableEmptySearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("insertBefore: {}", insertBefore);

        assertVariableSearchStepByDisplayName (displayValue, variableName,"", true, insertBefore);

        return this;
    }

    /******************************************************************************************
     Private method used for the previous assert-if-variable-has-value methods.
     @param displayValue The value of the displayName property of a step
     @param variableName The name of the variable as declared in the 'variables' section
     @param compareValue The value with which the variable or parameter is compared
     ******************************************************************************************/
    private AzDoPipeline assertVariableSearchStepByDisplayName (String displayValue,
                                                                String variableName,
                                                                String compareValue,
                                                                boolean equals,
                                                                boolean insertBefore) {

        // Create a script that compares the value of a variable
        // Note, that if the boolean 'equals' is true, the condition results in "ne"
        // (the condition in the pipeline fails if the value of 'variableName' is not equal to the 'compareValue')
        // If the boolean 'is false, the condition results in eq".
        Map<String, Object> stepToInsert;
        if (equals)
            stepToInsert = constructAssertStep(SECTION_VARIABLES, variableName, compareValue, CONDITION_NOT_EQUALS);
        else
            stepToInsert = constructAssertStep(SECTION_VARIABLES, variableName, compareValue, CONDITION_EQUALS);

        // Call the performAction method; find the SECTION_TASK with the displayName
        // Other arguments besides "task", "script", bash", and "pwsh" are: powershell | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        ActionResult ar;
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
        ar = yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // It can even be a SECTION_SCRIPT with that displayName
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty(STEP_SCRIPT, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionScript, STEP_SCRIPT, "");
        }

        // It can even be a SECTION_BASH
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionBash = new ActionInsertSectionByProperty(STEP_SCRIPT_BASH, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionBash, STEP_SCRIPT_BASH, "");
        }

        // It can even be a SECTION_PWSH
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionPSScript = new ActionInsertSectionByProperty(STEP_SCRIPT_PWSH, DISPLAY_NAME, displayValue, stepToInsert, insertBefore);
            yamlDocumentEntryPoint.performAction(actionPSScript, STEP_SCRIPT_PWSH, "");
        }

        return this;
    }

    /******************************************************************************************
     The assertFileExistsSearchStepByDisplayName() method validates at runtime the presence
     of a certain file on the Azure DevOps agent. If the file does not exist, the pipeline
     exits with an error.
     @param displayValue The value of the displayName property of a step
     @param fileName The file name of the file of which its existence is checked
     ******************************************************************************************/
    public AzDoPipeline assertFileExistsSearchStepByDisplayName (String displayValue,
                                                                 String fileName) {
        assertFileExistsSearchStepByDisplayName (displayValue, fileName, true); // Default is to insert before a step

        return this;
    }

    public AzDoPipeline assertFileExistsSearchStepByDisplayName (String displayValue,
                                                                 String fileName,
                                                                 boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertFileExistsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("fileName: {}", fileName);
        logger.debug("insertBefore: {}", insertBefore);

        // Create a Bash script or PowerShell task that checks on the existence of a file
        Map<String, Object> assertStep = new LinkedHashMap<>();
        String s;
        if (agentOS == AgentOSEnum.LINUX) {
            // Linux
            logger.debug("OS is Linux");
            String echo = String.format("echo \"AssertFileExists: file '%s' is not present (or empty) on the Azure DevOps Agent\"\n", fileName);
            s = "if [ ! -f " + fileName + " ]; then\n" +
                    "    " + echo +
                    "    exit 1\n" +
                    "fi\n" +
                    "if [ ! -s " + fileName + " ]; then\n" +
                    "    " + echo +
                    "    exit 1\n" +
                    "fi\n";
            assertStep.put(STEP_SCRIPT, s);
        }
        else {
            // Windows
            // TODO: Change PowerShell@2 task into pwsh script
            logger.debug("OS is Windows");
            Map<String, Object> inputs = new LinkedHashMap<>();
            assertStep.put(SECTION_TASK, TASK_POWERSHELL_2);
            inputs.put("targetType", "inline");
            s = "$FilePath = \"" + fileName + "\"\n" +
            "if (-not(Test-path $FilePath -PathType leaf)) {\n" +
                    "    Write-Host \"AssertFileExists: file \'" + fileName + "\' is not present (or empty) on the Azure DevOps Agent\"\n" +
                    "    exit 1\n" +
                    "}";
            inputs.put(STEP_SCRIPT, s);
            assertStep.put ("inputs", inputs);
        }

        // displayName
        s = "<Inserted> AssertFileExists: " + fileName;
        assertStep.put(DISPLAY_NAME, s);

        // Call the performAction method; find the SECTION_TASK section with the displayName
        // Other arguments besides SECTION_TASK and SECTION_SCRIPT are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        ActionResult ar;
        ActionInsertSectionByProperty actionTask = new ActionInsertSectionByProperty(SECTION_TASK, DISPLAY_NAME, displayValue, assertStep, insertBefore);
        ar = yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // It can even be a "script" with that displayName
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionScript = new ActionInsertSectionByProperty(STEP_SCRIPT, DISPLAY_NAME, displayValue, assertStep, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionScript, STEP_SCRIPT, "");
        }

        // It can even be a "bash" script
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionBash = new ActionInsertSectionByProperty(STEP_SCRIPT_BASH, DISPLAY_NAME, displayValue, assertStep, insertBefore);
            ar = yamlDocumentEntryPoint.performAction(actionBash, STEP_SCRIPT_BASH, "");
        }

        // It can even be a "pwsh" script
        if (ar == null || !ar.actionExecuted) {
            ActionInsertSectionByProperty actionPSScript = new ActionInsertSectionByProperty(STEP_SCRIPT_PWSH, DISPLAY_NAME, displayValue, assertStep, insertBefore);
            yamlDocumentEntryPoint.performAction(actionPSScript, STEP_SCRIPT_PWSH, "");
        }

        return this;
    }

    /******************************************************************************************
     Construct a step with a condition that validates a variable
     @param identifierType Possible values ["variables", "parameters"]
     @param identifier The value of the identification
     @param compareValue The value with which the variable or parameter is compared
     @param conditionOperator Possible values ["eq", "ne"]
     ******************************************************************************************/
    private Map<String, Object> constructAssertStep (String identifierType,
                                                     String identifier,
                                                     String compareValue,
                                                     String conditionOperator) {
        Map<String, Object> assertStep = new LinkedHashMap<>();

        String assertOperatorRepresentation = "";
        String actionDisplayName = "";
        if (CONDITION_NOT_EQUALS.equals(conditionOperator)) {
            actionDisplayName = "AssertEquals";
            if (compareValue == null || compareValue.isEmpty())
                actionDisplayName = "AssertEmpty";
            assertOperatorRepresentation = "equal";
        }
        if (CONDITION_EQUALS.equals(conditionOperator)) {
            assertOperatorRepresentation = "not equal";
            actionDisplayName = "AssertNotEquals";
            if (compareValue == null || compareValue.isEmpty())
                actionDisplayName = "AssertNotEmpty";
        }

        String s = "";

        // script
        if (SECTION_VARIABLES.equals(identifierType))
            s = String.format("echo \"%s: variable '%s' with value '$(%s)' is %s to '%s'\"\n", actionDisplayName, identifier, identifier, assertOperatorRepresentation, compareValue);
        if (SECTION_PARAMETERS.equals(identifierType))
            s = String.format("echo \"%s: parameter '%s' with value '${{ parameters.%s }}' is %s to '%s'\"\n", actionDisplayName, identifier, identifier, assertOperatorRepresentation, compareValue);
        s = s + "exit 1";
        assertStep.put(STEP_SCRIPT, s);

        // displayName
        if (SECTION_VARIABLES.equals(identifierType))
            s = "<Inserted> " + actionDisplayName + " variable " + identifier;
        if (SECTION_PARAMETERS.equals(identifierType))
            s = "<Inserted> " + actionDisplayName + " parameter " + identifier;
        assertStep.put(DISPLAY_NAME, s);

        // condition
        if (SECTION_VARIABLES.equals(identifierType))
            s = conditionOperator + "(variables['" + identifier + "'], '" + compareValue + "')";
        if (SECTION_PARAMETERS.equals(identifierType))
            s = conditionOperator + "(parameters['" + identifier + "'], '" + identifier + "')";

        assertStep.put(CONDITION, s);

        return assertStep;
    }

    /******************************************************************************************
     TODO; Add methods:
     setVariableSearchTemplateByIdentifier (String templateIdentifier, String variableName, String value, boolean insertBefore)
    ******************************************************************************************/
}

// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.action.*;
import azdo.hook.Hook;
import azdo.utils.*;
import azdo.yaml.ActionResult;
import azdo.yaml.RepositoryResource;
import azdo.yaml.YamlDocumentEntryPoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import java.io.IOException;
import java.util.*;
import static azdo.utils.Constants.*;

/******************************************************************************************
 AzDoPipeline is used in JUnit tests; it acts as a Java representation of an Azure DeVOps
 pipeline. It encapsulates classes that represents the Azure DevOps pipeline and
 template files (YAML). All AzDoPipeline methods used by the JUnit tests are forwarded
 to the encapsulated objects.
 *******************************************************************************************/
public class AzDoPipeline {
    private static final Log logger = Log.getLogger();
    private PropertyUtils properties = null; // Reference to PropertyUtils object, which is null by default
    private String propertyFile = null; // Optional reference to the property file, which is null by default
    private Git git = null;
    private CredentialsProvider credentialsProvider;
    private  ArrayList<String> validVariableGroups = null; // All 'variable groups' defined in the target Azure DevOps project
    private  ArrayList<String> validEnvironments = null; // All 'environments' defined in the target Azure DevOps project
    String yamlFile;
    Map<String, Object> yamlMap = null;
    String repositoryId = null;
    String pipelineId = null;
    RunResult runResult = null;
    private YamlDocumentEntryPoint yamlDocumentEntryPoint;

    // TODO: git, rm, ssh, cp, scp, rcp, sftp, rsync, mv, mkdir, touch, cat
    public String supportedBashCommands[] = { "curl", "wget", "ftp" }; // Valid commands for method mockBashCommandSearchStepByDisplayName()

    // TODO: Invoke-WebRequest
    public String supportedPowerShellCommands[] = { "Invoke-RestMethod" }; // Valid commands for method mockPowerShellCommandSearchStepByDisplayName()

    @SuppressWarnings("java:S1192")
    public AzDoPipeline (String propertyFile,
                         String pipelineFile) {
        this (null, propertyFile, pipelineFile);
    }

    public AzDoPipeline (PropertyUtils properties,
                         String pipelineFile) {
        this (properties, null, pipelineFile);
    }

    private AzDoPipeline (PropertyUtils properties,
                          String propertyFile,
                          String pipelineFile) {
        logger.debug("==> Object: AzDoPipeline");
        logger.debug("propertyFile {}:", propertyFile);
        logger.debug("pipelineFile {}:", pipelineFile);

        // Init the PropertyUtils
        if (properties != null) {
            this.properties = properties;
        } else if (propertyFile != null) {
            this.properties = new PropertyUtils(propertyFile);
            properties = this.properties;
        } else {
            logger.error("Arguments 'properties' and 'propertyFile' are both null");
            System.exit(1);
        }

        // Validate the main pipeline file before any other action.
        // If it is not valid, the test - executed on Azure DevOps - will fail anyway.
        Utils.validatePipelineFile(pipelineFile, properties.isContinueOnError());

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("Start AzDoPipeline: Initializing repository and pipeline");
        logger.debug(DEMARCATION);

        // Read the properties file and create the entry point.
        yamlDocumentEntryPoint = new YamlDocumentEntryPoint(properties.getSourcePath(),
                properties.getTargetPath(),
                properties.getSourceBasePathExternal(),
                properties.getTargetBasePathExternal(),
                properties.getSourceRepositoryName(),
                properties.getTargetRepositoryName());

        // Read the main pipeline file; this is the YAML file used in the Azure DevOps pipeline (in the Azure DeVOps test project).
        yamlMap = yamlDocumentEntryPoint.read(pipelineFile, properties.isContinueOnError());

        // The external resources section in the main YAML file is parsed and a list of repositories is stored in
        // the yamlDocumentEntryPoint. These repositories contain template YAML files, referred to in the pipeline.
        // In addition, these repositories are cloned and the source files are stored locally
        // (on the filesystem of the workstation). Because these repositories are also used in the Azure DevOps
        // test project, they are pushed to to this project. Any link in the pipeline YAML file with the original
        // repository location is removed.
        // This method is only executed if the pipeline is created with includeExternalTemplates = true.
        if (properties.isIncludeExternalTemplates())
            yamlDocumentEntryPoint.initExternalResources(yamlMap, properties);

        // Read templates; these are both local and external templates.
        // External templates are ignored if includeExternalTemplates = true.
        yamlDocumentEntryPoint.readTemplates (properties.isIncludeExternalTemplates(), properties.isContinueOnError());

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
        // Example pipeline name: my-repository.my-pipeline-yaml.
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

        // Retrieve all valid variable groups and environments from the target Azure DevOps project.
        // This is done once when the AzDoPipeline object is created to prevent multiple API calls.
        if (properties.isVariableGroupsValidate()) {
            validVariableGroups = AzDoUtils.callGetPropertyList(properties.getAzDoUser(),
                    properties.getAzdoPat(),
                    properties.getAzdoEndpoint(),
                    properties.getVariableGroupsApi(),
                    properties.getVariableGroupsApiVersion());
        }
        if (properties.isEnvironmentsValidate()) {
            validEnvironments = AzDoUtils.callGetPropertyList(properties.getAzDoUser(),
                    properties.getAzdoPat(),
                    properties.getAzdoEndpoint(),
                    properties.getEnvironmentsApi(),
                    properties.getEnvironmentsApiVersion());
        }

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
    The startPipeline() method has different flavors, that allow to pass hooks or perform
     a dryrun (not starting the pipeline).
    @throws IOException
    *******************************************************************************************/
    public void startPipeline() throws IOException {
        startPipeline (GitUtils.BRANCH_MASTER, null, false);
    }

    /******************************************************************************************
     @param dryRun Does not start the pipeline in Azure DevOps.
     @throws IOException
     *******************************************************************************************/
    public void startPipeline(boolean dryRun) throws IOException {
        startPipeline (GitUtils.BRANCH_MASTER, null, dryRun);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts.
     @throws IOException
     *******************************************************************************************/
    public void startPipeline (String branchName) throws IOException {
        startPipeline (branchName, null, false);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts.
     @param dryRun Does not start the pipeline in Azure DevOps.
     @throws IOException
     *******************************************************************************************/
    public void startPipeline (String branchName,
                              boolean dryRun) throws IOException {
        startPipeline (branchName, null, dryRun);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts.
     @param hooks List of hooks to be executed locally before the pipeline starts.
     @throws IOException
     *******************************************************************************************/
    public void startPipeline (String branchName,
                              List<Hook> hooks) throws IOException {
        startPipeline (branchName, hooks, false);
    }

    /******************************************************************************************
     @param branchName The branch from which the pipeline starts.
     @param hooks List of hooks to be executed locally before the pipeline starts.
     @param dryRun Does not start the pipeline in Azure DevOps.
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
        runResult = new RunResult(); // Initialize runResult; this is needed if startPipeline() is called multiple times.

        // Clone the target repository to local if not done earlier.
        // Keep the reference to the git object.
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

        // If git object is invalid after the clone or if the repository was not cloned, recreate the git object again.
        if (git == null) {
            logger.debug("Recreate git object");
            git = GitUtils.createGit(properties.getTargetPath());
        }

        // Check whether there is a remote branch; pipelines can be started using files from any branch.
        boolean isRemote = GitUtils.containsBranch(git, branchName);

        // Perform the checkout. This may fail, but that's not a problem. The main concern is that
        // the branch is created in the remote repository in the Azure DevOps test project.
        GitUtils.checkout(git, properties.getTargetPath(), branchName, !isRemote);

        // Copy local resources from main source to the target directory.
        try {
            // Copy all sources from the source local repo to the target local repo.
            Utils.copyAll(properties.getSourcePath(), properties.getTargetPath(), properties.getTargetExludeList());
        }
        catch (Exception e) {
            logger.debug("Exception occurred.Cannot copy local files to target: {}", e.getMessage());
        }

        /*******************************************************************************************
                              Doing stuff for the external repositories
         *******************************************************************************************/

        // The actions on external repositories are only performed if includeExternalTemplates = true.
        if (properties.isIncludeExternalTemplates()) {
            // Copy all resources from a local version of the external repositories.
            // This cleans up the local 'external resources' repositories after it was poluted by the previous testrun.
            yamlDocumentEntryPoint.copyAllSourceFiles(properties.getTargetExludeList());

            // Repositories in the resources section of the yaml pipeline are copied to the Azure DevOps
            // test project. This makes them git repositories, all with type = git (which means Azure DevOps),
            // and all in the same Azure DevOps project. This is independent whether the repositories are
            // originally from another Azure DeVOps project or from GithHub.
            yamlDocumentEntryPoint.makeResourcesLocal();
        }

        /*******************************************************************************************
                                      Prepare for running the pipeline
         *******************************************************************************************/

        // Save the manipulated main YAML (incl. template files) to the target location.
        // The manipulated YAML files are stored in memory (in a YamlDocument or YamlTemplate object). The target
        // location is a local repository, with a remote repository residing in the Azure DevOps test project.
        // Manipulation is performed in JUnit tests by calling the pipeline actions (overrideVariable, overrideLiteral. etc...).
        yamlDocumentEntryPoint.dumpYaml();

        // Perform all (pre)hooks
        if (hooks != null) {
            logger.debug("Execute hooks");
            int size = hooks.size();
            for (int i = 0; i < size; i++) {
                hooks.get(i).executeHook();
            }
        }

        // Validate all manipulated YAML files
        yamlDocumentEntryPoint.validateTargetOutputFilesAndTemplates(validVariableGroups,
                validEnvironments,
                properties.getTargetProject(),
                properties.isContinueOnError());

        /*******************************************************************************************
           Push everything to the main and external repositories in the Azure DevOps test project
         *******************************************************************************************/

        // Push the local (main) repo to remote; this is the repository containing the main pipeline YAML file.
        RepositoryResource metadataRepository = new RepositoryResource(); // Only used for logging
        metadataRepository.repository = properties.getTargetRepositoryName();
        GitUtils.commitAndPush(git,
                properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getCommitPatternList(),
                metadataRepository,
                properties.isContinueOnError());

        if (git != null)
            git.close();

        // Commit and Push all external repositories to remote.
        // The repositoryList is maintained by the YamlDocumentEntryPoint, so delegate to the YamlDocumentEntryPoint.
        // This results in pushing all manipulated template files to the remote repositories in the Azure DevOps test project.
        yamlDocumentEntryPoint.commitAndPushTemplates (properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getCommitPatternList(),
                properties.isContinueOnError());

        /*******************************************************************************************
                         Run the pipeline and retrieve the pipeline run result
         *******************************************************************************************/
        // Call Azure Devops API to start the pipeline and retrieve the result.
        // If dryRun is true, the pipeline does not start.
        if (!dryRun) {
            logger.info("Execute the pipeline remotely in Azure DevOps project \'{}\' with branch \'{}\'", properties.getTargetProject(), branchName);
            AzDoUtils.callPipelineRunApi (properties.getAzDoUser(),
                    properties.getAzdoPat(),
                    properties.getAzdoEndpoint(),
                    properties.getBuildApi(),
                    properties.getBuildApiVersion(),
                    pipelineId,
                    branchName,
                    properties.isContinueOnError());
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

            // Write the detailed result to the log
            runResult.reorganize();
            runResult.dumpTimelineToLog();
        }
        else {
            logger.info("dryRun is true; skip executing the pipeline");
        }

        // Re-read the original pipeline for the next test (for a clean start of the next test).
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
     @param stageIdentifier The identification of a stage.

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
     Skip a stage, but search it using the displayName.
     @param displayValue The displayName of the stage in the pipeline.
     ******************************************************************************************/
    public AzDoPipeline skipStageSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipStageSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a SECTION_STAGE
        // If a stage is found (can be any stage), determine whether its property name (in this case PROPERTY_DISPLAY_NAME), has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_STAGE, PROPERTY_DISPLAY_NAME, displayValue),
                SECTION_STAGE,
                "");

        return this;
    }

    /******************************************************************************************
     Same as the previous one, but instead of a fixed property (displayName), another property
     can be used to skip the stage ('pool', if you want).
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The value of this property.
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
     @param jobIdentifier The identifier of a job.
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
     Skip a job, but search it using the displayName.
     @param displayValue The value of the displayName property of a job.
     ******************************************************************************************/
    public AzDoPipeline skipJobSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipJobSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Call the performAction method; find a SECTION_JOB
        // If it is found, determine whether its property name (in this case PROPERTY_DISPLAY_NAME), has a certain value
        yamlDocumentEntryPoint.performAction (new ActionDeleteSectionByProperty(SECTION_JOB, PROPERTY_DISPLAY_NAME, displayValue),
                SECTION_JOB,
                "");

        return this;
    }

    /******************************************************************************************
     Skip a step or task.
     @param stepIdentifier The identification of a step.

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
     Skip a step, but search it using the displayName.
     @param displayValue The value of the displayName property of a step.
     ******************************************************************************************/
    public AzDoPipeline skipStepSearchByDisplayName (String displayValue) {
        logger.debug("==> Method: AzDoPipeline.skipStepSearchByDisplayName");
        logger.debug("displayValue: {}", displayValue);

        // Search the section types below for the displayName and perform the action
        ArrayList<String> sectionTypes = new ArrayList<>();
        sectionTypes.add(SECTION_TASK);
        sectionTypes.add(STEP_SCRIPT);
        sectionTypes.add(STEP_SCRIPT_BASH);
        sectionTypes.add(STEP_SCRIPT_PWSH);
        performAction (sectionTypes, "ActionDeleteSectionByProperty", PROPERTY_DISPLAY_NAME, displayValue, null, false);

        return this;
    }

    /******************************************************************************************
     Skip a template with a specific identifier. This is similar to the
     skipStageSearchByIdentifier() method but for templates.
     @param templateIdentifier The identification of a template..

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
     @param sectionType Possible values ["stage", "job", "template", "task", ...].
     @param sectionIdentifier The identification of a section.
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
     @param sectionType Possible values ["stage", "job", "template", "task", ...].
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The value of this property.
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
     @param stepIdentifier The identification of a step.
     @param stepToInsert The actual step to insert. Representation is a Map.
     @param insertBefore Determines whether the script is inserted before (true) or after (false)
                         the given step.
     ******************************************************************************************/
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

    public AzDoPipeline insertSectionSearchStepByIdentifier (String stepIdentifier,
                                                             Map<String, Object> stepToInsert) {
        return insertSectionSearchStepByIdentifier (stepIdentifier, stepToInsert, true); // Default is to insert before a step
    }

    /******************************************************************************************
     Inserts a script before or after a given step.
     @param displayValue The displayName of a .
     @param inlineScript The script to insert, before or after the step.
     @param insertBefore Determines whether the script is inserted before (true) or
                         after (false) the given step.
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
        String s = "<Inserted> Script";
        scriptToInsert.put(PROPERTY_DISPLAY_NAME, s);

        // Search the section types below for the displayName and perform the action
        ArrayList<String> sectionTypes = new ArrayList<>();
        sectionTypes.add(SECTION_TASK);
        sectionTypes.add(STEP_SCRIPT);
        sectionTypes.add(STEP_SCRIPT_BASH);
        sectionTypes.add(STEP_SCRIPT_PWSH);
        performAction (sectionTypes, "ActionInsertSectionByProperty", PROPERTY_DISPLAY_NAME, displayValue, scriptToInsert, insertBefore);

        return this;
    }

    /******************************************************************************************
     Inserts a template section before or after a given section. The section is of type "stage",
     "job", "script", "task", "bash", "pwsh" or "powershell".
     @param sectionType Possible values ["stage", "job", "script", "task", "bash", "pwsh",
                        "powershell"].
     @param displayValue The displayName of a sectionType.
     @param templateIdentifier The identification of a template (= template name).
     @param insertBefore Determines whether the script is inserted before (true) or
                         after (false) the given section.
     ******************************************************************************************/
    public AzDoPipeline insertTemplateSearchSectionByDisplayName (String sectionType,
                                                                  String displayValue,
                                                                  String templateIdentifier,
                                                                  Map<String, String> parameters,
                                                                  boolean insertBefore) {

        logger.debug("==> Method: AzDoPipeline.insertTemplateSearchByDisplayName");
        logger.debug("sectionType: {}", sectionType);
        logger.debug("displayValue: {}", displayValue);
        logger.debug("templateName: {}", templateIdentifier);
        logger.debug("insertBefore: {}", insertBefore);

        if (sectionType == null)
            return this;

        // Create a template step
        Map<String, Object> sectionToInsert = new LinkedHashMap<>();
        sectionToInsert.put(SECTION_TEMPLATE, templateIdentifier);

        // Add parameters
        if (parameters != null && !parameters.isEmpty()) {
            sectionToInsert.put(SECTION_PARAMETERS, parameters);
        }

        // Call the performAction method; find the section - identified by sectionType - and the displayName
        yamlDocumentEntryPoint.performAction (new ActionInsertSectionByProperty(sectionType, PROPERTY_DISPLAY_NAME, displayValue, sectionToInsert, insertBefore),
                sectionType,
                null);

        return this;
    }

    public AzDoPipeline insertTemplateSearchSectionByDisplayName (String sectionType,
                                                                  String displayValue,
                                                                  String templateIdentifier,
                                                                  Map<String, String> parameters) {
        return insertTemplateSearchSectionByDisplayName (sectionType, displayValue, templateIdentifier, parameters, true); // Default is to insert before a step
    }

    /******************************************************************************************
     Inserts a template section before or after a given section.
     @param sectionIdentifier The identification of a section to search for. This can be a
                              section of type 'stage', 'job', or 'template'.
     @param templateIdentifier The identification of a template (= template name).
     @param insertBefore Determines whether the script is inserted before (true) or
                         after (false) the given section.
     ******************************************************************************************/
    public AzDoPipeline insertTemplateSearchSectionByIdentifier (String sectionIdentifier,
                                                                 String templateIdentifier,
                                                                 Map<String, String> parameters,
                                                                 boolean insertBefore) {

        logger.debug("==> Method: AzDoPipeline.insertTemplateSearchSectionByIdentifier");
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        logger.debug("templateIdentifier: {}", templateIdentifier);
        logger.debug("insertBefore: {}", insertBefore);

        // Create a template step
        Map<String, Object> sectionToInsert = new LinkedHashMap<>();
        sectionToInsert.put(SECTION_TEMPLATE, templateIdentifier);

        // Add parameters
        if (parameters != null && !parameters.isEmpty()) {
            sectionToInsert.put(SECTION_PARAMETERS, parameters);
        }

        // Search for the possible section types whether the identifier (sectionIdentifier) matches
        // If there is a match, the action (ActionInsertSection) is performed
        ArrayList<String> sectionTypes = new ArrayList<>();
        sectionTypes.add(SECTION_STAGE);
        sectionTypes.add(SECTION_JOB);
        sectionTypes.add(SECTION_TEMPLATE);
        performAction (sectionTypes, "ActionInsertSection", null, sectionIdentifier, sectionToInsert, insertBefore);

        return this;
    }

    /******************************************************************************************
     Find a section in the yaml, identified by a certain type (e.g. "pool", "stage", "task"),
     and an identifier (can have a value or empty). If the section is found, a property with a
     certain value is added.
     <br>
     <br>
     Example:
     <pre>
     resources:
       repositories:
       - repository: external
         name: Templates/Templates
         type: git
         ref: refs/heads/develop
     </pre>

     pipeline.addPropertyToSectionSearchByTypeAndIdentifier("repository", "external", "endpoint", "p1") // Add endpoint

     <pre>
     resources:
       repositories:
       - repository: external
         name: Templates/Templates
         type: git
         ref: refs/heads/develop
         endpoint: p1
     </pre>

     @param sectionType Possible values ["stage", "job", "template", "task", ...].
     @param sectionIdentifier The identification of a section.
     @param property The name of the property of a section; this can be "displayName", "pool", ...
     @param propertyValue The  value of this property.
     ******************************************************************************************/
    public AzDoPipeline addPropertyToSectionSearchByTypeAndIdentifier (String sectionType,
                                                                       String sectionIdentifier,
                                                                       String property,
                                                                       String propertyValue) {
        logger.debug("==> Method: AzDoPipeline.addPropertySearchStepByIdentifier");
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        ActionAddPropertyToSection action = new ActionAddPropertyToSection (sectionType,
                sectionIdentifier,
                property,
                propertyValue);
        yamlDocumentEntryPoint.performAction (action, sectionType, sectionIdentifier);

        return this;
    }

    /******************************************************************************************
     Replaces the value of a variable in the 'variables' section.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param value The new value of the variable.

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
     Use a Powershell (pwsh) script; it runs both on Linux and Windows
     @param stepIdentifier The identification of a step.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param value The new value of the variable.
     @param insertBefore Determines whether the script is inserted before (true) or
                         after (false) the given step.
     ******************************************************************************************/
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
        Map<String, Object> stepToInsert = constructSetVariableSection (variableName, value);

        // Call the performAction method; find the SECTION_TASK section with the identifier
        // Other arguments besides SECTION_TASK are: powershell | pwsh | bash | checkout | download | downloadBuild | getPackage | publish | reviewApp
        // These are not implemented
        yamlDocumentEntryPoint.performAction (new ActionInsertSection(SECTION_TASK, stepIdentifier, stepToInsert, insertBefore),
                SECTION_TASK,
                stepIdentifier);

        return this;
    }

    public AzDoPipeline setVariableSearchStepByIdentifier (String stepIdentifier,
                                                           String variableName,
                                                           String value) {
        setVariableSearchStepByIdentifier (stepIdentifier, variableName, value, true); // Default is to set the value before a step

        return this;
    }

    /******************************************************************************************
     Sets (changes) the value of a variable (identified by "variableName") just before a certain
     template is executed. This means that the variable value is changed at runtime (while running
     the pipeline), unlike the overrideVariable() method, which replaces the value during
     pre-processing the pipelines.
     This template is found using the "templateIdentifier".
     Use a Powershell (pwsh) script; it runs both on Linux and Windows
     @param templateIdentifier The identification of a template.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param value The new value of the variable.
     @param insertBefore Determines whether the script is inserted before (true) or
                         after (false) the given template.
     ******************************************************************************************/
    public AzDoPipeline setVariableSearchTemplateByIdentifier (String templateIdentifier,
                                                               String variableName,
                                                               String value,
                                                               boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.setVariableSearchTemplateByIdentifier");
        logger.debug("templateIdentifier: {}", templateIdentifier);
        logger.debug("variableName: {}", variableName);
        logger.debug("value: {}", value);
        logger.debug("insertBefore: {}", insertBefore);

        // Create a script that sets the value of a variable
        Map<String, Object> stepToInsert = constructSetVariableSection (variableName, value);

        // Call the performAction method; find the SECTION_TEMPLATE section with the identifier
        yamlDocumentEntryPoint.performAction (new ActionInsertSection(SECTION_TEMPLATE, templateIdentifier, stepToInsert, insertBefore),
                SECTION_TEMPLATE,
                templateIdentifier);

        return this;
    }

    public AzDoPipeline setVariableSearchTemplateByIdentifier (String templateIdentifier,
                                                               String variableName,
                                                               String value) {
        setVariableSearchTemplateByIdentifier (templateIdentifier, variableName, value, true); // Default is to set the value before a template

        return this;
    }

    /******************************************************************************************
     Set the variable at runtime, just as the previous method, but search the step using the
     displayName. The step can be of any type "step", SECTION_TASK, or SECTION_SCRIPT.
     Use a Powershell (pwsh) script; it runs both on Linux and Windows.
     @param displayValue The value of the displayName property of a step.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param value The new value of the variable.
     @param insertBefore Determines whether the script is inserted before (true) or
                         after (false) the given step.
     ******************************************************************************************/
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
        // Create a script that sets the value of a variable
        Map<String, Object> stepToInsert = constructSetVariableSection (variableName, value);

        // Search the section types below for the displayName and perform the action
        ArrayList<String> sectionTypes = new ArrayList<>();
        sectionTypes.add(SECTION_TASK);
        sectionTypes.add(STEP_SCRIPT);
        sectionTypes.add(STEP_SCRIPT_BASH);
        sectionTypes.add(STEP_SCRIPT_PWSH);
        performAction (sectionTypes, "ActionInsertSectionByProperty", PROPERTY_DISPLAY_NAME, displayValue, stepToInsert, insertBefore);

        return this;
    }

    public AzDoPipeline setVariableSearchStepByDisplayName (String displayValue,
                                                            String variableName,
                                                            String value) {
        setVariableSearchStepByDisplayName (displayValue, variableName, value, true); // Default is to set the value before a step

        return this;
    }

    /******************************************************************************************
     Private method to construct a section in which the variable is set.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param value The new value of the variable.
     ******************************************************************************************/
    private Map<String, Object> constructSetVariableSection (String variableName,
                                                             String value) {
        // Create a script that sets the value of a variable
        Map<String, Object> stepToInsert = new LinkedHashMap<>();
        String s = "Write-Host \"echo ##vso[task.setvariable variable=" + variableName + "]" + value + "\"";
        stepToInsert.put(STEP_SCRIPT_PWSH, s);

        s = String.format("<Inserted> Set variable %s = %s", variableName, value);
        stepToInsert.put(PROPERTY_DISPLAY_NAME, s);

        return stepToInsert;
    }

    /******************************************************************************************
     Replaces the value of a parameter in the 'template' section.
     @param parameterName The name of the paramter as declared in the 'parameters' section
                          of a template declaration.
     @param value The new value of the parameter.

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
     @param parameterName The name of the paramter as declared in the 'parameters' section.
     @param defaultValue The new default value of the parameter.

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
     @param literalToReplace A substring of a yaml pipeline- or template definition.
     @param newValue The substring is replaced by 'newValue'.
     @param replaceAll Replace all occurences in all yaml files, including the templates.

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
     @param newBranchName New branch name that overrides an occurence of the current branch.
     @param replaceAll Replace all occurences in all yaml files, including the templates.

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
     Replace the identifier of a section (stage, job, vmImage, ...) with a new identifier value.
     @param sectionType Possible values ["stage", "job", "template", "task", ...].
     @param sectionIdentifier The identification of a section.
     @param newSectionIdentifier The new identification of a section.
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
     @param sectionType Possible values ["stage", "job", "template", "task", ...].
     @param sectionIdentifier The identification of a section.
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param propertyValue The new value of this property.
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
     Reset the trigger in the main pipeline to 'trigger: none'
     This prevents a pipeline from starting twice as part of performing the pipeline unit test,
     if the trigger in the main pipeline is configured to start after every commit.
     ******************************************************************************************/
    public AzDoPipeline resetTrigger (){
        logger.debug("==> Method: AzDoPipeline.resetTrigger");

        // Call the action
        ActionResetTrigger action = new ActionResetTrigger();
        yamlDocumentEntryPoint.performAction (action, SECTION_TRIGGER, null);

        return this;
    }

    /******************************************************************************************
     Replace the content of a step with an inline script. The step is searched using the
     'stepIdentifier', for example "AWSShellScript@1".
     @param stepIdentifier The identification of a step.
     @param inlineScript The inline script.
     ******************************************************************************************/
    public AzDoPipeline mockStepSearchByIdentifier (String stepIdentifier,
                                                    String inlineScript){
        logger.debug("==> Method: AzDoPipeline.mockStepSearchByIdentifier");
        logger.debug("stepIdentifier: {}", stepIdentifier); // For example AWSShellScript@1
        logger.debug("inlineScript: {}", inlineScript);

        Map<String, Object> mockScript = new LinkedHashMap<>();
        mockScript.put(STEP_SCRIPT, inlineScript);
        mockScript.put(PROPERTY_DISPLAY_NAME, "<Replaced> Mock script");

        // Call the performAction method; find the step section with the stepIdentifier
        ActionUpdateSection action = new ActionUpdateSection(SECTION_TASK, stepIdentifier, mockScript);
        yamlDocumentEntryPoint.performAction (action, SECTION_TASK, stepIdentifier);

        return this;
    }

    /******************************************************************************************
     Replace the content of a step with an inline script. The step is searched using the
     'displayName', for example "Deploy step".
     @param displayValue The value of the displayName property of a step.
     @param inlineScript The inline script.
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
        ActionUpdateSectionByProperty actionTask = new ActionUpdateSectionByProperty(SECTION_TASK, PROPERTY_DISPLAY_NAME, displayValue, stepToInsert);
        ar = yamlDocumentEntryPoint.performAction (actionTask, SECTION_TASK, "");

        // Also check whether a script must be updated, instead of a task
        if (ar == null || !ar.actionExecuted) {
            ActionUpdateSectionByProperty actionScript = new ActionUpdateSectionByProperty(STEP_SCRIPT, PROPERTY_DISPLAY_NAME, displayValue, stepToInsert);
            yamlDocumentEntryPoint.performAction(actionScript, STEP_SCRIPT, "");
        }

        return this;
    }

    /******************************************************************************************
     Mock a bash command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step.
     @param command Bash command; for example "curl", "wget", "ftp".
     @param commandOutputArray The return value of the Bash command. This method signature takes
                               an array of Strings. Reason is, that the step may contain multiple
                               instances of the same command. The order of the String array is
                               also the order in which the commands are located in the Bash script.

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

            // DisplayName
            s = "<Inserted> Mock " + command;
            stepToInsert.put(PROPERTY_DISPLAY_NAME, s);
            newLine = ". " + functionFileName + "\n";
        }
        else {
            logger.warn("Mocking command \'{}\' is not yet supported", command);
        }

        if (newLine != null) {
            // Insert the bash step before a searched step of type 'script' (if found)
            ActionResult ar;
            ActionInsertSectionByProperty scriptAction = new ActionInsertSectionByProperty(STEP_SCRIPT, PROPERTY_DISPLAY_NAME, displayValue, stepToInsert, true);
            ar = yamlDocumentEntryPoint.performAction (scriptAction, STEP_SCRIPT, "");

            // Add a new line to the subsequent script step
            ActionInsertLineInSection scriptActionInsertLineInSection = new ActionInsertLineInSection(STEP_SCRIPT, PROPERTY_DISPLAY_NAME, displayValue, newLine);
            yamlDocumentEntryPoint.performAction(scriptActionInsertLineInSection, STEP_SCRIPT, "");

            if (ar == null || !ar.actionExecuted) {
                // If the script was not found, try to insert the bash step before a searched step of type 'bash'
                ActionInsertSectionByProperty bashAction = new ActionInsertSectionByProperty(STEP_SCRIPT_BASH, PROPERTY_DISPLAY_NAME, displayValue, stepToInsert, true);
                ar = yamlDocumentEntryPoint.performAction(bashAction, STEP_SCRIPT_BASH, "");

                // Add a new line to the subsequent bash step
                ActionInsertLineInSection bashActionInsertLineInSection = new ActionInsertLineInSection(STEP_SCRIPT_BASH, PROPERTY_DISPLAY_NAME, displayValue, newLine);
                yamlDocumentEntryPoint.performAction(bashActionInsertLineInSection, STEP_SCRIPT_BASH, "");
            }

            if (ar == null || !ar.actionExecuted) {
                // If the searched step is not a "bash" or "script" type, it may be a "Bash@3" task. Btw, use the SECTION_TASK instead of the TASK_BASH_3
                ActionInsertSectionByProperty actionBashTask = new ActionInsertSectionByProperty(SECTION_TASK, PROPERTY_DISPLAY_NAME, displayValue, stepToInsert, true);
                yamlDocumentEntryPoint.performAction(actionBashTask, SECTION_TASK, "");

                // Insert a new line to the "Bash@3" script; the line contains a declaration of the function file created in the inserted step
                // The inline script in a "Bash@3" task is > inputs > script
                ActionInsertLineInInnerSection actionInsertLineInInnerSection = new ActionInsertLineInInnerSection(SECTION_TASK,
                        PROPERTY_DISPLAY_NAME,
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
     Mock a bash command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step.
     @param command Bash command; for example "curl", "wget", "ftp".
     @param commandOutput The return value of the bash command.

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
     Private method returning the script of the mocked bash command
     ******************************************************************************************/
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

    /******************************************************************************************
     Mock a PowerShell command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step.
     @param command PowerShell command.
     @param commandOutputArray The return value of the PowerShell command. This method signature takes
                               an array of Strings. Reason is, that the step may contain multiple
                               instances of the same command. The order of the String array is
                               also the order in which the commands are located in the PowerShell
                               script.

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

                // DisplayName
                s = "<Inserted> Mock Invoke-RestMethod";
                stepToInsert.put(PROPERTY_DISPLAY_NAME, s);
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
            ActionInsertSectionByProperty actionPSScript = new ActionInsertSectionByProperty(STEP_SCRIPT_PWSH, PROPERTY_DISPLAY_NAME, displayValue, stepToInsert, true);
            yamlDocumentEntryPoint.performAction (actionPSScript, STEP_SCRIPT_PWSH, "");

            // Insert a new line to the "pwsh" script; the line contains a declaration of the function file created in the inserted step
            ActionInsertLineInSection actionInsertLineInSection = new ActionInsertLineInSection(STEP_SCRIPT_PWSH, PROPERTY_DISPLAY_NAME, displayValue, newLine);
            ar = yamlDocumentEntryPoint.performAction(actionInsertLineInSection, STEP_SCRIPT_PWSH, "");

            if (ar == null || !ar.actionExecuted) {
                // If it is not a "pwsh" script, it may be a "PowerShel@2" task. Btw, use the SECTION_TASK instead of the TASK_POWERSHELL_2
                ActionInsertSectionByProperty actionPSTask = new ActionInsertSectionByProperty(SECTION_TASK, PROPERTY_DISPLAY_NAME, displayValue, stepToInsert, true);
                yamlDocumentEntryPoint.performAction (actionPSTask, SECTION_TASK, "");

                // Insert a new line to the "PowerShell@2" script; the line contains a declaration of the function file created in the inserted step
                // The inline script in a "PowerShell@2" task is > inputs > script
                ActionInsertLineInInnerSection actionInsertLineInInnerSection = new ActionInsertLineInInnerSection(SECTION_TASK,
                        PROPERTY_DISPLAY_NAME,
                        displayValue,
                        INPUTS,
                        SCRIPT,
                        newLine);
                yamlDocumentEntryPoint.performAction(actionInsertLineInInnerSection, TASK_POWERSHELL_2, "");
            }
        }

        return this;
    }

    /******************************************************************************************
     Mock a PowerShell command in a script. The real command will not be executed.
     The step is found using the displayName.
     @param displayValue The value of the displayName property of a step.
     @param command PowerShell command.
     @param commandOutput The return value of the PowerShell command.

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
     The assertion is performed just before or after the execution of the step, identified by the
     'displayValue'.
     @param displayValue The value of the displayName property of a step.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param compareValue The value with which the variable is compared.
     @param insertBefore Determines whether assertion is performed before or after a certain step.

     Example:
     Calling assertVariableEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "myValue", true) means
     that just before the step with displayName "Deploy the app" is executed, the variable
     'myVar' value is compared with "myValue".
     If you want to validate just after execution of the step, call
     assertVariableEqualsSearchStepByDisplayName ("Deploy the app", "myVar", "myValue", false)
     ******************************************************************************************/
    public AzDoPipeline assertVariableEqualsSearchStepByDisplayName (String displayValue,
                                                                     String variableName,
                                                                     String compareValue,
                                                                     boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, variableName, TYPE_VARIABLE, compareValue, true, insertBefore);

        return this;
    }

    public AzDoPipeline assertVariableEqualsSearchStepByDisplayName (String displayValue,
                                                                     String variableName,
                                                                     String compareValue) {
        assertVariableEqualsSearchStepByDisplayName (displayValue, variableName, compareValue, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     The assertVariableEqualsSearchTemplateByIdentifier() method validates a variable during
     runtime of the pipeline. If the variable - with 'variableName' - is not equal to
     'compareValue', the pipeline aborts.
     The assertion is performed just before or after the execution of a template.
     @param templateIdentifier The identification of a template (= template name).
     @param variableName The name of the variable as declared in the 'variables' section.
     @param compareValue The value with which the variable is compared.
     @param insertBefore Determines whether assertion is performed before or after the template.
     ******************************************************************************************/
    public AzDoPipeline assertVariableEqualsSearchTemplateByIdentifier (String templateIdentifier,
                                                                        String variableName,
                                                                        String compareValue,
                                                                        boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertVariableEqualsSearchTemplateByIdentifier");
        logger.debug("templateIdentifier: {}", templateIdentifier);
        logger.debug("variableName: {}", variableName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchTemplateByIdentifier (templateIdentifier, variableName, TYPE_VARIABLE, compareValue, true, insertBefore);

        return this;
    }

    public AzDoPipeline assertVariableEqualsSearchTemplateByIdentifier (String templateIdentifier,
                                                                        String variableName,
                                                                        String compareValue) {

        assertMutableSearchTemplateByIdentifier (templateIdentifier, variableName, TYPE_VARIABLE, compareValue, true, true);

        return this;
    }

    /******************************************************************************************
     The assertVariableNotEqualsSearchStepByDisplayName() method validates a variable during
     runtime of the pipeline. If the variable - with 'variableName' - is equal to
     'compareValue', the pipeline aborts.
     The assertion is performed just before or after the execution of the step, identifier by the
     'displayValue'.
     @param displayValue The value of the displayName property of a step.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param compareValue The value with which the variable is compared.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertVariableNotEqualsSearchStepByDisplayName (String displayValue,
                                                                        String variableName,
                                                                        String compareValue,
                                                                        boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertVariableNotEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, variableName, TYPE_VARIABLE, compareValue, false, insertBefore);

        return this;
    }

    public AzDoPipeline assertVariableNotEqualsSearchStepByDisplayName (String displayValue,
                                                                        String variableName,
                                                                        String compareValue) {
        assertVariableNotEqualsSearchStepByDisplayName (displayValue, variableName, compareValue, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     Same as assertVariableNotEqualsSearchStepByDisplayName(), but it is compared to an empty
     value.
     @param displayValue The value of the displayName property of a step.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertVariableNotEmptySearchStepByDisplayName (String displayValue,
                                                                       String variableName,
                                                                       boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertVariableNotEmptySearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, variableName, TYPE_VARIABLE, "", false, insertBefore);

        return this;
    }

    public AzDoPipeline assertVariableNotEmptySearchStepByDisplayName (String displayValue,
                                                                       String variableName) {
        assertVariableNotEmptySearchStepByDisplayName (displayValue, variableName, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     Same as assertVariableEqualsSearchStepByDisplayName() but it is compared to an empty value.
     @param displayValue The value of the displayName property of a step.
     @param variableName The name of the variable as declared in the 'variables' section.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertVariableEmptySearchStepByDisplayName (String displayValue,
                                                                    String variableName,
                                                                    boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertVariableEmptySearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("variableName: {}", variableName);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, variableName, TYPE_VARIABLE, "", true, insertBefore);

        return this;
    }

    public AzDoPipeline assertVariableEmptySearchStepByDisplayName (String displayValue,
                                                                    String variableName) {
        assertVariableEmptySearchStepByDisplayName (displayValue, variableName, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     Same as the assertVariableEqualsSearchStepByDisplayName() method, but for parameters.
     @param displayValue The value of the displayName property of a step.
     @param parameterName The name of the parameter as declared in the 'parameters' section.
     @param compareValue The value with which the parameter is compared.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertParameterEqualsSearchStepByDisplayName (String displayValue,
                                                                      String parameterName,
                                                                      String compareValue,
                                                                      boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertParameterEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("parameterName: {}", parameterName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, parameterName, TYPE_PARAMETER, compareValue, true, insertBefore);

        return this;
    }

    public AzDoPipeline assertParameterEqualsSearchStepByDisplayName (String displayValue,
                                                                      String parameterName,
                                                                      String compareValue) {
        assertParameterEqualsSearchStepByDisplayName (displayValue, parameterName, compareValue, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     The assertParameterEqualsSearchTemplateByIdentifier() method validates a parameter during
     runtime of the pipeline. If the parameter - with 'parameterName' - is not equal to
     'compareValue', the pipeline aborts.
     The assertion is performed just before or after the execution of a template.
     @param templateIdentifier The identification of a template (= template name).
     @param parameterName The name of the parameter as declared in the 'parameters' section.
     @param compareValue The value with which the parameter is compared.
     @param insertBefore Determines whether assertion is performed before or after the template.
     ******************************************************************************************/
    public AzDoPipeline assertParameterEqualsSearchTemplateByIdentifier (String templateIdentifier,
                                                                        String parameterName,
                                                                        String compareValue,
                                                                        boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertParameterEqualsSearchTemplateByIdentifier");
        logger.debug("templateIdentifier: {}", templateIdentifier);
        logger.debug("parameterName: {}", parameterName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchTemplateByIdentifier (templateIdentifier, parameterName, TYPE_PARAMETER, compareValue, true, insertBefore);

        return this;
    }

    public AzDoPipeline assertParameterEqualsSearchTemplateByIdentifier (String templateIdentifier,
                                                                         String parameterName,
                                                                        String compareValue) {
        assertMutableSearchTemplateByIdentifier (templateIdentifier, parameterName, TYPE_PARAMETER, compareValue, true, true);

        return this;
    }

    /******************************************************************************************
     Same as the assertVariableNotEqualsSearchStepByDisplayName() method but for paramters.
     @param displayValue The value of the displayName property of a step.
     @param parameterName The name of the parameter as declared in the 'parameters' section.
     @param compareValue The value with which the parameter is compared.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertParameterNotEqualsSearchStepByDisplayName (String displayValue,
                                                                         String parameterName,
                                                                         String compareValue,
                                                                         boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertParameterNotEqualsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("parameterName: {}", parameterName);
        logger.debug("compareValue: {}", compareValue);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, parameterName, TYPE_PARAMETER, compareValue, false, insertBefore);

        return this;
    }

    public AzDoPipeline assertParameterNotEqualsSearchStepByDisplayName (String displayValue,
                                                                         String parameterName,
                                                                         String compareValue) {
        assertParameterNotEqualsSearchStepByDisplayName (displayValue, parameterName, compareValue, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     Same as assertVariableNotEmptySearchStepByDisplayName() but for parameters.
     @param displayValue The value of the displayName property of a step.
     @param parameterName The name of the parameter as declared in the 'parameters' section.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertParameterNotEmptySearchStepByDisplayName (String displayValue,
                                                                        String parameterName,
                                                                        boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertParameterNotEmptySearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("parameterName: {}", parameterName);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, parameterName, TYPE_PARAMETER, "", false, insertBefore);

        return this;
    }

    public AzDoPipeline assertParameterNotEmptySearchStepByDisplayName (String displayValue,
                                                                        String parameterName) {
        assertParameterNotEmptySearchStepByDisplayName (displayValue, parameterName, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     Same as assertVariableEmptySearchStepByDisplayName() but for parameters.
     @param displayValue The value of the displayName property of a step.
     @param parameterName The name of the parameter as declared in the 'parameters' section.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertParameterEmptySearchStepByDisplayName (String displayValue,
                                                                     String parameterName,
                                                                     boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertParameterEmptySearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("parameterName: {}", parameterName);
        logger.debug("insertBefore: {}", insertBefore);

        assertMutableSearchStepByDisplayName (displayValue, parameterName, TYPE_PARAMETER, "", true, insertBefore);

        return this;
    }

    public AzDoPipeline assertParameterEmptySearchStepByDisplayName (String displayValue,
                                                                     String parameterName) {
        assertParameterEmptySearchStepByDisplayName (displayValue, parameterName, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     Private method used for the previous assert-if-identifier-has-value methods. The identifier
     is of type 'variable' or 'parameter'.
     @param displayValue The value of the displayName property of a step.
     @param mutable The name of the variable or parameter as declared in the 'variables'
                    or 'parameters' section.
     @param mutableType Variable (TYPE_VARIABLE) or Parameter (TYPE_PARAMETER).
     @param compareValue The value with which the variable or parameter is compared.
     @param equals Determines the compare operator:
                   - True = Equals
                   - False = Not equals
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    private AzDoPipeline assertMutableSearchStepByDisplayName (String displayValue,
                                                               String mutable,
                                                               String mutableType,
                                                               String compareValue,
                                                               boolean equals,
                                                               boolean insertBefore) {

        // Create a script that compares the value of a variable or parameter with another value
        Map<String, Object> stepToInsert;
        stepToInsert = constructAssertSection(mutable, mutableType, compareValue, equals);

        // Search the section types below for the displayName and perform the action
        ArrayList<String> sectionTypes = new ArrayList<>();
        sectionTypes.add(SECTION_TASK);
        sectionTypes.add(STEP_SCRIPT);
        sectionTypes.add(STEP_SCRIPT_BASH);
        sectionTypes.add(STEP_SCRIPT_PWSH);
        performAction (sectionTypes, "ActionInsertSectionByProperty", PROPERTY_DISPLAY_NAME, displayValue, stepToInsert, insertBefore);

        return this;
    }

    /******************************************************************************************
     Private method used for the previous assert-if-identifier-has-value methods. The identifier
     is of type 'variable' or 'parameter'.
     @param templateIdentifier The identifier of a template.
     @param mutable The name of the variable or parameter as declared in the 'variables'
                    or 'parameters' section.
     @param mutableType Variable (TYPE_VARIABLE) or Parameter (TYPE_PARAMETER).
     @param compareValue The value with which the variable or parameter is compared.
     @param equals Determines the compare operator:
                   - True = Equals
                   - False = Not equals
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    private AzDoPipeline assertMutableSearchTemplateByIdentifier (String templateIdentifier,
                                                                  String mutable,
                                                                  String mutableType,
                                                                  String compareValue,
                                                                  boolean equals,
                                                                  boolean insertBefore) {

        // Create a script that compares the value of a variable or parameter with another value
        Map<String, Object> stepToInsert;
        stepToInsert = constructAssertSection(mutable, mutableType, compareValue, equals);

        // Search the section types below for the displayName and perform the action
        ArrayList<String> sectionTypes = new ArrayList<>();
        sectionTypes.add(SECTION_TEMPLATE);
        performAction (sectionTypes, "ActionInsertSection", null, templateIdentifier, stepToInsert, insertBefore);

        return this;
    }

    /******************************************************************************************
     The assertFileExistsSearchStepByDisplayName() method validates at runtime the presence
     of a certain file on the Azure DevOps agent. If the file does not exist, the pipeline
     exits with an error.
     Use a Powershell (pwsh) script; it runs both on Linux and Windows.
     @param displayValue The value of the displayName property of a step.
     @param fileName The file name of the file of which its existence is checked.
     @param insertBefore Determines whether assertion is performed before or after a certain step.
     ******************************************************************************************/
    public AzDoPipeline assertFileExistsSearchStepByDisplayName (String displayValue,
                                                                 String fileName,
                                                                 boolean insertBefore) {
        logger.debug("==> Method: AzDoPipeline.assertFileExistsSearchStepByDisplayName");
        logger.debug("displayValue: {}", displayValue); // Can be something like "Execute this step"
        logger.debug("fileName: {}", fileName);
        logger.debug("insertBefore: {}", insertBefore);

        // Create a pwsh task that checks on the existence of a file
        Map<String, Object> assertStep = new LinkedHashMap<>();
        String s;
        s = "$FilePath = \"" + fileName + "\"\n" +
                "if (-not(Test-path $FilePath -PathType leaf)) {\n" +
                "    Write-Host \"AssertFileExists: file \'" + fileName + "\' is not present (or empty) on the Azure DevOps Agent\"\n" +
                "    exit 1\n" +
                "}";
        assertStep.put(STEP_SCRIPT_PWSH, s);

        // DisplayName
        s = "<Inserted> AssertFileExists: " + fileName;
        assertStep.put(PROPERTY_DISPLAY_NAME, s);

        // Call the performAction method; find the section with the PROPERTY_DISPLAY_NAME
        ArrayList<String> sectionTypes = new ArrayList<>();
        sectionTypes.add(SECTION_TASK);
        sectionTypes.add(STEP_SCRIPT);
        sectionTypes.add(STEP_SCRIPT_BASH);
        sectionTypes.add(STEP_SCRIPT_PWSH);
        performAction (sectionTypes, "ActionInsertSectionByProperty", PROPERTY_DISPLAY_NAME, displayValue, assertStep, insertBefore);

        return this;
    }

    public AzDoPipeline assertFileExistsSearchStepByDisplayName (String displayValue,
                                                                 String fileName) {
        assertFileExistsSearchStepByDisplayName (displayValue, fileName, true); // Default is to insert before a step

        return this;
    }

    /******************************************************************************************
     Construct a step with a condition that validates a variable.
     Use a Powershell (pwsh) script; it runs both on Linux and Windows.
     @param mutable The name of the variable or parameter as declared in the 'variables'
                    or 'parameters' section.
     @param mutableType Possible values ["variables", "parameters"].
     @param compareValue The value with which the variable or parameter is compared.
     @param equals If true  - The compareValue must match
                   If false - The compareValue must NOT match
     ******************************************************************************************/
    private Map<String, Object> constructAssertSection (String mutable,
                                                        String mutableType,
                                                        String compareValue,
                                                        boolean equals) {
        String mutableTypeDisplay = "";
        String value = "";
        if (TYPE_VARIABLE.equals(mutableType)) {
            mutableTypeDisplay = "variable";
            value = "$(" + mutable + ")";
        }
        if (TYPE_PARAMETER.equals(mutableType)) {
            mutableTypeDisplay = "parameter";
            value = "${{ parameters."+ mutable + " }}";
        }

        String actionDisplayName = "";
        String s = "$str = \"" + value + "\"\n";
        if (equals) {
            // Applies to AssertEquals and AssertEmpty
            if (compareValue == null || compareValue.isEmpty()) {
                actionDisplayName = "AssertEmpty";
                s = s + "if ($str) {\n" +
                        String.format("  Write-Host \"%s: %s '%s' with value '%s' is not empty\"\n", actionDisplayName, mutableTypeDisplay, mutable, value);
            }
            else {
                actionDisplayName = "AssertEquals";
                s = s + "if (\"$str\" -ne \"" + compareValue + "\") {\n" +
                        String.format("  Write-Host \"%s: %s '%s' with value '%s' is not equal to compared value '%s'\"\n", actionDisplayName, mutableTypeDisplay, mutable, value, compareValue);
            }
        }
        else {
            // Applies to AssertNotEquals and AssertNotEmpty
            if (compareValue == null || compareValue.isEmpty()) {
                actionDisplayName = "AssertNotEmpty";
                s = s + "if (-Not $str) {\n" +
                        String.format("  Write-Host \"%s: %s '%s' is empty\"\n", actionDisplayName, mutableTypeDisplay, mutable);
            }
            else {
                actionDisplayName = "AssertNotEquals";
                s = s + "if (\"$str\" -eq \"" + compareValue + "\") {\n" +
                        String.format("  Write-Host \"%s: %s '%s' with value '%s' is equal to compared value '%s'\"\n", actionDisplayName, mutableTypeDisplay, mutable, value, compareValue);
            }
        }
        s = s + "  exit 1\n" +
        "}\n" +
        "else {Write-Host \"Assert is true; continue\"}";

        Map<String, Object> assertStep = new LinkedHashMap<>();
        assertStep.put(STEP_SCRIPT_PWSH, s);

        // DisplayName
        s = "<Inserted> " + actionDisplayName + " " + mutableTypeDisplay + " " + mutable;
        assertStep.put(PROPERTY_DISPLAY_NAME, s);

        return assertStep;
    }

    /******************************************************************************************
     Insert, update, or delete a section in the pipeline YAML.
     @param sectionTypes Contains an array of section types that need to be searched for. Only
                         section types in this list are taken into account in the search criteria.
                         This argument contains an array, for example
                         [SECTION_TASK, STEP_SCRIPT, STEP_SCRIPT_BASH, STEP_SCRIPT_PWSH]
     @param actionType Possible values (corresponds with the class):
                       - ActionInsertSectionByProperty
                       - ActionInsertSection
                       - ActionDeleteSectionByProperty
                       - ActionUpdateSectionByProperty
     @param property The name of the property of a stage; this can be "displayName", "pool", ...
     @param sectionIdentifier The value of a property (propertyValue) or identifier of a section.
     @param sectionToInsert In case of insert/update, this is the section to be inserted in
                            a pipeline YAML.
     ******************************************************************************************/
    private void performAction (ArrayList<String> sectionTypes,
                                String actionType,
                                String property,
                                String sectionIdentifier,
                                Map<String, Object> sectionToInsert,
                                boolean insertBefore) {
        int size = sectionTypes.size();
        String sectionType;
        ActionResult ar = null;
        for (int i = 0; i < size; i++) {
            sectionType = sectionTypes.get(i);
            switch (actionType) {
                case "ActionInsertSectionByProperty":
                    ActionInsertSectionByProperty actionInsertSectionByProperty = new ActionInsertSectionByProperty(sectionType, property, sectionIdentifier, sectionToInsert, insertBefore);
                    ar = yamlDocumentEntryPoint.performAction(actionInsertSectionByProperty, sectionType, "");
                    break;

                case "ActionInsertSection":
                    ActionInsertSection actionInsertSection = new ActionInsertSection(sectionType, sectionIdentifier, sectionToInsert, insertBefore);
                    ar = yamlDocumentEntryPoint.performAction(actionInsertSection, sectionType, sectionIdentifier);
                    break;

                case "ActionDeleteSectionByProperty":
                    ActionDeleteSectionByProperty actionDeleteSectionByProperty = new ActionDeleteSectionByProperty(sectionType, property, sectionIdentifier);
                    ar = yamlDocumentEntryPoint.performAction(actionDeleteSectionByProperty, sectionType, "");
                    break;

                case "ActionUpdateSectionByProperty":
                    ActionUpdateSectionByProperty actionUpdateSectionByProperty = new ActionUpdateSectionByProperty(sectionType, property, sectionIdentifier, sectionToInsert);
                    ar = yamlDocumentEntryPoint.performAction(actionUpdateSectionByProperty, sectionType, "");
                    break;

                default:
                    break;
            }

            if (ar != null && ar.actionExecuted) {
                return;
            }
        }
    }
}

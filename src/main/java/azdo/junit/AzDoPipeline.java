// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.command.CommandBundle;
import azdo.hook.Hook;
import azdo.utils.AzDoUtils;
import azdo.utils.GitUtils;
import azdo.utils.PropertyUtils;
import azdo.utils.Utils;
import azdo.yaml.ActionEnum;
import azdo.yaml.YamlDocumentEntryPoint;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/*
   AzDoPipeline is used in JUnit tests; it acts as a Java representation of an Azure DeVOps pipeline.
   It encapsulates classes that represents the Azure DevOps pipeline and template files (YAML).
   All AzDoPipeline methods used by the JUnit tests are forwarded to the encapsulated objects.
*/
public class AzDoPipeline implements Pipeline {
    private static Logger logger = LoggerFactory.getLogger(AzDoPipeline.class);
    private static final String SECTION_PARAMETERS = "parameters";
    private static final String SECTION_STAGE = "stage";
    private static final String SECTION_STAGES = "stages";
    private static final String SECTION_JOB = "job";
    private static final String SECTION_JOBS = "jobs";
    private static final String SECTION_STEP = "step";
    private static final String SECTION_TASK = "task";
    private static final String SECTION_SCRIPT = "script";
    private static final String SECTION_STEPS = "steps";
    private static final String IDENTIFIER_NAME = "name";

    private static final String DEMARCATION = "==============================================================================";
    private PropertyUtils properties;
    private Git git = null;
    private CredentialsProvider credentialsProvider;
    String yamlFile;
    Map<String, Object> yamlMap = null;
    String repositoryId = null;
    String pipelineId = null;
    RunResult runResult = new RunResult();
    private YamlDocumentEntryPoint yamlDocumentEntryPoint;
    public CommandBundle commandBundle = new CommandBundle();
    private static final String EXCLUDEFILESLIST = "\\excludedfileslist.txt";

    @SuppressWarnings("java:S1192")
    public AzDoPipeline(String propertyFile, String pipelineFile) {
        logger.debug("==> Object: AzDoPipeline");
        logger.debug("propertyFile {}:", propertyFile);
        logger.debug("pipelineFile {}:", pipelineFile);

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("Start AzDoPipeline: Initializing repository and pipeline");
        logger.debug(DEMARCATION);

        // Read the properties file and create the entry point
        properties = new PropertyUtils(propertyFile);
        yamlDocumentEntryPoint = new YamlDocumentEntryPoint(properties.getSourcePath(),
                properties.getTargetPath(),
                properties.getSourceBasePathExternal(),
                properties.getTargetBasePathExternal());

        // Read the main pipeline file; this is the YAML file used in the Azure DevOps pipeline (in the Azure DeVOps test project)
        yamlMap = yamlDocumentEntryPoint.read(pipelineFile);

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
                properties.getRepositoryName(),
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
                properties.getRepositoryName(),
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
        logger.debug("branchName {}:", branchName);
        logger.debug("dryRun {}:", dryRun);

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("Start pipeline {} for branch {}", properties.getRepositoryName(), branchName);
        logger.debug(DEMARCATION);

        /*******************************************************************************************
                                  Doing stuff for the main repository
         *******************************************************************************************/

        // Clone the repository to local if not done earlier
        // Keep the reference to the git object
        try {
            // Clone the main repository to local and initialize
            git = GitUtils.cloneAzdoToLocal(properties.getTargetPath(),
                    properties.getRepositoryName(),
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
                             Prepare for takeoff... of the pipeline .. and run it
         *******************************************************************************************/

        // Execute the commands in the bundle are executed
        commandBundle.execute(this);

        // Save the manipulated main YAML (incl. template files) to the target location.
        // The manipulated YAML files are stored in memory (in a YamlDocument or Template object). The target
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
        yamlMap = yamlDocumentEntryPoint.read(yamlFile);

        logger.debug("");
        logger.debug(DEMARCATION);
        logger.debug("End pipeline {} for branch {}", properties.getRepositoryName(), branchName);
        logger.debug(DEMARCATION);
    }

    /* Replace the value of a variable in the 'variables' section. Two constructions are possible:

       Construction 1:
       ==============
       variables:
         myVar : myValue

       Construction 2:
       ==============
       variables:
       - name: myVar
         value: myValue

      overrideVariable("myVar", "myNewValue") results in resp.
       variables:
         myVar : myNewValue

       variables:
       - name: myVar
         value: myNewValue

       This method does not replace variables defined in a Library.
     */
    public void overrideVariable(String variableName, String value) {
        logger.debug("==> Method: AzDoPipeline.overrideVariable {} with {}", variableName, value);

        // Replace according to construction 1
        yamlDocumentEntryPoint.executeCommand(ActionEnum.replaceValue,
                "variables",
                "",
                "",
                "",
                variableName,
                value,
                false);

        // Replace according to construction 2
        yamlDocumentEntryPoint.executeCommand(ActionEnum.replaceValue,
                "variables",
                "",
                IDENTIFIER_NAME,
                variableName,
                "value",
                value,
                false);
    }

    /* Replace the default value of a parameter in the 'parameters' section. Example:
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
      - name: myNumber
        type: number
        default: 8
        values:
        - 1
        - 2
        - 4
        - 8
        - 16
     */
    public void overrideParameterDefault(String parameterName, String value) {
        logger.debug("==> Method: AzDoPipeline.overrideParameterDefault {} with {}", parameterName, value);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.replaceValue,
                SECTION_PARAMETERS,
                "",
                IDENTIFIER_NAME,
                parameterName,
                "default",
                value,
                false);
    }

    /* Replace the value of a parameter in the 'template' section. Example:
       - template: step/mytemplate.yml
         parameters:
           tag: $(version)

       To replace the version to a fixed value (2.1.0), use:
       overrideTemplateParameter("tag", "2.1.0"). This results in:
       - template: step/mytemplate.yml@templates
         parameters:
           tag: 2.1.0
     */
    public void overrideTemplateParameter(String parameterName, String value) {
        logger.debug("==> Method: AzDoPipeline.overrideTemplateParameter: {} with {}", parameterName, value);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.replaceValue,
                parameterName,
                "",
                "",
                "",
                parameterName,
                value,
                false);
    }

    /* Override (or overwrite) any arbitrary string in the yaml file.
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

       If replaceAll is 'true' all occurences of literal in both the main YAML and the templates are replaced.
       If replaceAll is 'false' the first occurence of literal in both the main YAML and the templates are replaced.
     */
    public void overrideLiteral(String findLiteral, String replaceLiteral, boolean replaceAll) {
        logger.debug("==> Method: AzDoPipeline.overrideLiteral: Replaces {} with {}", findLiteral, replaceLiteral);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.replaceLiteral,
                "",
                "",
                "",
                "",
                findLiteral,
                replaceLiteral,
                replaceAll);
    }
    public void overrideLiteral(String findLiteral, String replaceLiteral) {
        overrideLiteral(findLiteral, replaceLiteral, true);
    }

    /* Replace the current branch with a given branch name.
       Example: Assume the following condition:
           and(succeeded(), eq(variables['Build.SourceBranchName'], 'main'))

       After applying public void overrideCurrentBranch("myFeature") it becomes
           and(succeeded(), eq('myFeature', 'main'))

       If replaceAll is 'true', all occurences in both the main YAML and the templates are replaced.
       If replaceAll is 'false', the first occurence in both the main YAML and the templates are replaced.
     */
    public void overrideCurrentBranch(String newBranchName, boolean replaceAll){
        logger.debug("==> Method: AzDoPipeline.overrideCurrentBranch with {}", newBranchName);
        overrideLiteral("variables[\'Build.SourceBranch\']", "\'refs/heads/" + newBranchName + "\'", replaceAll);
        overrideLiteral("$(Build.SourceBranch)", "refs/heads/" + newBranchName, replaceAll);
        overrideLiteral("variables[\'Build.SourceBranchName\']", "\'" + newBranchName + "\'", replaceAll);
        overrideLiteral("$(Build.SourceBranchName)", newBranchName, replaceAll);
        overrideLiteral("BUILD_SOURCEBRANCH", "refs/heads/" + newBranchName, replaceAll);
        overrideLiteral("BUILD_SOURCEBRANCHNAME", newBranchName, replaceAll);
    }
    public void overrideCurrentBranch(String newBranchName) {
        overrideCurrentBranch( newBranchName, true);
    }

    /* Skip a stage.
       The result is, that the stage is completely removed from the output pipeline yaml file, which basically is
       the same as skipping it.

       Example:
       =========
       - stage: my_stage
         displayName: 'This is my stage'

       Call skipStage("my_stage")
       ==> The stage with name "my_stage" is skipped
     */
    public void skipStage(String stageName) {
        logger.debug("==> Method: AzDoPipeline.skipStage: {}", stageName);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
                SECTION_STAGES,
                "",
                "",
                "",
                SECTION_STAGE,
                stageName,
                false);
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
    public void skipJob(String jobName) {
        logger.debug("==> Method: AzDoPipeline.skipJob: {}", jobName);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
                SECTION_JOBS,
                "",
                "",
                "",
                SECTION_JOB,
                jobName,
                false);
    }

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
    public void skipStep(String stepName) {
        logger.debug("==> Method: AzDoPipeline.skipStep: {}", stepName);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
                SECTION_STEPS,
                "",
                "",
                "",
                SECTION_STEP,
                stepName,
                false);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
                SECTION_STEPS,
                "",
                "",
                "",
                SECTION_TASK,
                stepName,
                false);
    }

    /* The original step is replaced by a mock step. This is a step of type script. The argument 'inlineScript' is
       added to the mock. Depending on the job pool this can be a Powershell script (Windows) or a bash script (Linux)
     */
    public void mockStep(String stepValue, String inlineScript){
        logger.debug("==> Method: AzDoPipeline.mockStep: {}", stepValue);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.mock,
                SECTION_STEPS,
                "",
                "",
                "",
                stepValue,
                inlineScript,
                false);
    }

    public RunResult getRunResult() {
        return runResult;
    }

    public void setRunResult(RunResult runResult) {
        this.runResult = runResult;
    }

    public PropertyUtils getProperties() {
        return properties;
    }
}


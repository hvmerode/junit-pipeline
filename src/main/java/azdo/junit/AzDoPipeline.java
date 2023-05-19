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
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class AzDoPipeline implements Pipeline {
    private static Logger logger = LoggerFactory.getLogger(AzDoPipeline.class);
    private PropertyUtils properties;
    private Git git = null;
    private CredentialsProvider credentialsProvider;
    String yamlFile;
    String repositoryId = null;
    String pipelineId = null;
    RunResult runResult = new RunResult();
    private YamlDocumentEntryPoint yamlDocumentEntryPoint;
    public CommandBundle commandBundle = new CommandBundle();
    private static final String EXCLUDEFILESLIST = "\\excludedfileslist.txt";

    @SuppressWarnings("java:S1192")
    public AzDoPipeline(String propertyFile, String pipelineFile) {
        logger.debug("==> Object: AzDoPipeline");
        logger.debug("");
        logger.debug("=================================================================");
        logger.debug("Start AzDoPipeline: Initializing repository and pipeline");
        logger.debug("=================================================================");

        properties = new PropertyUtils(propertyFile);
        yamlDocumentEntryPoint = new YamlDocumentEntryPoint();

        // Read the main pipeline file
        Map<String, Object> yamlMap = yamlDocumentEntryPoint.read(pipelineFile);

        // Initialize some stuff needed for external resources; this must only be done only nce
        yamlDocumentEntryPoint.initExternalResources(yamlMap, properties);

        yamlFile = pipelineFile;
        credentialsProvider = new UsernamePasswordCredentialsProvider(
                properties.getAzDoUser(),
                properties.getAzdoPat());

        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        // If no repository exists, create a new repo in Azure DevOps. Otherwise, make use of the existing repo //
        //////////////////////////////////////////////////////////////////////////////////////////////////////////
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

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // If no pipeline exists, create a new pipeline in Azure DevOps. Otherwise, make use of the existing pipeline //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        try {
            // Get the pipelineId of the existing pipeline
            pipelineId = AzDoUtils.callGetPipelineApi (properties.getAzDoUser(),
                    properties.getAzdoPat(),
                    properties.getRepositoryName(),
                    properties.getAzdoEndpoint(),
                    properties.getPipelinesApi(),
                    properties.getPipelinesApiVersion());
        }
        catch (Exception e) {
            logger.debug("Exception occurred; continue");
        }

        try {
            logger.debug("Create a new pipeline if not existing");
            // Create a new pipeline if not existing
            if (pipelineId == null) {
                // Create a pipeline; the name is equal to the name of the repository
                pipelineId = AzDoUtils.callCreatePipelineApi (properties.getAzDoUser(),
                        properties.getAzdoPat(),
                        properties.getPipelinePathRepository(),
                        properties.getRepositoryName(),
                        properties.getAzdoEndpoint(),
                        properties.getPipelinesApi(),
                        properties.getPipelinesApiVersion(),
                        repositoryId);
            }
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot create a new pipeline");
            e.printStackTrace();
        }

        logger.debug("");
        logger.debug("=================================================================");
        logger.debug("End AzDoPipeline: Initializing repository and pipeline");
        logger.debug("=================================================================");
        logger.debug("");
    }

    /* After the yaml file has been manipulated and can be used to perform the unittest, the
       startPipeline method is called.
       This method creates a new yaml file with the manipulated settings in the local target repository
       used for the unittest. The local repository is committed and pushed to the remote repository.
       After all files are pushed, the pipeline in Azure Devops (that makes use of the manipulated yaml file)
       is called by means of an API.
       The last step is to reload the original yaml file, so it can be used for the next test.
     */
    public void startPipeline() throws IOException {
        startPipeline("master", null, false);
    }
    public void startPipeline(boolean dryRun) throws IOException {
        startPipeline("master", null, dryRun);
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

        // Clone the repository to local if not done earlier
        // Keep the reference to the git object
        try {
            //Utils.deleteDirectory(properties.getTargetPath());
            git = gitClone();
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot clone repository to local");
            e.printStackTrace();
            return;
        }

        // If git object is invalid after the clone (for some reason), recreate it again
        if (git == null) {
            logger.debug("Recreate git object");
            git = GitUtils.createGit(properties.getTargetPath());
            //File f = new File(properties.getTargetPath());
            //git = Git.open(f);
        }

        // Check whether there is a remote branch
        boolean isRemote = GitUtils.containsBranch(git, branchName);

        // Perform the checkout
        GitUtils.checkout(git, properties.getTargetPath(), branchName, !isRemote);

        // Copy local resources from main source to target directory
        try {
            // Copy all sources from the source local repo to the target local repo
            Utils.copyAll(properties.getSourcePath(), properties.getTargetPath(), properties.getTargetExludeList());
        }
        catch (Exception e) {
            logger.debug("Exception occurred.Cannot copy local files to target");
            e.printStackTrace();
        }

        // Execute the commands in the bundle are executed
        commandBundle.execute(this);

        //  Save the manipulated main YAML (incl. template files) to the target location
        yamlDocumentEntryPoint.dumpYaml(properties.getTargetPath());

        // Perform all (pre)hooks
        if (hooks != null) {
            logger.debug("Execute hooks");
            int size = hooks.size();
            for (int i = 0; i < size; i++) {
                hooks.get(i).executeHook();
            }
        }

        // Push the local repo to remote
        GitUtils.commitAndPush(git,
                properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getCommitPatternList());

        if (git != null)
            git.close();

        // Call Azure Devops API to start the pipeline and retrieve the result
        // If dryRun is true, do not start the pipeline
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

        // Re-read the original pipeline for the next test (for a clean start of the next test)
        yamlDocumentEntryPoint.read(yamlFile);
    }

    public void executeScript(String filePath) throws IOException{
        logger.debug("==> Method: AzDoPipeline.executeScript: {}", filePath);
        File file = new File(filePath);
        if(!file.isFile()){
            throw new IllegalArgumentException("The file " + filePath + " does not exist");
        }
        if(Utils.isLinux()){
            logger.debug("Executing on Linux");
            Runtime.getRuntime().exec(new String[] {"/bin/sh ", "-c", filePath}, null);
        } else if(Utils.isWindows()){
            logger.debug("Executing on Windows");
            Runtime.getRuntime().exec("cmd /c call " + filePath);
        }
    }

    // Clone the repo to local and initialize
    private Git gitClone () {
        logger.debug("==> Method: AzDoPipeline.gitClone");

        return GitUtils.cloneAzdoToLocal(properties.getTargetPath(),
                properties.getRepositoryName(),
                properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getTargetOrganization(),
                properties.getTargetProject());
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

        logger.debug("==> Method: AzDoPipeline.overrideVariable: " + variableName + " with " + value);

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
                "name",
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
                "parameters",
                "",
                "name",
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
                "stages",
                "",
                "",
                "",
                "stage",
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
                "jobs",
                "",
                "",
                "",
                "job",
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
                "steps",
                "",
                "",
                "",
                "step",
                stepName,
                false);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.delete,
                "steps",
                "",
                "",
                "",
                "task",
                stepName,
                false);
    }

    /* The original step is replaced by a mock step. This is a step of type script. The argument 'inlineScript' is
       added to the mock. Depending on the job pool this can be a Powershell script (Windows) or a bash script (Linux)
     */
    public void mockStep(String stepValue, String inlineScript){
        logger.debug("==> Method: AzDoPipeline.mockStep: {}", stepValue);
        yamlDocumentEntryPoint.executeCommand(ActionEnum.mock,
                "steps",
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


// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.command.CommandBundle;
import azdo.hook.Hook;
import azdo.utils.GitUtils;
import azdo.utils.Utils;
import azdo.yaml.ActionEnum;
import azdo.yaml.YamlDocumentEntryPoint;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.List;

public class AzDoPipeline implements Pipeline {
    private static Logger logger = LoggerFactory.getLogger(AzDoPipeline.class);
    private TestProperties properties;
    private Git git = null;
    private CredentialsProvider credentialsProvider;
    String yamlFile;
    String repositoryId = null;
    String pipelineId = null;
    RunResult runResult = null;
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

        properties = new TestProperties(propertyFile);
        yamlDocumentEntryPoint = new YamlDocumentEntryPoint();
        yamlDocumentEntryPoint.read(pipelineFile,
                properties.getTargetBasePathExternal(),
                properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getTargetOrganization(),
                properties.getTargetProject());
        yamlFile = pipelineFile;
        credentialsProvider = new UsernamePasswordCredentialsProvider(
                properties.getAzDoUser(),
                properties.getAzdoPat());

        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        // If no repository exists, create a new repo in Azure DevOps. Otherwise, make use of the existing repo //
        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        createRepositoryIfNotExists(properties.getRepositoryName());

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // If no pipeline exists, create a new pipeline in Azure DevOps. Otherwise, make use of the existing pipeline //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        try {
            // Get the pipelineId of the existing pipeline
            pipelineId = AzDoApi.callGetPipelineApi (properties);
        }
        catch (Exception e) {
            logger.debug("Exception occurred; continue");
        }

        try {
            // Create a new pipeline if not existing
            if (pipelineId == null) {
                // Create a pipeline; the name is equal to the name of the repository
                pipelineId = AzDoApi.callCreatePipelineApi(properties, repositoryId);
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
        startPipeline("master", null);
    }
    public void startPipeline(String branchName) throws IOException {
        startPipeline(branchName, null);
    }
    public void startPipeline(String branchName, List<Hook> hooks) throws IOException {
        logger.debug("==> Method: AzDoPipeline.startPipeline");
        boolean recreate = false;

        // Clone the repository to local if not done earlier
        try {
            Utils.deleteDirectory(properties.getTargetPath());
            //git = gitClone();
            gitClone();
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot clone repository to local");
            e.printStackTrace();
        }

        // If git object is invalid after the clone (for some reason), recreate it again
        if (git == null) {
            logger.debug("Recreate git object");
            File f = new File(properties.getTargetPath());
            git = Git.open(f);
            recreate = true;
        }

        // Check whether there is a remote branch
        boolean isRemote = containsBranch(branchName);

        // Perform the checkout
        try {
            logger.debug("git.checkout");
            git.checkout()
                    .setCreateBranch(!isRemote)
                    .setName(branchName)
                    .call();
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot checkout; just continue");
            e.printStackTrace();
        }

        // Copy local resources from main source to target directory
        try {
            // Copy all sources from the source local repo to the target local repo
            copyAll(properties.getSourcePath(), properties.getTargetPath());
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
        try {
            logger.debug("git.add");
            git.add()
                    .addFilepattern(".")
                    .call();

            // Stage all changed files, including deleted files
            int size = properties.getCommitPatternList().size();
            AddCommand command = git.add();
            for (int i = 0; i < size; i++) {
                command = command.addFilepattern(properties.getCommitPatternList().get(i));
                logger.debug("Pattern: {}", properties.getCommitPatternList().get(i));
            }
            command.call();
            gitCommitAndPush ();
        }

        catch (Exception e) {
            logger.debug("Exception pushing to repo");
            e.printStackTrace();
        }

        // If Git was recreated, close it
        if (recreate) {
            logger.debug("git.close");
            git.close();
        }

        // Call Azure Devops API to start the pipeline and retrieve the result
        AzDoApi.callPipelineRunApi(properties, pipelineId, branchName);
        runResult = AzDoApi.callRunResult(properties, pipelineId);

        // Re-read the original pipeline for the next test (for a clean start of the next test)
        yamlDocumentEntryPoint.read(yamlFile,
                properties.getTargetBasePathExternal(),
                properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getTargetOrganization(),
                properties.getTargetProject());
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

    public void copyAll(String source, String target) throws IOException{
        logger.debug("==> Method: AzDoPipeline.copyAll");
        if(Utils.isLinux()){
            logger.debug("Executing on Linux: cp {} {}", source, target);
            // TODO: Exclude certain file types and directories
            Runtime.getRuntime().exec("/bin/sh -c cp " + source + " " + target);
        } else if(Utils.isWindows()){
            logger.debug("Executing on Windows: xcopy {} {}  /E /H /C /I /Y /exclude:{}", source, target, target + EXCLUDEFILESLIST);
            Runtime.getRuntime().exec("cmd.exe /c mkdir " + target);
            Utils.wait(3000);
            //Runtime.getRuntime().exec("cmd.exe /c (echo idea& echo target& echo .git& echo class) > " + target + EXCLUDEFILESLIST);
            Runtime.getRuntime().exec("cmd.exe /c " + properties.getTargetExludeList() + " > " + target + EXCLUDEFILESLIST);
            Utils.wait(3000);
            Runtime.getRuntime().exec("cmd.exe /c xcopy " + source + " " + target + " /E /H /C /I /Y /exclude:" + target + EXCLUDEFILESLIST);
            Utils.wait(3000);
        }
    }

    private void gitCommitAndPush () throws GitAPIException {
        logger.debug ("==> Method: AzDoPipeline.gitCommitAndPush");

        if (git != null) {
            logger.debug("git.commit");
            git.commit()
                    .setAll(true)
                    .setAuthor(properties.getAzDoUser(), "")
                    .setCommitter(properties.getAzDoUser(), "")
                    .setMessage("Init repo")
                    .call();

            logger.debug("git.push");
            git.push()
                    .setPushAll()
                    .setCredentialsProvider(credentialsProvider)
                    .setForce(true)
                    .call();
        }
    }

    // Clone the repo to local and initialize
    private void gitClone () {
        logger.debug("==> Method: AzDoPipeline.gitClone");

        GitUtils.cloneAzdoToLocal(properties.getTargetPath(),
                properties.getRepositoryName(),
                properties.getAzDoUser(),
                properties.getAzdoPat(),
                properties.getTargetOrganization(),
                properties.getTargetProject());
    }

//    private Git gitClone () {
//        logger.debug("==> Method: AzDoPipeline.gitClone");

        // Create the target path if not existing
//        Utils.makeDirectory(properties.getTargetPath());

        // Create the credentials provider
//        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(
//                properties.getAzDoUser(),
//                properties.getAzdoPat());

        // Clone the repo
//        try {
//            logger.debug("git.clone");
//            git = Git.cloneRepository()
//                    .setURI(properties.getUriTargetRepository())
//                    .setCloneAllBranches(true)
//                    .setCredentialsProvider(credentialsProvider)
//                    .setDirectory(new File(properties.getTargetPath()))
//                    .call();
//        }
//        catch (Exception e) {
//            logger.debug("Cannot clone, but just proceed");
//        }

//        return git;
//    }


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

    public TestProperties getProperties() {
        return properties;
    }

    private boolean containsBranch(String name) {
        logger.debug("==> Method: AzDoPipeline.containsBranch");
        try {
            ListBranchCommand command = git.branchList();
            command.setListMode(ListBranchCommand.ListMode.ALL);
            List<Ref> branches = command.call();
            for (Ref ref : branches) {
                if (ref.getName().endsWith("/" + name)) {
                    return true;
                }
            }
        }
        catch (Exception e) {
            logger.debug("Cannot check whether the branch is remote");
        }
        return false;
    }

    // Create a new repo in Azure DevOps (if it not exists)
    private void createRepositoryIfNotExists (String repositoryName){
        String localRepositoryPath = ""; // Local directory where to which this repository will be cloned
        try {
            // Get the repositoryId of the existing repository
            repositoryId = AzDoApi.callGetRepositoryApi(properties, repositoryName);
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Repository probable does exist; continue");
        }

        try {
            // Create a new repository if not existing
            if (repositoryId == null) {
                // Retrieve the project-id of the Azure DevOps project with a given name
                String projectId = AzDoApi.callGetProjectIdApi(properties);

                logger.debug("Delete local repository in directory {}", localRepositoryPath);
                Utils.deleteDirectory(localRepositoryPath);

                // Create remote repo using the AzDo API (this may fail if exists, but just continue)
                repositoryId = AzDoApi.callCreateRepoApi(properties, repositoryName, projectId);

                // The repo did not exist; clone the repo to local and initialize
                //git = gitClone();
                gitClone();
            }
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot create a new repository");
            e.printStackTrace();
        }
    }
}


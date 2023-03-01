package azdo.junit;

import azdo.command.CommandBundle;
import azdo.command.CommandEnum;
import azdo.yaml.YamlDocumentSet;
import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Map;

public class AzDoPipeline {
    private static Logger logger = LoggerFactory.getLogger(AzDoPipeline.class);
    private Map<String, Object> yamlMap;
    private TestProperties properties = new TestProperties();
    private Git git;
    String yamlFile;
    String repositoryId = null;
    String pipelineId = null;
    RunResult runResult = null;
    private YamlDocumentSet yamlDocumentSet;
    public CommandBundle commandBundle = new CommandBundle();

    public AzDoPipeline(String pipelineFile) {
        yamlDocumentSet = new YamlDocumentSet();
        yamlDocumentSet.read(pipelineFile);
        yamlFile = pipelineFile;

        logger.info("");
        logger.info("=================================================================");
        logger.info("Start AzDoPipeline: Initializing repository and pipeline");
        logger.info("=================================================================");
        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        // If no repository exists, create a new repo in Azure DevOps. Otherwise, make use of the existing repo //
        //////////////////////////////////////////////////////////////////////////////////////////////////////////
        try {
            // Get the repositoryId of the existing repository
            repositoryId = AzDoApi.callGetRepositoryApi(properties.getRepositoryName());
        }
        catch (Exception e) {
            logger.info("==> Exception occurred; continue");
        }

        try {
            // Create a new repository if not existing
            if (repositoryId == null) {
                logger.info("==> Delete local repository in directory ", properties.getTargetPath());
                deleteDirectory(new File(properties.getTargetPath()));

                // Create remote repo using the AzDo API (this may fail if exists, but just continue)
                repositoryId = AzDoApi.callCreateRepoApi();

                CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(
                        properties.getUserTargetRepository(),
                        properties.getPasswordTargetRepository());

                // The repo did not exist; clone the repo to local and initialize
                logger.info("==> Git clone.");
                git = Git.cloneRepository()
                        .setURI(properties.getUriTargetRepository())
                        .setBranch(properties.getBranchTargetRepository())
                        .setCredentialsProvider(credentialsProvider)
                        .setDirectory(new File(properties.getTargetPath()))
                        .call();
                gitCommitAndPush (git, credentialsProvider, false);
            }
        }
        catch (Exception e) {
            logger.info("==> Exception occurred. Cannot create a new repository");
            e.printStackTrace();
        }

        /////////////////////////////////////////////////////////
        // Copy local resource from source to target directory //
        /////////////////////////////////////////////////////////
        try {
            // Copy all sources from the source local repo to the target local repo
            copyAll(properties.getSourcePath(), properties.getTargetPath());
        }
        catch (Exception e) {
            logger.info("==> Exception occurred.Cannot copy local files to target");
            e.printStackTrace();
        }

        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        // If no pipeline exists, create a new pipeline in Azure DevOps. Otherwise, make use of the existing pipeline //
        ////////////////////////////////////////////////////////////////////////////////////////////////////////////////
        try {
            // Get the pipelineId of the existing pipeline
            pipelineId = AzDoApi.callGetPipelineApi (properties.getRepositoryName());
        }
        catch (Exception e) {
            logger.info("==> Exception occurred; continue");
        }

        try {
            // Create a new pipeline if not existing
            if (pipelineId == null) {
                // Create a pipeline; the name is equal to the name of the repository
                pipelineId = AzDoApi.callCreatePipelineApi(repositoryId, properties.getPipelinePathRepository());
            }
        }
        catch (Exception e) {
            logger.info("==> Exception occurred. Cannot create a new pipeline");
            e.printStackTrace();

        }

        logger.info("");
        logger.info("=================================================================");
        logger.info("End AzDoPipeline: Initializing repository and pipeline");
        logger.info("=================================================================");
        logger.info("");
    }

    private boolean deleteDirectory(File directoryToBeDeleted) {
        File[] allContents = directoryToBeDeleted.listFiles();
        if (allContents != null) {
            for (File file : allContents) {
                deleteDirectory(file);
            }
        }
        return directoryToBeDeleted.delete();
    }

    public static void wait(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }

    public void executeScript(String filePath) throws IOException{
        logger.info("==> executeScript: " + filePath);
        File file = new File(filePath);
        if(!file.isFile()){
            throw new IllegalArgumentException("The file " + filePath + " does not exist");
        }
        if(isLinux()){
            logger.info("==> Executing on Linux");
            Runtime.getRuntime().exec(new String[] {"/bin/sh", "-c", filePath}, null);
        } else if(isWindows()){
            logger.info("==> Executing on Windows");
            Runtime.getRuntime().exec("cmd /c call " + filePath);
        }
    }

    public void copyAll(String source, String target) throws IOException{
        logger.info("==> copyAll");
        if(isLinux()){
            logger.info("==> Executing on Linux: " + "cp " + source + " " + target);
            // TODO: Exclude certain file types and directories
            Runtime.getRuntime().exec("cp " + source + " " + target);
        } else if(isWindows()){
            logger.info("==> Executing on Windows: " + "xcopy " + source + " " + target + " /E /H /C /I /Y /exclude:" + target + "\\excludedfileslist.txt");
            Runtime.getRuntime().exec("cmd.exe /c mkdir " + target);
            wait(3000);
            Runtime.getRuntime().exec("cmd.exe /c (echo idea& echo target& echo .git& echo class) > " + target + "\\excludedfileslist.txt");
            wait(3000);
            Runtime.getRuntime().exec("cmd.exe /c xcopy " + source + " " + target + " /E /H /C /I /Y /exclude:" + target + "\\excludedfileslist.txt");
            wait(3000);
        }
    }

    public static boolean isLinux(){
        String os = System.getProperty("os.name");
        return os.toLowerCase().indexOf("linux") >= 0;
    }

    public static boolean isWindows(){
        String os = System.getProperty("os.name");
        return os.toLowerCase().indexOf("windows") >= 0;
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
        logger.info("==> Method startPipeline");

        // Every time the pipeline starts, the commands in the bundle are executed
        commandBundle.execute(this);

        //  Save the manipulated main YAML and template files to the target location
        yamlDocumentSet.dumpYaml(properties.getTargetPath());

        // Create the credentials provider
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(
                properties.getUserTargetRepository(),
                properties.getPasswordTargetRepository());

        // Push the local repo to remote
        try {
            logger.info("==> Git.open");
            File f = new File(properties.getTargetPath());
            Git git = Git.open(f);
            git.add()
                    .addFilepattern(".")
                    .call();

            // Stage all changed files, including deleted files
            int size = properties.getCommitPatternList().size();
            AddCommand command = git.add();
            for (int i = 0; i < size; i++) {
                command = command.addFilepattern(properties.getCommitPatternList().get(i));
                logger.info("==> " + properties.getCommitPatternList().get(i));
            }
            command.call();
            gitCommitAndPush (git, credentialsProvider, true);

            logger.info("==> git.close");
            git.close();
        }

        catch (Exception e) {
            logger.info("==> Exception pushing to repo");
            e.printStackTrace();
        }

        // Call Azure Devops API to start the pipeline and retrieve the result
        AzDoApi.callPipelineRunApi(pipelineId);
        runResult = AzDoApi.callRunResult(pipelineId, properties.getBuildApiPollFrequency(), properties.getBuildApiPollTimeout());

        // Re-read the original pipeline for the next test
        yamlDocumentSet.read(yamlFile);
    }

    private void gitCommitAndPush (Git git, CredentialsProvider credentialsProvider, boolean useRefSpecs) throws GitAPIException {
        logger.info("==> git.commit");
        git.commit()
                .setAll(true)
                .setAuthor(properties.getUserTargetRepository(), "")
                .setCommitter(properties.getUserTargetRepository(), "")
                .setMessage("Init repo")
                .call();

        logger.info("==> git.push");
        if(useRefSpecs) {
            git.push()
                    .setPushAll()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote(properties.getUriTargetRepository())
                    .setForce(true)
                    .setRemote("origin")
                    .setRefSpecs(new RefSpec(properties.getBranchTargetRepository() + ":" + properties.getBranchTargetRepository()))
                    .call();
        } else {
            git.push()
                    .setPushAll()
                    .setCredentialsProvider(credentialsProvider)
                    .setRemote(properties.getUriTargetRepository())
                    .setForce(true)
                    .setRemote("origin")
                    .call();
        }
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

        logger.info("==> Method overrideVariable: " + variableName + " with " + value);

        // Replace according to construction 1
        yamlDocumentSet.executeCommand(CommandEnum.replaceValue,
                "variables",
                "",
                "",
                "",
                variableName,
                value,
                false);

        // Replace according to construction 2
        yamlDocumentSet.executeCommand(CommandEnum.replaceValue,
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
        logger.info("==> Method overrideParameterDefault: " + parameterName + " with " + value);
        yamlDocumentSet.executeCommand(CommandEnum.replaceValue,
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
        logger.info("==> Method overrideTemplateParameter: " + parameterName + " with " + value);
        yamlDocumentSet.executeCommand(CommandEnum.replaceValue,
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
        logger.info("==> Method overrideLiteral: Replaces " + findLiteral + " with " + replaceLiteral);
        yamlDocumentSet.executeCommand(CommandEnum.replaceLiteral,
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
        logger.info("==> Method overrideCurrentBranch with: " + newBranchName);
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
        logger.info("==> Method skipStage: " + stageName);
        yamlDocumentSet.executeCommand(CommandEnum.delete,
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
        logger.info("==> Method skipJob: " + jobName);
        yamlDocumentSet.executeCommand(CommandEnum.delete,
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
        logger.info("==> Method skipStep: " + stepName);
        yamlDocumentSet.executeCommand(CommandEnum.delete,
                "steps",
                "",
                "",
                "",
                "step",
                stepName,
                false);
        yamlDocumentSet.executeCommand(CommandEnum.delete,
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
        yamlDocumentSet.executeCommand(CommandEnum.mock,
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
}

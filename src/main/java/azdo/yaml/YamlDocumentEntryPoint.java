// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

import azdo.action.Action;
import azdo.utils.*;
import org.eclipse.jgit.api.Git;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 A YamlDocumentEntryPoint is the entry point of the main pipeline YAML file.
 YamlDocumentEntryPoint forms the point of contact between the pipeline - as represented by
 the AzDoPipeline class - and the YamlDocument class that represents the main YAML
 pipeline file.
 *******************************************************************************************/
public class YamlDocumentEntryPoint {
    private static final Log logger = Log.getLogger();

    // Refers to the main pipeline file, which is the entrypoint of the pipeline.
    private YamlDocument mainYamlDocument;

    // The location of the main repository - containing the main pipeline file - on the local filesystem.
    // This location contains the original (source) pipeline and template files (from the same main repository).
    private String sourcePath = "";


    // The location of the main repository - containing the main pipeline file - on the local filesystem.
    // This location contains the manipulated pipeline and template files (from the same main repository).
    private String targetPath = "";

    // the sourceBasePathExternal and targetBasePathExternal refer to the local filesystem, containing the
    // source files of external repositories (defined in the resources section of the main pipeline file)
    // and the target files (to which the manipulated template files are written).
    private String sourceBasePathExternal = "";
    private String targetBasePathExternal = "";

    // The source- and target repository names refer to the name of the main repository (the source) and its
    // corresponding repository in the Azure DevOps test project.
    private String sourceRepositoryName = "";
    private String targetRepositoryName = "";

    // List of repositories, defined in the resources section in the main pipeline file.
    ArrayList<RepositoryResource> repositoryList = null;

    // Constructor
    public YamlDocumentEntryPoint(String sourcePath,
                                  String targetPath,
                                  String sourceBasePathExternal,
                                  String targetBasePathExternal,
                                  String sourceRepositoryName,
                                  String targetRepositoryName) {
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.sourceBasePathExternal = sourceBasePathExternal;
        this.targetBasePathExternal = targetBasePathExternal;
        this.sourceRepositoryName = sourceRepositoryName;
        this.targetRepositoryName = targetRepositoryName;
    }

    @SuppressWarnings("java:S1192")
    /******************************************************************************************
     Parses the yaml map of the main pipeline file and initializes the resources (external
     repositories) in the "resources" section.
     @param yamlMap The Java object structure of the main YAML file.
     @param properties Reference to PropertyUtils object
     ******************************************************************************************/
    public void initExternalResources(Map<String, Object> yamlMap, PropertyUtils properties) {
        logger.debug("==> Method: YamlDocumentEntryPoint.initExternalResources");

        if (yamlMap == null) {
            logger.debug("The yamlMap parameter is null; return");
            return;
        }

        // Get all repositories containing external templates from the resources section.
        // Use the source project if no project is defined in the resource; assumed it that the external repositories
        // are from the original project if no project is defined.
        repositoryList = getRepositoriesFromResources(yamlMap,
                properties.getTargetBasePathExternal(),
                properties.getSourceProject());

        // Run trough all repositories and determine whether they need to be cloned and pushed to the Azure DevOps test project.
        repositoryList.forEach(repository -> {
            String source = repository.localBase + "/" + repository.name + RepositoryResource.LOCAL_SOURCE_POSTFIX;
            if (Utils.pathIsEmptyOrNotExisting(source)) {

                // Clone the repository containing external templates from the remote (source) repository to
                // the local file system, and remove the .git directory. The .git directory must be removed to
                // 'unmount' from the original repository; it is later pushed to the Azure DevOps test repository.
                // The directory must be copied to prevent it is overwritten by the createRemoteRepositories command.
                cloneAndRenameExternalRepositories(repository,
                        properties.getAzDoUser(),
                        properties.getAzdoPat(),
                        properties.getTargetOrganization(),
                        true);

                // Create remote repository in the Azure DevOps test project.
                // This repository is a copy from the original external repository.
                // To have all external repositories in one Azure DevOps test project means that they can also be manipulated.
                createRemoteRepositories(repository,
                        properties.getAzDoUser(),
                        properties.getAzdoPat(),
                        properties.getTargetOrganization(),
                        properties.getTargetProject(),
                        properties.getAzdoBaseUrl(),
                        properties.getAzdoEndpoint(),
                        properties.getGitApi(),
                        properties.getGitApiVersion(),
                        properties.getProjectApi(),
                        properties.getProjectApiVersion(),
                        properties.getGitApiRepositories());

                // Copy the files of the source (local copy of external repository files) to the local target.
                copyAllSourceFiles(repository, properties.getTargetExludeList());

                // Checkout/push the local repository containing external templates to the Azure DevOps test project.
                commitAndPushAllCode(repository,
                        properties.getAzDoUser(),
                        properties.getAzdoPat(),
                        properties.getCommitPatternList(),
                        properties.isContinueOnError());
            }
        });
    }

    /******************************************************************************************
     Read all templates. This includes both local and external templates.
     repositories) in the "resources" section.
     If includeExternalTemplates is 'false', external templates are not taken into account.
     @param includeExternalTemplates Determines whether also external templates (templates
                                     in other repositories and even other projects) must be read.
                                     They are either include or excluded from parsing and
                                     manipulation.
     @param continueOnError If an error situation occurs, it is logged as an error (not always)
                            and execution continues if the value is 'true'.
     ******************************************************************************************/
    public void readTemplates (boolean includeExternalTemplates, boolean continueOnError) {
        mainYamlDocument.readTemplates(
                sourcePath,
                targetPath,
                sourceBasePathExternal,
                targetBasePathExternal,
                sourceRepositoryName,
                targetRepositoryName,
                repositoryList,
                includeExternalTemplates,
                continueOnError);
    }

    @SuppressWarnings("java:S1192")
    /******************************************************************************************
     Reads the original main pipeline file from the local file system and creates a main
     YAML map object.
     This map is kept in memory. In addition, it creates YAML maps from template files that
     are referenced in the main pipeline file. This cascades recursively until the last template
     file.
     @param mainPipelineFile The name of the mainPipelineFile as providedas argument while
                             creating the AzDoPipeline object.
     @param continueOnError If an error situation occurs, it is logged as an error (not always)
                            and execution continues if the value is 'true'.
     ******************************************************************************************/
    public Map<String, Object> read (String mainPipelineFile, boolean continueOnError) {
        logger.debug("==> Method: YamlDocumentEntryPoint.read");
        logger.debug("mainPipelineFile: {}", mainPipelineFile);

        // First read the main YAML file
        // Relativize if it contains the full path
        mainPipelineFile = Utils.relativize(sourcePath, mainPipelineFile);
        logger.info("mainPipelineFile: {}", mainPipelineFile);
        mainYamlDocument = new YamlDocument(mainPipelineFile,
                sourcePath,
                targetPath,
                sourceRepositoryName,
                targetRepositoryName);
        Map<String, Object> yamlMap = mainYamlDocument.readYaml(continueOnError);

        return yamlMap;
    }

    /******************************************************************************************
     Create remote - external - repositories in the Azure DevOps test project.
     @param repository Repository characteristics of the repository created in the
                       target AzDo project.
     @param azdoUser User used in the Azure DevOps API calls, defined in the target AzDo project.
     @param azdoPat Personal Access Token used in the Azure DevOps API calls, defined in the
                    target AzDo project.
     @param organization Organization of the target AzDo project as defined in property file.
                         For example "myorg".
     @param project Project of the target AzDo project as defined in property file.
                    For example "myTargetProject".
     @param azdoBaseUrl Url of the target AzDo organization in the
                        format https://dev.azure.com/{organization}.
     @param azdoEndpoint Url of the target AzDo API endpoint in the
                         format https://dev.azure.com/{organization}/{project}/_apis.
     @param azdoGitApi Has default value "/git".
     @param azdoGitApiVersion Version of the Git API, for example "api-version=7.0".
     @param azdoProjectApi Has default value "/projects".
     @param azdoProjectApiVersion Version of the Project API, for example "api-version=7.0".
     @param azdoGitApiRepositories Has default value "/repositories".
     ******************************************************************************************/
    private void createRemoteRepositories(RepositoryResource repository,
                                          String azdoUser,
                                          String azdoPat,
                                          String organization,
                                          String project,
                                          String azdoBaseUrl,
                                          String azdoEndpoint,
                                          String azdoGitApi,
                                          String azdoGitApiVersion,
                                          String azdoProjectApi,
                                          String azdoProjectApiVersion,
                                          String azdoGitApiRepositories) {
        logger.debug("==> Method: YamlDocumentEntryPoint.createRemoteRepositories");
        logger.debug("organization: {}", organization);
        logger.debug("project: {}", project);

        // Create the repository in the test project
        String path = repository.localBase + "/" + repository.name;
        logger.debug("Path: {}", path);
        //Utils.deleteDirectory(path); // Delete it first
        AzDoUtils.createRepositoryIfNotExists (azdoUser,
                azdoPat,
                path,
                repository.name,
                organization,
                project,
                azdoBaseUrl,
                azdoEndpoint,
                azdoGitApi,
                azdoGitApiVersion,
                azdoProjectApi,
                azdoProjectApiVersion,
                azdoGitApiRepositories);

        // Checkout main branch
        // Note, that in the Azure Devops test project, external repositories only contain a "master" branch, although
        // the pipeline may point to another branch (using 'ref').
        Git git = GitUtils.createGit (path);
        boolean isRemote = GitUtils.containsBranch(git, GitUtils.BRANCH_MASTER);
        GitUtils.checkout(git, path, GitUtils.BRANCH_MASTER, !isRemote);
    }

    /******************************************************************************************
     Commit and push the repository with the manipulated template files to the Azure DevOps test
     project; first method signature.
     @param azdoUser User used in the Azure DevOps API calls, defined in the target AzDo project.
     @param azdoPat Personal Access Token used in the Azure DevOps API calls, defined in the
                    target AzDo project.
     @param commitPatternList List of file types and directories included in a commit.
     @param continueOnError If an error situation occurs, it is logged as an error (not always)
                            and execution continues if the value is 'true'.
     ******************************************************************************************/
    public void commitAndPushTemplates (String azdoUser,
                                        String azdoPat,
                                        ArrayList<String> commitPatternList,
                                        boolean continueOnError) {
        logger.debug("==> Method: YamlDocumentEntryPoint.commitAndPushTemplates");

        commitAndPushAllCode (repositoryList, azdoUser, azdoPat, commitPatternList, continueOnError);
    }

    /******************************************************************************************
     Commit and push the repository with the manipulated template files to the Azure DevOps test
     project; second method signature. The repositoryList (derived from the resources section)
     is added as argument.
     @param repositoryResourceList List of repository resources derived from the resources section.
     @param azdoUser User used in the Azure DevOps API calls, defined in the target AzDo project.
     @param azdoPat Personal Access Token used in the Azure DevOps API calls, defined in the
                    target AzDo project.
     @param commitPatternList List of file types and directories included in a commit.
     @param continueOnError If an error situation occurs, it is logged as an error (not always)
                            and execution continues if the value is 'true'.
     ******************************************************************************************/
    private void commitAndPushAllCode (ArrayList<RepositoryResource> repositoryResourceList,
                                       String azdoUser,
                                       String azdoPat,
                                       ArrayList<String> commitPatternList,
                                       boolean continueOnError) {
        logger.debug("==> Method: YamlDocumentEntryPoint.commitAndPushAllCode (second method signature)");

        // Return if there is nothing to push
        if (repositoryResourceList == null)
            return;

        repositoryResourceList.forEach(repository -> {
            commitAndPushAllCode (repository, azdoUser, azdoPat, commitPatternList, continueOnError);
        });
    }

    private void commitAndPushAllCode (RepositoryResource repository,
                                       String azdoUser,
                                       String azdoPat,
                                       ArrayList<String> commitPatternList,
                                       boolean continueOnError) {
        logger.debug("==> Method: YamlDocumentEntryPoint.commitAndPushAllCode (first method signature)");

        Git git = GitUtils.createGit (repository.localBase + "/" + repository.name);
        if (git != null) {
            logger.debug("Commit and Push repository {}", repository.name);
            GitUtils.commitAndPush(git,
                    azdoUser,
                    azdoPat,
                    commitPatternList,
                    repository,
                    continueOnError);
        }
    }

    /******************************************************************************************
     Copy all files in the external repositories (containing template files) from the source
     location to the target location.
     The source location has the same name as the repository,but with a "-source" prefix.
     @param excludeList Determines which files and directories must not be copied to the target
                        location.
     ******************************************************************************************/
    public void copyAllSourceFiles (String excludeList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.copyAllSourceFiles (first method signature)");

        // Return if there is nothing to copy
        if (repositoryList == null)
            return;

        repositoryList.forEach(repository -> {
            copyAllSourceFiles (repository, excludeList);
        });
    }

    /******************************************************************************************
     Copy all files in the external repositories (containing template files) from the source
     location to the target location.
     The source location has the same name as the repository,but with a "-source" prefix.
     @param repository The source repository of which the files must be copied to the target
                       location.
     @param excludeList Determines which files and directories must not be copied to the target
                        location.
     ******************************************************************************************/
    private void copyAllSourceFiles (RepositoryResource repository, String excludeList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.copyAllSourceFiles (second method signature)");

        String source = repository.localBase + "/" + repository.name + RepositoryResource.LOCAL_SOURCE_POSTFIX;
        String target = repository.localBase + "/" + repository.name;
        logger.debug("source: {}", source);
        logger.debug("target: {}", target);

        Utils.copyAll(source, target, excludeList);
    }

    /******************************************************************************************
     Clone the external repositories from their original remotes and copy them to a safe
     location on the filesystem. This - source - location forms is used to re-read the template
     files again. The relation with the original remote is gone (unmounted), by removing the
     .git directory.
     @param repository The source repository to be cloned.
     @param azdoUser User used in the Azure DevOps API calls, defined in the target AzDo project.
     @param azdoPat Personal Access Token used in the Azure DevOps API calls, defined in the
                    target AzDo project.
     @param organization Organization of the target AzDo project as defined in property file.
                         For example "myorg".
     @param deleteGitDirectory Default value (true).
     ******************************************************************************************/
    private void cloneAndRenameExternalRepositories (RepositoryResource repository,
                                                     String azdoUser,
                                                     String azdoPat,
                                                     String organization,
                                                     boolean deleteGitDirectory) {
        logger.debug("==> Method: YamlDocumentEntryPoint.cloneAndRenameExternalRepositories");

            // Copy (clone) the external repositories to the local filesystem
            String source = repository.localBase + "/" +  repository.name + RepositoryResource.LOCAL_SOURCE_POSTFIX;
            String temp = repository.localBase + "/" + repository.name;
            Git git = null;

            if (repository.type != null) {
                if ("git".equals(repository.type)) {
                    git = GitUtils.cloneAzdoToLocal(temp,
                            repository.name,
                            azdoUser,
                            azdoPat,
                            organization,
                            repository.project);
                } else if ("github".equals(repository.type)) {
                    git = GitUtils.cloneGitHubToLocal(temp,
                            repository.name,
                            repository.project);
                }

                // Checkout the remote branch
                if (repository.ref != null) {
                    String branchName = GitUtils.resolveBranchNameFromRef (repository.ref);
                    git = GitUtils.checkout(git,
                            temp,
                            "origin/" + branchName,
                            false);
                }

                // Git must be closed, otherwise the .git directory cannot be deleted
                if (git != null) {
                    git.close();
                    Utils.wait(1000);
                }
            }

            // Keep the cloned repository. It acts as a local source
            Utils.copyAll(temp, source, "");

            // If the repo is cloned and copied, the .git directory should be deleted.
            if (deleteGitDirectory)
                Utils.deleteDirectory(source + "/.git");

            // Delete the original local repository, because it is re-used by the createRemoteRepositories method.
            Utils.deleteDirectory(temp);
    }

    // Get the repositories in the resources section from the main .yml file

    /******************************************************************************************
     Get the repositories in the resources section from the main .yml file.
     @param map yaml map of the parsed YAML file.
     @param basePathExternal Root directory on the file system where the repositories are
                             stored. If a source (external) repo is called "myRepo" and
                             located on C:\temp\myRepo, the basePathExternal has value
                             C:\temp
     @param sourceProject Project name that contains the source repository.
     ******************************************************************************************/
    private ArrayList<RepositoryResource> getRepositoriesFromResources (Map<String, Object> map,
                                                                        String basePathExternal,
                                                                        String sourceProject) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (first method signature)");
        logger.debug("basePathExternal: {}", basePathExternal);
        logger.debug("sourceProject: {}", sourceProject);

        if (map == null) {
            logger.warn("map is null");
            return null;
        }

        ArrayList<RepositoryResource> repositoryResourceList = new ArrayList<>();
        getRepositoriesFromResources (map, repositoryResourceList, basePathExternal, sourceProject);
        return repositoryResourceList;
    }

    // Get a list of repositories
    private void getRepositoriesFromResources (Map<String, Object> map,
                                               ArrayList<RepositoryResource> repositoryResourceList,
                                               String basePathExternal,
                                               String sourceProject) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (second method signature)");
        logger.debug("basePathExternal: {}", basePathExternal);

        RepositoryResource repositoryResource = new RepositoryResource();

        // Run through the YAML file and add the resources to the list
        boolean found = false;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            logger.debug("Key: {}", entry.getKey());
            logger.debug("Value: {}", entry.getValue());

            if ("repository".equals(entry.getKey())) {
                logger.debug("Found a repository");
                repositoryResource.repository = entry.getValue().toString();
                found = true;
            } else {
                // Go a level deeper
                if (entry.getValue() instanceof Map) {
                    getRepositoriesFromResources((Map<String, Object>) entry.getValue(), repositoryResourceList, basePathExternal, sourceProject);
                }
                if (entry.getValue() instanceof ArrayList) {
                    getRepositoriesFromResources((ArrayList<Object>) entry.getValue(), repositoryResourceList, basePathExternal, sourceProject);
                }
            }

            if (found) {
                if ("type".equals(entry.getKey())) {
                   /*
                       'type' can have value:
                           "git" (= Azure DevOps)
                           "github" (= GitHub)
                           "githubenterprise" (= GitHubEnterprise)
                           "bitbucket" (= Bitbucket Cloud)
                    */
                    repositoryResource.type = entry.getValue().toString();
                }
                if ("name".equals(entry.getKey())) {
                    String name = entry.getValue().toString();
                    String project = "";
                    if (name.contains("/")) {
                        String[] parts = name.split("/");
                        project = parts[0];
                        name = parts[1];
                    }
                    if (project.isEmpty()) {
                        // If the project is empty, it was not included in the name
                        // In that case, assume the project of the main repository
                        project = sourceProject;
                    }

                    repositoryResource.name = name;
                    repositoryResource.project = project;
                    repositoryResource.localBase = basePathExternal;
                }
                if ("trigger".equals(entry.getKey())) {
                    repositoryResource.trigger = entry.getValue().toString();
                }
                if ("endpoint".equals(entry.getKey())) {
                    repositoryResource.endpoint = entry.getValue().toString();
                }
                if ("ref".equals(entry.getKey())) {
                    repositoryResource.ref = entry.getValue().toString();
                }
            }
        }

        if (repositoryResource.repository != null) {
            logger.debug("Add repositoryResource to list; repository: {}, name: {}, type: {}",
                    repositoryResource.repository,
                    repositoryResource.name,
                    repositoryResource.type);
            repositoryResourceList.add(repositoryResource);
        }
    }

    private void getRepositoriesFromResources(ArrayList<Object> inner,
                                              ArrayList<RepositoryResource> repositoryResourceList,
                                              String basePathExternal,
                                              String sourceProject) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (third method signature)");
        logger.debug("basePathExternal: {}", basePathExternal);

        if (inner == null) {
            logger.warn("inner is null");
            return;
        }

        inner.forEach(entry -> {
            if (entry == null) {
                logger.warn("entry is null");
                return;
            }

            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                getRepositoriesFromResources((Map<String, Object>)entry, repositoryResourceList, basePathExternal, sourceProject);
            }
            if (entry instanceof ArrayList) {
                getRepositoriesFromResources((ArrayList<Object>)entry, repositoryResourceList, basePathExternal, sourceProject);
            }
        });
    }

    /******************************************************************************************
     The manipulated yaml maps are saved onto the local file system. The location is a target
     location.
     ******************************************************************************************/
    public void dumpYaml () throws IOException {
        logger.debug("==> Method: YamlDocumentEntryPoint.dumpYaml");

        // Dump the updated YAML files to the target directory (with the same name as the original file in the source directory)
        logger.info("Write output files");
        mainYamlDocument.dumpYaml();
    }

    /******************************************************************************************
     The manipulated yaml maps are validated.
     @param validVariableGroups List of all Variable Group of the Azure DevOps project,
                                retrieved by an API.
     @param validEnvironments List of all Environments of the Azure DevOps project, retrieved
                              by an API.
     @param project Target Azure DevOps project.
     @param continueOnError If an error situation occurs, it is logged as an error (not always)
                            and execution continues if the value is 'true'.
     ******************************************************************************************/
    public void validateTargetOutputFilesAndTemplates (ArrayList<String> validVariableGroups,
                                                       ArrayList<String> validEnvironments,
                                                       String project,
                                                       boolean continueOnError) {
        logger.debug("==> Method: YamlDocumentEntryPoint.validateTargetOutputFilesAndTemplates");

        logger.info("Validate output files");
        mainYamlDocument.validateTargetOutputFilesAndTemplates(validVariableGroups, validEnvironments, project, continueOnError);
    }

    /******************************************************************************************
     Forward the action to the main yaml document. The main yaml document delegates it again
     to all underlying templates.
     @param action Specialized Action object, for example 'ActionDeleteSection'.
     @param sectionType Name of the section in which the action is executed. For example, "stage".
     @param sectionIdentifier Identifiation of the section.
     ******************************************************************************************/
    public ActionResult performAction (Action action,
                                       String sectionType,
                                       String sectionIdentifier) {
        logger.debug("==> Method: YamlDocumentEntryPoint.performAction");
        logger.debug("action: {}", action.getClass().getName());
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        return mainYamlDocument.performAction (action, sectionType, sectionIdentifier);
    }

    /******************************************************************************************
     Replaces a string (identified by 'literalToReplace') in a YAML file with another string
     (identified by 'newValue').
     @param literalToReplace Value of the literal in the YAML document that needs to be replaced.
     @param newValue New value.
     @param replaceAll Replaces all occurences of 'literalToReplace' with 'newValue'.
     ******************************************************************************************/
    public void overrideLiteral (String literalToReplace, String newValue, boolean replaceAll) {
        logger.debug("==> Method: YamlDocumentEntryPoint.overrideLiteral");
        logger.debug("literalToReplace: {}", literalToReplace);
        logger.debug("newValue: {}", newValue);
        logger.debug("continueSearching: {}", replaceAll);

        mainYamlDocument.overrideLiteral(literalToReplace, newValue, replaceAll);
    }

    public void makeResourcesLocal () {
        mainYamlDocument.makeResourcesLocal();
    }
}




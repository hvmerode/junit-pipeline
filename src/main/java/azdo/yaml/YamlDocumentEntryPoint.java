// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

import azdo.utils.PropertyUtils;
import azdo.utils.AzDoUtils;
import azdo.utils.GitUtils;
import azdo.utils.Utils;
import org.eclipse.jgit.api.Git;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

/*
   A YamlDocumentEntryPoint is the entry point of the main pipeline YAML file.
   YamlDocumentEntryPoint forms the point of contact between the pipeline - as represented by the AzDoPipeline class -
   and the YamlDocument class that represents the main YAML pipeline file.
*/
public class YamlDocumentEntryPoint {
    private static Logger logger = LoggerFactory.getLogger(YamlDocumentEntryPoint.class);

    // Refers to the main pipeline file, which is the entrypoint of the pipeline.
    private YamlDocument mainYamlDocument;

    // The location of the main repository - containing the main pipeline file - on the local filesystem.
    // This location contains the manipulated pipeline and template files (from the same main repository).
    private String targetPath = "";

    // the sourceBasePathExternal and targetBasePathExternal refer to the local filesystem, containing the
    // source files of external repositories (defined in the resources section of the main pipeline file)
    // and the target files (to which the manipulated template files are written).
    private String sourceBasePathExternal = "";
    private String targetBasePathExternal = "";

    // List of repositories, defined in the resources section in the main pipeline file.
    ArrayList<RepositoryResource> repositoryList = null;

    // Constructor
    public YamlDocumentEntryPoint (String targetPath,
                                   String sourceBasePathExternal,
                                   String targetBasePathExternal) {
        this.targetPath = targetPath;
        this.sourceBasePathExternal = sourceBasePathExternal;
        this.targetBasePathExternal = targetBasePathExternal;
    }

    /*
       Parses the yaml map of the main pipeline file and initializes the resources (external repositories)
     */
    @SuppressWarnings("java:S1192")
    public void initExternalResources (Map<String, Object> yamlMap, PropertyUtils properties) {
        logger.debug("==> Method: YamlDocumentEntryPoint.initExternalResources");

        if (yamlMap == null) {
            logger.debug("The yamlMap parameter is null; return");
            return;
        }

        // Get all repositories containing external templates from the resources section
        repositoryList = getRepositoriesFromResources(yamlMap, properties.getTargetBasePathExternal());

        // Run trough all repositories and determine whether they need to be cloned and pushed to the Azure DevOps test project
        repositoryList.forEach(repository -> {
            String source = repository.localBase + "/" +  repository.name + RepositoryResource.LOCAL_SOURCE_POSTFIX;
            if (Utils.pathIsEmptyOrNotExisting(source)) {

                // Clone the repository containing external templates from the remote (source) repository to
                // the local file system, and remove the .git directory. The .git directory must be removed to
                // 'unmount' from the original repository; it is later pushed to the Azure DevOps test repository.
                // The directory must be copied to prevent it is overwritten by the createRemoteRepositories command.
                // TODO: The cloneAndRenameExternalRepositories method does not take the ref into account; this needs to be fixed later
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

                // Copy the files of the source (local copy of external repository files) to the local target
                copyAllSourceFiles(repository, properties.getTargetExludeList());

                // Checkout/push the local repository containing external templates to the Azure DevOps test project
                commitAndPushAllCode(repository, properties.getAzDoUser(), properties.getAzdoPat(), properties.getCommitPatternList());
            }
        });

        // Read all templates
        mainYamlDocument.readTemplates(
                targetPath,
                sourceBasePathExternal,
                targetBasePathExternal,
                repositoryList);
    }

    /*
       Reads the original main pipeline file from the local file system and creates a main YAML map object.
       This map is kept in memory. In addition, it creates YAML maps from template files.
     */
    @SuppressWarnings("java:S1192")
    public Map<String, Object> read (String mainPipelineFile) {
        logger.debug("==> Method: YamlDocumentEntryPoint.read");
        logger.debug("mainPipelineFile: {}", mainPipelineFile);

        // First read the main YAML file
        Path mainPipelinePath = Paths.get(mainPipelineFile);
        mainPipelinePath = mainPipelinePath.normalize();
        mainPipelineFile = mainPipelinePath.toString();
        mainYamlDocument = new YamlDocument(mainPipelineFile, targetPath);
        Map<String, Object> yamlMap = mainYamlDocument.readYaml();

        return yamlMap;
    }

    // Create remote - external - repositories in the Azure DevOps test project
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
        Git git = GitUtils.createGit (path);
        boolean isRemote = GitUtils.containsBranch(git, GitUtils.BRANCH_MASTER);
        GitUtils.checkout(git, path, GitUtils.BRANCH_MASTER, !isRemote);
    }

    // Commit and push the repository with the manipulated template files to the Azure DevOps test project.
    public void commitAndPushTemplates (String azdoUser, String azdoPat, ArrayList<String> commitPatternList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.commitAndPushTemplates");

        commitAndPushAllCode (repositoryList, azdoUser, azdoPat, commitPatternList);
    }

    private void commitAndPushAllCode (RepositoryResource repository,
                                       String azdoUser,
                                       String azdoPat,
                                       ArrayList<String> commitPatternList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.commitAndPushAllCode (first method signature)");

        Git git = GitUtils.createGit (repository.localBase + "/" + repository.name);
        if (git != null) {
            logger.debug("Commit and Push repository {}", repository.name);
            GitUtils.commitAndPush(git,
                    azdoUser,
                    azdoPat,
                    commitPatternList);
        }
    }

    private void commitAndPushAllCode (ArrayList<RepositoryResource> repositoryResourceList,
                                       String azdoUser,
                                       String azdoPat,
                                       ArrayList<String> commitPatternList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.commitAndPushAllCode (second method signature)");

        repositoryResourceList.forEach(repository -> {
            commitAndPushAllCode (repository, azdoUser, azdoPat, commitPatternList);
        });
    }

    // Copy all files in the external repositories (containing template files) from the source location to the target location.
    // The source location has the same name as the repository,but with a "-source" prefix.
    private void copyAllSourceFiles (RepositoryResource repository, String excludeList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.copyAllSourceFiles");

        String source = repository.localBase + "/" + repository.name + RepositoryResource.LOCAL_SOURCE_POSTFIX;
        String target = repository.localBase + "/" + repository.name;
        logger.debug("source: {}", source);
        logger.debug("target: {}", target);

        Utils.copyAll(source, target, excludeList);
    }

    // Clone the external repositories from their original remotes and copy them to a safe location on the filesystem.
    // this - source - location forms is used to re-read the template files again. The relation with the original
    // remote is gone (unmounted), by removing the .git directory
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
    private ArrayList<RepositoryResource> getRepositoriesFromResources(Map<String, Object> map, String basePathExternal) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (first method signature)");
        logger.debug("basePathExternal: {}", basePathExternal);

        ArrayList<RepositoryResource> repositoryResourceList = new ArrayList<>();
        getRepositoriesFromResources (map, repositoryResourceList, basePathExternal);
        return repositoryResourceList;
    }

    // Get a list of repositories
    private void getRepositoriesFromResources (Map<String, Object> map,
                                               ArrayList<RepositoryResource> repositoryResourceList,
                                               String basePathExternal) {
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
                    getRepositoriesFromResources((Map<String, Object>) entry.getValue(), repositoryResourceList, basePathExternal);
                }
                if (entry.getValue() instanceof ArrayList) {
                    getRepositoriesFromResources((ArrayList<Object>) entry.getValue(), repositoryResourceList, basePathExternal);
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
                    String name  = entry.getValue().toString();
                    String[] parts = name.split("/");
                    String project = parts[0];
                    name = parts[1];

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
                                              String basePathExternal) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (third method signature)");
        logger.debug("basePathExternal: {}", basePathExternal);

        inner.forEach(entry -> {
            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                getRepositoriesFromResources((Map<String, Object>)entry, repositoryResourceList, basePathExternal);
            }
            if (entry instanceof ArrayList) {
                getRepositoriesFromResources((ArrayList<Object>)entry, repositoryResourceList, basePathExternal);
            }
        });
    }

    /*
       The manipulated yaml maps are saved onto the local file system. The location is a target location.
     */
    public void dumpYaml() throws IOException {
        logger.debug("==> Method: YamlDocumentEntryPoint.dumpYaml");

        // Dump the updated YAML files to the target directory (with the same name as the original file in the source directory)
        mainYamlDocument.dumpYaml();
    }

    public void executeCommand (ActionEnum actionEnum,
                                String sectionName,
                                String sectionValue,
                                String identifierName,
                                String identifierValue,
                                String keyName,
                                String keyValue,
                                boolean continueSearching) {
        logger.debug("==> Method: YamlDocumentEntryPoint.executeCommand");
        logger.debug("actionEnum: {}", actionEnum);
        logger.debug("sectionName: {}", sectionName);
        logger.debug("sectionValue: {}", sectionValue);
        logger.debug("identifierName: {}", identifierName);
        logger.debug("identifierValue: {}", identifierValue);
        logger.debug("keyName: {}", keyName);
        logger.debug("keyValue: {}", keyValue);
        logger.debug("continueSearching: {}", continueSearching);

        mainYamlDocument.executeCommand(actionEnum,
                sectionName,
                sectionValue,
                identifierName,
                identifierValue,
                keyName,
                keyValue,
                continueSearching);
    }

    public void makeResourcesLocal () {
        mainYamlDocument.makeResourcesLocal();
    }
}




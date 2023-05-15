// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

import azdo.utils.GitUtils;
import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Map;

/*
    A YamlDocumentEntryPoint is the entry point of the main pipeline file.
 */
public class YamlDocumentEntryPoint {
    private static Logger logger = LoggerFactory.getLogger(YamlDocumentEntryPoint.class);

    // Refers to the main pipeline file, which is the entrypoint of the pipeline
    private YamlDocument mainYamlDocument;

    /*
       Reads the original main pipeline file from the local file system and creates a main YAML map object.
       This map is kept into memory. In addition, it creates YAML maps from template files.
     */
    @SuppressWarnings("java:S1192")
    public void read(String mainPipelineFile,
                     String targetBasePathExternal,
                     String azdoUser,
                     String azdoPat,
                     String organization,
                     String project) {
        logger.debug("");
        logger.debug("*****************************************************************");
        logger.debug("Start YamlDocumentEntryPoint.read");
        logger.debug("*****************************************************************");

        // First read the main YAML file
        logger.debug("Read the main YAML file " + mainPipelineFile);
        Path mainPipelinePath = Paths.get(mainPipelineFile);
        mainPipelinePath = mainPipelinePath.normalize();
        mainPipelineFile = mainPipelinePath.toString();
        mainYamlDocument = new YamlDocument(mainPipelineFile);
        Map<String, Object> yamlMap = mainYamlDocument.readYaml();

        // Get all repositories from the resources section
        ArrayList<RepositoryResource> repositoryList = getRepositoriesFromResources(yamlMap);

        // Clone the repositories from the remote (source) repository to local and remove the .git directory
        // The .git directory must be removed to 'unmount' from the original repository; it is later pushed to
        // the test repository.
        cloneRepositories(repositoryList,
                targetBasePathExternal,
                azdoUser,
                azdoPat,
                organization,
                true);


        // Change the resources/repositories section in the main pipeline in such a way,
        // that is points to the correct repository in the Azure DevOps test project. This may mean
        // that the type changes from 'github' to 'git'
        // TODO

        // Read the templates
        // TODO: For external templates the location from where they are read must be derived
        mainYamlDocument.readTemplates();

        // Checkout/push to Azure DevOps
        // TODO

        logger.debug("*****************************************************************");
        logger.debug("End YamlDocumentEntryPoint.read");
        logger.debug("*****************************************************************");
        logger.debug("");
    }

    private void cloneRepositories (ArrayList<RepositoryResource> repositoryResourceList,
                                    String targetBasePathExternal,
                                    String azdoUser,
                                    String azdoPat,
                                    String organization,
                                    boolean deleteGitDirectory) {
        logger.debug("==> Method: YamlDocumentEntryPoint.cloneRepositories");

        repositoryResourceList.forEach(repository -> {
            // Copy (clone) the external repositories to the local filesystem
            String path = "";

            if (repository.type != null) {
                path = targetBasePathExternal + "/" + repository.name;
                if ("git".equals(repository.type)) {
                    GitUtils.cloneAzdoToLocal(path,
                            repository.name,
                            azdoUser,
                            azdoPat,
                            organization,
                            repository.project);
                } else if ("github".equals(repository.type)) {
                    GitUtils.cloneGitHubToLocal(path,
                            repository.name,
                            repository.project);
                }
            }

            /*
               If the repo is cloned, the .git directory should be deleted
               Reason is that it is cloned from another repository, but it must be pushed to the target Azure DevOps project
               The cloned repository still references to the original repository
             */
            if (deleteGitDirectory)
                Utils.deleteDirectory(path + "/.git");
        });
    }

    // Get the repositories in the resources section from the main .yml file
    private ArrayList<RepositoryResource> getRepositoriesFromResources(Map<String, Object> map) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (first method signature)");

        ArrayList<RepositoryResource> repositoryResourceList = new ArrayList<>();
        repositoryResourceList = getRepositoriesFromResources (map, repositoryResourceList);
        return repositoryResourceList;
    }

    // Get a list of repositories
    private ArrayList<RepositoryResource> getRepositoriesFromResources (Map<String, Object> map,
                                                                       ArrayList<RepositoryResource> repositoryResourceList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (second method signature)");

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
                    getRepositoriesFromResources((Map<String, Object>) entry.getValue(), repositoryResourceList);
                }
                if (entry.getValue() instanceof ArrayList) {
                    getRepositoriesFromResources((ArrayList<Object>) entry.getValue(), repositoryResourceList);
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
        return repositoryResourceList;
    }

    private void getRepositoriesFromResources(ArrayList<Object> inner, ArrayList<RepositoryResource> repositoryResourceList) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources (third method signature)");

        inner.forEach(entry -> {
            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                getRepositoriesFromResources((Map<String, Object>)entry, repositoryResourceList);
            }
            if (entry instanceof ArrayList) {
                getRepositoriesFromResources((ArrayList<Object>)entry, repositoryResourceList);
            }
        });
    }

    /*
       The manipulated yaml maps are saved onto the local file system. The location is a target location,
       other than the original location of the pipeline file.
     */
    public void dumpYaml(String targetPath) throws IOException {
        logger.debug("==> Method: YamlDocumentEntryPoint.dumpYaml");

        // Dump the updated YAML files to the target directory (with the same name as the original file in the source directory)
        mainYamlDocument.dumpYaml(targetPath);
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
        mainYamlDocument.executeCommand(actionEnum,
                sectionName,
                sectionValue,
                identifierName,
                identifierValue,
                keyName,
                keyValue,
                continueSearching);
    }
}

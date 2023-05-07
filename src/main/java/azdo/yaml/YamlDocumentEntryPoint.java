// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

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
    public void read(String mainPipelineFile) {
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
        // TODO: Other repos must also be cloned to the local filesystem
        getRepositoriesFromResources(yamlMap);

        // Read the templates
        // TODO: For external templates the location from where they are read must be derived
        mainYamlDocument.readTemplates();

        logger.debug("*****************************************************************");
        logger.debug("End YamlDocumentEntryPoint.read");
        logger.debug("*****************************************************************");
        logger.debug("");
    }

    // Get the repositories in the resources section from the main .yml file
    // These repositories are also cloned to the Azure DevOps project (if required), because they
    // may contain templates that are used by the main pipeline, and also need to be manipulated
    // TODO:
    // - Extend the read method with a flag that skips the call to 'getRepositoriesFromResources'
    // - getRepositoriesFromResources should return a list of repos, which are cloned later
    //   This list is empty if the flag passed to the read method is false (= default)
    // - Clone all repositories from this list (if not empty) to local
    // - Push all code of each cloned repository to the Azure DevOps project
    // - Change the resources/repositories section in the manipulated main pipeline in such a way,
    //   that is points to the correct repository in the Azure DevOps test project. This may mean
    //   that the type changes from 'github' to 'git'
    private void getRepositoriesFromResources(Map<String, Object> map) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources");

        // Run through the YAML file and add the template files to the list
        boolean found = false;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            logger.debug("Key: {}", entry.getKey());
            logger.debug("Value: {}", entry.getValue());

            // Add all template files to the list
            if ("repository".equals(entry.getKey())) {
                logger.debug("Found a repository");
                found = true;
            }
            else {
                // Go a level deeper
                if (entry.getValue() instanceof Map) {
                    getRepositoriesFromResources((Map<String, Object>) entry.getValue());
                }
                if (entry.getValue() instanceof ArrayList) {
                    getRepositoriesFromResources((ArrayList<Object>) entry.getValue());
                }
            }
            if (found) {
                /*
                    'type' can have value:
                      "git" (= Azure DevOps)
                      "github" (= GitHub)
                      "githubenterprise" (= GitHubEnterprise)
                      "bitbucket" (= Bitbucket Cloud)
                 */
                if ("type".equals(entry.getKey()) && "github".equals(entry.getValue()))
                    logger.debug("Repository is of type Github");
                if ("type".equals(entry.getKey()) && "git".equals(entry.getValue()))
                    logger.debug("Repository is of type Azure DevOps");
                if ("name".equals(entry.getKey()))
                    logger.debug("Clone repository <{}> to the Azure DevOps test project", entry.getValue());
            }
        }
    }

    private void getRepositoriesFromResources(ArrayList<Object> inner) {
        logger.debug("==> Method: YamlDocumentEntryPoint.getRepositoriesFromResources");

        inner.forEach(entry -> {
            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                getRepositoriesFromResources((Map<String, Object>)entry);
            }
            if (entry instanceof ArrayList) {
                getRepositoriesFromResources((ArrayList<Object>)entry);
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

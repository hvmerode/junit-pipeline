package azdo.yaml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

/*
    A YamlDocumentSet contains a group of YAML files and consists of the main YAML file and all templates used
    by the main YAML file.
    Each command operates on the whole set of files.
 */
public class YamlDocumentSet {
    private static Logger logger = LoggerFactory.getLogger(YamlDocumentSet.class);
    private ArrayList<String> templateList;

    // Contains a map with filename + yamlDocument
    private Map<String, YamlDocument> yamlDocuments = new LinkedHashMap<>();

    /*
       Reads the original main pipeline file from the local file system and creates a main YAML map object.
       This map is kept into memory.
       In addition, it creates template YAML maps from template files, used in the main YAML file. The
       template maps are also kept into memory.
       Each action/command operates both on the main YAML file and the template files
     */
    public void read(String mainPipelineFile) {
        logger.info("");
        logger.info("*****************************************************************");
        logger.info("Start YamlDocumentSet.read");
        logger.info("*****************************************************************");

        // First read the main YAML file
        logger.info("==> Read the main YAML file");
        Path mainPipelinePath = Paths.get(mainPipelineFile);
        mainPipelinePath = mainPipelinePath.normalize();
        mainPipelineFile = mainPipelinePath.toString();
        YamlDocument yamlDocument = new YamlDocument();
        Map<String, Object> yamlMap = yamlDocument.readYaml(mainPipelineFile);
        yamlDocuments.put(mainPipelineFile, yamlDocument);
        Path pathMain = Paths.get(mainPipelineFile);

        // Get the template files
        // Only the local templates are taken into account. External templates are excluded from unit tests
        templateList = new ArrayList<>();
        getTemplates(yamlMap);

        // Then read the template YAML files
        logger.info("==> Read the template YAML files");
        int index = 0;
        int size = templateList.size();
        String templateFile;
        Path templatePath;
        Path normalized;
        for (index = 0; index < size; index++) {
            templateFile = pathMain.getParent().toString() + "/" + templateList.get(index);
            templatePath = Paths.get(templateFile);
            templatePath = templatePath.normalize();
            templateFile = templatePath.toString();
            logger.info("Template file: " + templateFile);
            yamlDocument = new YamlDocument();
            yamlDocument.readYaml(templateFile);
            yamlDocuments.put(templateFile, yamlDocument);
        }
        logger.info("*****************************************************************");
        logger.info("End YamlDocumentSet.read");
        logger.info("*****************************************************************");
        logger.info("");
    }

    /*
       Create a list of all templates
     */
    private void getTemplates(Map<String, Object> inner) {
        // Run through the YAML file and add the template files to the list
        for (Map.Entry<String, Object> entry : inner.entrySet()) {

            // Add all template files to the list
            if ("template".equals(entry.getKey()))
                templateList.add((String)entry.getValue());

            // Go a level deeper
            if (entry.getValue() instanceof Map) {
                getTemplates((Map<String, Object>) entry.getValue());
            }
            if (entry.getValue() instanceof ArrayList) {
                getTemplates((ArrayList<Object>) entry.getValue());
            }
        }
    }
    private void getTemplates(ArrayList<Object> inner) {
        inner.forEach(entry -> {
            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                getTemplates((Map<String, Object>)entry);
            }
            if (entry instanceof ArrayList) {
                getTemplates((ArrayList<Object>)entry);
            }
        });
    }

    /*
       The manipulated yaml maps are saved onto the local file system. The location is a target location,
       other than the original location of the pipeline file.
     */
    public void dumpYaml(String targetPath) throws IOException {
        // Dump the updated YAML files to the target directory (with the same name as the original file in the source directory)
        YamlDocument yamlDocument;
        for (Map.Entry<String, YamlDocument> entry : yamlDocuments.entrySet()) {
            Path original = Paths.get(entry.getKey());
            logger.info("Original file: " + original);
            String target = targetPath + "/" + original.toString();
            logger.info("Dump from source file " + original + " to target file: " + target);

            // Dump each file
            yamlDocument = entry.getValue();
            yamlDocument.dumpYaml(target);
        }
    }

    public void executeCommand (ActionEnum actionEnum,
                                String sectionName,
                                String sectionValue,
                                String identifierName,
                                String identifierValue,
                                String keyName,
                                String keyValue,
                                boolean continueSearching) {
        logger.info("==> Method: YamlDocumentSet.read");
        YamlDocument yamlDocument;
        for (Map.Entry<String, YamlDocument> entry : yamlDocuments.entrySet()) {
            yamlDocument = entry.getValue();
            yamlDocument.executeCommand(actionEnum,
                    sectionName,
                    sectionValue,
                    identifierName,
                    identifierValue,
                    keyName,
                    keyValue,
                    continueSearching);
        }
    }
}

// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/*
    A YamlDocument represents one (1) YAML file.
 */
public class YamlDocument {
    private static Logger logger = LoggerFactory.getLogger(YamlDocument.class);
    private Map<String, Object> yamlMap;
    private ArrayList<Template> templateList; // Contains array with templates included in this YamlDocument
    private String originalFileName; // The filename of this YamlDocument, including the path in the repository

    // Default constructor
    public YamlDocument() {

    }

    public YamlDocument(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    /*
       Reads a pipeline file from the local file system and creates a yaml map object.
       This map is kept into memory and is manipulated by the methods of this class.
     */
    @SuppressWarnings("java:S1192")
    public Map<String, Object> readYaml() {
        logger.debug("");
        logger.debug("-----------------------------------------------------------------");
        logger.debug("Start YamlDocument.readYaml: " + originalFileName);
        logger.debug("-----------------------------------------------------------------");

        try {
            // Read the yaml file
            Yaml yaml = new Yaml();
            File file = new File(originalFileName);
            InputStream inputStream = new FileInputStream(file);
            yamlMap = yaml.load(inputStream);
            logger.debug("YamlMap: {}", yamlMap);
        } catch (Exception e) {
            logger.debug("Cannot find file {}", originalFileName);
        }

        logger.debug("-----------------------------------------------------------------");
        logger.debug("End YamlDocument.readYaml " + originalFileName);
        logger.debug("-----------------------------------------------------------------");
        logger.debug("");

        return yamlMap;
    }

    // Get the template files and read them
    void readTemplates(){
        logger.debug("==> Method: YamlDocument.readTemplates");

        templateList = new ArrayList<>(); // Use a new, empty list
        Path pathMain = Paths.get(originalFileName);

        // TODO: if this YamlDocument is an external template, the path is not the path of the main pipeline
        // It not even a path in the same directory as in which the main pipeline resided, but a different repository
        // If pathMain or pathMain.getParent() is null, return; the YamlDocument is an external template
        // The read location of external templates must be fixed later
        if (pathMain == null || pathMain.getParent() == null)
            return;

        logger.debug("Read the template YAML files");
        getTemplates(yamlMap, pathMain.getParent().toString() + "/");
        int index = 0;
        int size = templateList.size();
        Template template;
        for (index = 0; index < size; index++) {
            template = templateList.get(index);
            template.readYaml();
            template.readTemplates(); // Templates can contain other templates, so recursively  read them
        }
    }

    /*
       The manipulated yaml map is saved onto the local file system. The location is a target location,
       other than the original location of the pipeline file.
       Note, that originalFileName is the base path of the file in the directory. The targetPath is the location
       of the repository on the filesystem; appending both provides the exact location of the file on the filesystem
     */
    public void dumpYaml(String targetPath) throws IOException {
        logger.debug("==> Method: YamlDocument.dumpYaml");

        // Dump the updated yaml to target directory (with the same name as the original file in the source directory)
        String path = fixPath(targetPath + "/" + originalFileName);
        logger.debug("Dump the yamlMap of {} to {}", originalFileName, path);
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        final Yaml yaml = new Yaml(options);
        FileWriter writer = new FileWriter(targetPath + "/" + originalFileName);
        yaml.dump(yamlMap, writer);
        Utils.wait(1000);

        // Dump the templates
        logger.debug("Dump the yamlMap of the templates");
        int index = 0;
        int size = templateList.size();
        Template template;
        for (index = 0; index < size; index++) {
            template = templateList.get(index);
            template.dumpYaml(targetPath);
        }
    }

    /*
       Makes sure that a path resembles the correct path on the file system
       Fortunately, Windows also accepts forward slashes
     */
    private String fixPath (String path){
        path=path.replaceAll("\\\\","/");
        Path normalized = Paths.get(path);
        normalized = normalized.normalize();
        path = normalized.toString();
        return path;
    }

    /*
       If a specific section is found, the framework executes a actionEnum within that section.
     */
    public void executeCommand (ActionEnum actionEnum,
                                String sectionName,
                                String sectionValue,
                                String identifierName,
                                String identifierValue,
                                String keyName,
                                String keyValue,
                                boolean continueSearching) {

        logger.debug("==> Method: YamlDocument.executeCommand");

        if (actionEnum == ActionEnum.replaceLiteral) {
            logger.debug("Action replace literal <{}> with <{}>", keyName, keyValue);
            replaceLiteral(keyName, keyValue, continueSearching);
        }
        else {
            findSectionAndExecuteCommand(actionEnum,
                    yamlMap,
                    sectionName,
                    sectionValue,
                    identifierName,
                    identifierValue,
                    keyName,
                    keyValue,
                    false,
                    continueSearching);
        }

        // Execute the command in the template files
        executeCommandTemplates(actionEnum,
                sectionName,
                sectionValue,
                identifierName,
                identifierValue,
                keyName,
                keyValue,
                continueSearching);
    }

    /*
       If a specific section is found, the framework executes a actionEnum within that section.
     */
    public void executeCommandTemplates (ActionEnum actionEnum,
                                         String sectionName,
                                         String sectionValue,
                                         String identifierName,
                                         String identifierValue,
                                         String keyName,
                                         String keyValue,
                                         boolean continueSearching) {
        // Execute the command in the template files
        int index = 0;
        int size = templateList.size();
        Template template;
        for (index = 0; index < size; index++) {
            template = templateList.get(index);
            template.executeCommand(actionEnum,
                    sectionName,
                    sectionValue,
                    identifierName,
                    identifierValue,
                    keyName,
                    keyValue,
                    continueSearching);
        }
    }

    /*
       Parses the yaml document until a specific section is found. A section is either a type,
       such as variables, parameters, stages, or a type in combination with a section name, for
       example, stage: myStage
       If the section is found, it tries to execute a actionEnum, given the specific arguments.
       This method is performing recursion by calling itself to search for sections deeper in the
       yaml structure.
     */
    public void findSectionAndExecuteCommand(ActionEnum actionEnum,
                                             Map<String, Object> section,
                                             String sectionName,
                                             String sectionValue,
                                             String identifierName,
                                             String identifierValue,
                                             String keyName,
                                             String keyValue,
                                             boolean sectionFound,
                                             boolean continueSearching) {

        logger.debug("==> Method: YamlDocument.findSectionAndExecuteCommand: ");
        if (section == null)
            return;

        // Find the section
        Map<String, Object> inner = section;
        for (Map.Entry<String, Object> entry : inner.entrySet()) {
            logger.debug("Key = {}, Value = {}, Class = {}", entry.getKey(), entry.getValue(), entry.getValue().getClass());

            // Check whether the section is found. If true, the next actions are performed on this section
            if (sectionName.equals(entry.getKey())) {
                if (sectionValue == null || sectionValue.isEmpty()) {
                    logger.debug("Found section <{}>", sectionName);
                    sectionFound = true;
                } else {
                    if (sectionValue.equals(entry.getValue())) {
                        logger.debug("Found section <{}>", sectionValue);
                        sectionFound = true;
                    }
                }
            }

            // If section is found, try to execute the actionEnum
            if (sectionFound) {
                String s = "";
                if (! (sectionName == null || sectionName.isEmpty()))
                    s = sectionName;
                else if (! (sectionValue == null || sectionValue.isEmpty()))
                    s = sectionValue;
                logger.debug("Execute actionEnum in section <{}>", s);

                // Variable 'entry' contains the section segment
                switch (actionEnum) {
                    case replaceValue:
                        //logger.debug("Replace <" + keyName + "> with value <" + keyValue + ">");
                        if (identifierName.isEmpty() && identifierValue.isEmpty())
                            replaceValue(entry, keyName, keyValue);
                        else
                            replaceValue(entry, identifierName, identifierValue, keyName, keyValue);
                        return;
                    case delete:
                        logger.debug("Skip section <{}> with name <{}>", keyName, keyValue);
                        skipSection(entry, keyName, keyValue);
                        return;
                    case mock:
                        logger.debug("Mock section in <{}> with name <{}>", s, keyName);
                        mockSection(entry, s, keyName, keyValue);
                        return;
                }
            }

            // Go a level deeper
            if (entry.getValue() instanceof Map) {
                findSectionAndExecuteCommand(actionEnum, (Map<String, Object>) entry.getValue(),
                        sectionName,
                        sectionValue,
                        identifierName,
                        identifierValue,
                        keyName,
                        keyValue,
                        sectionFound,
                        continueSearching);
            }
            if (entry.getValue() instanceof ArrayList) {
                findInnerSectionAndExecuteCommand(actionEnum, (ArrayList<Object>) entry.getValue(),
                        sectionName,
                        sectionValue,
                        identifierName,
                        identifierValue,
                        keyName,
                        keyValue,
                        sectionFound,
                        continueSearching);
            }
        }
    }

    /*
       Parses the yaml document until a certain section is found. Similar to findSection, but it has an
       ArrayList instead of a Map as section argument.
     */
    public void findInnerSectionAndExecuteCommand(ActionEnum actionEnum,
                                                  ArrayList<Object> section,
                                                  String sectionName,
                                                  String sectionValue,
                                                  String identifierName,
                                                  String identifierValue,
                                                  String keyName,
                                                  String keyValue,
                                                  boolean sectionFound,
                                                  boolean continueSearching) {

        logger.debug("==> Method: YamlDocument.findInnerSectionAndExecuteCommand: ");
        if (section == null)
            return;

        section.forEach(entry -> {
            logger.debug("Section = {}, Class: = {}", entry, entry.getClass());

            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                findSectionAndExecuteCommand(actionEnum, (Map<String, Object>)entry,
                        sectionName,
                        sectionValue,
                        identifierName,
                        identifierValue,
                        keyName,
                        keyValue,
                        sectionFound,
                        continueSearching);
            }
            if (entry instanceof ArrayList) {
                findInnerSectionAndExecuteCommand(actionEnum, (ArrayList<Object>)entry,
                        sectionName,
                        sectionValue,
                        identifierName,
                        identifierValue,
                        keyName,
                        keyValue,
                        sectionFound,
                        continueSearching);
            }
        });
    }

    /*
       Replace a string, identified by keyName, with a string, identified by keyValue
     */
    public void replaceLiteral(String findLiteral, String replaceLiteral, boolean continueSearching) {
        logger.debug("==> Method: YamlDocument.replaceLiteral");
        Yaml yaml = new Yaml();
        String s = yaml.dump(yamlMap);
        if (continueSearching)
            s = s.replace(findLiteral, replaceLiteral);
        else
            s = s.replaceFirst(Pattern.quote(findLiteral), replaceLiteral);
        yamlMap = (Map) yaml.load(s);
    }

    /*
       Replace the value of a keyName/keyValue pair in the yaml.
     */
    private void replaceValue(Map.Entry<String, Object> section,
                              String keyName,
                              String keyValue) {

        logger.debug("==> Method: YamlDocument.replaceValue");
        logger.debug("keyName: {}", keyName);
        logger.debug("keyValue: {}", keyValue);

        // First check whether the  value of this section must be replaced
        if (keyName.equals(section.getKey())) {
            section.setValue(keyValue);
        }
        else {
            // Run trough the elements of the entry and replace the value of a keyName with keyValue
            if (section.getValue() instanceof ArrayList) {
                ArrayList<Object> list = (ArrayList<Object>) section.getValue();
                int index = 0;
                int size = list.size();
                for (index = 0; index < size; index++) {
                    logger.debug("Element = {}, Class = {}", list.get(index), list.get(index).getClass());

                    // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                    if (list.get(index) instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) list.get(index);
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            // Check the key/value pairs of which the value needs to be replaced
                            if (keyName.equals(entry.getKey())) {
                                logger.debug("Found <{}> with value <{}>", entry.getKey(), entry.getValue());
                                logger.debug("Replace with value <{}>", keyValue);
                                entry.setValue(keyValue);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }

    /*
        Replace a value in the yaml. The construct is:
        s:
        - n1: value_n1
          k1: value_n1_k1
          k2: value_n1_k2
          k3: value_n1_k3
        - n2_: value_n2
          k1: value_n2_k1
          k2: value_n2_k2

          Assume the following arguments:
            section is 's'
            identifierName is 'n1'
            identifierValue is value_n1
            name is 'k3'
            value is 'myReplacedValue'

          After execution of this method, the value 'value_n1_k3' of k3 of section 's' and sectionName/name pair 'n1/value_n1' is replaced with 'myReplacedValue'
     */
    private void replaceValue(Map.Entry<String, Object> section,
                              String identifierName,
                              String identifierValue,
                              String keyName,
                              String keyValue) {

        logger.debug("==> Method: YamlDocument.replaceValue");
        logger.debug("identifierName: {}", identifierName);
        logger.debug("identifierValue: {}", identifierValue);
        logger.debug("keyName: {}", keyName);
        logger.debug("keyValue: {}", keyValue);

        // Run trough the elements of the entry and replace the value of a keyName with keyValue
        boolean foundName = false;
        if (section.getValue() instanceof ArrayList) {
            ArrayList<Object> list = (ArrayList<Object>)section.getValue();
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                logger.debug("Element = {}, Class = {}", list.get(index), list.get(index).getClass());

                // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                if (list.get(index) instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>)list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        if (identifierName.equals(entry.getKey()) && identifierValue.equals(entry.getValue())) {
                            // We found the name, but iterate a bit more to find the name type
                            logger.debug("Found name: {}", identifierName);
                            foundName = true;
                        }
                        if (foundName) {
                            // Check the key/value pairs of which the value needs to be replaced
                            if (keyName.equals(entry.getKey())) {
                                logger.debug("Found <{}> with value <{}>", entry.getKey(), entry.getValue());
                                logger.debug("Replace with value <{}>", keyValue);
                                entry.setValue(keyValue);
                                return;
                            }
                        }
                    }
                }
            }
        }
    }


    /*
       Skip a section in the yaml. This can be a specific stage, a specific job, or a specific step.
       It does not disable the stage, job, or step but completely removes it from the modified pipeline.
     */
    private void skipSection (Map.Entry<String, Object> section, String keyName, String value) {

        logger.debug("==> Method: YamlDocument.skipSection");
        logger.debug("key: {}", keyName);
        logger.debug("value: {}", value);

        // Run trough the elements of the list and replace the section with key/value
        if (section.getValue() instanceof ArrayList) {
            ArrayList<Object> list = (ArrayList<Object>)section.getValue();
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                logger.debug("Element = {}, Class = {}", list.get(index), list.get(index).getClass());

                // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                if (list.get(index) instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>)list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        // Check whether the entry has the given key and value
                        // Delete the entry from the list if this is the case
                        if (keyName.equals(entry.getKey()) && value.equals(entry.getValue())) {
                            logger.debug("Skip: {}", value);
                            list.remove(index);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void mockSection(Map.Entry<String, Object> section, String sectionName, String id, String inlineScript){
        logger.debug("==> Method: YamlDocument.mockSection");
        logger.debug("s: {}", sectionName);
        logger.debug("id: {}", id);
        logger.debug("inlineScript: {}", inlineScript);

        String subType = "task";
        if ("stages".equals(sectionName))
            subType = "stage";
        else if ("jobs".equals(sectionName))
            subType = "job";

        // Run trough the elements of the list and mock the one with key/value
        if (section.getValue() instanceof ArrayList) {
            ArrayList<Object> list = (ArrayList<Object>)section.getValue();
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                logger.debug("Element = {}, Class = {}", list.get(index), list.get(index).getClass());

                // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                if (list.get(index) instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>)list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        // Check whether the entry has the given subtype (stage, job, task) and key (id of the subtype)
                        // Replace the entry from the list with the
                        if (subType.equals(entry.getKey()) && id.equals(entry.getValue())) {
                            logger.debug("Mock: <{}> with name <{}>", subType, id);
                            LinkedHashMap<String, String> mock = new LinkedHashMap<>();
                            mock.put ("script", inlineScript);
                            list.remove(index);
                            list.add(index, mock);
                            return;
                        }
                    }
                }
            }
        }
    }

    /*
       Create a list of all templates.
     */
    private void getTemplates(Map<String, Object> inner, String basePath) {
        logger.debug("==> Method: YamlDocument.getTemplates");

        // Inner could be null
        if (inner == null){
            logger.debug("inner is null; the reason is probably because the file could not be read, so return");
            return;
        }

        // Run through the YAML file and add the template files to the list
        for (Map.Entry<String, Object> entry : inner.entrySet()) {

            // Add all template files to the list
            if ("template".equals(entry.getKey())) {
                templateList.add(new Template((String) entry.getValue(), basePath));
            }

            // Go a level deeper
            if (entry.getValue() instanceof Map) {
                getTemplates((Map<String, Object>) entry.getValue(), basePath);
            }
            if (entry.getValue() instanceof ArrayList) {
                getTemplates((ArrayList<Object>) entry.getValue(), basePath);
            }
        }
    }
    private void getTemplates(ArrayList<Object> inner, String basePath) {
        logger.debug("==> Method: YamlDocument.getTemplates");

        // Inner could be null
        if (inner == null){
            logger.debug("inner is null; the reason is probably because the file could not be read, so return");
            return;
        }

        inner.forEach(entry -> {
            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                getTemplates((Map<String, Object>)entry, basePath);
            }
            if (entry instanceof ArrayList) {
                getTemplates((ArrayList<Object>)entry, basePath);
            }
        });
    }
}

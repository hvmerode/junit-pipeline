// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import java.io.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

public class YamlDocument {
    private static Logger logger = LoggerFactory.getLogger(YamlDocument.class);
    private Map<String, Object> yamlMap;
    public void YamlDocument() {
    }

    /*
       Reads the original pipeline file from the local file system and creates a yaml map object.
       This map is kept into memory and is manipulated by the methods of this class.
     */
    public Map<String, Object> readYaml(String pipelineFile) {
        logger.info("");
        logger.info("");
        logger.info("-----------------------------------------------------------------");
        logger.info("Start YamlDocument.readYaml of file " + pipelineFile);
        logger.info("-----------------------------------------------------------------");

        try {
            // Read the yaml file
            Yaml yaml = new Yaml();
            //        InputStream inputStream = this.getClass()
            //                .getClassLoader()
            //                .getResourceAsStream(pipelineFile);
            File file = new File(pipelineFile);
            InputStream inputStream = new FileInputStream(file);
            yamlMap = yaml.load(inputStream);
            logger.info("YamlMap " + yamlMap);
        } catch (Exception e) {
            logger.info("Cannot find file ", pipelineFile);
        }

        logger.info("-----------------------------------------------------------------");
        logger.info("End YamlDocument.readYaml of file " + pipelineFile);
        logger.info("-----------------------------------------------------------------");
        logger.info("");

        return yamlMap;
    }

    /*
       The manipulated yaml map is saved onto the local file system. The location is a target location,
       other than the original location of the pipeline file.
     */
    public void dumpYaml(String targetPipelineFile) throws IOException {
        logger.info("==> Method: YamlDocument.dumpYaml");

        // Dump the updated yaml to target directory (with the same name as the original file in the source directory)
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        final Yaml yaml = new Yaml(options);
        FileWriter writer = new FileWriter(targetPipelineFile);
        yaml.dump(yamlMap, writer);
        Utils.wait(3000);
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

        logger.info("==> Method: YamlDocument.executeCommand");

        if (actionEnum == ActionEnum.replaceLiteral) {
            logger.info("Replace literal <" + keyName + "> with <" + keyValue + ">");
            replaceLiteral(keyName, keyValue, continueSearching);
            return;
        }

        findSectionAndExecuteCommand (actionEnum,
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

        logger.info("==> Method: YamlDocument.findSectionAndExecuteCommand: ");

        // Find the section
        Map<String, Object> inner = section;
        for (Map.Entry<String, Object> entry : inner.entrySet()) {
            logger.info("        Key = " + entry.getKey() +
                    ", Value = " + entry.getValue() + ", Class: " + entry.getValue().getClass());

            // Check whether the section is found. If true, the next actions are performed on this section
            if (sectionName.equals(entry.getKey())) {
                if (sectionValue == null || sectionValue.isEmpty()) {
                    logger.info("Found section <" + sectionName + ">");
                    sectionFound = true;
                } else {
                    if (sectionValue.equals(entry.getValue())) {
                        logger.info("Found section <" + sectionValue + ">");
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
                logger.info("Execute actionEnum in section <" + s + ">");

                // Variable 'entry' contains the section segment
                switch (actionEnum) {
                    case replaceValue:
                        logger.info("Replace <" + keyName + "> with value <" + keyValue + ">");
                        if (identifierName.isEmpty() && identifierValue.isEmpty())
                            replaceValue(entry, keyName, keyValue);
                        else
                            replaceValue(entry, identifierName, identifierValue, keyName, keyValue);
                        return;
                    case delete:
                        logger.info("Skip section <" + keyName + "> with name <" + keyValue + ">");
                        skipSection(entry, keyName, keyValue);
                        return;
                    case mock:
                        logger.info("Mock section in <" + s + "> with name <" + keyName + ">");
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

        logger.info("==> Method: YamlDocument.findInnerSectionAndExecuteCommand: ");
        section.forEach(entry -> {
            logger.info("        Section = " + entry + ", Class: " + entry.getClass());

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
        logger.info("==> Method: YamlDocument.replaceLiteral");
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

        logger.info("==> Method: YamlDocument.replaceValue");
        logger.info("keyName: " + keyName);
        logger.info("keyValue: " + keyValue);

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
                    logger.info("        Element = " + list.get(index) + ", Class: " + list.get(index).getClass());

                    // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                    if (list.get(index) instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) list.get(index);
                        for (Map.Entry<String, Object> entry : map.entrySet()) {
                            // Check the key/value pairs of which the value needs to be replaced
                            if (keyName.equals(entry.getKey())) {
                                logger.info("Found " + entry.getKey() + " with value: " + entry.getValue());
                                logger.info("Replace with value " + keyValue);
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

        logger.info("==> Method: YamlDocument.replaceValue");
        logger.info("identifierName: " + identifierName);
        logger.info("identifierValue: " + identifierValue);
        logger.info("keyName: " + keyName);
        logger.info("keyValue: " + keyValue);

        // Run trough the elements of the entry and replace the value of a keyName with keyValue
        boolean foundName = false;
        if (section.getValue() instanceof ArrayList) {
            ArrayList<Object> list = (ArrayList<Object>)section.getValue();
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                logger.info("        Element = " + list.get(index) + ", Class: " + list.get(index).getClass());

                // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                if (list.get(index) instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>)list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        if (identifierName.equals(entry.getKey()) && identifierValue.equals(entry.getValue())) {
                            // We found the name, but iterate a bit more to find the name type
                            logger.info("Found name " + identifierName);
                            foundName = true;
                        }
                        if (foundName) {
                            // Check the key/value pairs of which the value needs to be replaced
                            if (keyName.equals(entry.getKey())) {
                                logger.info("Found " + entry.getKey() + " with value: " + entry.getValue());
                                logger.info("Replace with value " + keyValue);
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

        logger.info("==> Method: YamlDocument.skipSection");
        logger.info("key: " + keyName);
        logger.info("value: " + value);

        // Run trough the elements of the list and replace the section with key/value
        if (section.getValue() instanceof ArrayList) {
            ArrayList<Object> list = (ArrayList<Object>)section.getValue();
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                logger.info("        Element = " + list.get(index) + ", Class: " + list.get(index).getClass());

                // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                if (list.get(index) instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>)list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        // Check whether the entry has the given key and value
                        // Delete the entry from the list if this is the case
                        if (keyName.equals(entry.getKey()) && value.equals(entry.getValue())) {
                            logger.info("Skip: " + value);
                            list.remove(index);
                            return;
                        }
                    }
                }
            }
        }
    }

    private void mockSection(Map.Entry<String, Object> section, String sectionName, String id, String inlineScript){
        logger.info("==> Method: YamlDocument.mockSection");
        logger.info("s: " + sectionName);
        logger.info("id: " + id);
        logger.info("inlineScript: " + inlineScript);

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
                logger.info("        Element = " + list.get(index) + ", Class: " + list.get(index).getClass());

                // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
                if (list.get(index) instanceof Map) {
                    Map<String, Object> map = (Map<String, Object>)list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        // Check whether the entry has the given subtype (stage, job, task) and key (id of the subtype)
                        // Replace the entry from the list with the
                        if (subType.equals(entry.getKey()) && id.equals(entry.getValue())) {
                            logger.info("Mock: " + subType + " with name " + id);
                            LinkedHashMap<String, String> mock = new LinkedHashMap<>();
                            mock.put ("script", inlineScript);
                            list.remove(index);
                            list.add(index, mock);
                            //list.set(index, mock);
                            return;
                        }
                    }
                }
            }
        }
    }
}

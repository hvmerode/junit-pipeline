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

import static azdo.utils.Constants.*;

/*
    A YamlDocument represents one YAML file; this is a pipeline file or a template file.
    In the case of a template file, the specialized YamlTemplate class is used.
 */
public class YamlDocument {
    private static Logger logger = LoggerFactory.getLogger(YamlDocument.class);
    private Map<String, Object> yamlMap; // Map of the pipeline/template yaml file.
    private ArrayList<YamlTemplate> yamlTemplateList = new ArrayList<>(); // Contains an array with templates referred in the yaml file associated with this YamlDocument.
    protected String rootInputFile; // The main yaml document, including the root path within the repository
    protected String sourcePath; // The path of the repository that contains the original main yaml document.
    protected String targetPath; // The path of the repository that contains the main yaml document.
    protected String sourceInputFile; // The source yaml filename associated with this YamlDocument, including the path in the repository.
    protected String targetOutputFile; // The target filename used to dump the manipulated yaml.
    protected String sourceRepositoryName; // The source repositoryName
    protected String targetRepositoryName; // The target repositoryName

    // If this YamlDocument is a template, it may be associated with an alias; this is the name defined in the resources > repositories
    // section of the pipeline
    protected String repositoryAlias = "";

    // If this YamlDocument is type of YamlTemplate, the templateName is filled. Otherwise it is empty.
    protected String templateName = ""; // The template name as defined in the pipeline (without the @ postfix)

    // Default constructor
    public YamlDocument() {}

    // Constructor
    public YamlDocument(String rootInputFile,
                        String sourcePath,
                        String targetPath,
                        String sourceRepositoryName,
                        String targetRepositoryName) {
        logger.debug("==> Class YamlDocument");
        logger.debug("rootInputFile: {}", rootInputFile);
        logger.debug("sourcePath: {}", sourcePath);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("sourceRepositoryName: {}", sourceRepositoryName);
        logger.debug("targetRepositoryName: {}", targetRepositoryName);

        this.rootInputFile = rootInputFile;
        this.targetPath = targetPath;
        this.sourceRepositoryName = sourceRepositoryName;
        this.targetRepositoryName = targetRepositoryName;
        sourceInputFile = sourcePath + "/" + rootInputFile;
        sourceInputFile = Utils.fixPath(sourceInputFile);
        targetOutputFile = targetPath + "/" + rootInputFile;
        targetOutputFile = Utils.fixPath(targetOutputFile);
        logger.debug("targetOutputFile: {}", targetOutputFile);
    }

    /*
       Reads a pipeline file from the local file system and creates a yaml map object.
       This map is kept into memory and is manipulated by the methods of this class.
     */
    @SuppressWarnings("java:S1192")
    public Map<String, Object> readYaml(boolean continueOnError) {
        logger.debug("");
        logger.debug("-----------------------------------------------------------------");
        logger.debug("Start YamlDocument.readYaml: {}", sourceInputFile);
        logger.debug("-----------------------------------------------------------------");

        if (sourceInputFile == null) {
            // This may be a false-positive, so don't exit
            // This typically happens if a parameter is called 'template'
            logger.error(RED + "sourceInputFile is null; this may be a false-positive" + RESET_COLOR);
            if (this instanceof YamlTemplate)
                logger.error(RED + "This is a template with name: {}" + RESET_COLOR, templateName);
            logger.debug("-----------------------------------------------------------------");
            logger.debug("End YamlDocument.readYaml");
            logger.debug("-----------------------------------------------------------------");
            logger.debug("");

            return null;
        }

        try {
            // Read the yaml file
            Yaml yaml = new Yaml();
            File file = new File(sourceInputFile);
            InputStream inputStream = new FileInputStream(file);
            yamlMap = yaml.load(inputStream);
            logger.debug("YamlMap: {}", yamlMap);
        } catch (Exception e) {
            logger.error(RED + "Cannot find file {}" + RESET_COLOR, sourceInputFile);
            logger.debug(DEMARCATION);
            if (continueOnError) return null; else System. exit(1);
        }

        logger.debug("-----------------------------------------------------------------");
        logger.debug("End YamlDocument.readYaml {}", sourceInputFile);
        logger.debug("-----------------------------------------------------------------");
        logger.debug("");

        return yamlMap;
    }

    // For each template file found in this yaml, a YamlTemplate object is created and added to the list.
    // This means that each YamlDocument - which is associated with a yaml file - has its own list of YamlTemplate objects.
    public void readTemplates(String sourcePath,
                              String targetPath,
                              String sourceBasePathExternal,
                              String targetBasePathExternal,
                              String sourceRepositoryName,
                              String targetRepositoryName,
                              ArrayList<RepositoryResource> repositoryList,
                              boolean continueOnError){
        logger.debug("==> Method: YamlDocument.readTemplates");
        logger.debug("sourcePath: {}", sourcePath);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("sourceBasePathExternal: {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal: {}", targetBasePathExternal);
        logger.debug("rootInputFile: {}", rootInputFile);
        logger.debug("continueOnError: {}", continueOnError);

        // The root represents the root path of the pipeline
        Path pathMain = Paths.get(rootInputFile);
        Path pathRoot = pathMain.getParent();
        String root = "";
        if (pathRoot != null) {
            // Get the root of the rootInputFile
            root = pathRoot.toString() + "/";
        }
        logger.debug("root: {}", root);

        getTemplates(yamlMap,
                root,
                sourcePath,
                targetPath,
                sourceBasePathExternal,
                targetBasePathExternal,
                sourceRepositoryName,
                targetRepositoryName,
                repositoryList,
                continueOnError);
        int index = 0;
        int size = yamlTemplateList.size();
        YamlTemplate yamlTemplate;
        for (index = 0; index < size; index++) {
            yamlTemplate = yamlTemplateList.get(index);
            yamlTemplate.readYaml(continueOnError);

            // Templates can contain other templates, so recursively read them
            yamlTemplate.readTemplates(sourcePath,
                    targetPath,
                    sourceBasePathExternal,
                    targetBasePathExternal,
                    sourceRepositoryName,
                    targetRepositoryName,
                    repositoryList,
                    continueOnError);
        }
    }

    /*
       The manipulated yaml map is saved onto the local file system. The location is a target location,
       other than the original location of the pipeline file.
     */
    public void dumpYaml() throws IOException {
        logger.debug("==> Method: YamlDocument.dumpYaml");

        // Dump the updated yaml to target directory (with the same name as the original file in the source directory)
        logger.debug("=================================================================");
        logger.debug("Dump the yamlMap of {} to {}", sourceInputFile, targetOutputFile);
        logger.debug("=================================================================");

        if (sourceInputFile == null) {
            // This may be a false-positive, so don't exit
            logger.error(RED + "sourceInputFile is null; this may be a false-positive" + RESET_COLOR);
            if (this instanceof YamlTemplate)
                logger.error(RED + "This is a template with name: {}" + RESET_COLOR, templateName);
            logger.debug("");

            return;
        }
        if (targetOutputFile == null) {
            // This may be a false-positive, so don't exit
            logger.error(RED + "targetOutputFile is null; this may be a false-positive" + RESET_COLOR);
            logger.debug("");

            return;
        }

        logger.debug("");
        final DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        final Yaml yaml = new Yaml(options);
        FileWriter writer = new FileWriter(targetOutputFile);
        yaml.dump(yamlMap, writer);
        Utils.wait(1000);

        // Dump the templates
        int index = 0;
        int size = yamlTemplateList.size();
        YamlTemplate yamlTemplate;
        for (index = 0; index < size; index++) {
            yamlTemplate = yamlTemplateList.get(index);
            yamlTemplate.dumpYaml();
        }
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
        logger.debug("==> Method: YamlDocument.executeCommandTemplates");

        // Execute the command in the yamlTemplate files
        int index = 0;
        int size = yamlTemplateList.size();
        YamlTemplate yamlTemplate;
        for (index = 0; index < size; index++) {
            yamlTemplate = yamlTemplateList.get(index);
            yamlTemplate.executeCommand(actionEnum,
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
        logger.debug("actionEnum: {}", actionEnum);
        logger.debug("sectionName: {}", sectionName);
        logger.debug("sectionValue: {}", sectionValue);
        logger.debug("identifierName: {}", identifierName);
        logger.debug("identifierValue: {}", identifierValue);
        logger.debug("keyName: {}", keyName);
        logger.debug("sectionFound: {}", sectionFound);
        logger.debug("keyValue: {}", keyValue);
        logger.debug("continueSearching: {}", continueSearching);

        if (section == null)
            return;

        // Find the section
        Map<String, Object> inner = section;
        for (Map.Entry<String, Object> entry : inner.entrySet()) {
            logger.debug("Key = {}, Value = {}, Class = {}", entry.getKey(), entry.getValue(), entry.getValue().getClass());

            // Check whether the section is found. If true, the next actions are performed on this section
            sectionFound = isSectionFound (entry, sectionName, sectionValue);

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
       Check whether a section is present in a map entry
     */
    private boolean isSectionFound (Map.Entry<String, Object> entry,
                                    String sectionName,
                                    String sectionValue) {
        if (sectionName.equals(entry.getKey())) {
            if (sectionValue == null || sectionValue.isEmpty()) {
                logger.debug("Found section: {}", sectionName);
                return true;
            } else {
                if (sectionValue.equals(entry.getValue())) {
                    logger.debug("Found section: {}", sectionValue);
                    return true;
                }
            }
        }
        return false;
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
        logger.debug("actionEnum: {}", actionEnum);
        logger.debug("sectionName: {}", sectionName);
        logger.debug("sectionValue: {}", sectionValue);
        logger.debug("identifierName: {}", identifierName);
        logger.debug("identifierValue: {}", identifierValue);
        logger.debug("keyName: {}", keyName);
        logger.debug("sectionFound: {}", sectionFound);
        logger.debug("keyValue: {}", keyValue);
        logger.debug("continueSearching: {}", continueSearching);

        if (section == null)
            return;

        section.forEach(entry -> {
            logger.debug("Section = {}, Class = {}", entry, entry.getClass());

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
        logger.debug("findLiteral: {}", findLiteral);
        logger.debug("replaceLiteral: {}", replaceLiteral);
        logger.debug("continueSearching: {}", continueSearching);

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
                                logger.debug("Replace with value: {}", keyValue);
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
                                logger.debug("Replace with value: {}", keyValue);
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

    // TODO
    // TESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTEST
//    private void insertAfterSection (Map.Entry<String, Object> section, String keyName, String value) {
//        logger.debug("==> Method: YamlDocument.insertAfterSection");
//        logger.debug("key: {}", keyName);
//        logger.debug("value: {}", value);
//
//        // Run trough the elements of the list and insert a section before and/or after
//        if (section.getValue() instanceof ArrayList) {
//            ArrayList<Object> list = (ArrayList<Object>)section.getValue();
//            int index = 0;
//            int size = list.size();
//            for (index = 0; index < size; index++) {
//                logger.debug("Element = {}, Class = {}", list.get(index), list.get(index).getClass());
//
//                // If it is a Map, it contains key/value; iterate trough them to detect whether the key/value pair exists
//                if (list.get(index) instanceof Map) {
//                    Map<String, Object> map = (Map<String, Object>)list.get(index);
//                    for (Map.Entry<String, Object> entry : map.entrySet()) {
//
//                        // Check whether the entry has the given key and value
//                        if (keyName.equals(entry.getKey()) && value.equals(entry.getValue())) {
//                            // TODO
//                            logger.debug("Insert after: {}", value);
//
//                            return;
//                        }
//                    }
//                }
//            }
//        }
//    }
    // TESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTEST

    /*
       Create a list of all templates.
     */
    private void getTemplates(Map<String, Object> inner,
                              String root,
                              String sourcePath,
                              String targetPath,
                              String sourceBasePathExternal,
                              String targetBasePathExternal,
                              String sourceRepositoryName,
                              String targetRepositoryName,
                              ArrayList<RepositoryResource> repositoryList,
                              boolean continueOnError) {
        logger.debug("==> Method: YamlDocument.getTemplates");
        logger.debug("root: {}", root);
        logger.debug("sourcePath: {}", sourcePath);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("sourceBasePathExternal: {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal: {}", targetBasePathExternal);
        logger.debug("sourceRepositoryName: {}", sourceRepositoryName);
        logger.debug("targetRepositoryName: {}", targetRepositoryName);
        logger.debug("continueOnError: {}", continueOnError);

        // Inner could be null
        if (inner == null){
            logger.error(RED + "inner is null; the reason is probably because the file could not be read, so return" + RESET_COLOR);
            return;
        }

        // Run through the YAML file and add the template files to the list
        for (Map.Entry<String, Object> entry : inner.entrySet()) {

            // Add all template files to the list
            if ("template".equals(entry.getKey())) {
                yamlTemplateList.add(new YamlTemplate((String) entry.getValue(),
                        sourcePath,
                        targetPath,
                        sourceBasePathExternal,
                        targetBasePathExternal,
                        sourceRepositoryName,
                        targetRepositoryName,
                        repositoryAlias,
                        repositoryList,
                        continueOnError));
                logger.debug("Found template {}; add it to the yamlTemplateList", entry.getValue());
            }

            // Go a level deeper
            if (entry.getValue() instanceof Map) {
                getTemplates((Map<String, Object>) entry.getValue(),
                        root,
                        sourcePath,
                        targetPath,
                        sourceBasePathExternal,
                        targetBasePathExternal,
                        sourceRepositoryName,
                        targetRepositoryName,
                        repositoryList,
                        continueOnError);
            }
            if (entry.getValue() instanceof ArrayList) {
                getTemplates((ArrayList<Object>) entry.getValue(),
                        root,
                        sourcePath,
                        targetPath,
                        sourceBasePathExternal,
                        targetBasePathExternal,
                        sourceRepositoryName,
                        targetRepositoryName,
                        repositoryList,
                        continueOnError);
            }
        }
    }
    private void getTemplates(ArrayList<Object> inner,
                              String root,
                              String sourcePath,
                              String targetPath,
                              String sourceBasePathExternal,
                              String targetBasePathExternal,
                              String sourceRepositoryName,
                              String targetRepositoryName,
                              ArrayList<RepositoryResource> repositoryList,
                              boolean continueOnError) {
        logger.debug("==> Method: YamlDocument.getTemplates");
        logger.debug("root: {}", root);
        logger.debug("sourcePath: {}", sourcePath);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("sourceBasePathExternal: {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal: {}", targetBasePathExternal);
        logger.debug("sourceRepositoryName: {}", sourceRepositoryName);
        logger.debug("targetRepositoryName: {}", targetRepositoryName);
        logger.debug("continueOnError: {}", continueOnError);

        // Inner could be null
        if (inner == null){
            logger.error(RED + "inner is null; the reason is probably because the file could not be read, so return" + RESET_COLOR);
            return;
        }

        inner.forEach(entry -> {
            if (entry == null) {
                logger.error(RED + "entry is null" + RESET_COLOR);
                return;
            }
            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                getTemplates((Map<String, Object>)entry,
                        root,
                        sourcePath,
                        targetPath,
                        sourceBasePathExternal,
                        targetBasePathExternal,
                        sourceRepositoryName,
                        targetRepositoryName,
                        repositoryList,
                        continueOnError);
            }
            if (entry instanceof ArrayList) {
                getTemplates((ArrayList<Object>)entry,
                        root,
                        sourcePath,
                        targetPath,
                        sourceBasePathExternal,
                        targetBasePathExternal,
                        sourceRepositoryName,
                        targetRepositoryName,
                        repositoryList,
                        continueOnError);
            }
        });
    }

    /* Repositories in the resources section of the yaml pipeline are made local, meaning that they
       are all of type 'git', even if they were originally from GitHub. The junit-pipeline framework
       copies there repositories to the Azure DeVOps test project.
     */
    public void makeResourcesLocal () {
        logger.debug("==> Method: YamlDocumentEntryPoint.makeResourcesLocal");
        makeResourcesLocal (yamlMap);
    }
    private void makeResourcesLocal (Map<String, Object> map) {
        if (map == null) {
            logger.error(RED + "map is null" + RESET_COLOR);
            return;
        }

        // Run through the YAML file and adjust the map
        boolean found = false;
        for (Map.Entry<String, Object> entry : map.entrySet()) {
            logger.debug("Key: {}", entry.getKey());
            logger.debug("Value: {}", entry.getValue());

            if ("repository".equals(entry.getKey())) {
                logger.debug("Found a repository");
                found = true;
            } else {
                // Go a level deeper
                if (entry.getValue() instanceof Map) {
                    makeResourcesLocal((Map<String, Object>) entry.getValue());
                }
                if (entry.getValue() instanceof ArrayList) {
                    makeResourcesLocal((ArrayList<Object>) entry.getValue());
                }
            }

            if (found) {
                if ("type".equals(entry.getKey())) {
                    entry.setValue("git"); // Make it always am Azure DeVOps Git project
                }
                if ("endpoint".equals(entry.getKey())) {
                    map.remove(entry.getKey()); // Don't use this for local repositories
                }
                if ("ref".equals(entry.getKey())) {
                    map.remove(entry.getKey()); // Always use the default, which is 'refs/heads/main'
                }
                if ("name".equals(entry.getKey())) {
                    String name  = entry.getValue().toString();
                    if (name.contains("/")) {
                        // The name consist of a project, a slash (/) and a name
                        String[] parts = name.split("/");
                        entry.setValue(parts[1]); // Only use the last part of the full name
                    }
                    else {
                        // Use the full name
                        entry.setValue(name);
                    }
                }
            }
        }
    }

    private void makeResourcesLocal(ArrayList<Object> inner) {
        if (inner == null) {
            logger.error(RED + "inner is null" + RESET_COLOR);
            return;
        }

        inner.forEach(entry -> {
            if (entry == null) {
                logger.error(RED + "entry is null" + RESET_COLOR);
                return;
            }

            // If inner sections are found, go a level deeper
            if (entry instanceof Map) {
                makeResourcesLocal((Map<String, Object>)entry);
            }
            if (entry instanceof ArrayList) {
                makeResourcesLocal((ArrayList<Object>)entry);
            }
        });
    }
}

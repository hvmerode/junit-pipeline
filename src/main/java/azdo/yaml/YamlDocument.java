// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

import azdo.action.Action;
import azdo.utils.Log;
import azdo.utils.Utils;
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
    private static Log logger = Log.getLogger();
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

    /******************************************************************************************
     Reads a pipeline file from the local file system and creates a yaml map object.
     This map is kept into memory and is manipulated by the methods of this class.
     ******************************************************************************************/
    @SuppressWarnings("java:S1192")
    public Map<String, Object> readYaml(boolean continueOnError) {
        logger.debug("");
        logger.debug("-----------------------------------------------------------------");
        logger.debug("Start YamlDocument.readYaml: {}", sourceInputFile);
        logger.debug("-----------------------------------------------------------------");

        if (sourceInputFile == null) {
            // This may be a false-positive, so don't exit
            // This typically happens if a parameter is called 'template'
            logger.warn("sourceInputFile is null; this may be a false-positive");
            if (this instanceof YamlTemplate)
                logger.warn("This is a template with name: {}", templateName);
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
            // This is a warning and not an error
            // Reason is that it may find a false-positive template file
            logger.warn("Cannot find file {}", sourceInputFile);
            logger.debug(DEMARCATION);
        }

        logger.debug("-----------------------------------------------------------------");
        logger.debug("End YamlDocument.readYaml {}", sourceInputFile);
        logger.debug("-----------------------------------------------------------------");
        logger.debug("");

        return yamlMap;
    }

    /******************************************************************************************
     For each template file found in this yaml, a YamlTemplate object is created and added to the list.
     This means that each YamlDocument - which is associated with a yaml file - has its own list of YamlTemplate objects.
     ******************************************************************************************/
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

    /******************************************************************************************
     The manipulated yaml map is saved onto the local file system. The location is a target location,
     other than the original location of the pipeline file.
     ******************************************************************************************/
    public void dumpYaml() throws IOException {
        logger.debug("==> Method: YamlDocument.dumpYaml");

        // Dump the updated yaml to target directory (with the same name as the original file in the source directory)
        logger.debug("=================================================================");
        logger.debug("Dump the yamlMap of {} to {}", sourceInputFile, targetOutputFile);
        logger.debug("=================================================================");

        if (sourceInputFile == null) {
            // This may be a false-positive, so don't exit
            logger.warn("sourceInputFile is null; this may be a false-positive");
            if (this instanceof YamlTemplate)
                logger.warn("This is a template with name: {}", templateName);
            logger.debug("");

            return;
        }
        if (targetOutputFile == null) {
            // This may be a false-positive, so don't exit
            logger.warn("targetOutputFile is null; this may be a false-positive");
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

    /******************************************************************************************
     Create a list of all templates.
     ******************************************************************************************/
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
            logger.warn("inner is null; the reason is probably because the file could not be read, so return");
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
            logger.warn("inner is null; the reason is probably because the file could not be read, so return");
            return;
        }

        inner.forEach(entry -> {
            if (entry == null) {
                logger.warn("entry is null");
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

    /******************************************************************************************
     Repositories in the resources section of the yaml pipeline are made local, meaning that they
     are all of type 'git', even if they were originally from GitHub. The junit-pipeline framework
     copies there repositories to the Azure DeVOps test project.
     ******************************************************************************************/
    public void makeResourcesLocal () {
        logger.debug("==> Method: YamlDocumentEntryPoint.makeResourcesLocal");
        makeResourcesLocal (yamlMap);
    }
    private void makeResourcesLocal (Map<String, Object> map) {
        if (map == null) {
            logger.warn("map is null");
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
                makeResourcesLocal((Map<String, Object>)entry);
            }
            if (entry instanceof ArrayList) {
                makeResourcesLocal((ArrayList<Object>)entry);
            }
        });
    }

    /********************************************************************************
     Parses the yaml document until a specific section is found. A section is either
     a type - identified using 'sectionType' - such as 'variables', 'parameters',
     'stages', or it is a type in combination with a 'sectionIdentifier', for example:
         'stage: myStage'
     This method is performing recursion by calling itself to search for sections
     deeper in the yaml structure.

     If the section is found, it tries to execute an action object containing
     specific variables.
     ********************************************************************************/
    public ActionResult performAction (Action action,
                                       String sectionType,
                                       String sectionIdentifier) {
        logger.debug("==> Method: YamlDocument.performAction");
        ActionResult ar = performActionOnThis (action, sectionType, sectionIdentifier);
        performActionOnTemplates (action, sectionType, sectionIdentifier);
        return ar;
    }
    private ActionResult performActionOnThis (Action action,
                                              String sectionType,
                                              String sectionIdentifier) {
        logger.debug("==> Method: YamlDocument.performActionOnThis (first level)");

        ActionResult actionResult = new ActionResult();
        actionResult.l1 = yamlMap;
        actionResult.l2 = null;
        actionResult.l3 = null;
        return performActionOnThis (actionResult, action, sectionType, sectionIdentifier);
    }

    private ActionResult performActionOnThis (ActionResult actionResult,
                                              Action action,
                                              String sectionType,
                                              String sectionIdentifier){
        logger.debug("==> Method: YamlDocument.performActionOnThis (second level)");
        logger.debug("action: {}", action.getClass().getName());
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);

        if (actionResult == null) {
            logger.warn("actionResult is null");
            return null;
        }

        if (actionResult.l1 == null) {
            logger.debug("actionResult.l1 is null; return");
        }

        if (sectionType == null || sectionType.isEmpty()) {
            logger.warn("sectionType is not provided");
        }

        // Find the section; handle the Map
        Object l1 = actionResult.l1;
        Map<String, Object> map = null;
        boolean found = false;

        // --------------------------------- Handle the map ---------------------------------
        if (l1 instanceof Map) {
            logger.debug("l1 is instance of map...");
            map = (Map<String, Object>) l1;
            String key;
            String stringValue;
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                key = entry.getKey();
                stringValue = entry.getValue().toString();
                logger.debug("Key: {}", key);
                logger.debug("Value: {}", stringValue);

                if (sectionType.equals(key)) {
                    logger.debug("Found a sectionType with the key: {}", key);

                    if (!action.needsSectionIdentifier()) {
                        actionResult = doAction (actionResult, action, entry);
                    }

                    // Check whether value equals to sectionIdentifier
                    if (sectionIdentifier != null && sectionIdentifier.equals(stringValue)) {
                        logger.debug("And the stringValue also equals the value: {}", stringValue);

                        if (action.needsSectionIdentifier()) {
                            //actionResult.l3 = actionResult.l2;
                            //actionResult.l2 = actionResult.l1;
                            actionResult = doAction (actionResult, action, entry);
                        }
                    }
                }
                if (sectionType.equals(stringValue)) {
                    logger.debug("Found a sectionType with the value: {}", stringValue);

                    if (!action.needsSectionIdentifier()) {
                        actionResult = doAction (actionResult, action, entry);
                    }

                    // Check whether key equals to sectionIdentifier
                    if (sectionIdentifier.equals(key)) {
                        logger.debug("And the stringValue also equals the key: {}", key);

                        if (action.needsSectionIdentifier()) {
                            //actionResult.l3 = actionResult.l2;
                            //actionResult.l2 = actionResult.l1;
                            actionResult = doAction (actionResult, action, entry);
                        }
                    }
                }

                if (actionResult.actionExecuted)
                    return actionResult;

                // Go a level deeper
                actionResult = goALevelDeeper (actionResult, entry, action, sectionType, sectionIdentifier);
            }
        }

        // --------------------------------- Handle the arraylist ---------------------------------
        if (l1 instanceof ArrayList) {
            logger.debug("l1 is instance of arraylist...");
            ArrayList<Object> list = (ArrayList<Object>) l1;
            if (list.isEmpty())
                return actionResult;

            int size = list.size();
            logger.debug("list.size(): {}", list.size());
            for (int i = 0; i < size; i++) {
                actionResult.l3 = actionResult.l2;
                actionResult.l2 = list;
                actionResult.l1 = list.get(i);
                logger.debug("list.get(i): {}", list.get(i));
                actionResult = performActionOnThis (actionResult,
                        action,
                        sectionType,
                        sectionIdentifier);

                // Break out of the loop
                // The action is executed and the list may contain less (after a delete action) or more (after an insert) entries
                if (actionResult.actionExecuted)
                    break;
            }
        }

        return actionResult;
    };

    private ActionResult doAction (ActionResult actionResult,
                                   Action action,
                                   Map.Entry<String, Object> entry) {
        actionResult.l3 = actionResult.l2;
        actionResult.l2 = actionResult.l1;
        actionResult.l1 = entry.getValue();
        action.execute(actionResult);
        return actionResult;
    }

    private ActionResult goALevelDeeper (ActionResult actionResult,
                                         Map.Entry<String, Object> entry,
                                         Action action,
                                         String sectionType,
                                         String sectionIdentifier) {
        logger.debug("==> Method: YamlDocument.goALevelDeeper");

        actionResult.l1 = entry.getValue();
        actionResult = performActionOnThis (actionResult,
                action,
                sectionType,
                sectionIdentifier);

        return actionResult;
    }

    public ActionResult performActionOnTemplates (Action action,
                                                  String sectionType,
                                                  String sectionIdentifier) {
        logger.debug("==> Method: YamlDocument.performActionOnTemplates");

        // Execute the command in the yamlTemplate files
        int index = 0;
        int size = yamlTemplateList.size();
        YamlTemplate yamlTemplate;
        for (index = 0; index < size; index++) {
            yamlTemplate = yamlTemplateList.get(index);
            yamlTemplate.performAction(action,
                    sectionType,
                    sectionIdentifier);
        }
        return null;
    }

    /******************************************************************************************
     Replace a string, identified by keyName, with a string, identified by keyValue
     ******************************************************************************************/
    public void overrideLiteral (String literalToReplace, String newValue, boolean replaceAll) {
        logger.debug("==> Method: YamlDocument.replaceLiteral");
        logger.debug("literalToReplace: {}", literalToReplace);
        logger.debug("newValue: {}", newValue);
        logger.debug("continueSearching: {}", replaceAll);

        Yaml yaml = new Yaml();
        String s = yaml.dump(yamlMap);
        if (s.contains(literalToReplace))
            logger.info("Override literal \'{}\' with \'{}\' in file \'{}\'", literalToReplace, newValue, targetOutputFile);

        if (replaceAll)
            s = s.replace(literalToReplace, newValue);
        else
            s = s.replaceFirst(Pattern.quote(literalToReplace), newValue);
        yamlMap = (Map) yaml.load(s);

        // Execute the command in the yamlTemplate files
        int index = 0;
        int size = yamlTemplateList.size();
        YamlTemplate yamlTemplate;
        for (index = 0; index < size; index++) {
            yamlTemplate = yamlTemplateList.get(index);
            yamlTemplate.overrideLiteral(literalToReplace, newValue, replaceAll);
        }
    }
}

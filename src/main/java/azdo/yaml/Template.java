package azdo.yaml;

import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.util.ArrayList;

/*
    A Template is a specific YamlDocument and represents a template YAML file.
    A template can be invoked by the main pipeline or invoked by other templates in the same or in other repositories.
 */
public class Template extends YamlDocument{
    public enum InternalOrExternalTemplate {
        INTERNAL,
        EXTERNAL
    }
    private InternalOrExternalTemplate internalOrExternalTemplate = InternalOrExternalTemplate.EXTERNAL;
    private String templateName; // The template name as defined in the pipeline (without the @ postfix)
    //private String repositoryAlias; // The name of the repository in which the template resides (in case of an external template)
    private static Logger logger = LoggerFactory.getLogger(Template.class);

    public Template (String templateName,
                     String sourcePath,
                     String targetPath,
                     String sourceBasePathExternal,
                     String targetBasePathExternal,
                     String parentAlias,
                     ArrayList<RepositoryResource> repositoryList){
        logger.debug("==> Object: Template");
        logger.debug("templateName: {}", templateName);
        logger.debug("sourcePath: {}", sourcePath);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("sourceBasePathExternal: {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal: {}", targetBasePathExternal);
        logger.debug("parentAlias: {}", parentAlias);

        this.templateName = templateName;
        this.rootInputFile = templateName;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;

        if (templateName != null && templateName.contains("@")) {
            // It is an EXTERNAL template (defined in another repository)
            logger.debug("{} is an EXTERNAL template referred with an @-character", templateName);
            handleExternalTemplate(templateName,
                    sourceBasePathExternal,
                    targetBasePathExternal,
                    parentAlias,
                    repositoryList,
                    true);
        }
        else {
            // It can still be an EXTERNAL template, but it is referred as a local template in the external repository (not using the @)
            // This also means it does not reside in the main repository; check this!
            sourceInputFile = Utils.findFullQualifiedFileNameInDirectory(sourcePath, templateName);
            if (sourceInputFile == null) {
                // It is an EXTERNAL template
                logger.debug("{} is an EXTERNAL template but referred as a local file", templateName);
                handleExternalTemplate(templateName,
                        sourceBasePathExternal,
                        targetBasePathExternal,
                        parentAlias,
                        repositoryList,
                        false);
            }
            else {
                // It is an INTERNAL template (defined in the same repository as the main pipeline file)
                logger.debug("{} is an INTERNAL template", templateName);
                handleInternalTemplate(templateName, sourcePath);
            }
        }
        sourceInputFile = Utils.fixPath(sourceInputFile);
        targetOutputFile = Utils.fixPath(targetOutputFile);
        logger.debug("sourceInputFile: {}", sourceInputFile);
        logger.debug("targetOutputFile: {}", targetOutputFile);
    }

    private void handleExternalTemplate (String templateName,
                                         String sourceBasePathExternal,
                                         String targetBasePathExternal,
                                         String parentAlias,
                                         ArrayList<RepositoryResource> repositoryList,
                                         boolean withAt) {
        // It is an EXTERNAL template (defined in another repository)
        logger.debug("==> Method: Template.handleExternalTemplate");
        logger.debug("templateName {}", templateName);
        logger.debug("sourceBasePathExternal {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal {}", targetBasePathExternal);
        logger.debug("parentAlias {}", parentAlias);

        internalOrExternalTemplate = InternalOrExternalTemplate.EXTERNAL;
        if (withAt) {
            // The template name contains an @
            repositoryAlias = templateName.substring(templateName.lastIndexOf('@') + 1);
            templateName = templateName.substring(0, templateName.lastIndexOf('@'));
        }
        else {
            // The template name does not contain an @
            repositoryAlias = parentAlias; // There is no alias, so use the one of the parent
        }

        logger.debug("repositoryAlias: {}", repositoryAlias);
        RepositoryResource repositoryResource = findRepositoryResourceByAlias(repositoryList, repositoryAlias);
        if (repositoryResource != null) {
            logger.debug("repositoryResource.name: {}", repositoryResource.name);
            String temp = Utils.fixPath(templateName);
            sourceInputFile = sourceBasePathExternal + "/" + repositoryResource.name + RepositoryResource.LOCAL_SOURCE_POSTFIX + "/" + temp;
            targetOutputFile = targetBasePathExternal + "/" + repositoryResource.name + "/" + temp;
            logger.debug("{} is an EXTERNAL template and resides in repository {}", templateName, repositoryResource.name);
        }
        else {
            logger.debug("repositoryResource is null; cannot determine the name of the resource (= repository name)");
        }
    }

    private void handleInternalTemplate (String templateName,
                                         String sourcePath) {
        logger.debug("==> Method: Template.handleInternalTemplate");
        logger.debug("templateName {}", templateName);
        logger.debug("sourcePath {}", sourcePath);

        // An internal template resides in the same repository as the main pipeline file
        sourceInputFile = Utils.findFullQualifiedFileNameInDirectory(sourcePath, templateName);
        if (sourceInputFile != null) {
            Path p = Path.of(sourceInputFile);
            String directory = p.getParent().toString();
            logger.debug("directory: {}", directory);
            Path f = Path.of(templateName);
            String fileName = f.getFileName().toString();
            logger.debug("fileName: {}", fileName);
            targetOutputFile = directory + "/" + fileName;
        }
    }

    private RepositoryResource findRepositoryResourceByAlias (ArrayList<RepositoryResource> repositoryList, String alias) {
        logger.debug("==> Method: Template.findRepositoryResourceByAlias");
        logger.debug("alias: {}", alias);
        logger.debug("repositoryList: {}", repositoryList.toString());

        RepositoryResource repositoryResource = null;
        int size = repositoryList.size();
        for (int i = 0; i < size; i++) {
            repositoryResource = repositoryList.get(i);
            if (alias.equals(repositoryResource.repository))
                return repositoryResource;
        }
        return null;
    }

    public InternalOrExternalTemplate getInternalOrExternalTemplate() {
        return internalOrExternalTemplate;
    }

    public String getTemplateName() {
        return templateName;
    }
}

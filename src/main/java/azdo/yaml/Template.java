package azdo.yaml;

import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Map;

/*
    A Template is a specific YamlDocument
    It can be invoked by the main pipeline or invoked by other templates in the same repository
    A template can also invoke templates in other repositories
    TODO: Taking external repos into account needs be be added
 */
public class Template extends YamlDocument{
    public enum InternalOrExternalTemplate {
        INTERNAL,
        EXTERNAL
    }
    private InternalOrExternalTemplate internalOrExternalTemplate = InternalOrExternalTemplate.INTERNAL;
    private String templateName; // The template name as defined in the pipeline (without the @ postfix)
    private String root = ""; // Location of the template file in the repository
    private String repositoryAlias; // The name of the repository in which the template resides (in case of an external template)
    //private String targetOutputFile; // The filename used to dump the manipulated yaml
    private static Logger logger = LoggerFactory.getLogger(Template.class);

    public Template (String templateName,
                     String root,
                     String targetPath,
                     String sourceBasePathExternal,
                     String targetBasePathExternal,
                     ArrayList<RepositoryResource> repositoryList){
        logger.debug("==> Object: Template");
        logger.debug("templateName: {}", templateName);
        logger.debug("root: {}", root);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("sourceBasePathExternal: {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal: {}", targetBasePathExternal);

        this.templateName = templateName;
        if (templateName != null && templateName.contains("@")) {
            // It is an EXTERNAL template (defined in another repository)
            internalOrExternalTemplate = InternalOrExternalTemplate.EXTERNAL;
            repositoryAlias = templateName.substring(templateName.lastIndexOf('@') + 1);
            templateName = templateName.substring(0, templateName.lastIndexOf('@'));
            RepositoryResource repositoryResource = findRepositoryResourceByAlias(repositoryList, repositoryAlias);
            if (repositoryResource != null) {
                root = repositoryResource.name;
            }
            sourceInputFile = sourceBasePathExternal + "/" + root + RepositoryResource.LOCAL_SOURCE_POSTFIX + "/" + templateName;
            sourceInputFile = Utils.fixPath(sourceInputFile);
            targetOutputFile = targetBasePathExternal + "/" + root + "/" + templateName;
            logger.debug("<{}> is an EXTERNAL template and resides in repository <{}>", templateName, repositoryAlias);
        }
        else {
            // It is an INTERNAL template (defined in the same repository as the main pipeline file)
            // The root must be set, because it is needed to resolve the location of the template on the filesystem
            logger.debug("<{}> is an INTERNAL template", templateName);
            this.root = root;

            // An internal template resides in the same repository as the main pipeline file; add the main root (basePath) of this main pipeline file
            sourceInputFile = root + templateName;
            targetOutputFile = targetPath + "/" + sourceInputFile;
        }
        sourceInputFile = Utils.fixPath(sourceInputFile);
        targetOutputFile = Utils.fixPath(targetOutputFile);
        logger.debug("root is <{}>", root);
        logger.debug("sourceInputFile is <{}>", sourceInputFile);
        logger.debug("targetOutputFile is <{}>", targetOutputFile);
    }

    private RepositoryResource findRepositoryResourceByAlias (ArrayList<RepositoryResource> repositoryList, String alias) {
        logger.debug("==> Method: Template.findRepositoryResourceByAlias");

        if (repositoryList == null) {
            logger.debug("repositoryList is null. Cannot find an alias");
            return null;
        }

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

    public String getRoot() {
        return root;
    }
}

package azdo.yaml;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private String basePath = ""; // Location of the template file in the repository
    private String repositoryAlias; // The name of the repository in which the template resides (in case of an external template)
    private static Logger logger = LoggerFactory.getLogger(YamlDocumentEntryPoint.class);

    public Template (String templateName, String basePath){
        super(basePath + templateName);
        logger.debug("==> Object: Template");

        this.templateName = templateName;
        if (templateName != null && templateName.contains("@")) {
            // It is an external template (defined in another repository)
            repositoryAlias = templateName.substring(templateName.lastIndexOf('@') + 1);
            templateName = templateName.substring(0, templateName.lastIndexOf('@'));
            logger.debug("<{}> is an external template and resides in repository <{}>", templateName, repositoryAlias);
        }
        else {
            // It is an internal template (defined in the same repository as the main pipeline file)
            // The basePath must be set, because it is needed to resolve the location of the template on the filesystem
            logger.debug("<{}> is an internal template", templateName);
            this.basePath = basePath;
        }
        logger.debug("basePath is <{}>", basePath);
        logger.debug("originalFileName is <{}>", basePath + templateName);
    }

    public InternalOrExternalTemplate getInternalOrExternalTemplate() {
        return internalOrExternalTemplate;
    }

    public String getTemplateName() {
        return templateName;
    }

    public String getFullTemplateName() {
        return basePath + templateName;
    }
}

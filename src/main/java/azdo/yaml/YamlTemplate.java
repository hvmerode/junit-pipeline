package azdo.yaml;

import azdo.utils.Log;
import azdo.utils.Utils;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;

/******************************************************************************************
 A YamlTemplate is a specific YamlDocument and represents a template YAML file.
 A template can be invoked by the main pipeline or invoked by other templates in the same
 or in other repositories.
 *******************************************************************************************/
public class YamlTemplate extends YamlDocument{
    public enum InternalOrExternalTemplate {
        INTERNAL,
        EXTERNAL
    }

    private static final Log logger = Log.getLogger();
    private InternalOrExternalTemplate internalOrExternalTemplate = InternalOrExternalTemplate.EXTERNAL;
    //private String templateName; // The template name as defined in the pipeline (without the @ postfix)

    public YamlTemplate(String templateName,
                        String sourcePath,
                        String targetPath,
                        String sourceBasePathExternal,
                        String targetBasePathExternal,
                        String sourceRepositoryName,
                        String targetRepositoryName,
                        String parentAlias,
                        ArrayList<RepositoryResource> repositoryList,
                        boolean includeExternalTemplates,
                        boolean continueOnError){
        logger.debug("==> Object: YamlTemplate");
        logger.debug("templateName: {}", templateName);
        logger.debug("sourcePath: {}", sourcePath);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("sourceBasePathExternal: {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal: {}", targetBasePathExternal);
        logger.debug("sourceRepositoryName: {}", sourceRepositoryName);
        logger.debug("targetRepositoryName: {}", targetRepositoryName);
        logger.debug("parentAlias: {}", parentAlias);
        logger.debug("includeExternalTemplates: {}", includeExternalTemplates);
        logger.debug("continueOnError: {}", continueOnError);

        this.templateName = templateName;
        this.rootInputFile = templateName;
        this.sourcePath = sourcePath;
        this.targetPath = targetPath;
        this.sourceRepositoryName = sourceRepositoryName;
        this.targetRepositoryName = targetRepositoryName;

        if (templateName != null && templateName.contains("@")) {
            // It is an EXTERNAL template (defined in another repository)
            logger.debug("{} is an EXTERNAL template referred with an @-character", templateName);

            // Only handle external templates if they are allowed to be manipulated; otherwise i
            if (includeExternalTemplates) {
                handleExternalTemplate(templateName,
                        sourceBasePathExternal,
                        targetBasePathExternal,
                        parentAlias,
                        repositoryList,
                        true);
            }
        }
        else {
            // It can still be an EXTERNAL template, but it is referred as a local template in the external repository (not using the @)
            // This also means it does not reside in the main repository; check this!
            sourceInputFile = Utils.findFullQualifiedFileNameInDirectory(sourcePath, templateName);
            if (sourceInputFile == null) {
                // It is an EXTERNAL template
                logger.debug("{} is an EXTERNAL template but referred as a local file in another external template", templateName);
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
                handleInternalTemplate(templateName, sourcePath, continueOnError);
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
        logger.debug("==> Method: YamlTemplate.handleExternalTemplate");
        logger.debug("templateName: {}", templateName);
        logger.debug("sourceBasePathExternal: {}", sourceBasePathExternal);
        logger.debug("targetBasePathExternal: {}", targetBasePathExternal);
        logger.debug("parentAlias: {}", parentAlias);

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
            logger.debug("{} is an EXTERNAL template and resides in repository: {}", templateName, repositoryResource.name);
        }
        else {
            logger.warn("repositoryResource is null; this may be a false-positive");
        }
    }

    private void handleInternalTemplate (String templateName,
                                         String sourcePath,
                                         boolean continueOnError) {
        logger.debug("==> Method: YamlTemplate.handleInternalTemplate");
        logger.debug("templateName: {}", templateName);
        logger.debug("sourcePath: {}", sourcePath);
        logger.debug("continueOnError: {}", continueOnError);

        // An internal template resides in the same repository as the main pipeline file
        sourceInputFile = Utils.findFullQualifiedFileNameInDirectory(sourcePath, templateName);
        if (sourceInputFile != null) {
            // Some juggling with the path is needed, because templates can be assigned by relative paths
            // This is particular annoying if a template with a relative path includes another templates with
            // a relative path
            Path p = Path.of(sourceInputFile);
            String directory = p.getParent().toString();
            logger.debug("directory: {}", directory);
            Path f = Path.of(templateName);
            String fileName = f.getFileName().toString();
            logger.debug("fileName: {}", fileName);

            // Derive the output file; it is the same as the source file, but with a different repository name
            String tempTargetOutputFile = directory + "/" + fileName; // This is not yet the final name
            Path sourceInputFilePath = Paths.get(sourceInputFile).normalize();
            Path targetInputFilePath = Paths.get(tempTargetOutputFile).normalize();
            if (targetInputFilePath.equals(sourceInputFilePath))
            {
                // Replace the repository name (replace the source repository with the target repository name)
                // Only replace the first occurrence
                // TODO: Maybe better to replace the sourcePath with the targetPath?
                logger.debug("Replace the repository name");
                logger.debug("sourceInputFilePath is: {}", sourceInputFile);
                logger.debug("tempTargetOutputFile is: {}", tempTargetOutputFile);
                targetOutputFile = tempTargetOutputFile.replace(sourceRepositoryName, targetRepositoryName);
            }
            else
                targetOutputFile = tempTargetOutputFile;

            // Validate the internal pipeline file before any other action
            // If it is not valid, the test may fail
            Utils.validatePipelineFile(sourceInputFile, continueOnError);
        }
    }

    private RepositoryResource findRepositoryResourceByAlias (ArrayList<RepositoryResource> repositoryList, String alias) {
        logger.debug("==> Method: YamlTemplate.findRepositoryResourceByAlias");
        logger.debug("alias: {}", alias);

        if (repositoryList == null)
            return null;
        logger.debug("repositoryList: {}", repositoryList.toString());

        RepositoryResource repositoryResource;
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

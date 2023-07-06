// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

public class PropertyUtils {
    private static Log logger = Log.getLogger();

    // Source
    private Properties properties;
    private String sourcePath;
    private String sourceRepositoryName;
    private String sourceBasePathExternal;
    private String sourceProject;

    // Target
    private String targetPath;
    private String targetBasePathExternal;
    private String targetOrganization;
    private String targetProject;
    private String uriTargetRepository;
    private String azdoUser;
    private String azdoPat;
    private String targetExludeList;

    // Pipeline
    private String azdoBaseUrl;
    private String azdoEndpoint;
    private String pipelinesApi = "/pipelines";
    private String pipelinesApiRuns = "/runs";
    private String pipelinesApiVersion = "api-version=7.0";

    // Azure DevOps API: Git
    private String gitApi = "/git";
    private String gitApiRepositories = "/repositories";
    private String gitApiVersion = "api-version=7.0";

    // Azure DevOps API: Build
    private String buildApi = "/build/builds";
    private int buildApiPollFrequency = 10;
    private int buildApiPollTimeout = 180;
    private String buildApiVersion = "api-version=7.0";

    // Azure DevOps API: Project
    private String projectApi = "/projects";
    private String projectApiVersion = "api-version=7.0";

    // Miscellaneous
    private String commitPattern;
    ArrayList<String> commitPatternList;
    private String targetRepositoryName;
    private boolean continueOnError = false;

    @SuppressWarnings("java:S1192")
    public PropertyUtils(String propertyFile) {
        try {
            logger.debug("==> Object: PropertyUtils");
            logger.debug("PropertyFile: {}", propertyFile);
            properties = new Properties();
            InputStream is = getClass().getClassLoader().getResourceAsStream(propertyFile);
            properties.load(is);

            logger.debug("");
            logger.debug("#################################################################");
            logger.debug("Start reading properties");
            logger.debug("#################################################################");

            // Source
            sourcePath = getStringProperty(properties, "source.path", sourcePath);
            sourceBasePathExternal = getStringProperty(properties, "source.base.path.external", sourceBasePathExternal);
            sourceRepositoryName = getStringProperty(properties, "source.repository.name", sourceRepositoryName);
            sourceProject = getStringProperty(properties, "source.project", sourceProject); // Only used for AzDo project

            // Target
            targetOrganization = getStringProperty(properties, "target.organization", targetOrganization);
            targetProject = getStringProperty(properties, "target.project", targetProject);
            targetPath = getStringProperty(properties, "target.path", targetPath);
            targetBasePathExternal = getStringProperty(properties, "target.base.path.external", targetBasePathExternal);
            targetRepositoryName = getStringProperty(properties, "target.repository.name", targetRepositoryName);
            azdoUser = getStringProperty(properties, "azdo.user", azdoUser, false);
            azdoPat = getStringProperty(properties, "azdo.pat", azdoPat, false);
            targetExludeList = getStringProperty(properties, "target.excludelist", targetExludeList);

            // Run trough the commit pattern and create a List
            commitPattern = getStringProperty(properties, "git.commit.pattern", commitPattern);
            commitPatternList = new ArrayList<>();
            var values = commitPattern.split(",");
            for (int i = 0; i < values.length; i++)
            {
                commitPatternList.add(values[i]);
            }

            // Azure DevOps Pipeline API
            pipelinesApi = getStringProperty(properties, "pipelines.api", pipelinesApi);
            pipelinesApiRuns = getStringProperty(properties, "pipelines.api.runs", pipelinesApiRuns);
            pipelinesApiVersion = getStringProperty(properties, "pipelines.api.version", pipelinesApiVersion);

            // Azure DevOps Git API
            gitApi = getStringProperty(properties, "git.api", gitApi);
            gitApiRepositories = getStringProperty(properties, "git.api.repositories", gitApiRepositories);
            gitApiVersion = getStringProperty(properties, "git.api.version", gitApiVersion);

            // Azure DevOps Build API
            buildApi = getStringProperty(properties, "build.api", buildApi);
            buildApiVersion = getStringProperty(properties, "build.api.version", buildApiVersion);
            buildApiPollFrequency = getIntProperty(properties, "build.api.poll.frequency", buildApiPollFrequency);
            buildApiPollTimeout = getIntProperty(properties, "build.api.poll.timeout", buildApiPollTimeout);

            // Azure DevOps Project API
            projectApi = getStringProperty(properties, "project.api", projectApi);
            projectApiVersion = getStringProperty(properties, "project.api.version", projectApiVersion);

            // Miscellaneous
            continueOnError = getBooleanProperty(properties, "error.continue", continueOnError);

            // Derived properties
            azdoBaseUrl="https://dev.azure.com/" + targetOrganization;
            logger.debug("Derived azdoBaseUrl: {}", azdoBaseUrl);
            uriTargetRepository = azdoBaseUrl + "/" + targetProject + "/_git/" + targetRepositoryName;
            uriTargetRepository = Utils.encodePath(uriTargetRepository);
            logger.debug("Derived uriTargetRepository: {}", uriTargetRepository);
            azdoEndpoint = azdoBaseUrl + "/" + targetProject + "/_apis";
            logger.debug("Derived azdoEndpoint: {}", azdoEndpoint);

            logger.debug("#################################################################");
            logger.debug("End reading properties");
            logger.debug("#################################################################");
            logger.debug("");
        }
        catch (FileNotFoundException e) {
            logger.debug("Property file not found");
        }
        catch (IOException e) {
            logger.debug("IOException");
        }
    }

    private String getStringProperty (Properties properties, String propertyName, String propertyValue) {
        return getStringProperty (properties, propertyName, propertyValue, true);
    }

    private String getStringProperty (Properties properties, String propertyName, String propertyValue, boolean showValueInLog) {
        String p = properties.getProperty(propertyName);
        if (p != null && !p.isEmpty())
            propertyValue = p;
        if (showValueInLog)
            logger.debug("{}: {}", propertyName, propertyValue);
        else
            logger.debug("{}: *****************", propertyName);

        return propertyValue;
    }

    private int getIntProperty (Properties properties, String propertyName, int propertyValue) {
        return getIntProperty (properties, propertyName, propertyValue, true);
    }

    private int getIntProperty (Properties properties, String propertyName, int propertyValue, boolean showValueInLog) {
        String p = properties.getProperty(propertyName);
        if (p != null && !p.isEmpty()) {
            propertyValue = Integer.parseInt(p);
        }
        if (showValueInLog)
            logger.debug("{}: {}", propertyName, propertyValue);
        else
            logger.debug("{}: *****************", propertyName);

        return propertyValue;
    }

    private boolean getBooleanProperty (Properties properties, String propertyName, boolean propertyValue) {
        return getBooleanProperty (properties, propertyName, propertyValue, true);
    }

    private boolean getBooleanProperty (Properties properties, String propertyName, boolean propertyValue, boolean showValueInLog) {
        String p = properties.getProperty(propertyName);
        if (p != null && !p.isEmpty()) {
            propertyValue = Boolean.parseBoolean(p);
        }
        if (showValueInLog)
            logger.debug("{}: {}", propertyName, propertyValue);
        else
            logger.debug("{}: *****************", propertyName);

        return propertyValue;
    }

    public String getSourcePath() { return sourcePath; }
    public String getSourceProject() {
        return sourceProject;
    }
    public String getTargetProject() {
        return targetProject;
    }
    public String getTargetRepositoryName() { return targetRepositoryName; }
    public String getSourceRepositoryName() { return sourceRepositoryName; }
    public String getTargetOrganization() {
        return targetOrganization;
    }
    public String getSourceBasePathExternal() { return sourceBasePathExternal; }
    public String getTargetPath() { return targetPath; }
    public String getTargetBasePathExternal() { return targetBasePathExternal; }
    public String getUriTargetRepository() { return uriTargetRepository; }
    public String getAzdoEndpoint() { return azdoEndpoint; }
    public String getTargetExludeList() { return targetExludeList; }

    // Pipeline API
    public String getAzdoBaseUrl() { return azdoBaseUrl; }
    public String getPipelinesApi() { return pipelinesApi; }
    public String getPipelinesApiRuns() { return pipelinesApiRuns; }
    public String getPipelinesApiVersion() { return pipelinesApiVersion; }

    // Git API
    public String getGitApi() { return gitApi; }
    public String getGitApiRepositories() { return gitApiRepositories; }
    public String getGitApiVersion() { return gitApiVersion; }
    public String getAzDoUser() { return azdoUser; }
    public String getAzdoPat() { return azdoPat; }

    // Build
    public String getBuildApi() { return buildApi; }
    public int getBuildApiPollFrequency() { return buildApiPollFrequency; }
    public int getBuildApiPollTimeout() { return buildApiPollTimeout; }
    public String getBuildApiVersion() { return buildApiVersion; }

    // Project
    public String getProjectApi() { return projectApi;}
    public String getProjectApiVersion() {
        return projectApiVersion;
    }

    // Miscellaneous
    public String getCommitPattern() { return commitPattern; }
    public ArrayList<String> getCommitPatternList() { return commitPatternList; }
    public boolean isContinueOnError() { return continueOnError; }
}

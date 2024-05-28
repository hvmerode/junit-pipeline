// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

/******************************************************************************************
 This class represents the .properties file in the resources directory of this repository.
 *******************************************************************************************/
public class PropertyUtils {
    private static final Log logger = Log.getLogger();

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

    // Azure DevOps API: Distributed tasks
    private String variableGroupsApi = "/distributedtask/variablegroups";
    private String variableGroupsApiVersion = "api-version=7.0";
    private boolean variableGroupsValidate = true;
    private String environmentsApi = "/distributedtask/environments";
    private String environmentsApiVersion = "api-version=7.0";
    private boolean environmentsValidate = true;

    // Miscellaneous
    private String commitPattern;
    ArrayList<String> commitPatternList;
    private String targetRepositoryName;
    private boolean includeExternalTemplates = true;
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

            // Azure DevOps Distributed task APIs
            variableGroupsApi = getStringProperty(properties, "variable.groups.api", variableGroupsApi);
            variableGroupsApiVersion = getStringProperty(properties, "variable.groups.api.version", variableGroupsApiVersion);
            variableGroupsValidate = getBooleanProperty(properties, "variable.groups.validate", variableGroupsValidate);
            environmentsApi = getStringProperty(properties, "environments.api", environmentsApi);
            environmentsApiVersion = getStringProperty(properties, "environments.api.version", environmentsApiVersion);
            environmentsValidate = getBooleanProperty(properties, "environments.validate", environmentsValidate);

            // Miscellaneous
            continueOnError = getBooleanProperty(properties, "error.continue", continueOnError);
            includeExternalTemplates = getBooleanProperty(properties, "templates.external.include", includeExternalTemplates);

            // Derived properties
            azdoBaseUrl="https://dev.azure.com/" + targetOrganization;
            logger.debug("Derived azdoBaseUrl: {}", azdoBaseUrl);
            uriTargetRepository = azdoBaseUrl + "/" + targetProject + "/_git/" + targetRepositoryName;
            uriTargetRepository = Utils.encodePath(uriTargetRepository);
            logger.debug("Derived uriTargetRepository: {}", uriTargetRepository);
            // An Azure project may contain spaces; perform URL encoding because the project name is part of the URL
            azdoEndpoint = azdoBaseUrl + "/" + Utils.encodePath(targetProject) + "/_apis";
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

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }
    public String getSourcePath() { return sourcePath; }

    public void setSourceProject(String sourceProject) {
        this.sourceProject = sourceProject;
    }
    public String getSourceProject() {
        return sourceProject;
    }

    public void setTargetProject(String targetProject) {
        this.targetProject = targetProject;
    }
    public String getTargetProject() {
        return targetProject;
    }

    public void setTargetRepositoryName(String targetRepositoryName) {
        this.targetRepositoryName = targetRepositoryName;
    }
    public String getTargetRepositoryName() { return targetRepositoryName; }

    public void setSourceRepositoryName(String sourceRepositoryName) {
        this.sourceRepositoryName = sourceRepositoryName;
    }
    public String getSourceRepositoryName() { return sourceRepositoryName; }

    public void setTargetOrganization(String targetOrganization) {
        this.targetOrganization = targetOrganization;
    }
    public String getTargetOrganization() {
        return targetOrganization;
    }

    public void setSourceBasePathExternal(String sourceBasePathExternal) {
        this.sourceBasePathExternal = sourceBasePathExternal;
    }
    public String getSourceBasePathExternal() { return sourceBasePathExternal; }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }
    public String getTargetPath() { return targetPath; }

    public void setTargetBasePathExternal(String targetBasePathExternal) {
        this.targetBasePathExternal = targetBasePathExternal;
    }
    public String getTargetBasePathExternal() { return targetBasePathExternal; }

    public void setUriTargetRepository (String uriTargetRepository) {
        this.uriTargetRepository = uriTargetRepository;
    }
    public String getUriTargetRepository() { return uriTargetRepository; }

    public void setAzdoEndpoint(String azdoEndpoint) {
        this.azdoEndpoint = azdoEndpoint;
    }
    public String getAzdoEndpoint() { return azdoEndpoint; }

    public void setTargetExludeList(String targetExludeList) {
        this.targetExludeList = targetExludeList;
    }
    public String getTargetExludeList() { return targetExludeList; }

    // Pipeline API

    public void setAzdoBaseUrl(String azdoBaseUrl) {
        this.azdoBaseUrl = azdoBaseUrl;
    }
    public String getAzdoBaseUrl() { return azdoBaseUrl; }

    public void setPipelinesApi(String pipelinesApi) {
        this.pipelinesApi = pipelinesApi;
    }
    public String getPipelinesApi() { return pipelinesApi; }

    public void setPipelinesApiRuns(String pipelinesApiRuns) {
        this.pipelinesApiRuns = pipelinesApiRuns;
    }
    public String getPipelinesApiRuns() { return pipelinesApiRuns; }

    public void setPipelinesApiVersion(String pipelinesApiVersion) {
        this.pipelinesApiVersion = pipelinesApiVersion;
    }
    public String getPipelinesApiVersion() { return pipelinesApiVersion; }

    // Git API

    public void setGitApi(String gitApi) {
        this.gitApi = gitApi;
    }
    public String getGitApi() { return gitApi; }

    public void setGitApiRepositories(String gitApiRepositories) {
        this.gitApiRepositories = gitApiRepositories;
    }
    public String getGitApiRepositories() { return gitApiRepositories; }

    public void setGitApiVersion(String gitApiVersion) {
        this.gitApiVersion = gitApiVersion;
    }
    public String getGitApiVersion() { return gitApiVersion; }

    public void setAzdoUser(String azdoUser) {
        this.azdoUser = azdoUser;
    }
    public String getAzDoUser() { return azdoUser; }

    public void setAzdoPat(String azdoPat) {
        this.azdoPat = azdoPat;
    }
    public String getAzdoPat() { return azdoPat; }

    // Build
    public void setBuildApi(String buildApi) {
        this.buildApi = buildApi;
    }
    public String getBuildApi() { return buildApi; }

    public void setBuildApiPollFrequency(int buildApiPollFrequency) {
        this.buildApiPollFrequency = buildApiPollFrequency;
    }
    public int getBuildApiPollFrequency() { return buildApiPollFrequency; }

    public void setBuildApiPollTimeout(int buildApiPollTimeout) {
        this.buildApiPollTimeout = buildApiPollTimeout;
    }
    public int getBuildApiPollTimeout() { return buildApiPollTimeout; }

    public void setBuildApiVersion(String buildApiVersion) {
        this.buildApiVersion = buildApiVersion;
    }
    public String getBuildApiVersion() { return buildApiVersion; }

    // Project
    public void setProjectApi(String projectApi) {
        this.projectApi = projectApi;
    }
    public String getProjectApi() { return projectApi;}

    public void setProjectApiVersion(String projectApiVersion) {
        this.projectApiVersion = projectApiVersion;
    }
    public String getProjectApiVersion() {
        return projectApiVersion;
    }


    // Distributed task
    public void setVariableGroupsApi(String variableGroupsApi) {
        this.variableGroupsApi = variableGroupsApi;
    }
    public String getVariableGroupsApi() { return variableGroupsApi;}

    public void setVariableGroupsApiVersion(String variableGroupsApiVersion) {
        this.variableGroupsApiVersion = variableGroupsApiVersion;
    }
    public String getVariableGroupsApiVersion() {
        return variableGroupsApiVersion;
    }

    public void setVariableGroupsValidate(boolean variableGroupsValidate) {
        this.variableGroupsValidate = variableGroupsValidate;
    }
    public boolean isVariableGroupsValidate() {
        return variableGroupsValidate;
    }

    public void setEnvironmentsApi(String environmentsApi) {
        this.environmentsApi = environmentsApi;
    }
    public String getEnvironmentsApi () { return environmentsApi;}

    public void setEnvironmentsApiVersion (String environmentsApiVersion) {
        this.environmentsApiVersion = environmentsApiVersion;
    }
    public String getEnvironmentsApiVersion () {
        return environmentsApiVersion;
    }

    public void setEnvironmentsValidate(boolean environmentsValidate) {
        this.environmentsValidate = environmentsValidate;
    }
    public boolean isEnvironmentsValidate() {
        return environmentsValidate;
    }


    // Miscellaneous
    public void setCommitPattern (String commitPattern) {
        this.commitPattern = commitPattern;
    }
    public String getCommitPattern() { return commitPattern; }


    public void setCommitPatternList (ArrayList<String> commitPatternList) {
        this.commitPatternList = commitPatternList;
    }
    public ArrayList<String> getCommitPatternList() { return commitPatternList; }

    public void setIncludeExternalTemplates (boolean includeExternalTemplates) {
        this.includeExternalTemplates = includeExternalTemplates;
    }
    public boolean isIncludeExternalTemplates () {
        return includeExternalTemplates;
    }

    public void setContinueOnError (boolean continueOnError) {
        this.continueOnError = continueOnError;
    }
    public boolean isContinueOnError () { return continueOnError; }
}

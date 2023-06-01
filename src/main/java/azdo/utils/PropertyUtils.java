// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

public class PropertyUtils {
    private static Logger logger = LoggerFactory.getLogger(PropertyUtils.class);

    // Source
    private Properties properties;
    private String sourcePath;
    private String sourceBasePathExternal;

    // Target
    private String targetPath;
    private String targetBasePathExternal;
    private String targetOrganization;
    private String targetProject;
    private String pipelinePathRepository;
    private String uriTargetRepository;
    private String azdoUser;
    private String azdoPat;
    private String targetExludeList;

    // Pipeline
    private String azdoBaseUrl;
    private String azdoEndpoint;
    private String pipelinesApi;
    private String pipelinesApiRuns;
    private String pipelinesApiVersion;

    // Azure DevOps API: Git
    private String gitApi;
    private String gitApiRepositories;
    private String gitApiVersion;

    // Azure DevOps API: Build
    private String buildApi;
    private int buildApiPollFrequency;
    private int buildApiPollTimeout;
    private String buildApiVersion;

    // Azure DevOps API: Project
    private String projectApi;
    private String projectApiVersion;

    // Miscellanious
    private String commitPattern;
    ArrayList<String> commitPatternList;
    private String repositoryName;

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
            sourcePath = properties.getProperty("source.path");
            logger.debug("source.path: {}", sourcePath);
            sourceBasePathExternal = properties.getProperty("source.base.path.external");
            logger.debug("source.base.path.external: {}", sourceBasePathExternal);

            // Target
            targetOrganization = properties.getProperty("target.organization");
            logger.debug("target.organization: {}", targetOrganization);
            targetProject = properties.getProperty("target.project");
            logger.debug("target.project: {}", targetProject);
            targetPath = properties.getProperty("target.path");
            logger.debug("target.path: {}", targetPath);
            targetBasePathExternal = properties.getProperty("target.base.path.external");
            logger.debug("target.base.path.external: {}", targetBasePathExternal);
            repositoryName = properties.getProperty("target.repository.name");
            logger.debug("target.repository.name: {}", repositoryName);
            pipelinePathRepository = properties.getProperty("repository.pipeline.path");
            logger.debug("repository.pipeline.path: {}", pipelinePathRepository);
            azdoUser = properties.getProperty("azdo.user");
            logger.debug("azdo.user: {}", azdoUser);
            azdoPat = properties.getProperty("azdo.pat");
            logger.debug("azdo.pat: {}", azdoPat);
            targetExludeList = properties.getProperty("target.excludelist");
            logger.debug("target.excludelist: {}", targetExludeList);

            // Run trough the commit pattern and create a List
            commitPattern = properties.getProperty("git.commit.pattern");
            commitPatternList = new ArrayList<>();
            var values = commitPattern.split(",");
            for (int i = 0; i < values.length; i++)
            {
                commitPatternList.add(values[i]);
            }
            logger.debug("git.commit.pattern: {}", commitPatternList);

            // Azure DevOps Pipeline API
            pipelinesApi = properties.getProperty("pipelines.api");
            logger.debug("pipelines.api: {}", pipelinesApi);
            pipelinesApiRuns = properties.getProperty("pipelines.api.runs");
            logger.debug("pipelines.api.runs: {}", pipelinesApiRuns);
            pipelinesApiVersion = properties.getProperty("pipelines.api.version");
            logger.debug("pipelines.api.version: {}", pipelinesApiVersion);

            // Azure DevOps Git API
            gitApi = properties.getProperty("git.api");
            logger.debug("git.api: {}", gitApi);
            gitApiRepositories = properties.getProperty("git.api.repositories");
            logger.debug("git.api.repositories: {}", gitApiRepositories);
            gitApiVersion = properties.getProperty("git.api.version");
            logger.debug("git.api.version: {}", gitApiVersion);

            // Azure DevOps Build API
            buildApi = properties.getProperty("build.api");
            logger.debug("build.api: {}", buildApi);
            buildApiPollFrequency = Integer.parseInt(properties.getProperty("build.api.poll.frequency"));
            logger.debug("build.api.poll.frequency: {}", buildApiPollFrequency);
            buildApiPollTimeout = Integer.parseInt(properties.getProperty("build.api.poll.timeout"));
            logger.debug("build.api.poll.timeout: {}", buildApiPollTimeout);
            buildApiVersion = properties.getProperty("build.api.version");
            logger.debug("build.api.version: {}", buildApiVersion);

            // Azure DevOps Project API
            projectApi = properties.getProperty("project.api");
            logger.debug("project.api: {}", projectApi);
            projectApiVersion = properties.getProperty("project.api.version");
            logger.debug("project.api.version: {}", projectApiVersion);

            // Derived properties
            azdoBaseUrl="https://dev.azure.com/" + targetOrganization;
            logger.debug("Derived azdoBaseUrl: {}", azdoBaseUrl);
            uriTargetRepository = azdoBaseUrl + "/" + targetProject + "/_git/" + repositoryName;
            logger.debug("Derived uriTargetRepository: {}", uriTargetRepository);
            azdoEndpoint = azdoBaseUrl + "/" + targetProject + "/_apis";
            logger.debug("Derived azdoEndpoint: {}", azdoEndpoint);

            logger.debug("#################################################################");
            logger.debug("End reading properties");
            logger.debug("#################################################################");
            logger.debug("");
        }
        catch (FileNotFoundException e) {
            logger.debug("File not found");
        }
        catch (IOException e) {
            logger.debug("IOException");
        }
    }

    public String getSourcePath() { return sourcePath; }
    public String getTargetProject() {
        return targetProject;
    }
    public String getTargetOrganization() {
        return targetOrganization;
    }
    public String getSourceBasePathExternal() { return sourceBasePathExternal; }
    public String getTargetPath() { return targetPath; }
    public String getTargetBasePathExternal() { return targetBasePathExternal; }
    public String getPipelinePathRepository() { return pipelinePathRepository; }
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

    // Miscellanious
    public String getCommitPattern() { return commitPattern; }
    public ArrayList<String> getCommitPatternList() { return commitPatternList; }
    public String getRepositoryName() { return repositoryName; }
}

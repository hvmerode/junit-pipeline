// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;

public class TestProperties {
    private static Logger logger = LoggerFactory.getLogger(TestProperties.class);

    // Source
    private Properties properties;
    private String sourcePath;

    // Target
    private String targetPath;
    private String targetOrganization;
    private String targetProject;
    private String pipelinePathRepository;
    private String uriTargetRepository;
    private String userTargetRepository;
    private String passwordTargetRepository;

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

    public TestProperties(String propertyFile) {
        try {
            logger.info("PropertyFile: " + propertyFile);
            properties = new Properties();
            InputStream is = getClass().getClassLoader().getResourceAsStream(propertyFile);
            properties.load(is);

            logger.info("");
            logger.info("#################################################################");
            logger.info("Start reading properties");
            logger.info("#################################################################");

            // Source
            sourcePath = properties.getProperty("source.path");
            logger.info("==> source.path: " + sourcePath);

            // Target
            targetOrganization = properties.getProperty("target.organization");
            logger.info("==> target.organization: " + targetOrganization);
            targetProject = properties.getProperty("target.project");
            logger.info("==> target.project: " + targetProject);
            targetPath = properties.getProperty("target.path");
            logger.info("==> target.path: " + targetPath);
            repositoryName = properties.getProperty("target.repository.name");
            logger.info("==> target.repository.name: " + repositoryName);
            pipelinePathRepository = properties.getProperty("repository.pipeline.path");
            logger.info("==> repository.pipeline.path: " + pipelinePathRepository);
            userTargetRepository = properties.getProperty("target.repository.user");
            logger.info("==> target.repository.user: " + userTargetRepository);
            passwordTargetRepository = properties.getProperty("target.repository.password");
            logger.info("==> target.repository.password: " + passwordTargetRepository);

            // Run trough the commit pattern and create a List
            commitPattern = properties.getProperty("git.commit.pattern");
            commitPatternList = new ArrayList<>();
            var values = commitPattern.split(",");
            for (int i = 0; i < values.length; i++)
            {
                commitPatternList.add(values[i]);
            }
            logger.info("==> git.commit.pattern: " + commitPatternList);

            // Azure DevOps Pipeline API
            pipelinesApi = properties.getProperty("pipelines.api");
            logger.info("==> pipelines.api: " + pipelinesApi);
            pipelinesApiRuns = properties.getProperty("pipelines.api.runs");
            logger.info("==> pipelines.api.runs: " + pipelinesApiRuns);
            pipelinesApiVersion = properties.getProperty("pipelines.api.version");
            logger.info("==> pipelines.api.version: " + pipelinesApiVersion);

            // Azure DevOps Git API
            gitApi = properties.getProperty("git.api");
            logger.info("==> git.api: " + gitApi);
            gitApiRepositories = properties.getProperty("git.api.repositories");
            logger.info("==> git.api.repositories: " + gitApiRepositories);
            gitApiVersion = properties.getProperty("git.api.version");
            logger.info("==> git.api.version: " + gitApiVersion);

            // Azure DevOps Build API
            buildApi = properties.getProperty("build.api");
            logger.info("==> build.api: " + buildApi);
            buildApiPollFrequency = Integer.parseInt(properties.getProperty("build.api.poll.frequency"));
            logger.info("==> build.api.poll.frequency: " + buildApiPollFrequency);
            buildApiPollTimeout = Integer.parseInt(properties.getProperty("build.api.poll.timeout"));
            logger.info("==> build.api.poll.timeout: " + buildApiPollTimeout);
            buildApiVersion = properties.getProperty("build.api.version");
            logger.info("==> build.api.version: " + buildApiVersion);

            // Azure DevOps Project API
            projectApi = properties.getProperty("project.api");
            logger.info("==> project.api: " + projectApi);
            projectApiVersion = properties.getProperty("project.api.version");
            logger.info("==> project.api.version: " + projectApiVersion);

            // Derived properties
            azdoBaseUrl="https://dev.azure.com/" + targetOrganization;
            logger.info("==> Derived azdoBaseUrl: " + azdoBaseUrl);
            uriTargetRepository = azdoBaseUrl + "/" + targetProject + "/_git/" + repositoryName;
            logger.info("==> Derived uriTargetRepository: " + uriTargetRepository);
            azdoEndpoint = azdoBaseUrl + "/" + targetProject + "/_apis";
            logger.info("==> Derived azdoEndpoint: " + azdoEndpoint);

                    logger.info("#################################################################");
            logger.info("End reading properties");
            logger.info("#################################################################");
            logger.info("");
        }
        catch (FileNotFoundException e) {
            logger.info("==> File not found");
        }
        catch (IOException e) {
            logger.info("==> IOException");
        }
    }

    public String getSourcePath() { return sourcePath; }
    public String getTargetProject() {
        return targetProject;
    }
    public String getTargetPath() { return targetPath; }
    public String getPipelinePathRepository() { return pipelinePathRepository; }
    public String getUriTargetRepository() { return uriTargetRepository; }
    public String getAzdoEndpoint() { return azdoEndpoint; }

    // Pipeline API
    public String getAzdoBaseUrl() { return azdoBaseUrl; }
    public String getPipelinesApi() { return pipelinesApi; }
    public String getPipelinesApiRuns() { return pipelinesApiRuns; }
    public String getPipelinesApiVersion() { return pipelinesApiVersion; }

    // Git API
    public String getGitApi() { return gitApi; }
    public String getGitApiRepositories() { return gitApiRepositories; }
    public String getGitApiVersion() { return gitApiVersion; }
    public String getUserTargetRepository() { return userTargetRepository; }
    public String getPasswordTargetRepository() { return passwordTargetRepository; }

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

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
    private String pipelinePathRepository;
    private String uriTargetRepository;
    private String branchTargetRepository;
    private String userTargetRepository;
    private String passwordTargetRepository;

    // Pipeline
    private String pipelineYamlName;
    private String azdoEndpoint;
    private String pipelinesApi;
    private String pipelinesApiRuns;
    private String pipelinesApiVersion;
    private String gitApi;
    private String gitApiRepositories;
    private String gitApiVersion;

    // Build
    private String buildApi;
    private int buildApiPollFrequency;
    private int buildApiPollTimeout;
    private String buildApiVersion;

    // Miscellanious
    private String commitPattern;
    ArrayList<String> commitPatternList;
    private String repositoryName;
    private String projectId;

    public TestProperties() {
        try {
            Properties properties = new Properties();
            InputStream is = getClass().getClassLoader().getResourceAsStream("unittest.properties");
            properties.load(is);

            logger.info("");
            logger.info("#################################################################");
            logger.info("Start reading properties");
            logger.info("#################################################################");

            // Source
            sourcePath = properties.getProperty("source.path");
            logger.info("==> source.path: " + sourcePath);

            // Target
            targetPath = properties.getProperty("target.path");
            logger.info("==> target.path: " + targetPath);
            repositoryName = properties.getProperty("target.repository.name");
            logger.info("==> target.repository.name: " + repositoryName);
            pipelinePathRepository = properties.getProperty("repository.pipeline.path");
            logger.info("==> repository.pipeline.path: " + pipelinePathRepository);
            uriTargetRepository = properties.getProperty("target.repository.uri") + "/" + repositoryName;
            logger.info("==> target.repository.uri: " + uriTargetRepository);
            branchTargetRepository = properties.getProperty("target.repository.branch");
            logger.info("==> target.repository.branch: " + branchTargetRepository);
            userTargetRepository = properties.getProperty("target.repository.user");
            logger.info("==> target.repository.user: " + userTargetRepository);
            passwordTargetRepository = properties.getProperty("target.repository.password");
            logger.info("==> target.repository.password: " + passwordTargetRepository);

            // Run trough the commit pattern and create a List
            commitPattern = properties.getProperty("git.commit.pattern");
            commitPatternList = new ArrayList<String>();
            var values = commitPattern.split(",");
            for (int i = 0; i < values.length; i++)
            {
                commitPatternList.add(values[i]);
            }
            logger.info("==> git.commit.pattern: " + commitPatternList);
            projectId = properties.getProperty("project.id");
            logger.info("==> project.id: " + projectId);

            // Pipeline
            azdoEndpoint = properties.getProperty("endpoint");
            logger.info("==> endpoint: " + azdoEndpoint);

            // Azure DevOps Pipeline API
            pipelineYamlName = properties.getProperty("pipeline.yaml");
            logger.info("==> pipeline.yaml: " + pipelineYamlName);
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

    public String getSourcePath() {
        return sourcePath;
    }

    public void setSourcePath(String sourcePath) {
        this.sourcePath = sourcePath;
    }

    public String getTargetPath() {
        return targetPath;
    }

    public void setTargetPath(String targetPath) {
        this.targetPath = targetPath;
    }

    public String getPipelinePathRepository() {
        return pipelinePathRepository;
    }

    public void setPipelinePathRepository(String pipelinePathRepository) { this.pipelinePathRepository = pipelinePathRepository;
    }

    public String getPipelineYamlName() {
        return pipelineYamlName;
    }

    public void setPipelineYamlName(String pipelineYamlName) {
        this.pipelineYamlName = pipelineYamlName;
    }

    public String getUriTargetRepository() {
        return uriTargetRepository;
    }

    public void setUriTargetRepository(String uriTargetRepository) {
        this.uriTargetRepository = uriTargetRepository;
    }

    public String getAzdoEndpoint() {
        return azdoEndpoint;
    }

    public void setAzdoEndpoint(String azdoEndpoint) {
        this.azdoEndpoint = azdoEndpoint;
    }

    // Pipeline API
    public String getPipelinesApi() {
        return pipelinesApi;
    }

    public void setPipelinesApi(String pipelinesApi) {this.pipelinesApi = pipelinesApi;
    }

    public String getPipelinesApiRuns() {
        return pipelinesApiRuns;
    }

    public void setPipelinesApiRuns(String pipelinesApiRuns) {this.pipelinesApiRuns = pipelinesApiRuns;
    }

    public String getPipelinesApiVersion() {
        return pipelinesApiVersion;
    }

    public void setPipelinesApiVersion(String pipelinesApiVersion) {this.pipelinesApiVersion = pipelinesApiVersion;
    }

    // Git API
    public String getGitApi() {
        return gitApi;
    }

    public void setGitApi(String gitApi) {this.gitApi = gitApi;
    }

    public String getGitApiRepositories() {
        return gitApiRepositories;
    }

    public void setGitApiRepositories(String gitApiRepositories) {this.gitApiRepositories = gitApiRepositories;
    }

    public String getGitApiVersion() {
        return gitApiVersion;
    }

    public void setGitApiVersion(String gitApiVersion) {this.gitApiVersion = gitApiVersion;
    }

    public String getBranchTargetRepository() {
        return branchTargetRepository;
    }

    public void setBranchTargetRepository(String branchTargetRepository) {
        this.branchTargetRepository = branchTargetRepository;
    }

    public String getUserTargetRepository() {
        return userTargetRepository;
    }

    public void setUserTargetRepository(String userTargetRepository) {
        this.userTargetRepository = userTargetRepository;
    }

    public String getPasswordTargetRepository() {
        return passwordTargetRepository;
    }

    public void setPasswordTargetRepository(String passwordTargetRepository) {
        this.passwordTargetRepository = passwordTargetRepository;
    }

    // Build
    public String getBuildApi() {
        return buildApi;
    }

    public void setBuildApi(String buildApi) {
        this.buildApi = buildApi;
    }

    public int getBuildApiPollFrequency() {
        return buildApiPollFrequency;
    }

    public void setBuildApiPollFrequency(int buildApiPollFrequency) {
        this.buildApiPollFrequency = buildApiPollFrequency;
    }

    public int getBuildApiPollTimeout() {
        return buildApiPollTimeout;
    }

    public void setBuildApiPollTimeout(int buildApiPollTimeout) {
        this.buildApiPollTimeout = buildApiPollTimeout;
    }

    public String getBuildApiVersion() {
        return buildApiVersion;
    }

    public void setBuildApiVersion(String buildApiVersion) {
        this.buildApiVersion = buildApiVersion;
    }


    // Miscellanious
    public String getCommitPattern() {
        return commitPattern;
    }

    public void setCommitPattern(String commitPattern) {
        this.commitPattern = commitPattern;
    }

    public ArrayList<String> getCommitPatternList() {
        return commitPatternList;
    }

    public void setCommitPatternList(ArrayList<String> commitPatternList) {
        this.commitPatternList = commitPatternList;
    }

    public String getRepositoryName() {
        return repositoryName;
    }

    public void setRepositoryName(String repositoryName) {
        this.repositoryName = repositoryName;
    }

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }
}

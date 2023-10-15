package azdo.utils;

import azdo.junit.RunResult;
import azdo.junit.TimelineRecord;
import org.eclipse.jgit.api.Git;
import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static azdo.utils.Constants.*;

public class AzDoUtils {
    private static final Log logger = Log.getLogger();
    private static final String BRACKET_OPEN_NEXTLINE = "{\n";
    private static final String BRACKET_CLOSE = "}";
    private static final String DOUBLE_QUOTE = "\"";
    private static final String DQUOTE_SCOL_DQUOTE = "\": \"";
    private static final String NEXTLINE = "\n";
    private static final String COMMA_NEXTLINE = ",\n";
    private static final String TAB = "\t";
    private static final String TWO_TAB = "\t\t";
    private static final String THREE_TAB = "\t\t\t";
    private static final String APPLICATION_JSON = "application/json";
    private static final String RESPONSE_IS = "Response is: {}";
    private static final String REPOSITORY_ID_IS = "Repository id is: {}";
    private static final String JSON_ELEMENT_VALUE = "value";
    private static final String JSON_ELEMENT_RECORDS = "records";
    private static final String JSON_ELEMENT_NAME = "name";
    private static final String JSON_ELEMENT_ID = "id";
    private enum HttpMethod {GET, PUT, POST, PATCH}
    private static boolean test = false;


    /******************************************************************************************
     Perform an Azure DevOps API call. This is a generic method to call an Azure DeVOps API.
     The endpoint, HTTP method and body (json) must be provided.
     *******************************************************************************************/
    public static HttpResponse<String> callApi (String azdoUser,
                                                String azdoPat,
                                                String http,
                                                AzDoUtils.HttpMethod httpMethod,
                                                String json) {
        if (test)
            return null;

        try {
            logger.debug("==> Method: AzDoUtils.callApi");
            logger.debug("http: {}", http);
            logger.debug("httpMethod: {}", httpMethod);
            logger.debug("json: {}", json);

            String encodedString = Base64.getEncoder().encodeToString((azdoUser + ":" + azdoPat).getBytes());
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request;
            if (httpMethod == AzDoUtils.HttpMethod.GET) {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(http))
                        .setHeader("Content-Type", APPLICATION_JSON)
                        .setHeader("Accept", APPLICATION_JSON)
                        .setHeader("Authorization", "Basic " + encodedString)
                        .GET()
                        .build();
            } else {
                request = HttpRequest.newBuilder()
                        .uri(URI.create(http))
                        .setHeader("Content-Type", APPLICATION_JSON)
                        .setHeader("Accept", APPLICATION_JSON)
                        .setHeader("Authorization", "Basic " + encodedString)
                        .method(httpMethod.toString(), HttpRequest.BodyPublishers.ofString(json))
                        .build();
            }

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response == null) {
                logger.debug("Response is null");
            }
            else {
                logger.debug("AzDo API response: {}", response);

                // check whether the HTTP status code is valid
                if (response.statusCode() > 299) {
                    logger.debug("Statuscode > 299");
                }
            }

            return response;
        }

        catch (InterruptedException e) {
            logger.debug("Interrupted! {}", e);
            Thread.currentThread().interrupt();
        }
        catch (Exception e) {
            logger.debug("Exception: {}", e.getLocalizedMessage());
        }

        return null;
    }

    /******************************************************************************************
     Create a new repo in Azure DevOps if it does not exist yet.
     *******************************************************************************************/
    public static String createRepositoryIfNotExists (String azdoUser,
                                                      String azdoPat,
                                                      String targetPath,
                                                      String repositoryName,
                                                      String organization,
                                                      String project,
                                                      String azdoBaseUrl,
                                                      String azdoEndpoint,
                                                      String azdoGitApi,
                                                      String azdoGitApiVersion,
                                                      String azdoProjectApi,
                                                      String azdoProjectApiVersion,
                                                      String azdoGitApiRepositories) {
        logger.debug("==> Method: AzDoUtils.createRepositoryIfNotExists");
        logger.debug("targetPath: {}", targetPath);
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("organization: {}", organization);
        logger.debug("project: {}", project);
        logger.debug("azdoBaseUrl: {}", azdoBaseUrl);
        logger.debug("azdoEndpoint: {}", azdoEndpoint);
        logger.debug("azdoGitApi: {}", azdoGitApi);
        logger.debug("azdoGitApiVersion: {}", azdoGitApiVersion);
        logger.debug("azdoProjectApi: {}", azdoProjectApi);
        logger.debug("azdoProjectApiVersion: {}", azdoProjectApiVersion);
        logger.debug("azdoGitApiRepositories: {}", azdoGitApiRepositories);

        String repositoryId = "";
        try {
            // Get the repositoryId of the existing repository
            repositoryId = callGetRepositoryApi(azdoUser,
                    azdoPat,
                    repositoryName,
                    azdoEndpoint,
                    azdoGitApi,
                    azdoGitApiVersion,
                    azdoGitApiRepositories);
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Repository probable does exist; continue");
        }

        try {
            // Create a new repository if not existing
            if (repositoryId == null) {
                logger.debug("Repository {} does not exist; create it", repositoryName);

                // Retrieve the project-id of the Azure DevOps project with a given name
                String projectId = callGetProjectIdApi(azdoUser,
                        azdoPat,
                        project,
                        azdoBaseUrl,
                        azdoProjectApi,
                        azdoProjectApiVersion);

                // Create remote repo using the AzDo API (this may fail if exists, but just continue)
                repositoryId = callCreateRepoApi(azdoUser,
                        azdoPat,
                        azdoEndpoint,
                        azdoGitApi,
                        azdoGitApiVersion,
                        azdoGitApiRepositories,
                        repositoryName,
                        projectId);

                // Initialize main branch with a README
                initializeMaster (azdoUser, azdoPat, targetPath, repositoryName, organization, project);
            }

            // Always clone
            GitUtils.cloneAzdoToLocal(targetPath,
                    repositoryName,
                    azdoUser,
                    azdoPat,
                    organization,
                    project);
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot create a new repository: {}", e);
        }

        return repositoryId;
    }

    /******************************************************************************************
     Initialize the master branch.
     *******************************************************************************************/
    private static void initializeMaster (String azdoUser,
                                          String azdoPat,
                                          String targetPath,
                                          String repositoryName,
                                          String organization,
                                          String project) {
        logger.debug("Initialize main branch with a README");
        Utils.createDirectory(targetPath);
        GitUtils.cloneAzdoToLocal(targetPath,
                repositoryName,
                azdoUser,
                azdoPat,
                organization,
                project);
        Path newFilePath = Paths.get(targetPath + "/readme.md");
        newFilePath = newFilePath.normalize();
        try {
            Files.createFile(newFilePath);
        }
        catch (IOException e) {
            logger.debug("Cannot create a readme.md file");
        }
        Git git = GitUtils.createGit(targetPath);
        GitUtils.checkout(git, targetPath, "master", true);
        ArrayList<String> commitPatternList = new ArrayList<>();
        commitPatternList.add(".md");
        GitUtils.commitAndPush(git,
                azdoUser,
                azdoPat,
                commitPatternList,
                null,
                true);
        if (git != null)
            git.close();
    }

    /******************************************************************************************
     Create a new pipeline in Azure DevOps if it does not exist yet.
     *******************************************************************************************/
    public static String createPipelineIfNotExists (String azdoUser,
                                                    String azdoPat,
                                                    String pipelinePath,
                                                    String pipelineName,
                                                    String repositoryName,
                                                    String azdoEndpoint,
                                                    String azdoPipelinesApi,
                                                    String azdoPipelinesApiVersion,
                                                    String repositoryId) {
        logger.debug("==> Method: AzDoUtils.createPipelineIfNotExists");
        logger.debug("pipelinePath: {}", pipelinePath);
        logger.debug("pipelineName: {}", pipelineName);
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("azdoEndpoint: {}", azdoEndpoint);
        logger.debug("azdoPipelinesApi: {}", azdoPipelinesApi);
        logger.debug("azdoPipelinesApiVersion: {}", azdoPipelinesApiVersion);
        logger.debug("repositoryId: {}", repositoryId);

        String pipelineId = "";
        try {
            // Get the pipelineId of an existing pipeline
            pipelineId = AzDoUtils.callGetPipelineApi (azdoUser,
                    azdoPat,
                    pipelineName,
                    azdoEndpoint,
                    azdoPipelinesApi,
                    azdoPipelinesApiVersion);
        }
        catch (Exception e) {
            logger.debug("Exception occurred; continue");
        }

        try {
            // Create a new pipeline if not existing
            if (pipelineId == null) {
                logger.debug("The pipeline does not exist; create a new one with name \'{}\'", pipelineName);

                // Create a pipeline
                pipelineId = AzDoUtils.callCreatePipelineApi (azdoUser,
                        azdoPat,
                        pipelinePath,
                        pipelineName,
                        repositoryName,
                        azdoEndpoint,
                        azdoPipelinesApi,
                        azdoPipelinesApiVersion,
                        repositoryId);
            }
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot create a new pipeline: {}", e);
        }

        return pipelineId;
    }

    /******************************************************************************************
     Return the project-id of a project in a specific Azure DevOps organization.
     *******************************************************************************************/
    public static String callGetProjectIdApi (String azdoUser,
                                              String azdoPat,
                                              String project,
                                              String azdoBaseUrl,
                                              String azdoProjectApi,
                                              String azdoProjectApiVersion) {
        logger.debug("==> Method: AzDoUtils.callGetProjectIdApi");

        String projectId = null;
        String http = azdoBaseUrl +
                "/_apis/" +
                azdoProjectApi +
                "?" +
                azdoProjectApiVersion;

        HttpResponse<String> response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.GET, null);

        // Get the project id from the response
        Yaml yaml = new Yaml();
        String name = null;
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS, yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                projectId = iterateYamlArrayListAndFindElement (arr, JSON_ELEMENT_NAME, project, JSON_ELEMENT_ID);
            }
            logger.debug("Project id is: {}", projectId);
        }

        return projectId;
    }

    /******************************************************************************************
     Run a pipeline.
     *******************************************************************************************/
    public static void callPipelineRunApi (String azdoUser,
                                           String azdoPat,
                                           String azdoEndpoint,
                                           String azdoBuildApi,
                                           String azdoBuildApiVersion,
                                           String pipelineId,
                                           String branchName,
                                           boolean continueOnError) {
        logger.debug("==> Method: AzDoUtils.callPipelineRunApi");
        logger.debug("pipelineId: {}", pipelineId);
        logger.debug("branchName: {}", branchName);

        if (pipelineId == null)
        {
            logger.debug("Nothing to run; the pipelineId is null");
        } else {
            String sourceBranch = branchName;
            if (branchName == null || branchName.equals("main") || branchName.equals(GitUtils.BRANCH_MASTER) || branchName.equals(""))
                sourceBranch = GitUtils.BRANCH_MASTER;

            String http = azdoEndpoint +
                    azdoBuildApi +
                    "?" +
                    azdoBuildApiVersion;

            String json = BRACKET_OPEN_NEXTLINE +
                    TAB + DOUBLE_QUOTE + "definition" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                    TWO_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DOUBLE_QUOTE + ": " + pipelineId + BRACKET_CLOSE + COMMA_NEXTLINE +
                    TAB + DOUBLE_QUOTE + "sourceBranch" + DOUBLE_QUOTE + ": " + DOUBLE_QUOTE + sourceBranch + DOUBLE_QUOTE + NEXTLINE +
                    BRACKET_CLOSE;

            HttpResponse<String> response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.POST, json);
            if (response != null) {
                logger.debug(RESPONSE_IS, response);
                if (response.statusCode() > 299) {
                    // Make the error explicit, because otherwise it is unclear why the pipeline did not run
                    logger.error("Error while trying to run the pipeline. This can be caused by various issues:");
                    logger.error("- One of the output yaml files contains a syntax error");
                    logger.error("- A reference to a non-existing template, or service connection is used");
                    logger.error("- A pipeline decorator enforces a specific precondition");
                    if (continueOnError) return; else System. exit(1);
                }
            }
        }
    }

    /******************************************************************************************
     Wait until the build is finished and return the result of the pipeline run.
     *******************************************************************************************/
    public static RunResult callRunResult (String azdoUser,
                                           String azdoPat,
                                           int pollFrequency,
                                           int timeout,
                                           String azdoEndpoint,
                                           String azdoBuildApi,
                                           String azdoBuildApiVersion,
                                           String pipelineId,
                                           boolean continueOnError) {
        logger.debug("==> Method: AzDoUtils.callRunResult");
        logger.debug("pollFrequency: {}", pollFrequency);
        logger.debug("timeout: {}", timeout);
        logger.debug("pipelineId: {}", pipelineId);

        RunResult runResult = new RunResult();
        Instant start = Instant.now();
        HttpResponse<String> response;
        Yaml yaml;
        String status = null;
        String result = null;
        String buildNumber = null;
        String id = null;
        String webUrl = null;
        boolean firstPoll = true;

        long timeElapsed = 0;
        String http = azdoEndpoint +
                azdoBuildApi +
                "?definitions=" +
                pipelineId +
                "&maxBuildsPerDefinition=1&queryOrder=queueTimeDescending" +
                "&" +
                azdoBuildApiVersion;

        String json = "{}";

        /******************************************************************************************
         1. Poll the API until the status is completed or timed out.
            Polling is determined using a certain frequency.
         *******************************************************************************************/
        boolean runEnds = false;
        while (!runEnds) {

            // Call the API
            logger.debug("Call the API");
            response = callApi(azdoUser, azdoPat, http, HttpMethod.GET, json);

            // Get the result from the response
            yaml = new Yaml();
            if (response != null) {
                Map<String, Object> yamlMap = yaml.load(response.body().toString());
                if (yamlMap == null) {
                    logger.error("Retrieving the pipeline result failed");
                    if (continueOnError) return null; else System. exit(1);
                }

                logger.debug(RESPONSE_IS, yamlMap.toString());
                if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                    ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                    if (arr != null) {
                        int size = arr.size();

                        // Go through list of values (should be only 1)
                        for (int counter = 0; counter < size; counter++) {
                            LinkedHashMap<String, Object> value = ((LinkedHashMap<String, Object>) arr.get(counter));
                            if (value.get("status") != null)
                                status = value.get("status").toString();
                            if (value.get("result") != null)
                                result = value.get("result").toString();
                            if (value.get("buildNumber") != null)
                                buildNumber = value.get("buildNumber").toString();
                            if (value.get("id") != null)
                                id = value.get("id").toString();
                            if (value.get("_links") != null) {
                                LinkedHashMap<String, Object> links = ((LinkedHashMap<String, Object>) value.get("_links"));
                                if (links.get("web") != null) {
                                    LinkedHashMap<String, Object> web = ((LinkedHashMap<String, Object>) links.get("web"));
                                    if (web.get("href") != null)
                                        webUrl = web.get("href").toString();
                                }
                            }
                        }
                    }
                }
            }

            runResult = new RunResult(result, status, id);

            // Wait until the next poll is allowed
            Utils.wait(pollFrequency * 1000);
            Instant finish = Instant.now();
            timeElapsed = Duration.between(start, finish).toSeconds();
            logger.debug("Time elapsed: {}", Long.toString(timeElapsed));

            if (runResult.result != RunResult.Result.none) {
                runEnds = true;
            } else if (timeElapsed > (long) timeout) {
                runResult.result = RunResult.Result.undetermined;
                runResult.status = RunResult.Status.timeout;
                runEnds = true;
            }
            String pipelineResult = runResult.result.toString();
            logger.info(DEMARCATION);
            if (firstPoll) {
                logger.info("Buildnumber: {}", buildNumber);
                logger.info("Pipeline url: {}", webUrl);
                logger.info("BuildId: {}", runResult.buildId);
            }
            if (runResult.status == RunResult.Status.timeout) {
                logger.warn("Status: {}", runResult.status.toString());
                logger.warn("Timeout on retrieval of the run results; check whether an approval is needed");
                logger.warn("A timeout also occurs if the pipeline is queued for a long time");
            }
            else
                logger.info("Status: {}", runResult.status.toString());

            String color = LIGHT_GREEN;
            if (RunResult.Result.failed.toString().equals(pipelineResult))
                color = LIGHT_RED;
            if (RunResult.Result.canceled.toString().equals(pipelineResult))
                color = YELLOW;
            if (RunResult.Result.partiallySucceeded.toString().equals(pipelineResult))
                color = YELLOW;

            logger.infoColor(color, "Result: {}", runResult.result.toString());

            firstPoll = false;
        }

        /******************************************************************************************
         2. Retrieve the details of the build using the timeline.
         *******************************************************************************************/
        http = azdoEndpoint +
                azdoBuildApi +
                "/" +
                id +
                "/timeline" +
                "?" +
                azdoBuildApiVersion;

        response = callApi(azdoUser, azdoPat, http, HttpMethod.GET, null);

        // Get the build timeline from the response
        if (response != null) {
            yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            if (yamlMap != null) {
                logger.debug(RESPONSE_IS, yamlMap.toString());

                // Parse the json response and add the result to runResult
                if (yamlMap.get(JSON_ELEMENT_RECORDS) instanceof ArrayList) {
                    int sizeRecords = 0;
                    ArrayList<Object> records = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_RECORDS);
                    if (records != null) {
                        sizeRecords = records.size();

                        // Go through list of records
                        Map<String, Object> map;
                        TimelineRecord timelineRecord;
                        String key;
                        String value;
                        for (int recordCounter = 0; recordCounter < sizeRecords; recordCounter++) {
                            map = (Map<String, Object>) records.get(recordCounter);
                            timelineRecord = new TimelineRecord();
                            for (Map.Entry<String, Object> entry : map.entrySet()) {
                                if (entry.getKey() != null)
                                    key = entry.getKey().toString();
                                else key = "";
                                if (entry.getValue() != null)
                                    value = entry.getValue().toString();
                                else value = "";

                                if ("id".equals(key))
                                    timelineRecord.id = value;
                                if ("parentId".equals(key))
                                    timelineRecord.parentId = value;
                                if ("type".equals(key))
                                    timelineRecord.type = value;
                                if ("name".equals(key))
                                    timelineRecord.name = value;
                                if ("startTime".equals(key))
                                    timelineRecord.startTime = value;
                                if ("finishTime".equals(key))
                                    timelineRecord.finishTime = value;
                                if ("state".equals(key))
                                    timelineRecord.state = value;
                                if ("result".equals(key))
                                    timelineRecord.result = value;
                            }
                            runResult.addTimelineRecord(timelineRecord);
                        }
                    }
                }
            }
            else
                logger.error("Retrieving the build timeline failed; just continue");
        }

        return runResult;
    }

    /******************************************************************************************
     Create a new pipeline.
     *******************************************************************************************/
    public static String callCreatePipelineApi (String azdoUser,
                                                String azdoPat,
                                                String pipelinePath,
                                                String pipelineName,
                                                String repositoryName,
                                                String azdoEndpoint,
                                                String azdoPipelinesApi,
                                                String azdoPipelinesApiVersion,
                                                String repositoryId) {
        logger.debug("==> Method: AzDoUtils.callCreatePipelineApi");
        logger.debug("pipelinePath: {}", pipelinePath);
        logger.debug("pipelineName: {}", pipelineName);
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("repositoryId: {}", repositoryId);

        String pipelineId = null;
        String http = azdoEndpoint +
                azdoPipelinesApi +
                "?" +
                azdoPipelinesApiVersion;
        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + pipelineName + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TAB + DOUBLE_QUOTE + "configuration" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "yaml" + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "path" + DQUOTE_SCOL_DQUOTE + pipelinePath + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "repository" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                THREE_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DQUOTE_SCOL_DQUOTE + repositoryId + DOUBLE_QUOTE + COMMA_NEXTLINE +
                THREE_TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + repositoryName + DOUBLE_QUOTE + COMMA_NEXTLINE +
                THREE_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "azureReposGit" + DOUBLE_QUOTE + NEXTLINE +
                TWO_TAB + BRACKET_CLOSE + NEXTLINE +
                TAB + BRACKET_CLOSE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse<String> response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.POST, json);

        // Get the pipeline id from the response
        Yaml yaml = new Yaml();
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS, yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_ID) != null) {
                pipelineId = yamlMap.get(JSON_ELEMENT_ID).toString();
                logger.debug("Pipeline id is: {}", pipelineId);
            }
        }

        return pipelineId;
    }

    /******************************************************************************************
     Check whether a pipeline  with a certain name already exists.
     If available, the pipeline Id is returned.
     *******************************************************************************************/
    public static String callGetPipelineApi (String azdoUser,
                                             String azdoPat,
                                             String pipelineName,
                                             String azdoEndpoint,
                                             String azdoPipelinesApi,
                                             String azdoPipelinesApiVersion) {
        logger.debug("==> Method: AzDoUtils.callGetPipelineApi");
        logger.debug("pipelineName: {}", pipelineName);

        String pipelineId = null;
        String http = azdoEndpoint +
                azdoPipelinesApi +
                "?" +
                azdoPipelinesApiVersion;

        HttpResponse<String> response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.GET, null);

        // Get the pipeline id from the response
        Yaml yaml = new Yaml();
        String name = null;
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS, yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                pipelineId = iterateYamlArrayListAndFindElement (arr, JSON_ELEMENT_NAME, pipelineName, JSON_ELEMENT_ID);
            }
            logger.debug("Pipeline id is: {}", pipelineId);
        }

        return pipelineId;
    }

    /******************************************************************************************
     Create a new repository and return the Azure DevOps repository-Id.
     *******************************************************************************************/
    public static String callCreateRepoApi (String azdoUser,
                                            String azdoPat,
                                            String azdoEndpoint,
                                            String azdoGitApi,
                                            String azdoGitApiVersion,
                                            String azdoGitApiRepositories,
                                            String repositoryName,
                                            String projectId) {
        logger.debug("==> Method: AzDoUtils.callCreateRepoApi");
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("projectId: {}", projectId);

        String repositoryId = null;
        String http = azdoEndpoint +
                azdoGitApi +
                azdoGitApiRepositories +
                "?" +
                azdoGitApiVersion;
        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + repositoryName + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TAB + DOUBLE_QUOTE + "project" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DQUOTE_SCOL_DQUOTE + projectId + DOUBLE_QUOTE + NEXTLINE +
                TAB + BRACKET_CLOSE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse<String> response = callApi(azdoUser, azdoPat, http, HttpMethod.POST, json);

        if (response != null) {
            logger.debug("AzDo API response body: {}", response.body().toString());

            // Get the repository id from the response
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            repositoryId = yamlMap.get(JSON_ELEMENT_ID).toString();
            logger.debug(REPOSITORY_ID_IS, repositoryId);
        }

        return repositoryId;
    }

    /******************************************************************************************
     Update a repository with a new default branch.
     *******************************************************************************************/
    public static String callUpdateRepoApi (String azdoUser,
                                            String azdoPat,
                                            String azdoEndpoint,
                                            String azdoGitApi,
                                            String azdoGitApiVersion,
                                            String azdoGitApiRepositories,
                                            String repositoryId,
                                            String branchName) {
        logger.debug("==> Method: AzDoUtils.callUpdateRepoApi");
        logger.debug("repositoryId: {}", repositoryId);
        logger.debug("branchName: {}", branchName);

        String http = azdoEndpoint +
                azdoGitApi +
                azdoGitApiRepositories +
                "/" + repositoryId +
                "?" +
                azdoGitApiVersion;

        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + "defaultBranch" + DQUOTE_SCOL_DQUOTE + branchName + DOUBLE_QUOTE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse<String> response = callApi(azdoUser, azdoPat, http, HttpMethod.PATCH, json);

        if (response != null) {
            logger.debug("AzDo API response body: {}", response.body().toString());

            // Get the repository id from the response
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            repositoryId = yamlMap.get(JSON_ELEMENT_ID).toString();
            logger.debug(REPOSITORY_ID_IS, repositoryId);
        }

        return repositoryId;
    }

    /******************************************************************************************
     Check whether a Git repository with a certain name already exists.
     If available, the repository-Id is returned.
     *******************************************************************************************/
    public static String callGetRepositoryApi (String azdoUser,
                                               String azdoPat,
                                               String repositoryName,
                                               String azdoEndpoint,
                                               String azdoGitApi,
                                               String azdoGitApiVersion,
                                               String azdoGitApiRepositories) {
        logger.debug("==> Method: AzDoUtils.callGetRepositoryApi");
        logger.debug("repositoryName: {}", repositoryName);

        String repositoryId = null;
        String http = azdoEndpoint +
                azdoGitApi +
                azdoGitApiRepositories +
                "?" +
                azdoGitApiVersion;

        HttpResponse<String> response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.GET, null);

        // Get the repository id from the response
        Yaml yaml = new Yaml();
        String name;
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS, yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                repositoryId = iterateYamlArrayListAndFindElement (arr, JSON_ELEMENT_NAME, repositoryName, JSON_ELEMENT_ID);
            }
            logger.debug(REPOSITORY_ID_IS, repositoryId);
        }

        return repositoryId;
    }

    /******************************************************************************************
     Utility method that returns the value of a certain key in an array.
     *******************************************************************************************/
    public static String iterateYamlArrayListAndFindElement (ArrayList<Object> arr, String key, String compareKey, String val) {
        logger.debug("==> Method: AzDoUtils.iterateYamlArrayListAndFindElement");
        logger.debug("key: {}", key);
        logger.debug("compareKey: {}", compareKey);
        logger.debug("val: {}", val);

        String compareValue = null;
        if (arr != null) {
            int size = arr.size();
            String name;

            // Go through list of values
            for (int counter = 0; counter < size; counter++) {
                LinkedHashMap<String, Object> value = ((LinkedHashMap<String, Object>) arr.get(counter));
                name = value.get(key).toString();
                if (name != null && name.equals(compareKey)) {
                    logger.debug("Found value {}", compareKey);
                    compareValue = value.get(val).toString();
                    break;
                }
            }
        }

        return compareValue;
    }

    /******************************************************************************************
     Check whether a list of property values exists in the Azure DevOps project.
     This list (propertyList) is for example a list with variable groups or environments.
     An API call is performed to retrieve the actual list for these properties.
     If the 'propertyList' contains values that are not present in the target Azure DevOps
     project, an error is raised (if continueOnError is true).
     Note, that the response of the API is assumed to be the same. even if the endpoint differs.
     *******************************************************************************************/
    public static void callValidatePropertyList (String azdoUser,
                                                 String azdoPat,
                                                 String project,
                                                 ArrayList<String> propertyList,
                                                 String azdoEndpoint,
                                                 String azdoPipelinesApi,
                                                 String azdoPipelinesApiVersion,
                                                 String propertyNameInLog,
                                                 boolean continueOnError) {
        logger.debug("==> Method: AzDoUtils.callValidatePropertyList");

        String http = azdoEndpoint +
                azdoPipelinesApi +
                "?" +
                azdoPipelinesApiVersion;

        HttpResponse<String> response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.GET, null);

        // Get the list of properties from the response
        ArrayList<String> validPropertyList = new ArrayList<>();
        Yaml yaml = new Yaml();
        String name = null;
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS, yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> list = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                int index;
                int size = list.size();
                for (index = 0; index < size; index++) {
                    if (list.get(index) instanceof Map) {
                        Map<String, Object> map = (Map<String, Object>) list.get(index);
                        for (Map.Entry<String, Object> entry : map.entrySet()) {

                            // Add the property values to the list with valid properties
                            logger.debug("entry.getKey(): {}", entry.getKey());
                            logger.debug("entry.getValue(): {}", entry.getValue());
                            if ("name".equals(entry.getKey()))
                                validPropertyList.add(entry.getValue().toString());
                        }
                    }
                }
            }
        }

        // Validate whether the values in 'variableGroups' exist in 'validPropertyList'.
        int index;
        int size = propertyList.size();
        String propertyValue;
        for (index = 0; index < size; index++) {
            propertyValue = propertyList.get(index);
            if (!validPropertyList.contains(propertyValue)) {
                if (continueOnError) {
                    logger.debug("{} \'{}\' is not defined in Azure DevOps project \'{}\'", propertyNameInLog, propertyValue, project);
                    return;
                }
                else {
                    logger.error("{} \'{}\' is not defined in Azure DevOps project \'{}\'", propertyNameInLog, propertyValue, project);
                    System.exit(1);
                }
            }
        }
    }
}

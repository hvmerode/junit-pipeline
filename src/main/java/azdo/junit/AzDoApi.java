package azdo.junit;

import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

public class AzDoApi<runResult> {
    private static Logger logger = LoggerFactory.getLogger(AzDoApi.class);
    private static String BRACKET_OPEN_NEXTLINE = "{\n";
    private static String BRACKET_CLOSE = "}";
    private static String DOUBLE_QUOTE = "\"";
    private static String DQUOTE_SCOL_DQUOTE = "\": \"";
    private static String NEXTLINE = "\n";
    private static String COMMA_NEXTLINE = ",\n";
    private static String TAB = "\t";
    private static String TWO_TAB = "\t\t";
    private static String THREE_TAB = "\t\t\t";
    private enum HttpMethod {GET, PUT, POST, PATCH}
    private static boolean test = false;

    public AzDoApi() {
    }
        // Perform Azure DevOps API call
        public static HttpResponse callApi (TestProperties properties, String http, HttpMethod httpMethod, String json) {
            if (test)
                return null;

            try {
                logger.info("==> postApi");
                logger.info("==> HTTP Endpoint: " + http);
                logger.info("==> JSON: " + json);

                String encodedString = Base64.getEncoder().encodeToString((properties.getUserTargetRepository() + ":" + properties.getPasswordTargetRepository()).getBytes());
                HttpClient client = HttpClient.newHttpClient();
                HttpRequest request;
                if (httpMethod == HttpMethod.GET) {
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(http))
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Accept", "application/json")
                            .setHeader("Authorization", "Basic " + encodedString)
                            .GET()
                            .build();
                } else {
                    request = HttpRequest.newBuilder()
                            .uri(URI.create(http))
                            .setHeader("Content-Type", "application/json")
                            .setHeader("Accept", "application/json")
                            .setHeader("Authorization", "Basic " + encodedString)
                            .method(httpMethod.toString(), HttpRequest.BodyPublishers.ofString(json))
                            .build();
                }

                HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response == null) {
                    logger.info("==> Response is null");
                    throw new IOException();
                }
                else {
                    logger.info("==> AzDo API response" + response.toString());

                    // check whether the HTTP status code is valid
                    if (response.statusCode() > 299) {
                        Exception e = new Exception("Received invalid HTTP status code from Azure DevOps API");
                        throw e;
                    }
                }

                return response;
            }

        catch (Exception e) {
            logger.info("Exception:" + e.getLocalizedMessage());
        }

        return null;
    }

    // Return the project-id
    public static String callGetProjectIdApi (TestProperties properties) {
        logger.info("==> AzDoApi.callGetProjectIdApi");
        String projectName = properties.getTargetProject();
        String projectId = null;
        String http = properties.getAzdoBaseUrl() +
                "/_apis/" +
                properties.getProjectApi() +
                "?" +
                properties.getProjectApiVersion();

        HttpResponse response = callApi(properties, http, HttpMethod.GET, null);

        // Get the project id from the response
        Yaml yaml = new Yaml();
        String name = null;
        Map<String, Object> yamlMap = yaml.load(response.body().toString());
        logger.info("==> Response is: " + yamlMap.toString());
        if (yamlMap.get("value") instanceof ArrayList) {
            ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get("value");
            if (arr != null) {
                int size = arr.size();

                // Go through list of values
                for (int counter = 0; counter < size; counter++) {
                    LinkedHashMap<String, Object> value = ((LinkedHashMap<String, Object>) arr.get(counter));
                    name = value.get("name").toString();
                    if (name != null && name.equals(projectName)){
                        logger.info("==> Found project " + projectName);
                        projectId = value.get("id").toString();
                        break;
                    }
                }
            }
        }

        logger.info("==> Project id is: " + projectId);
        return projectId;
    }

    // Execute a pipeline
    public static void callPipelineRunApi (TestProperties properties, String pipelineId, String branchName) {
        logger.info("==> AzDoApi.callPipelineRunApi");
        if (pipelineId == null)
        {
            logger.info("==> Nothing to run; the pipelineId is null");
        } else {
            String sourceBranch = branchName;
            if (branchName == null || branchName.equals("main") || branchName.equals("master") || branchName.equals(""))
                sourceBranch = "master";

                String http = properties.getAzdoEndpoint() +
                    properties.getBuildApi() +
                    "?" +
                    properties.getBuildApiVersion();

            String json = BRACKET_OPEN_NEXTLINE +
                    TAB + DOUBLE_QUOTE + "definition" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                        TWO_TAB + DOUBLE_QUOTE + "id" + DOUBLE_QUOTE + ": " + pipelineId + BRACKET_CLOSE + COMMA_NEXTLINE +
                    TAB + DOUBLE_QUOTE + "sourceBranch" + DOUBLE_QUOTE + ": " + DOUBLE_QUOTE + sourceBranch + DOUBLE_QUOTE + NEXTLINE +
                    BRACKET_CLOSE;

            HttpResponse response = callApi(properties, http, HttpMethod.POST, json);
            logger.info("==> Response is: " + response.toString());
        }
    }

    // Wait until the build finished and return the result
    public static RunResult callRunResult (TestProperties properties, String pipelineId) {
        logger.info("==> AzDoApi.callRunResult");
        int pollFrequency = properties.getBuildApiPollFrequency();
        int timeout = properties.getBuildApiPollTimeout();
        RunResult runResult = new RunResult();
        Instant start = Instant.now();
        HttpResponse response;
        Yaml yaml;
        String status = null;
        String result = null;
        String buildNumber = null;

        long timeElapsed = 0;
        String http = properties.getAzdoEndpoint() +
                properties.getBuildApi() +
                "?definitions=" +
                pipelineId +
                "&maxBuildsPerDefinition=1&queryOrder=queueTimeDescending" +
                "&" +
                properties.getBuildApiVersion();

        String json = "{}";

        // Poll the API until the status is completed or timed out
        // Polling is determined using a certain frequency
        while (runResult.result == RunResult.Result.none) {

            // Call the API
            logger.info("==> Call the API");
            response = callApi(properties, http, HttpMethod.GET, json);

            // Get the result from the response
            yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.info("==> Response is: " + yamlMap.toString());
            if (yamlMap.get("value") instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get("value");
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
                    }
                }
            }

            runResult = new RunResult(result, status);

            // Wait until the next poll is allowed
            Utils.wait(pollFrequency * 1000);
            Instant finish = Instant.now();
            timeElapsed = Duration.between(start, finish).toSeconds();
            logger.info("==> Time elapsed: " + Long.toString(timeElapsed));

            if (runResult.result == RunResult.Result.none && timeElapsed > (long)timeout) {
                runResult.result = RunResult.Result.undetermined;
                runResult.status = RunResult.Status.timeout;
            }
            logger.info("==> Buildnumber: " + buildNumber);
            logger.info("==> Status response: " + runResult.status.toString());
            logger.info("==> Status response: " + runResult.status.toString());
        }
        return runResult;
    }

    // Create a new pipeline
    public static String callCreatePipelineApi (TestProperties properties, String repositoryId) {
        logger.info("==> AzDoApi.callCreatePipelineApi");
        String path = properties.getPipelinePathRepository();
        String pipelineId = null;
        String http = properties.getAzdoEndpoint() +
                properties.getPipelinesApi() +
                "?" +
                properties.getPipelinesApiVersion();;
        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + "name" + DQUOTE_SCOL_DQUOTE + properties.getRepositoryName() + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TAB + DOUBLE_QUOTE + "configuration" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "yaml" + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "path" + DQUOTE_SCOL_DQUOTE + path + DOUBLE_QUOTE + COMMA_NEXTLINE +
                    TWO_TAB + DOUBLE_QUOTE + "repository" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                        THREE_TAB + DOUBLE_QUOTE + "id" + DQUOTE_SCOL_DQUOTE + repositoryId + DOUBLE_QUOTE + COMMA_NEXTLINE +
                        THREE_TAB + DOUBLE_QUOTE + "name" + DQUOTE_SCOL_DQUOTE + properties.getRepositoryName() + DOUBLE_QUOTE + COMMA_NEXTLINE +
                        THREE_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "azureReposGit" + DOUBLE_QUOTE + NEXTLINE +
                    TWO_TAB + BRACKET_CLOSE + NEXTLINE +
                TAB + BRACKET_CLOSE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse response = callApi(properties, http, HttpMethod.POST, json);

        // Get the pipeline id from the response
        Yaml yaml = new Yaml();
        Map<String, Object> yamlMap = yaml.load(response.body().toString());
        logger.info("==> Response is: " + yamlMap.toString());
        if (yamlMap.get("id") != null) {
            pipelineId = yamlMap.get("id").toString();
            logger.info("==> Pipeline id is: " + pipelineId);
        }

        return pipelineId;
    }

    // Check whether a pipeline  with a certain name already exists
    // If available, the pipeline Id is returned
    public static String callGetPipelineApi (TestProperties properties) {
        logger.info("==> AzDoApi.callGetPipelineApi");
        String pipelineName = properties.getRepositoryName();
        String pipelineId = null;
        String http = properties.getAzdoEndpoint() +
                properties.getPipelinesApi() +
                "?" +
                properties.getPipelinesApiVersion();

        HttpResponse response = callApi(properties, http, HttpMethod.GET, null);

        // Get the repository id from the response
        Yaml yaml = new Yaml();
        String name = null;
        Map<String, Object> yamlMap = yaml.load(response.body().toString());
        logger.info("==> Response is: " + yamlMap.toString());
        if (yamlMap.get("value") instanceof ArrayList) {
            ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get("value");
            if (arr != null) {
                int size = arr.size();

                // Go through list of values
                for (int counter = 0; counter < size; counter++) {
                    LinkedHashMap<String, Object> value = ((LinkedHashMap<String, Object>) arr.get(counter));
                    name = value.get("name").toString();
                    if (name != null && name.equals(pipelineName)){
                        logger.info("==> Found pipeline " + pipelineName);
                        pipelineId = value.get("id").toString();
                        break;
                    }
                }
            }
        }

        logger.info("==> Pipeline id is: " + pipelineId);
        return pipelineId;
    }

    // Create a new repository and return the Azure DevOps repository id
    public static String callCreateRepoApi (TestProperties properties, String projectId) {
        logger.info("==> AzDoApi.callCreateRepoApi");
        String repositoryId = null;
        String http = properties.getAzdoEndpoint() +
                properties.getGitApi() +
                properties.getGitApiRepositories() +
                "?" +
                properties.getGitApiVersion();
        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + "name" + DQUOTE_SCOL_DQUOTE + properties.getRepositoryName() + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TAB + DOUBLE_QUOTE + "project" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "id" + DQUOTE_SCOL_DQUOTE + projectId + DOUBLE_QUOTE + NEXTLINE +
                TAB + BRACKET_CLOSE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse response = callApi(properties, http, HttpMethod.POST, json);
        logger.info("==> AzDo API response body" + response.body().toString());

        // Get the repository id from the response
        Yaml yaml = new Yaml();
        Map<String, Object> yamlMap = yaml.load(response.body().toString());
        repositoryId = yamlMap.get("id").toString();
        logger.info("==> Repository id is: " + repositoryId);
        return repositoryId;
    }

    // Update a repository with a new default branch
    public static String callUpdateRepoApi (TestProperties properties, String repositoryId, String branchName) {
        logger.info("==> AzDoApi.callUpdateRepoApi");
        String http = properties.getAzdoEndpoint() +
                properties.getGitApi() +
                properties.getGitApiRepositories() +
                "/" + repositoryId +
                "?" +
                properties.getGitApiVersion();

        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + "defaultBranch" + DQUOTE_SCOL_DQUOTE + branchName + DOUBLE_QUOTE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse response = callApi(properties, http, HttpMethod.PATCH, json);
        logger.info("==> AzDo API response body" + response.body().toString());

        // Get the repository id from the response
        Yaml yaml = new Yaml();
        Map<String, Object> yamlMap = yaml.load(response.body().toString());
        repositoryId = yamlMap.get("id").toString();
        logger.info("==> Repository id is: " + repositoryId);
        return repositoryId;
    }

    // Check whether a Git repository with a certain name already exists
    // If available, the repository Id is returned
    public static String callGetRepositoryApi (TestProperties properties) {
        logger.info("==> AzDoApi.callGetRepositoryApi");
        String repositoryName = properties.getRepositoryName();
        String repositoryId = null;
        String http = properties.getAzdoEndpoint() +
                properties.getGitApi() +
                properties.getGitApiRepositories() +
                "?" +
                properties.getGitApiVersion();

        HttpResponse response = callApi(properties, http, HttpMethod.GET, null);

        // Get the repository id from the response
        Yaml yaml = new Yaml();
        String name;
        Map<String, Object> yamlMap = yaml.load(response.body().toString());
        logger.info("==> Response is: " + yamlMap.toString());
        if (yamlMap.get("value") instanceof ArrayList) {
            ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get("value");
            if (arr != null) {
                int size = arr.size();

                // Go through list of values
                for (int counter = 0; counter < size; counter++) {
                    LinkedHashMap<String, Object> value = ((LinkedHashMap<String, Object>) arr.get(counter));
                    name = value.get("name").toString();
                    if (name != null && name.equals(repositoryName)){
                        logger.info("==> Found repository " + repositoryName);
                        repositoryId = value.get("id").toString();
                        break;
                    }
                }
            }
        }

        logger.info("==> Repository id is: " + repositoryId);
        return repositoryId;
    }
}


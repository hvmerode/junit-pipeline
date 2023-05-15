// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;
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
    private static final String RESPONSE_IS = "Response is: ";
    private static final String REPOSITORY_ID_IS = "Repository id is: ";
    private static final String JSON_ELEMENT_VALUE = "value";
    private static final String JSON_ELEMENT_NAME = "name";
    private static final String JSON_ELEMENT_ID = "id";
    private enum HttpMethod {GET, PUT, POST, PATCH}
    private static boolean test = false;

    // Perform Azure DevOps API call
    public static HttpResponse callApi (TestProperties properties, String http, HttpMethod httpMethod, String json) {
        if (test)
            return null;

        try {
            logger.debug("==> Method: AzDoApi.callApi");
            logger.debug("HTTP Endpoint: {}", http);
            logger.debug("JSON: {}", json);

            String encodedString = Base64.getEncoder().encodeToString((properties.getAzDoUser() + ":" + properties.getAzdoPat()).getBytes());
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request;
            if (httpMethod == HttpMethod.GET) {
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

            HttpResponse response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response == null) {
                logger.debug("Response is null");
            }
            else {
                logger.debug("AzDo API response: {}", response.toString());

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

    // Return the project-id
    public static String callGetProjectIdApi (TestProperties properties) {
        logger.debug("==> Method: AzDoApi.callGetProjectIdApi");
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
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS + yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                projectId = iterateYamlArrayListAndFindElement (arr, JSON_ELEMENT_NAME, projectName, JSON_ELEMENT_ID);
            }
            logger.debug("Project id is: {}", projectId);
        }

        return projectId;
    }

    // Execute a pipeline
    public static void callPipelineRunApi (TestProperties properties, String pipelineId, String branchName) {
        logger.debug("==> Method: AzDoApi.callPipelineRunApi");
        if (pipelineId == null)
        {
            logger.debug("Nothing to run; the pipelineId is null");
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
                        TWO_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DOUBLE_QUOTE + ": " + pipelineId + BRACKET_CLOSE + COMMA_NEXTLINE +
                    TAB + DOUBLE_QUOTE + "sourceBranch" + DOUBLE_QUOTE + ": " + DOUBLE_QUOTE + sourceBranch + DOUBLE_QUOTE + NEXTLINE +
                    BRACKET_CLOSE;

            HttpResponse response = callApi(properties, http, HttpMethod.POST, json);
            if (response != null)
                logger.debug(RESPONSE_IS + response.toString());
        }
    }

    // Wait until the build finished and return the result
    public static RunResult callRunResult (TestProperties properties, String pipelineId) {
        logger.debug("==> Method: AzDoApi.callRunResult");
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
            logger.debug("Call the API");
            response = callApi(properties, http, HttpMethod.GET, json);

            // Get the result from the response
            yaml = new Yaml();
            if (response != null) {
                Map<String, Object> yamlMap = yaml.load(response.body().toString());
                logger.debug(RESPONSE_IS + yamlMap.toString());
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
                        }
                    }
                }
            }

            runResult = new RunResult(result, status);

            // Wait until the next poll is allowed
            Utils.wait(pollFrequency * 1000);
            Instant finish = Instant.now();
            timeElapsed = Duration.between(start, finish).toSeconds();
            logger.debug("Time elapsed: {}", Long.toString(timeElapsed));

            if (runResult.result == RunResult.Result.none && timeElapsed > (long) timeout) {
                runResult.result = RunResult.Result.undetermined;
                runResult.status = RunResult.Status.timeout;
            }
            logger.debug("Buildnumber: {}", buildNumber);
            logger.debug("Status response: {}", runResult.status.toString());
            logger.debug("Status response: {}", runResult.status.toString());
        }

        return runResult;
    }

    // Create a new pipeline
    public static String callCreatePipelineApi (TestProperties properties, String repositoryId) {
        logger.debug("==> Method: AzDoApi.callCreatePipelineApi");
        String path = properties.getPipelinePathRepository();
        String pipelineId = null;
        String http = properties.getAzdoEndpoint() +
                properties.getPipelinesApi() +
                "?" +
                properties.getPipelinesApiVersion();;
        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + properties.getRepositoryName() + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TAB + DOUBLE_QUOTE + "configuration" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "yaml" + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "path" + DQUOTE_SCOL_DQUOTE + path + DOUBLE_QUOTE + COMMA_NEXTLINE +
                    TWO_TAB + DOUBLE_QUOTE + "repository" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                        THREE_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DQUOTE_SCOL_DQUOTE + repositoryId + DOUBLE_QUOTE + COMMA_NEXTLINE +
                        THREE_TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + properties.getRepositoryName() + DOUBLE_QUOTE + COMMA_NEXTLINE +
                        THREE_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "azureReposGit" + DOUBLE_QUOTE + NEXTLINE +
                    TWO_TAB + BRACKET_CLOSE + NEXTLINE +
                TAB + BRACKET_CLOSE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse response = callApi(properties, http, HttpMethod.POST, json);

        // Get the pipeline id from the response
        Yaml yaml = new Yaml();
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS + yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_ID) != null) {
                pipelineId = yamlMap.get(JSON_ELEMENT_ID).toString();
                logger.debug("Pipeline id is: {}", pipelineId);
            }
        }

        return pipelineId;
    }

    // Check whether a pipeline  with a certain name already exists
    // If available, the pipeline Id is returned
    public static String callGetPipelineApi (TestProperties properties) {
        logger.debug("==> Method: AzDoApi.callGetPipelineApi");
        String pipelineName = properties.getRepositoryName();
        String pipelineId = null;
        String http = properties.getAzdoEndpoint() +
                properties.getPipelinesApi() +
                "?" +
                properties.getPipelinesApiVersion();

        HttpResponse response = callApi(properties, http, HttpMethod.GET, null);

        // Get the pipeline id from the response
        Yaml yaml = new Yaml();
        String name = null;
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS + yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                pipelineId = iterateYamlArrayListAndFindElement (arr, JSON_ELEMENT_NAME, pipelineName, JSON_ELEMENT_ID);
            }
            logger.debug("Pipeline id is: {}", pipelineId);
        }

        return pipelineId;
    }

    // Create a new repository and return the Azure DevOps repository Id
    public static String callCreateRepoApi (TestProperties properties, String repositoryName, String projectId) {
        logger.debug("==> Method: AzDoApi.callCreateRepoApi");
        String repositoryId = null;
        String http = properties.getAzdoEndpoint() +
                properties.getGitApi() +
                properties.getGitApiRepositories() +
                "?" +
                properties.getGitApiVersion();
        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + repositoryName + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TAB + DOUBLE_QUOTE + "project" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DQUOTE_SCOL_DQUOTE + projectId + DOUBLE_QUOTE + NEXTLINE +
                TAB + BRACKET_CLOSE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse response = callApi(properties, http, HttpMethod.POST, json);

        if (response != null) {
            logger.debug("AzDo API response body: {}", response.body().toString());

            // Get the repository id from the response
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            repositoryId = yamlMap.get(JSON_ELEMENT_ID).toString();
            logger.debug(REPOSITORY_ID_IS + repositoryId);
        }

        return repositoryId;
    }

    // Update a repository with a new default branch
    public static String callUpdateRepoApi (TestProperties properties, String repositoryId, String branchName) {
        logger.debug("==> Method: AzDoApi.callUpdateRepoApi");
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

        if (response != null) {
            logger.debug("AzDo API response body: {}", response.body().toString());

            // Get the repository id from the response
            Yaml yaml = new Yaml();
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            repositoryId = yamlMap.get(JSON_ELEMENT_ID).toString();
            logger.debug(REPOSITORY_ID_IS + repositoryId);
        }

        return repositoryId;
    }

    // Check whether a Git repository with a certain name already exists
    // If available, the repository Id is returned
    public static String callGetRepositoryApi (TestProperties properties, String repositoryName) {
        logger.debug("==> Method: AzDoApi.callGetRepositoryApi");
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
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS + yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                repositoryId = iterateYamlArrayListAndFindElement (arr, JSON_ELEMENT_NAME, repositoryName, JSON_ELEMENT_ID);
            }
            logger.debug(REPOSITORY_ID_IS + repositoryId);
        }

        return repositoryId;
    }

    // Return the value of a certain key
    public static String iterateYamlArrayListAndFindElement (ArrayList<Object> arr, String key, String compareKey, String val) {
        logger.debug("==> Method: AzDoApi.iterateYamlArrayListAndFindElement");
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
}


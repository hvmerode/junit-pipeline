package azdo.utils;

import azdo.junit.RunResult;
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

public class AzDoUtils {
    private static Logger logger = LoggerFactory.getLogger(AzDoUtils.class);
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

    /* Perform an Azure DevOps API call. This is a generic method to call an Azure DeVOps API. The endpoint,
       HTTP method and body (json) must be provided.
     */
    public static HttpResponse callApi (String azdoUser,
                                        String azdoPat,
                                        String http,
                                        AzDoUtils.HttpMethod httpMethod,
                                        String json) {
        if (test)
            return null;

        try {
            logger.debug("==> Method: AzDoUtils.callApi");
            logger.debug("HTTP Endpoint: {}", http);
            logger.debug("JSON: {}", json);

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

    /* Create a new repo in Azure DevOps if it does not exist yet
     */
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
            logger.debug("Exception occurred. Cannot create a new repository");
            e.printStackTrace();
        }

        return repositoryId;
    }

    /* Create a new pipeline in Azure DevOps if it does not exist yet
     */
    public static String createPipelineIfNotExists (String azdoUser,
                                                    String azdoPat,
                                                    String pipelinePath,
                                                    String pipelineName,
                                                    String azdoEndpoint,
                                                    String azdoPipelinesApi,
                                                    String azdoPipelinesApiVersion,
                                                    String repositoryId) {
        logger.debug("==> Method: AzDoUtils.createPipelineIfNotExists");
        logger.debug("pipelinePath: {}", pipelinePath);
        logger.debug("pipelineName: {}", pipelineName);
        logger.debug("azdoEndpoint: {}", azdoEndpoint);
        logger.debug("azdoPipelinesApi: {}", azdoPipelinesApi);
        logger.debug("azdoPipelinesApiVersion: {}", azdoPipelinesApiVersion);

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
            logger.debug("Create a new pipeline if not existing");
            // Create a new pipeline if not existing
            if (pipelineId == null) {

                // Create a pipeline
                pipelineId = AzDoUtils.callCreatePipelineApi (azdoUser,
                        azdoPat,
                        pipelinePath,
                        pipelineName,
                        azdoEndpoint,
                        azdoPipelinesApi,
                        azdoPipelinesApiVersion,
                        repositoryId);
            }
        }
        catch (Exception e) {
            logger.debug("Exception occurred. Cannot create a new pipeline");
            e.printStackTrace();
        }

        return pipelineId;
    }

    /* Return the project-id of a project in a specific Azure DevOps organization
     */
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

        HttpResponse response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.GET, null);

        // Get the project id from the response
        Yaml yaml = new Yaml();
        String name = null;
        if (response != null) {
            Map<String, Object> yamlMap = yaml.load(response.body().toString());
            logger.debug(RESPONSE_IS + yamlMap.toString());
            if (yamlMap.get(JSON_ELEMENT_VALUE) instanceof ArrayList) {
                ArrayList<Object> arr = (ArrayList<Object>) yamlMap.get(JSON_ELEMENT_VALUE);
                projectId = iterateYamlArrayListAndFindElement (arr, JSON_ELEMENT_NAME, project, JSON_ELEMENT_ID);
            }
            logger.debug("Project id is: {}", projectId);
        }

        return projectId;
    }

    /* Execute a pipeline
     */
    public static void callPipelineRunApi (String azdoUser,
                                           String azdoPat,
                                           String azdoEndpoint,
                                           String azdoBuildApi,
                                           String azdoBuildApiVersion,
                                           String pipelineId,
                                           String branchName) {
        logger.debug("==> Method: AzDoUtils.callPipelineRunApi");

        if (pipelineId == null)
        {
            logger.debug("Nothing to run; the pipelineId is null");
        } else {
            String sourceBranch = branchName;
            if (branchName == null || branchName.equals("main") || branchName.equals("master") || branchName.equals(""))
                sourceBranch = "master";

            String http = azdoEndpoint +
                    azdoBuildApi +
                    "?" +
                    azdoBuildApiVersion;

            String json = BRACKET_OPEN_NEXTLINE +
                    TAB + DOUBLE_QUOTE + "definition" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                    TWO_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DOUBLE_QUOTE + ": " + pipelineId + BRACKET_CLOSE + COMMA_NEXTLINE +
                    TAB + DOUBLE_QUOTE + "sourceBranch" + DOUBLE_QUOTE + ": " + DOUBLE_QUOTE + sourceBranch + DOUBLE_QUOTE + NEXTLINE +
                    BRACKET_CLOSE;

            HttpResponse response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.POST, json);
            if (response != null)
                logger.debug(RESPONSE_IS + response.toString());
        }
    }

    /* Wait until the build is finished and return the result of the pipeline run.
       Unfortunately, the amount of information that can be retrieved from Azure DevOps is limited to
       the result:
       - canceled
       - failed
       - succeeded
       - partiallySucceeded

       and the status:
       - cancelling
       - completed
       - inProgress
       - notStarted
       - postponed
       - timeout
     */
    public static RunResult callRunResult (String azdoUser,
                                           String azdoPat,
                                           int pollFrequency,
                                           int timeout,
                                           String azdoEndpoint,
                                           String azdoBuildApi,
                                           String azdoBuildApiVersion,
                                           String pipelineId) {
        logger.debug("==> Method: AzDoUtils.callRunResult");

        RunResult runResult = new RunResult();
        Instant start = Instant.now();
        HttpResponse response;
        Yaml yaml;
        String status = null;
        String result = null;
        String buildNumber = null;

        long timeElapsed = 0;
        String http = azdoEndpoint +
                azdoBuildApi +
                "?definitions=" +
                pipelineId +
                "&maxBuildsPerDefinition=1&queryOrder=queueTimeDescending" +
                "&" +
                azdoBuildApiVersion;

        String json = "{}";

        // Poll the API until the status is completed or timed out
        // Polling is determined using a certain frequency
        while (runResult.result == RunResult.Result.none) {

            // Call the API
            logger.debug("Call the API");
            response = callApi(azdoUser, azdoPat, http, HttpMethod.GET, json);

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

    /* Create a new pipeline
     */
    public static String callCreatePipelineApi (String azdoUser,
                                                String azdoPat,
                                                String path,
                                                String repositoryName,
                                                String azdoEndpoint,
                                                String azdoPipelinesApi,
                                                String azdoPipelinesApiVersion,
                                                String repositoryId) {
        logger.debug("==> Method: AzDoUtils.callCreatePipelineApi");

        String pipelineId = null;
        String http = azdoEndpoint +
                azdoPipelinesApi +
                "?" +
                azdoPipelinesApiVersion;;
        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + repositoryName + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TAB + DOUBLE_QUOTE + "configuration" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "yaml" + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "path" + DQUOTE_SCOL_DQUOTE + path + DOUBLE_QUOTE + COMMA_NEXTLINE +
                TWO_TAB + DOUBLE_QUOTE + "repository" + DOUBLE_QUOTE + ": " + BRACKET_OPEN_NEXTLINE +
                THREE_TAB + DOUBLE_QUOTE + JSON_ELEMENT_ID + DQUOTE_SCOL_DQUOTE + repositoryId + DOUBLE_QUOTE + COMMA_NEXTLINE +
                THREE_TAB + DOUBLE_QUOTE + JSON_ELEMENT_NAME + DQUOTE_SCOL_DQUOTE + repositoryName + DOUBLE_QUOTE + COMMA_NEXTLINE +
                THREE_TAB + DOUBLE_QUOTE + "type" + DQUOTE_SCOL_DQUOTE + "azureReposGit" + DOUBLE_QUOTE + NEXTLINE +
                TWO_TAB + BRACKET_CLOSE + NEXTLINE +
                TAB + BRACKET_CLOSE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.POST, json);

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

    /* Check whether a pipeline  with a certain name already exists.
       If available, the pipeline Id is returned.
     */
    public static String callGetPipelineApi (String azdoUser,
                                             String azdoPat,
                                             String pipelineName,
                                             String azdoEndpoint,
                                             String azdoPipelinesApi,
                                             String azdoPipelinesApiVersion) {
        logger.debug("==> Method: AzDoUtils.callGetPipelineApi");

        String pipelineId = null;
        String http = azdoEndpoint +
                azdoPipelinesApi +
                "?" +
                azdoPipelinesApiVersion;

        HttpResponse response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.GET, null);

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

    /* Create a new repository and return the Azure DevOps repository-Id.
     */
    public static String callCreateRepoApi (String azdoUser,
                                            String azdoPat,
                                            String azdoEndpoint,
                                            String azdoGitApi,
                                            String azdoGitApiVersion,
                                            String azdoGitApiRepositories,
                                            String repositoryName,
                                            String projectId) {
        logger.debug("==> Method: AzDoUtils.callCreateRepoApi");

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
        HttpResponse response = callApi(azdoUser, azdoPat, http, HttpMethod.POST, json);

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

    /* Update a repository with a new default branch.
     */
    public static String callUpdateRepoApi (String azdoUser,
                                            String azdoPat,
                                            String azdoEndpoint,
                                            String azdoGitApi,
                                            String azdoGitApiVersion,
                                            String azdoGitApiRepositories,
                                            String repositoryId,
                                            String branchName) {
        logger.debug("==> Method: AzDoUtils.callUpdateRepoApi");

        String http = azdoEndpoint +
                azdoGitApi +
                azdoGitApiRepositories +
                "/" + repositoryId +
                "?" +
                azdoGitApiVersion;

        String json = BRACKET_OPEN_NEXTLINE +
                TAB + DOUBLE_QUOTE + "defaultBranch" + DQUOTE_SCOL_DQUOTE + branchName + DOUBLE_QUOTE + NEXTLINE +
                BRACKET_CLOSE;
        HttpResponse response = callApi(azdoUser, azdoPat, http, HttpMethod.PATCH, json);

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

    /* Check whether a Git repository with a certain name already exists.
       If available, the repository-Id is returned.
     */
    public static String callGetRepositoryApi (String azdoUser,
                                               String azdoPat,
                                               String repositoryName,
                                               String azdoEndpoint,
                                               String azdoGitApi,
                                               String azdoGitApiVersion,
                                               String azdoGitApiRepositories) {
        logger.debug("==> Method: AzDoUtils.callGetRepositoryApi");

        String repositoryId = null;
        String http = azdoEndpoint +
                azdoGitApi +
                azdoGitApiRepositories +
                "?" +
                azdoGitApiVersion;

        HttpResponse response = callApi(azdoUser, azdoPat, http, AzDoUtils.HttpMethod.GET, null);

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

    /* Utility method that returns the value of a certain key in an array.
     */
    public static String iterateYamlArrayListAndFindElement (ArrayList<Object> arr, String key, String compareKey, String val) {
        logger.debug("==> Method: AzDoUtils.iterateYamlArrayListAndFindElement");

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

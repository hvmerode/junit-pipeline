// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

import azdo.hook.Hook;
import azdo.junit.AzDoPipeline;
import azdo.junit.RunResult;
import azdo.utils.Log;
import azdo.utils.PropertyUtils;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.*;
import static azdo.utils.Constants.*;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineUnit {
    private static final Log logger = Log.getLogger();
    private static AzDoPipeline pipeline;

    @BeforeAll
    public static void setUpClass() {
        logger.debug("setUpClass");
    }

    @Test
    @Order(1)
    public void test1() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test1");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/simple-pipeline.yml");

        try {
            // Create a hook to perform an action just before starting the pipeline
            List<Hook> hookList = new ArrayList<>();
            class TestHook extends Hook {
                @Override
                public void executeHook() {
                    logger.debug("Executes hook with an argument");
                }
            }

            // Create a list with hooks and pass it to the startPipeline
            hookList.add(new TestHook());

            // Manipulate the pipeline and validate the 'testVar' and the existence of file  "output.csv"
            pipeline.resetTrigger()
                    .overrideSectionPropertySearchByTypeAndIdentifier("pool", "", "vmImage", "windows-latest")
                    .setVariableSearchStepByDisplayName ("Testing, testing", "testVar", "myReplacedValue")
                    .assertFileExistsSearchStepByDisplayName("Testing, testing", "output.csv", true)
                    .assertVariableEqualsSearchStepByDisplayName("Testing, testing", "testVar", "myReplacedValue", false)
                    .startPipeline("master", hookList);
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.failed, pipeline.getRunResult().result);
        RunResult.Result stageResult = pipeline.getRunResult().getStageResultSearchByName("simple_stage");
        Assertions.assertEquals (RunResult.Result.failed, stageResult);
        logger.info("Test successful");
        logger.info("Expected pipeline result: {}", RunResult.Result.failed);
        logger.info("Actual pipeline result: {}", pipeline.getRunResult().result);
        logger.info("Expected stage result: {}", RunResult.Result.failed);
        logger.info("Actual stage result: {}", stageResult);
    }

    @Test
    @Order(2)
    public void test2() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test2");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline-test.yml");

        try {
            String inlineScript = "echo \"This is a mock script\"\n" +
                    "echo \"This is line 2\"";
            String inlineScript2 = "echo \"This is an inserted script\"";
            pipeline.mockStepSearchByIdentifier("AWSShellScript@1", inlineScript)
                    .insertScriptSearchStepByDisplayName ("DeployStage job_xe script", inlineScript2, false)
                    .setVariableSearchStepByDisplayName ("DeployStage job_xe script", "aws_connection", "42")
                    .skipStageSearchByIdentifier("Stage_B")
                    .skipStageSearchByIdentifier("ExecuteScriptStage")
                    .assertVariableNotEqualsSearchStepByDisplayName ("DeployStage job_xd script", "myVar", "donotfail", true)
                    .startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
        logger.info("Test successful");
        logger.info("Expected: {}", RunResult.Result.succeeded);
        logger.info("Actual: {}", pipeline.getRunResult().result);
    }

    @Test
    @Order(3)
    public void test3() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test3");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline-test.yml");

        try {
            String inlineScript = "echo \"This is a mock script\"\n" +
                    "echo \"This is line 2\"";
            pipeline.mockStepSearchByIdentifier("AWSShellScript@1", inlineScript)
                    .startPipeline("myFirstFeature");
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
        logger.info("Test successful");
        logger.info("Expected: {}", RunResult.Result.succeeded);
        logger.info("Actual: {}", pipeline.getRunResult().result);
    }

    @Test
    @Order(4)
    public void test4() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test4");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline-test.yml");

        try {
            pipeline.overrideParameterDefault("sleep", "5")
                    .overrideTemplateParameter("aNiceParam", "replaced_parameter")
                    .overrideVariable("jobVar", "replacedJobVar")
                    .overrideLiteral("Job_2.Task_3: Sleep some seconds", "Sleep")
                    .overrideVariable("aws_region", "eu-west-1")
                    .skipJobSearchByIdentifier("Job_XD")
                    .setVariableSearchStepByIdentifier ("AWSShellScript@1", "aws_connection", "42")
                    .setVariableSearchTemplateByIdentifier("templates/steps/template-steps.yml", "environment", "prod")
                    .setVariableSearchTemplateByIdentifier("templates/steps/template-steps.yml", "sleep", "2", false)
                    .setVariableSearchStepByDisplayName ("ExecuteScriptStage job_xc script", "myVar", "myReplacedValue")
                    .assertVariableEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar")
                    .assertVariableNotEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar")
                    .assertVariableNotEmptySearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar")
                    .startPipeline("myFirstFeature", null, true);
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e.getMessage());
        }
    }

    @Test
    @Order(5)
    public void test5() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test5");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/bash-mock.yml");

        try {
            String[] strArr = new String[3];
            strArr[0] = "HTTP/2 200";
            strArr[1] = "HTTP/2 403";
            strArr[2] = "HTTP/2 501";
            pipeline.mockBashCommandSearchStepByDisplayName("Curl step 1 of 2", "curl", strArr)
                    .mockBashCommandSearchStepByDisplayName("Curl step 2 of 2", "curl","HTTP/2 200")
                    .mockBashCommandSearchStepByDisplayName("Wget step", "wget", "mock 100%[=================================================>]  15.01M  6.77MB/s    in 2.2s")
                    .mockBashCommandSearchStepByDisplayName("Ftp step", "ftp",  "")
                    .mockBashCommandSearchStepByDisplayName("Bash@3 task", "curl", "HTTP/2 403")
                    .startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
        logger.info("Test successful");
        logger.info("Expected: {}", RunResult.Result.succeeded);
        logger.info("Actual: {}", pipeline.getRunResult().result);
    }

    @Test
    @Order(6)
    public void test6() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test6");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/powershell-mock.yml");

        try {
            String[] strArr = new String[2];
            strArr[0] = "{\"element\" : \"value_1\"}";
            strArr[1] = "{\"element\" : \"value_2\"}";
            pipeline.mockPowerShellCommandSearchStepByDisplayName("Invoke-RestMethod step 1 of 2",
                            "Invoke-RestMethod",
                            strArr)
                    .mockPowerShellCommandSearchStepByDisplayName("Invoke-RestMethod step 2 of 2",
                            "Invoke-RestMethod",
                            strArr[1])
                    .mockPowerShellCommandSearchStepByDisplayName("PowerShell@2 task",
                            "Invoke-RestMethod",
                            "{\"element\" : \"value_3\"}")
                    .startPipeline("master");
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
        logger.info("Test successful");
        logger.info("Expected: {}", RunResult.Result.succeeded);
        logger.info("Actual: {}", pipeline.getRunResult().result);
    }

    @Test
    @Order(7)
    public void test7() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test7");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/simple-deployment.yml");

        try {
            pipeline.overrideLiteral("dev", "prod")
                    .overrideLiteral("true:", "'on':") // This is a bug in snakeyaml; it replaces "on:" with "true:"
                    .startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @Test
    @Order(8)
    public void test8() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test8");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/simple-pipeline.yml");
        Map<String, String> stageParameters = new HashMap<>();
        stageParameters.put("aNiceParam", "stage_val81");
        stageParameters.put("template", "stage_val82");
        Map<String, String> mockParameters = new HashMap<>();
        mockParameters.put("param_1", "mock_val81");
        mockParameters.put("param_2", "mock_val82");

        try {
            pipeline.insertTemplateSearchSectionByDisplayName(SECTION_STAGE, "simple_stage", "templates/stages/template-stages.yml", stageParameters, false)
                    .insertTemplateSearchSectionByDisplayName(SECTION_JOB, "simple_job", "templates/jobs/template-jobs.yml", null)
                    .insertTemplateSearchSectionByDisplayName(STEP_SCRIPT, "Testing, testing", "templates/steps/template-mock.yml", mockParameters)
                    .insertTemplateSearchSectionByDisplayName(STEP_SCRIPT, "Testing, testing", "templates/steps/template-steps.yml", null, false)
                    .startPipeline("master");
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }

        RunResult pipelineRunResult = pipeline.getRunResult();
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.result);
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getStageResultSearchByName("simple_stage"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getJobResultSearchByName("simple_job"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getStepResultSearchByName("Testing, testing"));
    }

    @Test
    @Order(9)
    public void test9() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test9");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/simple-pipeline.yml");
        Map<String, String> stageParameters = new HashMap<>();
        stageParameters.put("aNiceParam", "stage_val91");
        stageParameters.put("template", "stage_val92");
        Map<String, String> jobParameters = new HashMap<>();
        jobParameters.put("param_1", "job_val91");
        Map<String, String> stepParameters = new HashMap<>();
        stepParameters.put("param_1", "step_val91");
        stepParameters.put("param_2", "step_val92");

        try {
            // Test the insertTemplateSearchSectionByIdentifier with some combinations
            pipeline.resetTrigger()
                    .insertTemplateSearchSectionByIdentifier("simpleStage", "templates/stages/template-stages.yml", stageParameters, true)
                    .insertTemplateSearchSectionByIdentifier("simpleJob", "templates/jobs/template-jobs.yml", jobParameters, true)
                    .insertTemplateSearchSectionByIdentifier("templates/steps/template-script.yml", "templates/steps/template-mock.yml", stepParameters, false)
                    .skipStepSearchByDisplayName("Testing, testing")
                    .startPipeline("mySecondFeature");
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        RunResult pipelineRunResult = pipeline.getRunResult();
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.result);
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getStageResultSearchByName("template-stages.yml stage"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getStageResultSearchByName("simple_stage"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getJobResultSearchByName("template-stages.yml job"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getJobResultSearchByName("template-jobs.yml job"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getJobResultSearchByName("simple_job"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getStepResultSearchByName("This is script step"));
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.getStepResultSearchByName("template-mock.yml script"));
    }

    @Test
    @Order(10)
    public void test10() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test10");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/simple-pipeline.yml");

        try {
            // Test the assertParameterEqualsSearchTemplateByIdentifier and assertVariableEqualsSearchTemplateByIdentifier
            pipeline.resetTrigger()
                    .assertParameterEqualsSearchTemplateByIdentifier ("templates/steps/template-script.yml", "param_1", "default")
                    .assertVariableEqualsSearchTemplateByIdentifier ("templates/steps/template-script.yml", "testVar", "test_wrong", false)
                    .startPipeline("myFirstFeature");
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        RunResult pipelineRunResult = pipeline.getRunResult();
        Assertions.assertEquals (RunResult.Result.failed, pipelineRunResult.result);
    }

    @Test
    @Order(11)
    public void test11() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test11");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        // Include external resources (this makes them local resources in the pipeline and the templates in the local repos are executed)
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/external-resources-pipeline.yml");

        try {
            // Test the external resources
            pipeline.startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        RunResult pipelineRunResult = pipeline.getRunResult();
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.result);
    }

    @Test
    @Order(12)
    public void test12() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test12");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        // Exclude external resources (this leaves the 'resources' section intact and the external templates are not manipulated)
        PropertyUtils properties = new PropertyUtils("junit_pipeline_my.properties");
        properties.setIncludeExternalTemplates(false);
        pipeline = new AzDoPipeline(properties, "./pipeline/external-resources-pipeline.yml");

        try {
            // Test the external resources
            pipeline.startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        RunResult pipelineRunResult = pipeline.getRunResult();
        Assertions.assertEquals (RunResult.Result.succeeded, pipelineRunResult.result);
    }

    @Test
    @Order(13)
    public void test13() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test13");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        // Initialize the pipeline
        // Exclude external resources (this leaves the 'resources' section intact and the external templates are not manipulated)
        PropertyUtils properties = new PropertyUtils("junit_pipeline_my.properties");
        properties.setIncludeExternalTemplates(false);
        pipeline = new AzDoPipeline(properties, "./pipeline/external-resources-pipeline.yml");

        try {
            pipeline
                    .addPropertyToSectionSearchByTypeAndIdentifier("repository", "external", "endpoint", "p1") // Add endpoint
                    .addPropertyToSectionSearchByTypeAndIdentifier("repository", "external2", "endpoint", "p2") // Replace existing endpoint
                    .addPropertyToSectionSearchByTypeAndIdentifier("job", "externalResourcesJob", "condition", "eq(1,2)") // Disable job
                    .addPropertyToSectionSearchByTypeAndIdentifier("script", null, "enabled", "false") // Disable step
                    .startPipeline(true); // Do not run, because it would fail (no valid endpoints)
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
    }
}

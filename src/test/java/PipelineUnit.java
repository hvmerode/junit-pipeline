// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

import azdo.hook.Hook;
import azdo.junit.AzDoPipeline;
import azdo.junit.RunResult;
import azdo.utils.Log;
import org.junit.jupiter.api.*;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineUnit {
    private static Log logger = Log.getLogger();
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
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/simple-pipeline.yml", AzDoPipeline.AgentOSEnum.WINDOWS);

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
                    .assertFileExistsSearchStepByDisplayName("Testing, testing", "output.csv", false)
                    .assertVariableEqualsSearchStepByDisplayName("Testing, testing", "testVar", "myReplacedValue", false)
                    .startPipeline("master", hookList);
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.failed, pipeline.getRunResult().result);
        RunResult.Result stageResult = pipeline.getRunResult().getStageResultSearchByDisplayName("simpleStage");
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
                    .startPipeline("myFeature");
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
                    .skipSectionSearchByTypeAndIdentifier("template", "test-template.yml@external2")
                    .overrideVariable("aws_region", "eu-west-1")
                    .skipJobSearchByIdentifier("Job_XD")
                    .setVariableSearchStepByIdentifier ("AWSShellScript@1", "aws_connection", "42")
                    .setVariableSearchStepByDisplayName ("ExecuteScriptStage job_xc script", "myVar", "myReplacedValue")
                    .assertVariableEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar")
                    .assertVariableNotEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar")
                    .assertVariableNotEmptySearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar")
                    .startPipeline("myFeature", null, true);
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.none, pipeline.getRunResult().result);
        logger.info("Test successful");
        logger.info("Expected: {}", RunResult.Result.none);
        logger.info("Actual: {}", pipeline.getRunResult().result);
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
        logger.info("Expected: {}", RunResult.Result.failed);
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
                    .startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }
}

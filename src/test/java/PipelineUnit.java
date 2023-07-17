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

import static azdo.junit.AzDoPipeline.BASH_COMMAND.*;
import static azdo.junit.AzDoPipeline.POWERSHELL_COMMAND.INVOKE_RESTMETHOD;

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
            pipeline.overrideSectionPropertySearchByTypeAndIdentifier("pool", "", "vmImage", "windows-latest")
                    .setVariableSearchStepByDisplayName ("Testing, testing", "testVar", "myReplacedValue")
                    .assertFileNotExistsSearchStepByDisplayName("Testing, testing", "output.csv", false)
                    .assertNotEqualsSearchStepByDisplayName("Testing, testing", "testVar", "myReplacedValue", false)
                    .startPipeline("master", hookList);
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e.getMessage());
        }
        Assertions.assertEquals (RunResult.Result.failed, pipeline.getRunResult().result);
        logger.info("Test successful");
        logger.info("Expected: {}", RunResult.Result.failed);
        logger.info("Actual: {}", pipeline.getRunResult().result);
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
            pipeline.mockStepSearchByIdentifier("AWSShellScript@1", inlineScript)
                    .skipStageSearchByIdentifier("Stage_B")
                    .skipStageSearchByIdentifier("ExecuteScriptStage")
                    .assertEqualsSearchStepByDisplayName ("DeployStage job_xd script", "myVar", "donotfail", true)
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
                    .assertNotEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar")
                    .assertEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar")
                    .assertEmptySearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar")
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
            pipeline.mockBashCommandSearchStepByDisplayName("Curl step", CURL,"HTTP/2 501")
                    .mockBashCommandSearchStepByDisplayName("Wget step", WGET, "mock 100%[=================================================>]  15.01M  6.77MB/s    in 2.2s")
                    .mockBashCommandSearchStepByDisplayName("Ftp step", FTP,  "")
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
            pipeline.mockPowershellCommandSearchStepByDisplayName("Invoke-RestMethod step", INVOKE_RESTMETHOD, "This is a mock Invoke-RestMethod")
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
}

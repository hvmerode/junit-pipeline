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
            class TestHook extends Hook {
                @Override
                public void executeHook() {
                    logger.debug("Executes hook with an argument");
                }
            }

            // Create a list with hooks and pass it to the startPipeline
            // Note: The startPipeline has a dryRun = true setting, meaning that it does not start the pipeline in AzUre DevOps
            // This is temporary, and only used for testing
            List<Hook> hookList = new ArrayList<>();
            hookList.add(new TestHook());

            pipeline.overrideSectionPropertySearchByTypeAndIdentifier("pool", "", "vmImage", "windows-latest");
            pipeline.setVariableSearchStepByDisplayName ("Testing, testing", "testVar", "myReplacedValue");
            pipeline.assertNotEqualsSearchStepByDisplayName("Testing, testing", "testVar", "myReplacedValue", false);
            pipeline.assertFileNotExistsSearchStepByDisplayName("Testing, testing", "test.txt", false);
            pipeline.startPipeline("master", hookList, false);
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e);
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

        String inlineScript = "echo \"This is a mock script\"\n" +
                "echo \"This is line 2\"";
        pipeline.mockStepSearchByIdentifier("AWSShellScript@1", inlineScript);
        pipeline.skipStageSearchByIdentifier("Stage_B");
        pipeline.skipStageSearchByIdentifier("ExecuteScriptStage");
        pipeline.assertEqualsSearchStepByDisplayName ("DeployStage job_xd script", "myVar", "donotfail", true);

        try {
            pipeline.startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e);
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

        String inlineScript = "echo \"This is a mock script\"\n" +
                "echo \"This is line 2\"";
        pipeline. mockStepSearchByIdentifier("AWSShellScript@1", inlineScript);

        try {
            pipeline.startPipeline("myFeature");
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e);
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
            pipeline.overrideParameterDefault("sleep", "5");
            pipeline.overrideTemplateParameter("aNiceParam", "replaced_parameter");
            pipeline.overrideVariable("jobVar", "replacedJobVar"); // There 3 occurrences of jobVar
            pipeline.overrideLiteral("Job_2.Task_3: Sleep some seconds", "Sleep");
            pipeline.skipSectionSearchByTypeAndIdentifier("template", "test-template.yml@external2");
            pipeline.overrideVariable("aws_region", "eu-west-1");
            pipeline.skipJobSearchByIdentifier("Job_XD");
            pipeline.setVariableSearchStepByIdentifier ("AWSShellScript@1", "aws_connection", "42");
            pipeline.setVariableSearchStepByDisplayName ("ExecuteScriptStage job_xc script", "myVar", "myReplacedValue");
            pipeline.assertNotEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar");
            pipeline.assertEqualsSearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar", "replacedJobVar");
            pipeline.assertEmptySearchStepByDisplayName("ExecuteScriptStage job_xa script", "jobVar");
            pipeline.startPipeline("myFeature", null, true);
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e);
        }
        Assertions.assertEquals (RunResult.Result.none, pipeline.getRunResult().result);
        logger.info("Test successful");
        logger.info("Expected: {}", RunResult.Result.none);
        logger.info("Actual: {}", pipeline.getRunResult().result);
    }

    @AfterAll
    public static void tearDown() {
        logger.debug("\ntearDown");
    }
}
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

        // Initialize the pipeline (resource path is default)
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline_test.yml");
    }
    @Test
    @Order(1)
    public void test1() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test1");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        String inlineScript = "echo \"This is a mock script\"\n" +
                "echo \"This is line 2\"";
        pipeline.mockStep("AWSShellScript@1", inlineScript);

        try {
            pipeline.startPipeline();
        }
        catch (IOException e) {
            logger.debug("Exception occurred after the pipeline was started: {}", e);
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @Test
    @Order(2)
    public void test2() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test2");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        String inlineScript = "echo \"This is a mock script\"\n" +
                "echo \"This is line 2\"";
        pipeline.mockStep("AWSShellScript@1", inlineScript);

        try {
            pipeline.startPipeline("myFeature");
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e);
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @Test
    @Order(3)
    public void test3() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test3");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        try {
            pipeline.overrideParameterDefault("sleep", "5");
            pipeline.overrideTemplateParameter("aNiceParam", "replaced_parameter");
            pipeline.overrideVariable("jobVar", "replacedJobVar");
            pipeline.overrideLiteral("Job_2.Task_3: Sleep some seconds", "Sleep");
            pipeline.skipSectionSearchByTypeAndIdentifier("template", "test-template.yml@external2");
            pipeline.overrideVariable("aws_region", "eu-west-1");
            pipeline.skipJobSearchByIdentifier("Job_XD");
            pipeline.setVariableBeforeStepSearchByIdentifier ("AWSShellScript@1", "aws_connection", "42");
//            pipeline.setVariableBeforeStepSearchByDisplayName ("DeployStage job_xe AWSShellScript", "aws_connection", "42");

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
            pipeline.startPipeline("myFeature", hookList, true);
        }
        catch (IOException e) {
            logger.debug("Exception occurred: {}", e);
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @AfterAll
    public static void tearDown() {
        logger.debug("\ntearDown");
    }
}
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

        // Add commands to the bundle. These commands are executed for every test, so you only have to do it once
        // Always replace template-steps.yml with a mock template
        pipeline.commandBundle.overrideLiteral("templates/steps/template-steps.yml", "templates/steps/template-mock.yml");
    }
    @Test
    @Order(1)
    public void test1() {
        logger.debug("");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.debug("Perform unittest: test1");
        logger.debug("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        pipeline.overrideTemplateParameter("aNiceParam", "aNiceValue");
        pipeline.overrideLiteral("This is step 1 of file template-steps.yml with", "This is", true);
        pipeline.overrideLiteral("project <Templates>", "project <Templates> !!!!!!");
        pipeline.overrideParameterDefault("aNiceParam", "aNiceDefault");
        pipeline.overrideParameterDefault("param_1", "xxx");
        pipeline.overrideParameterDefault("sleep", "5");
        pipeline.overrideVariable("test", "replaced");
        pipeline.overrideVariable("aws_connection", "9999999999");
        pipeline.overrideVariable("aws_region", "eu-west-1");
        pipeline.skipJob("ScriptJob");

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
            // TESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTEST
            //pipeline.skipStage("ExecuteScriptStage");
            //pipeline.skipStage("DeployStage");
            //pipeline.overrideVariable("jobVar", "666");
            //pipeline.overrideVariable("aws_connection", "999");
            //pipeline.skipStageByDisplayName("The executeScriptStage");
            //pipeline.skipSection("template", "templates/stages/template-stages.yml");
            //pipeline.overrideParameterDefault("environment", "prod");

            //pipeline.setVariableBeforeStep ("AWSShellScript@1", "aws_connection", "666");
            pipeline.setVariableBeforeStepByDisplayName ("DeployStage job_xd script", "aws_connection", "666");
            // TESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTESTTEST

            // Create a hook to perform an action just before starting the pipeline
            class TestHook extends Hook {
                @Override
                public void executeHook() {
                    logger.debug("Executes hook with an argument");
                }
            }
            //pipeline.skipStage("ExecuteScriptStage");
            //pipeline.skipStage("DeployStage");

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
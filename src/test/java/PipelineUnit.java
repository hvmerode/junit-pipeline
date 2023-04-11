// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

import azdo.hook.Hook;
import azdo.junit.AzDoPipeline;
import azdo.junit.RunResult;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineUnit {
    private static Logger logger = LoggerFactory.getLogger(PipelineUnit.class);
    private static AzDoPipeline pipeline;

    @BeforeAll
    public static void setUpClass() {
        logger.info("setUpClass");

        // Initialize the pipeline (resource path is default)
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline_test.yml");

        // Add commands to the bundle. These commands are executed for every test, so you only have to do it once
        // Always replace template-steps_1.yml with a mock template
        pipeline.commandBundle.overrideLiteral("templates/steps/template-steps_1.yml", "templates/steps/template-mock.yml");
    }

    @Test
    @Order(1)
    public void test1() {
        logger.info("");
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.info("Perform unittest: test1");
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        pipeline.overrideTemplateParameter("aNiceParam", "aNiceValue");
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
            e.printStackTrace();
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @Test
    @Order(2)
    public void test2() {
        logger.info("");
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.info("Perform unittest: test2");
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        String inlineScript = "echo \"This is a mock script\"\n" +
                "echo \"This is line 2\"";
        pipeline.mockStep("AWSShellScript@1", inlineScript);

        try {
            pipeline.startPipeline("myFeature");
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @Test
    @Order(3)
    public void test3() {
        logger.info("");
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        logger.info("Perform unittest: test3");
        logger.info("~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");

        try {
            // Create a hook to perform an action just before starting the pipeline
            class TestHook extends Hook {
                @Override
                public void executeHook() {
                    super.executeHook();
                    logger.info("Executes hook with an argument");
                }
            }
            pipeline.skipStage("ExecuteScriptStage");
            pipeline.skipStage("DeployStage");

            // Create a list with hooks and pass it to the startPipeline
            List<Hook> hookList = new ArrayList<>();
            hookList.add(new TestHook());
            pipeline.startPipeline("myFeature", hookList);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @AfterAll
    public static void tearDown() {
        logger.info("\ntearDown");
    }
}
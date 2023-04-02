import azdo.junit.AzDoPipeline;
import azdo.junit.RunResult;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PipelineUnit {
    private static Logger logger = LoggerFactory.getLogger(PipelineUnit.class);
    private static AzDoPipeline pipeline;

    @BeforeAll
    public static void setUpClass() {
        System.out.println("setUpClass");

        // Initialize the pipeline (resource path is default)
        pipeline = new AzDoPipeline("junit_pipeline_my.properties", "./pipeline/pipeline_test_3.yml");

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

        //pipeline.skipStage("ExecuteScriptStage");
        pipeline.skipJob("ScriptJob");

        String inlineScript = "echo \"This is a mock script\"\n" +
                "echo \"This is line 2\"";
        pipeline.mockStep("AWSShellScript@1", inlineScript);

        try {
            //pipeline.startPipeline("test2");
            pipeline.startPipeline();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        Assertions.assertEquals (RunResult.Result.succeeded, pipeline.getRunResult().result);
    }

    @AfterAll
    public static void tearDown() {
        System.out.println("\ntearDown");
    }

}

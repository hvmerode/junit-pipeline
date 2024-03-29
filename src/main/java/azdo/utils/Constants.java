package azdo.utils;

/******************************************************************************************
 Constants, used throughout the junit-pipeline code.
 *******************************************************************************************/
public class Constants {
    // *************************************** Types in YAML files ***************************************
    // *************************************** Sections ***************************************
    public static final String SECTION_TRIGGER = "trigger";
    public static final String SECTION_VARIABLES = "variables";
    public static final String SECTION_PARAMETERS = "parameters";
    public static final String SECTION_STAGES = "stages";
    public static final String SECTION_STAGE = "stage";
    public static final String SECTION_JOBS = "jobs";
    public static final String SECTION_JOB = "job";
    public static final String SECTION_STEPS = "steps";
    public static final String SECTION_STEP = "step";
    public static final String SECTION_TASK = "task";
    public static final String STEP_SCRIPT = "script";
    public static final String SCRIPT = "script";
    public static final String STEP_SCRIPT_PWSH = "pwsh";
    public static final String TASK_POWERSHELL_2 = "PowerShell@2";
    public static final String TASK_BASH_3 = "Bash@3";
    public static final String STEP_SCRIPT_BASH = "bash";
    public static final String SECTION_TEMPLATE = "template";

    // *************************************** Other ***************************************
    public static final String IDENTIFIER_NAME = "name";
    public static final String PROPERTY_DISPLAY_NAME = "displayName";
    public static final String PROPERTY_VARIABLE_GROUP = "group";
    public static final String PROPERTY_ENVIRONMENT = "environment";
    public static final String INPUTS = "inputs";
    public static final String CONDITION = "condition";
    public static final String TYPE_VARIABLE = "variable";
    public static final String TYPE_PARAMETER = "parameter";
    public static final String TYPE_STEP = SECTION_STEP;
    public static final String TYPE_TEMPLATE = SECTION_TEMPLATE;

    // *************************************** Colors ***************************************
    public static final String RED = "\u001B[31m";
    public static final String GREEN = "\u001B[32m";
    public static final String YELLOW = "\u001B[33m";
    public static final String BLUE = "\u001B[34m";
    public static final String PURPLE = "\u001B[35m";
    public static final String CYAN = "\u001B[36m";
    public static final String WHITE = "\u001B[37m";
    public static final String LIGHT_RED = "\u001B[91m";
    public static final String LIGHT_GREEN = "\u001B[92m";
    public static final String LIGHT_YELLOW = "\u001B[93m";
    public static final String LIGHT_WHITE = "\u001B[97m";
    public static final String RESET_COLOR = "\u001B[0m";

    // *************************************** Files ***************************************
    public static final String JSON_SCHEMA = "azure-pipelines-schema.json";

    // *************************************** Visualization ***************************************
    public static final String DEMARCATION = "==============================================================================";
    public static final String HEADER_FOOTER = "--------------------------------------------------------------------------------------------------------------------------------";
    public static final String ARROW_DOWN = "▼";
}

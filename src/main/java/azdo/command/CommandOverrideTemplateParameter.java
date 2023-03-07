package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideTemplateParameter implements Command{
    private String parameterName;
    private String value;
    private boolean replaceAll = true;

    public CommandOverrideTemplateParameter(String parameterName, String value, boolean replaceAll){
        this.parameterName = parameterName;
        this.value = value;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideTemplateParameter(String parameterName, String value){
        this(parameterName, value, true);
    }

    public void execute(Pipeline pipeline) {
        pipeline.overrideTemplateParameter(parameterName, value);
    }
}

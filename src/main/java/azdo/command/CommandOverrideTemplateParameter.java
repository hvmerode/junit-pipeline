package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandOverrideTemplateParameter extends Command{
    private String parameterName;
    private String value;

    public CommandOverrideTemplateParameter(String parameterName, String value, boolean replaceAll){
        this.parameterName = parameterName;
        this.value = value;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideTemplateParameter(String parameterName, String value){
        this(parameterName, value, true);
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.overrideTemplateParameter(parameterName, value);
    }
}

package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideParameterDefault implements Command {
    private String parameterName;
    private String value;

    public CommandOverrideParameterDefault(String parameterName, String value){
        this.parameterName = parameterName;
        this.value = value;
    }
    public void execute(Pipeline pipeline) {
        pipeline.overrideParameterDefault(parameterName, value);
    }
}

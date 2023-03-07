package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideParameterDefault implements Command {
    private String parameterName;
    private String value;
    private boolean replaceAll = true;

    public CommandOverrideParameterDefault(String parameterName, String value, boolean replaceAll){
        this.parameterName = parameterName;
        this.value = value;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideParameterDefault(String parameterName, String value){
        this(parameterName, value, true);
    }

    public void execute(Pipeline pipeline) {
        pipeline.overrideParameterDefault(parameterName, value);
    }
}

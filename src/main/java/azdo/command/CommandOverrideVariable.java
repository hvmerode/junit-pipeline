package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideVariable implements Command {
    private String variableName;
    private String value;
    private boolean replaceAll = true;

    public CommandOverrideVariable(String variableName, String value, boolean replaceAll){
        this.variableName = variableName;
        this.value = value;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideVariable(String variableName, String value){
        this(variableName, value, true);
    }

    public void execute(Pipeline pipeline) {
        pipeline.overrideVariable(variableName, value);
    }
}

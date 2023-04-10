package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideVariable implements Command {
    private String variableName;
    private String value;

    public CommandOverrideVariable(String variableName, String value){
        this.variableName = variableName;
        this.value = value;
    }

    public void execute(Pipeline pipeline) {
        pipeline.overrideVariable(variableName, value);
    }
}

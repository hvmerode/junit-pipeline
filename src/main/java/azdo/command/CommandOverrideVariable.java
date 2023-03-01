package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandOverrideVariable extends Command {
    private String variableName;
    private String value;

    public CommandOverrideVariable(String variableName, String value, boolean replaceAll){
        this.variableName = variableName;
        this.value = value;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideVariable(String variableName, String value){
        this(variableName, value, true);
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.overrideVariable(variableName, value);
    }
}

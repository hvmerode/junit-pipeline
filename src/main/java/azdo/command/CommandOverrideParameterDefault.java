package azdo.command;
import azdo.junit.AzDoPipeline;

public class CommandOverrideParameterDefault extends Command {
    private String parameterName;
    private String value;

    public CommandOverrideParameterDefault(String parameterName, String value, boolean replaceAll){
        this.parameterName = parameterName;
        this.value = value;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideParameterDefault(String parameterName, String value){
        this(parameterName, value, true);
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.overrideParameterDefault(parameterName, value);
    }
}

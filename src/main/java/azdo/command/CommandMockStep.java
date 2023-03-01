package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandMockStep extends Command {
    private String stepValue;
    private String inlineScript;

    public CommandMockStep(String stepValue, String inlineScript) {
        this.stepValue = stepValue;
        this.inlineScript = inlineScript;
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.mockStep(stepValue, inlineScript);
    }
}

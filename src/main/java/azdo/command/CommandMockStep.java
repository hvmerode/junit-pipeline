package azdo.command;

import azdo.junit.Pipeline;

public class CommandMockStep implements Command {
    private String stepValue;
    private String inlineScript;

    public CommandMockStep(String stepValue, String inlineScript) {
        this.stepValue = stepValue;
        this.inlineScript = inlineScript;
    }

    public void execute(Pipeline pipeline) {
        pipeline.mockStep(stepValue, inlineScript);
    }
}

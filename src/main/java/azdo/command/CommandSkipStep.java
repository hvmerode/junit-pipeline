package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandSkipStep extends Command {
    private String stepName;

    public CommandSkipStep(String stepName) {
        this.stepName = stepName;
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.skipStep(stepName);
    }
}

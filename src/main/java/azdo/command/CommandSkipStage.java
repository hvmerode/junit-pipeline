package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandSkipStage extends Command {
    private String stageName;

    public CommandSkipStage(String stageName) {
        this.stageName = stageName;
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.skipStage(stageName);
    }
}

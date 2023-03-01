package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandSkipJob extends Command {
    private String jobName;

    public CommandSkipJob(String jobName) {
        this.jobName = jobName;
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.skipJob(jobName);
    }
}

package azdo.command;

import azdo.junit.Pipeline;

public class CommandSkipJob implements Command {
    private String jobName;

    public CommandSkipJob(String jobName) {
        this.jobName = jobName;
    }

    public void execute(Pipeline pipeline) {
        pipeline.skipJob(jobName);
    }
}

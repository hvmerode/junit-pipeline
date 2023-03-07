package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideCurrentBranch implements Command {
    private String newBranchName;
    private boolean replaceAll = true;

    public CommandOverrideCurrentBranch(String newBranchName, boolean replaceAll) {
        this.newBranchName = newBranchName;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideCurrentBranch(String newBranchName) {
        this(newBranchName, true);
    }

    public void execute(Pipeline pipeline)
    {
        pipeline.overrideCurrentBranch(newBranchName, replaceAll);
    }
}

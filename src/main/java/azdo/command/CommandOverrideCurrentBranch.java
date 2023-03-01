package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandOverrideCurrentBranch extends Command {
    private String newBranchName;

    public CommandOverrideCurrentBranch(String newBranchName, boolean replaceAll) {
        this.newBranchName = newBranchName;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideCurrentBranch(String newBranchName) {
        this(newBranchName, true);
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.overrideCurrentBranch(newBranchName, replaceAll);
    }
}

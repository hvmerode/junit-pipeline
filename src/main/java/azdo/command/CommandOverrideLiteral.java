package azdo.command;

import azdo.junit.AzDoPipeline;

public class CommandOverrideLiteral extends Command {
    private String findLiteral;
    private String replaceLiteral;

    public CommandOverrideLiteral(String findLiteral, String replaceLiteral, boolean replaceAll) {
        this.findLiteral = findLiteral;
        this.replaceLiteral = replaceLiteral;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideLiteral(String findLiteral, String replaceLiteral) {
        this(findLiteral, replaceLiteral, true);
    }

    public void execute(AzDoPipeline azDoPipeline) {
        azDoPipeline.overrideLiteral(findLiteral, replaceLiteral, replaceAll);
    }
}

package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideLiteral implements Command {
    private String findLiteral;
    private String replaceLiteral;
    private boolean replaceAll = true;

    public CommandOverrideLiteral(String findLiteral, String replaceLiteral, boolean replaceAll) {
        this.findLiteral = findLiteral;
        this.replaceLiteral = replaceLiteral;
        this.replaceAll = replaceAll;
    }
    public CommandOverrideLiteral(String findLiteral, String replaceLiteral) {
        this(findLiteral, replaceLiteral, true);
    }

    public void execute(Pipeline pipeline) {
        System.out.println("==> CommandOverrideLiteral: " + this.toString()); // TEST
        System.out.println("==> With pipeline: " + pipeline.toString()); // TEST
        pipeline.overrideLiteral(findLiteral, replaceLiteral, replaceAll);
    }
}

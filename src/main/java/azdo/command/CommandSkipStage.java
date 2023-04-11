// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.command;

import azdo.junit.AzDoPipeline;
import azdo.junit.Pipeline;

public class CommandSkipStage implements Command {
    private String stageName;

    public CommandSkipStage(String stageName) {
        this.stageName = stageName;
    }

    public void execute(Pipeline pipeline) {
        pipeline.skipStage(stageName);
    }
}

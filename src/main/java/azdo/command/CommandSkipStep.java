// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.command;

import azdo.junit.Pipeline;

public class CommandSkipStep implements Command {
    private String stepName;

    public CommandSkipStep(String stepName) {
        this.stepName = stepName;
    }

    public void execute(Pipeline pipeline) {
        pipeline.skipStep(stepName);
    }
}

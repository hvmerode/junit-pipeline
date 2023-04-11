// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.command;

import azdo.junit.Pipeline;

public interface Command {
    public void execute (Pipeline pipeline);
}

// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.command;

import azdo.junit.Pipeline;

public class CommandOverrideTemplateParameter implements Command{
    private String parameterName;
    private String value;

    public CommandOverrideTemplateParameter(String parameterName, String value){
        this.parameterName = parameterName;
        this.value = value;
    }

    public void execute(Pipeline pipeline) {
        pipeline.overrideTemplateParameter(parameterName, value);
    }
}

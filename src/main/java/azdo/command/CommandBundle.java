// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.command;

import azdo.junit.Pipeline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;

/*
    The CommandBundle is used to store a list of commands that are executed later. These commands manipulate the
    main YAML file and the template files, similar to the corresponding methods in the AzDoPipeline class.
    It is used to specify a group of commands only once. In each JUnit test these commands are executed as part of
    the startPipeline method in AzDoPipeline.
 */
public class CommandBundle {
    private static Logger logger = LoggerFactory.getLogger(CommandBundle.class);
    private ArrayList<Command> commands = new ArrayList<>();

    public void overrideVariable(String variableName, String value) {
        logger.debug("==> Method CommandBundle.overrideVariable");
        CommandOverrideVariable command = new CommandOverrideVariable(variableName, value);
        commands.add(command);
    }

    public void overrideParameterDefault(String parameterName, String value) {
        logger.debug("==> Method CommandBundle.overrideVariable");
        CommandOverrideParameterDefault command = new CommandOverrideParameterDefault(parameterName, value);
        commands.add(command);
    }

    public void overrideTemplateParameter(String parameterName, String value) {
        logger.debug("==> Method CommandBundle.overrideTemplateParameter");
        CommandOverrideTemplateParameter command = new CommandOverrideTemplateParameter(parameterName, value);
        commands.add(command);
    }

    public void overrideLiteral(String findLiteral, String replaceLiteral, boolean replaceAll) {
        logger.debug("==> Method CommandBundle.overrideLiteral");
        CommandOverrideLiteral command = new CommandOverrideLiteral(findLiteral, replaceLiteral, replaceAll);
        commands.add(command);
    }
    public void overrideLiteral(String findLiteral, String replaceLiteral) {
        overrideLiteral(findLiteral, replaceLiteral, true);
    }

    public void overrideCurrentBranch(String newBranchName, boolean replaceAll){
        logger.debug("==> Method CommandBundle.overrideCurrentBranch");
        CommandOverrideCurrentBranch command = new CommandOverrideCurrentBranch(newBranchName, replaceAll);
        commands.add(command);
    }

    public void overrideCurrentBranch(String newBranchName) {
        overrideCurrentBranch(newBranchName, true);
    }

    public void skipStage(String stageName) {
        logger.debug("==> Method CommandBundle.skipStage");
        CommandSkipStage command = new CommandSkipStage(stageName);
        commands.add(command);
    }

    public void skipJob(String jobName) {
        logger.debug("==> Method CommandBundle.skipJob");
        CommandSkipJob command = new CommandSkipJob(jobName);
        commands.add(command);
    }

    public void skipStep(String stepName) {
        logger.debug("==> Method CommandBundle.skipStep");
        CommandSkipStep command = new CommandSkipStep(stepName);
        commands.add(command);
    }

    public void mockStep(String stepValue, String inlineScript){
        logger.debug("==> Method CommandBundle.mockStep");
        CommandMockStep command = new CommandMockStep(stepValue, inlineScript);
        commands.add(command);
    }

    // Execute all commands
    public void execute (Pipeline pipeline){
        logger.debug("==> Method CommandBundle.execute");
        int index = 0;
        int size = commands.size();
        if (size == 0) {
            logger.debug("==> No commands in this bundle");
            return;
        }
        Command command = null;
        for (index = 0; index < size; index++) {
            logger.debug("==> Executing command: " + commands.get(index).toString());
            command = commands.get(index);
            command.execute(pipeline);
        }
    }
}

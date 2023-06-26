package azdo.action;

import azdo.yaml.ActionResult;

import java.util.Map;

public interface Action {

    // Executes the action
    void execute (ActionResult actionResult);

    // You can ask a question to the action; does it need a section name to be executed or is a section type sufficient
    boolean needsSectionIdentifier ();
}

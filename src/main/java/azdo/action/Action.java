package azdo.action;

import azdo.yaml.ActionResult;

import java.util.Map;

public interface Action {
    public enum ACTION {INSERT_SECTION, UPDATE_SECTION, DELETE_SECTION, INSERT_SECTION_LINE, GET_PROPERTY};
    // Executes the action
    void execute (ActionResult actionResult);

    // You can ask a question to the action; does it need a section name to be executed or is a section type sufficient
    boolean needsSectionIdentifier ();

    // A custom action does not follow the pattern of regular actions
    boolean isCustomAction ();
}

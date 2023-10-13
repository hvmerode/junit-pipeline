package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

import static azdo.utils.Constants.SECTION_TRIGGER;

public class ActionResetTrigger implements Action {
    private static Log logger = Log.getLogger();

    public ActionResetTrigger() {}

    /******************************************************************************************
     Perform an action on the 'trigger' section (replace with new trigger section).
     @param actionResult Contains parts of the YAML structure. It is used to search for the
     section in the l3 structure.
     ******************************************************************************************/
    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionResetTrigger.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);

        if (actionResult.l1 == null) {
            logger.debug("actionResult.l1 is null; return");
            return;
        }

        if (actionResult.l1 instanceof Map) {
            logger.info("Reset trigger to \'none\'");
            Map<String, Object> map = (Map<String, Object>) actionResult.l1;
            if (map.containsKey(SECTION_TRIGGER))
                map.put(SECTION_TRIGGER, "none");
        }
    }

    // This action can only be executed if the section type and section identification are found in the YAML file
    public boolean needsSectionIdentifier() {
        return false;
    }

    // This action is not a custom action
    public boolean isCustomAction () { return true; }
}

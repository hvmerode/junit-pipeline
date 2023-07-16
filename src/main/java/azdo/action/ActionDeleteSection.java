package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;
import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 @deprecated
 This class is used to delete a section. This section is searched using 'sectionType'
 and 'sectionIdentifier'. For example:
 Assume, that 'sectionType' has the value "stage", and 'sectionIdentifier' has the
 value "mystage".
 The stage with the identifier "mystage" is searched in the yaml pipeline.
 If found, the stage section is deleted from the yaml.
 ******************************************************************************************/
public class ActionDeleteSection implements Action {
    private static Log logger = Log.getLogger();
    private String sectionType; // Is "stage", "job", "script"
    private String sectionIdentifier; // Identifier of the section

    public ActionDeleteSection(String sectionType, String sectionIdentifier)
    {
        this.sectionType = sectionType;
        this.sectionIdentifier = sectionIdentifier;
    }

    public void execute (ActionResult actionResult)
    {
        logger.debug("==> Method ActionDeleteSection.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.l3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        if (actionResult.l3 instanceof ArrayList) {
            logger.debug("l3 is instance of ArrayList");

            // Run through the elements of the list and remove the section
            ArrayList<Object> list = (ArrayList<Object>) actionResult.l3;
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                if (list.get(index) instanceof Map) {
                    logger.debug("list.get(index) is instance of ArrayList");

                    Map<String, Object> map = (Map<String, Object>) list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        // Check whether the entry has the given key and value
                        // Delete the entry from the list if this is the case
                        logger.debug("entry.getKey(): {}", entry.getKey());
                        logger.debug("entry.getValue(): {}", entry.getValue());
                        if (sectionType.equals(entry.getKey()) && sectionIdentifier.equals(entry.getValue())) {
                            logger.info("Skip section type \'{}\' with sectionIdentifier \'{}\'", sectionType, sectionIdentifier);
                            list.remove(index);
                            actionResult.actionExecuted = true;
                            return;
                        }
                    }
                }
            }
        }
        return;
    }

    // This action can only be executed if the section type and section identification are found in the YAML file
    public boolean needsSectionIdentifier() {
        return true;
    }
}

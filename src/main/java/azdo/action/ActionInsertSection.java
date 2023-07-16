package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.*;

/******************************************************************************************
 @deprecated
 This class is used to insert a section before or after another section. This section is
 searched using the 'sectionType' and 'sectionIdentifier'. For example:
 Assume, that 'sectionType' has the value "stage" and 'sectionIdentifier' has the value
 "mystage". The stage with the name (identifier) "mystage" is searched in the yaml pipeline.
 If found, a section - defined by  'sectionToInsert' - is inserted before or after "mystage",
 depending on the value of 'insertBefore'.

 The variable 'sectionToInsert' is of type Map, because this is the way how a section is
 represented in a yaml object in Snakeyaml.
 ******************************************************************************************/
public class ActionInsertSection implements Action {
    private static Log logger = Log.getLogger();
    private String sectionType; // Is "stage", "job", "script"
    private String sectionIdentifier; // Identifier of the section
    private Map<String, Object> sectionToInsert; // The section in YAML inserted before or after the section found

    // If 'true', the 'sectionToInsert' YAML string is inserted before the given section. If 'false',
    // the 'sectionToInsert' YAML is inserted after the given section.
    private boolean insertBefore = true;

    public ActionInsertSection (String sectionType,
                                String sectionIdentifier,
                                Map<String, Object> sectionToInsert,
                                boolean insertBefore)
    {
        this.sectionType = sectionType;
        this.sectionIdentifier = sectionIdentifier;
        this.sectionToInsert = sectionToInsert;
        this.insertBefore = insertBefore;
    }

    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionInsertSection.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.l3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        logger.debug("sectionToInsert: {}", sectionToInsert);
        logger.debug("insertBefore: {}", insertBefore);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        if (actionResult.l3 instanceof ArrayList) {
            logger.debug("l3 is instance of ArrayList");

            // Run through the elements of the list and insert the new section
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
                            if (insertBefore) {
                                logger.info("Insert a new section before section \'{}\' with identifier \'{}\'", sectionType, sectionIdentifier);
                                list.add(index, sectionToInsert);
                                actionResult.actionExecuted = true;
                            }
                            else {
                                logger.info("Insert a new section after section \'{}\' with identifier \'{}\'", sectionType, sectionIdentifier);
                                list.add(index + 1, sectionToInsert);
                                actionResult.actionExecuted = true;
                            }
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

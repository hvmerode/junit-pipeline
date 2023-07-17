package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;
import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is used to perform an action on a section. This section is searched using
 'sectionType' and 'sectionIdentifier'. For example:
 Assume, that 'sectionType' has the value "stage", and 'sectionIdentifier' has the
 value "mystage".
 The stage with the identifier "mystage" is searched in the yaml pipeline.
 If found, the stage section is, for example, deleted from the yaml if the action is DELETE_SECTION.
 ******************************************************************************************/
public class ActionOnSection implements Action {
    private static Log logger = Log.getLogger();
    private ACTION action; // The action on a section
    private String sectionType; // Is "stage", "job", "script"
    private String sectionIdentifier; // Identifier of the section
    // newSection is only needed in combination with ACTION_UPDATE and ACTION_INSERT
    private Map<String, Object> newSection; // The new section in YAML

    // insertBefore is only used in combination with action == INSERT
    // If 'true', the 'sectionToInsert' YAML string is inserted before the given section. If 'false',
    // the 'sectionToInsert' YAML is inserted after the given section.
    private boolean insertBefore = true;

    public ActionOnSection(ACTION action,
                           String sectionType,
                           String sectionIdentifier,
                           Map<String, Object> newSection,
                           boolean insertBefore)
    {
        this.action = action;
        this.sectionType = sectionType;
        this.sectionIdentifier = sectionIdentifier;
        this.newSection = newSection;
        this.insertBefore = insertBefore;
    }

    /******************************************************************************************
     Perform an action on a section. The action properties are sey during creation of the object.
     @param actionResult Contains parts of the YAML structure. It is used to search for the
     section in the l3 structure.
     ******************************************************************************************/
    public void execute (ActionResult actionResult)
    {
        logger.debug("==> Method ActionDeleteSection.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.l3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        logger.debug("newSection: {}", newSection);
        logger.debug("insertBefore: {}", insertBefore);

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
                            logger.debug("Found section type \'{}\' sectionIdentifier \'{}\'", sectionType, sectionIdentifier);
                            switch (action) {
                                case INSERT_SECTION: {
                                    if (insertBefore) {
                                        logger.info("Insert a new section before section \'{}\' with identifier \'{}\'", sectionType, sectionIdentifier);
                                        list.add(index, newSection);
                                        actionResult.actionExecuted = true;
                                    }
                                    else {
                                        logger.info("Insert a new section after section \'{}\' with identifier \'{}\'", sectionType, sectionIdentifier);
                                        list.add(index + 1, newSection);
                                        actionResult.actionExecuted = true;
                                    }
                                    return;
                                }
                                case UPDATE_SECTION: {
                                    logger.info("Replace section type \'{}\' with sectionIdentifier \'{}\'", sectionType, sectionIdentifier);
                                    list.remove(index);
                                    list.add(index, newSection);
                                    actionResult.actionExecuted = true;
                                    return;
                                }
                                case DELETE_SECTION: {
                                    logger.info("Skip section type \'{}\' with sectionIdentifier \'{}\'", sectionType, sectionIdentifier);
                                    list.remove(index);
                                    actionResult.actionExecuted = true;
                                    return;
                                }
                                default: {
                                    logger.warn("Action {} is not supported by ActionOnSectionByProperty", action);
                                    actionResult.actionExecuted = true;
                                    return;
                                }
                            }
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

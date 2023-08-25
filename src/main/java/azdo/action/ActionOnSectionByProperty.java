package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;
import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is used to perform an action on a section. This section is searched using
 'sectionType' and 'property'. For example:
 Assume, that 'sectionType' has the value "script", 'property' has the value "displayName",
 and 'propertyValue' has the value "Deploy task".
 The script with the displayName "Deploy task" is searched in the yaml pipeline.
 If found, the step section is, for example, deleted from the yaml if the action is DELETE_SECTION.
 ******************************************************************************************/
public class ActionOnSectionByProperty implements Action {

    private static Log logger = Log.getLogger();
    private ACTION action; // The action on a section
    private String sectionType; // Is "job", for example
    private String property; // The property of the section, for example "displayName"
    private String propertyValue; // The value of the property

    // newSection is only needed in combination with ACTION_UPDATE and ACTION_INSERT
    private Map<String, Object> newSection; // The new section in YAML

    // insertBefore is only used in combination with action == INSERT
    // If 'true', the 'sectionToInsert' YAML string is inserted before the given section. If 'false',
    // the 'sectionToInsert' YAML is inserted after the given section.
    private boolean insertBefore = true;
    public ActionOnSectionByProperty(ACTION action,
                                     String sectionType,
                                     String property,
                                     String propertyValue,
                                     Map<String, Object> newSection,
                                     boolean insertBefore) {
        this.action = action;
        this.sectionType = sectionType;
        this.property = property;
        this.propertyValue = propertyValue;
        this.newSection = newSection;
        this.insertBefore = insertBefore;
    }

    /******************************************************************************************
     Perform an action on a section. The action properties are sey during creation of the object.
     @param actionResult Contains parts of the YAML structure. It is used to search for the
     section in the l3 structure.
     ******************************************************************************************/
    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionDeleteSectionByProperty.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.L3: {}", actionResult.l3);
        logger.debug("action: {}", action);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);
        logger.debug("newSection: {}", newSection);
        logger.debug("insertBefore: {}", insertBefore);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        boolean foundType = false;
        if (actionResult.l3 instanceof ArrayList) {
            logger.debug("l3 is instance of ArrayList");

            // Run through the elements of the list and update the section
            ArrayList<Object> list = (ArrayList<Object>) actionResult.l3;
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                if (list.get(index) instanceof Map) {

                    Map<String, Object> map = (Map<String, Object>) list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        logger.debug("entry.getKey(): {}", entry.getKey());
                        logger.debug("entry.getValue(): {}", entry.getValue());
                        if (sectionType.equals(entry.getKey())) {
                            // Found the right type
                            logger.debug("Found the right type: {}", sectionType);
                            foundType = true;
                        }

                        if (property.equals(entry.getKey()) && propertyValue.equals(entry.getValue()) && foundType) {
                            // Found the right property with the correct value
                            logger.debug("Found section type \'{}\' with property \'{}\': \'{}\'", sectionType, property, propertyValue);
                            switch (action) {
                                case INSERT_SECTION: {
                                    if (insertBefore) {
                                        logger.info("Insert a new section before section \'{}\' with property \'{}\': \'{}\'", sectionType, property, propertyValue);
                                        list.add(index, newSection);
                                        actionResult.actionExecuted = true;
                                    }
                                    else {
                                        logger.info("Insert a new section after section \'{}\' with property \'{}\': \'{}\'", sectionType, property, propertyValue);
                                        list.add(index + 1, newSection);
                                        actionResult.actionExecuted = true;
                                    }
                                    return;
                                }
                                case UPDATE_SECTION: {
                                    logger.info("Update section of type \'{}\' with property \'{}\': \'{}\'", sectionType, property, propertyValue);
                                    list.remove(index);
                                    list.add(index, newSection);
                                    actionResult.actionExecuted = true;
                                    return;
                                }
                                case DELETE_SECTION: {
                                    logger.info("Skip section of type \'{}\' with property \'{}\': \'{}\'", sectionType, property, propertyValue);
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

                foundType = false;
            }
        }
        return;
    }

    // This action can be executed if only the appropriate section type is found
    public boolean needsSectionIdentifier() {
        return false;
    }

    // This action is not a custom action
    public boolean isCustomAction () { return false; }
}

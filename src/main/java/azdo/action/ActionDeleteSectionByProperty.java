package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;
import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is used to delete a section. This section is searched using 'sectionType'
 and 'property'. For example:
 Assume, that 'sectionType' has the value "stage", 'property' has the value "displayName",
 and 'propertyValue' has the value "Execute this stage".
 The stage with the displaName "Execute this stage" is searched in the yaml pipeline.
 If found, the stage section is deleted from the yaml.
 ******************************************************************************************/
public class ActionDeleteSectionByProperty implements Action {
    private static Log logger = Log.getLogger();
    private String sectionType; // Is "job", for example
    private String property; // The property of the section, for example "displayName"
    private String propertyValue; // The value of the property

    public ActionDeleteSectionByProperty (String sectionType,
                                          String property,
                                          String propertyValue) {
        this.sectionType = sectionType;
        this.property = property;
        this.propertyValue = propertyValue;
    }
    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionDeleteSectionByProperty.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.L3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        boolean foundType = false;
        if (actionResult.l3 instanceof ArrayList) {
            logger.debug("l3 is instance of ArrayList");

            // Run through the elements of the list and remove the section
            ArrayList<Object> list = (ArrayList<Object>) actionResult.l3;
            int index = 0;
            int size = list.size();
            for (index = 0; index < size; index++) {
                if (list.get(index) instanceof Map) {
                    //logger.debug("list.get(index) is instance of ArrayList");

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
                            logger.info("Skip section type \'{}\' with property \'{}\': \'{}\'", sectionType, property, propertyValue);
                            list.remove(index);
                            actionResult.actionExecuted = true;
                            return;
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
}


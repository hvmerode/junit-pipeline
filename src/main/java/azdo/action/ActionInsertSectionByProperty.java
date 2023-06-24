package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is used to insert a section before or after another section. This section is
 searched using the 'sectionType'
 and 'property'. For example:
 Assume, that 'sectionType' has the value "stage", 'property' has the value "displayName",
 and 'propertyValue' has the value "Execute this stage".
 If found, a section - defined by  'sectionToInsert' - is inserted before or after "mystage",
 depending on the value of 'insertBefore'.

 The variable 'sectionToInsert' is of type Map, because this is the way how a section is
 represented in a yaml object in Snakeyaml.

 ******************************************************************************************/
public class ActionInsertSectionByProperty  implements Action {
    private static Log logger = Log.getLogger();
    private String sectionType; // Is "job", for example
    private String property; // The property of the section, for example "displayName"
    private String propertyValue; // The value of the property
    private Map<String, Object> sectionToInsert; // The section in YAML inserted before or after the section found

    // If 'true', the 'sectionToInsert' YAML string is inserted before the given section. If 'false',
    // the 'sectionToInsert' YAML is inserted after the given section.
    private boolean insertBefore = true;

    public ActionInsertSectionByProperty (String sectionType,
                                          String property,
                                          String propertyValue,
                                          Map<String, Object> sectionToInsert,
                                          boolean insertBefore) {
        this.sectionType = sectionType;
        this.property = property;
        this.propertyValue = propertyValue;
        this.sectionToInsert = sectionToInsert;
        this.insertBefore = insertBefore;
    }

    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionDeleteSectionByProperty.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.L3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);
        logger.debug("sectionToInsert: {}", sectionToInsert);
        logger.debug("insertBefore: {}", insertBefore);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        boolean foundType = false;
        if (actionResult.l3 instanceof ArrayList) {
            //logger.debug("l1 is instance of ArrayList");

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
                        if (propertyValue.equals(entry.getKey())) {
                            logger.info("TestTestTestTestTestTestTest");
                        }
                        if (property.equals(entry.getKey()) && propertyValue.equals(entry.getValue()) && foundType) {
                            // Found the right property with the correct value
                            if (insertBefore) {
                                logger.info("Insert the new section before section \'{}\' with \'{}\' = \'{}\'", sectionType, property, propertyValue);
                                list.add(index, sectionToInsert);
                            }
                            else {
                                logger.info("Insert the new section after section \'{}\' with \'{}\' = \'{}\'", sectionType, property, propertyValue);
                                list.add(index + 1, sectionToInsert);
                            }
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

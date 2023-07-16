package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static azdo.utils.Constants.SECTION_SCRIPT;

public class ActionInsertLineInSection implements Action {
    private static Log logger = Log.getLogger();
    private String sectionType; // Is "script", for example
    private String property; // The property of the section, for example "displayName"
    private String propertyValue; // The value of the property
    private String newLine; // The line to add to this section; this is the first line

    public ActionInsertLineInSection(String sectionType,
                                     String property,
                                     String propertyValue,
                                     String newLine) {
        this.sectionType = sectionType;
        this.property = property;
        this.propertyValue = propertyValue;
        this.newLine = newLine;
    }

    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionDeleteSectionByProperty.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.L3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);
        logger.debug("newLine: {}", newLine);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        boolean foundType = false;
        if (actionResult.l3 instanceof ArrayList) {

            // Run through the elements of the list and insert the section
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
                            logger.info("Add new line to step with property \'{}\': \'{}\'", property, propertyValue);
                            String s = (String)map.get(sectionType);
                            logger.debug("String: {}", s);
                            s = newLine + s;
                            logger.debug("String: {}", s);
                            map.put(sectionType, s);
                            logger.debug("Map: {}", map);
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

package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is typically used to add a new property to a section.
 It searches a section using a 'sectionType' and optionally a sectionIdentification, which
 can be "" or null.
 If the section is found, a property is added to the section.
 There is no validation whether the property is valid.
 ******************************************************************************************/
public class ActionAddPropertyToSection implements Action {
    private static final Log logger = Log.getLogger();
    private String sectionType; // Is "@Bash03", for example
    private String sectionIdentifier; // Optional identification of the section type
    private String property; // The property of the section, for example "enabled"
    private String propertyValue; // The value of the property, for example "true"

    public ActionAddPropertyToSection(String sectionType,
                                      String sectionIdentifier,
                                      String property,
                                      String propertyValue) {
        this.sectionType = sectionType;
        this.sectionIdentifier = sectionIdentifier;
        this.property = property;
        this.propertyValue = propertyValue;
    }

    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionAddPropertyToSection.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.L3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("sectionIdentifier: {}", sectionIdentifier);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        boolean foundSection = false;
        if (actionResult.l3 instanceof ArrayList) {

            // Run through the elements of the list and insert the section
            ArrayList<Object> list = (ArrayList<Object>) actionResult.l3;
            int index;
            int size = list.size();
            for (index = 0; index < size; index++) {
                if (list.get(index) instanceof Map) {

                    Map<String, Object> map = (Map<String, Object>) list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        logger.debug("entry.getKey(): {}", entry.getKey());
                        logger.debug("entry.getValue(): {}", entry.getValue());
                        if (entry.getKey() instanceof String && sectionType.equals(entry.getKey())) {
                            if (sectionIdentifier == null || sectionIdentifier.isEmpty()) {
                                logger.debug("Found the section: {}", sectionType);
                                foundSection = true;
                            }
                            else if (sectionIdentifier.equals(entry.getValue())) {
                                logger.debug("Found the section: {}", sectionType);
                                foundSection = true;
                            }
                        }
                        if (foundSection) {
                            // Add property to the section
                            // This means updating the old one if it already exists, or adding the new one if not present.
                            if (property != null && propertyValue != null && !property.isEmpty() && !propertyValue.isEmpty()) {
                                if (map.containsKey(property))
                                    map.replace(property, propertyValue);
                                else
                                    map.put(property, propertyValue);
                            }
                            actionResult.actionExecuted = true;
                            return;
                        }
                    }
                }
                foundSection = false;
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

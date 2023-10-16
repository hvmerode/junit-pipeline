package azdo.action;

import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is used to return the value of a property in a section.
 This section is searched using 'sectionType' and 'property'.
 If the property is found, it's value is returned.
 ******************************************************************************************/
public class ActionReturnPropertyValue extends ActionOnSectionByProperty {

    private ArrayList<String> propertyValues = new ArrayList<>();
    public ActionReturnPropertyValue(String sectionType,
                                     String property) {
        super(ACTION.GET_PROPERTY,
                sectionType,
                property,
                null,
                null,
                false);
    }

    // Perform a custom execute
    public void execute(ActionResult actionResult) {
        if (actionResult.l1 == null) {
            logger.debug("l1 is null");
            return;
        }

        if (actionResult.l1 instanceof ArrayList) {
            logger.debug("l1 is instance of ArrayList");

            // Run through the elements of the list and update the section
            ArrayList<Object> list = (ArrayList<Object>) actionResult.l1;
            int index;
            int size = list.size();
            for (index = 0; index < size; index++) {
                if (list.get(index) instanceof Map) {

                    Map<String, Object> map = (Map<String, Object>) list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {
                        logger.debug("entry.getKey(): {}", entry.getKey());
                        logger.debug("entry.getValue(): {}", entry.getValue());
                        if (this.property.equals(entry.getKey())) {
                            logger.debug("Return value {} of property {}", entry.getValue(), property);
                            propertyValues.add(entry.getValue().toString());
                        }
                    }
                }
            }
        }
    }

    public ArrayList<String> getPropertyValues () {
        return propertyValues;
    }
}
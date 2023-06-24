package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;
import java.util.ArrayList;
import java.util.Map;

public class ActionOverrideElement implements Action {
    private static Log logger = Log.getLogger();

    // Defines the identifier of the elements' key (e.g., this is 'name' for both variables and parameters)
    private String keyIdentifier = "name";

    // Defines the identifier of the elements' value (e.g., this is 'value' variables and 'default' for parameters)
    private String valueIdentifier = "value";
    private String elementName; // Is the name of the element (e.g., a variable name)
    private String elementValue; // The string representation of the new value

    public ActionOverrideElement (String elementName, String elementValue) {
        this.elementName = elementName;
        this.elementValue = elementValue;
    }

    public ActionOverrideElement (String elementName,
                                  String elementValue,
                                  String keyIdentifier,
                                  String valueIdentifier) {
        this.elementName = elementName;
        this.elementValue = elementValue;
        this.keyIdentifier = keyIdentifier;
        this.valueIdentifier = valueIdentifier;
    }
    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionOverrideElement.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.L3: {}", actionResult.l3);
        logger.debug("elementName: {}", elementName);
        logger.debug("elementValue: {}", elementValue);

        if (actionResult.l1 == null) {
            logger.debug("actionResult.l1 is null; return");
        }

        boolean found = false;
        if (actionResult.l1 instanceof ArrayList) {
            ArrayList<Object> list = (ArrayList<Object>) actionResult.l1;
            int size = list.size();
            for (int index = 0; index < size; index++) {
                if (list.get(index) instanceof Map) {
                    logger.debug("Array[{}]: ", list.get(index));

                    Map<String, Object> map = (Map<String, Object>) list.get(index);
                    for (Map.Entry<String, Object> entry : map.entrySet()) {

                        logger.debug("entry.getKey(): {}", entry.getKey());
                        logger.debug("entry.getValue(): {}", entry.getValue());
                        if (keyIdentifier.equals(entry.getKey()) && elementName.equals(entry.getValue())) {
                            // Take the next one to override the value
                            found = true;
                        }
                        // Only replace if it has a different value; otherwise continue searching
                        // This allows to change multiple elements in the same file with the same name
                        if (found && valueIdentifier.equals(entry.getKey())) {
                            logger.info("Override elementName \'{}\' with value \'{}\'", elementName, elementValue);
                            entry.setValue(elementValue);
                            return;
                        }
                    }
                }
            }
        }
        return;
    }

    // This action can be executed if only the appropriate section type is found
    public boolean needsSectionIdentifier() {
        return false;
    }
}

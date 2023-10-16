package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is typically used to insert a line to the beginning of a script, although
 it is made generic, and it can be also used fore other section types.
 It searches a section using a 'property', with a 'propertyValue', for example,
 'property' == "displayName", and 'propertyValue' == "Deploy step".
 If the section is found, a new (script) line is added to the beginning of the section.
 For example:
 {@code
 - task: PowerShell@2
   inputs:
     targetType: 'inline'
     script: |
       echo "This is the first line"
 }
 and the ActionInsertLineInInnerSection.execute() method is called, the result becomes:
 {@code
 - task: PowerShell@2
   inputs:
     targetType: 'inline'
     script: |
       echo "And now this is the first line"
       echo "This is the first line"
 }

 This action differs from ActionInsertLineInSection. The ActionInsertLineInInnerSection
 class searches for a certain section within another section.
 ******************************************************************************************/
public class ActionInsertLineInInnerSection implements Action {
    private static final Log logger = Log.getLogger();
    private String sectionType; // Refers to the main section to be found. "Powershell@2", for example.
    private String property; // The property of the section, for example "displayName"; searching is based on this property.
    private String propertyValue; // The value of the property
    private String innerSectionProperty; // The property of the inner section, for example "inputs"
    private String innerSectionType; // The type of the inner section where to add the new line, for example "script"
    private String newLine; // The line to add to the most inner section of the main section; this is the first line

    public ActionInsertLineInInnerSection(String sectionType,
                                          String property,
                                          String propertyValue,
                                          String innerSectionProperty,
                                          String innerSectionType,
                                          String newLine) {
        this.sectionType = sectionType;
        this.property = property;
        this.propertyValue = propertyValue;
        this.innerSectionProperty = innerSectionProperty;
        this.innerSectionType = innerSectionType;
        this.newLine = newLine;
    }

    public void execute (ActionResult actionResult) {
        logger.debug("==> Method ActionInsertLineInInnerSection.execute");
        logger.debug("actionResult.l1: {}", actionResult.l1);
        logger.debug("actionResult.l2: {}", actionResult.l2);
        logger.debug("actionResult.L3: {}", actionResult.l3);
        logger.debug("sectionType: {}", sectionType);
        logger.debug("property: {}", property);
        logger.debug("propertyValue: {}", propertyValue);
        logger.debug("innerSectionProperty: {}", innerSectionProperty);
        logger.debug("innerSectionType: {}", innerSectionType);
        logger.debug("newLine: {}", newLine);

        if (actionResult.l3 == null) {
            logger.debug("actionResult.l3 is null; return");
        }

        boolean foundType = false;
        boolean foundProperty = false;
        boolean foundinnerSection = false;
        Object innerSection = null;
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
                        if (sectionType.equals(entry.getKey())) {
                            // Found the right type
                            logger.debug("Found the right type: {}", sectionType);
                            foundType = true;
                        }
                        if (property.equals(entry.getKey()) && propertyValue.equals(entry.getValue()) && foundType) {
                            // Found the right property with the correct value
                            logger.debug("Add new line to step with property \'{}\': \'{}\'", property, propertyValue);
                            foundProperty = true;
                        }
                        if (innerSectionProperty.equals(entry.getKey())) {
                            // The first element of the innerSectionPath equals the property of this section
                            // For example, the "inputs" property of a "Powershel@2" task.
                            logger.debug("Found inner section property: {}", innerSectionProperty);
                            foundinnerSection = true;
                            innerSection = entry.getValue();
                        }
                        if (foundType && foundProperty && foundinnerSection) {
                            innerSection(innerSectionType, innerSection);
                            actionResult.actionExecuted = true; // Whether the line was added or not, this is the right section
                        }
                    }
                }
                foundType = false;
            }
        }
        return;
    }

    private void innerSection (String innerSectionType, Object innerSection) {
        if (innerSection == null)
            logger.debug("innerSection is null");

        // Only a Map section is assumed valid
        if (innerSection instanceof Map) {
            logger.debug("innerSection is a Map: {}", innerSection);

            Map<String, Object> innerMap = (Map<String, Object>) innerSection;
            for (Map.Entry<String, Object> innerEntry : innerMap.entrySet()) {
                logger.debug("Inner section entry.getKey(): {}", innerEntry.getKey());
                logger.debug("Inner section entry.getValue(): {}", innerEntry.getValue());
                if (innerSectionType.equals(innerEntry.getKey())) {
                    // Found the type; add the line
                    logger.debug("Found the type; add the line for inner section type \'{}\'", innerSectionType);
                    String s = (String)innerMap.get(innerSectionType);
                    logger.debug("String: {}", s);
                    s = newLine + s;
                    logger.debug("String: {}", s);
                    innerMap.put(innerSectionType, s);
                    logger.debug("Map: {}", innerMap);
                }
            }
        }
    }

    // This action can be executed if only the appropriate section type is found
    public boolean needsSectionIdentifier() {
        return false;
    }

    // This action is not a custom action
    public boolean isCustomAction () { return false; }
}

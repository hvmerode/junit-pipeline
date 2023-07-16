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
public class ActionInsertSectionByProperty2 extends ActionOnSectionByProperty {

    public ActionInsertSectionByProperty2(String sectionType,
                                          String property,
                                          String propertyValue,
                                          Map<String, Object> sectionToInsert,
                                          boolean insertBefore) {
        super(ACTION.INSERT_SECTION,
                sectionType,
                property,
                propertyValue,
                sectionToInsert,
                insertBefore);
    }
}
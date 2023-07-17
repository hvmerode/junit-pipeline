package azdo.action;

import java.util.Map;

/******************************************************************************************
 This class is used to replace a section. This section is searched using 'sectionType'
 and 'property'.
 If the section is found, it is replaced by 'newSection'.
 ******************************************************************************************/
public class ActionUpdateSectionByProperty extends ActionOnSectionByProperty {

    public ActionUpdateSectionByProperty(String sectionType,
                                         String property,
                                         String propertyValue,
                                         Map<String, Object> newSection) {
        super(ACTION.UPDATE_SECTION,
                sectionType,
                property,
                propertyValue,
                newSection,
                false);
    }
}

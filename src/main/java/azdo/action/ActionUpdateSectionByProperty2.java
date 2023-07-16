package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

public class ActionUpdateSectionByProperty2 extends ActionOnSectionByProperty {

    public ActionUpdateSectionByProperty2(String sectionType,
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

package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is used to replace a section. This section is searched using 'sectionType'
 and 'sectionIdentifier'. For example:
 Assume, that 'sectionType' has the value "task", and 'sectionIdentifier' has the
 value "AWSShellScript@1".
 The stage with the identifier "AWSShellScript@1" is searched in the yaml pipeline.
 If the task is found, it is replaced by .
 ******************************************************************************************/
public class ActionUpdateSection2 extends ActionOnSection {

    public ActionUpdateSection2(String sectionType,
                                String sectionIdentifier,
                                Map<String, Object> newSection)
    {
        super(ACTION.UPDATE_SECTION,
                sectionType,
                sectionIdentifier,
                newSection,
                false);
    }
}

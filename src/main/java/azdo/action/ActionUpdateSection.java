package azdo.action;

import java.util.Map;

/******************************************************************************************
 This class is used to replace a section. This section is searched using 'sectionType'
 and 'sectionIdentifier'. For example:
 Assume, that 'sectionType' has the value "task", and 'sectionIdentifier' has the
 value "AWSShellScript@1".
 The stage with the identifier "AWSShellScript@1" is searched in the yaml pipeline.
 If the task is found, it is replaced by 'newSection'.
 ******************************************************************************************/
public class ActionUpdateSection extends ActionOnSection {

    public ActionUpdateSection(String sectionType,
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

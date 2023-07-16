package azdo.action;

import azdo.utils.Log;
import azdo.yaml.ActionResult;

import java.util.ArrayList;
import java.util.Map;

/******************************************************************************************
 This class is used to delete a section. This section is searched using 'sectionType'
 and 'sectionIdentifier'. For example:
 Assume, that 'sectionType' has the value "stage", and 'sectionIdentifier' has the
 value "mystage".
 The stage with the identifier "mystage" is searched in the yaml pipeline.
 If found, the stage section is deleted from the yaml.
 ******************************************************************************************/
public class ActionDeleteSection2 extends ActionOnSection {

    public ActionDeleteSection2(String sectionType, String sectionIdentifier)
    {
        super(ACTION.DELETE_SECTION,
                sectionType,
                sectionIdentifier,
                null,
                false);
    }
}

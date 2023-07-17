package azdo.action;

import java.util.Map;

/******************************************************************************************
 This class is used to insert a section before or after another section. This section is
 searched using the 'sectionType' and 'sectionIdentifier'. For example:
 Assume, that 'sectionType' has the value "stage" and 'sectionIdentifier' has the value
 "mystage". The stage with the name (identifier) "mystage" is searched in the yaml pipeline.
 If found, a section - defined by  'sectionToInsert' - is inserted before or after "mystage",
 depending on the value of 'insertBefore'.

 The variable 'sectionToInsert' is of type Map, because this is the way how a section is
 represented in a yaml object in Snakeyaml.
 ******************************************************************************************/
public class ActionInsertSection extends ActionOnSection {
    public ActionInsertSection(String sectionType,
                               String sectionIdentifier,
                               Map<String, Object> sectionToInsert,
                               boolean insertBefore)
    {
        super(ACTION.INSERT_SECTION,
                sectionType,
                sectionIdentifier,
                sectionToInsert,
                insertBefore);
    }
}

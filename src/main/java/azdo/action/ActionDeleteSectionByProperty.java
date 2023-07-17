package azdo.action;

/******************************************************************************************
 This class is used to delete a section. This section is searched using 'sectionType'
 and 'property'. For example:
 Assume, that 'sectionType' has the value "stage", 'property' has the value "displayName",
 and 'propertyValue' has the value "Execute this stage".
 The stage with the displaName "Execute this stage" is searched in the yaml pipeline.
 If found, the stage section is deleted from the yaml.
 ******************************************************************************************/
public class ActionDeleteSectionByProperty extends ActionOnSectionByProperty {
    public ActionDeleteSectionByProperty(String sectionType,
                                         String property,
                                         String propertyValue) {
        super(ACTION.DELETE_SECTION,
                sectionType,
                property,
                propertyValue,
                null,
                false);
    }
}

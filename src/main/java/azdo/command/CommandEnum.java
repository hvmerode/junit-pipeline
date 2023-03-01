package azdo.command;

public enum CommandEnum {
    replaceValue,            // Replace a 'value' of a 'key' with a new value
    replaceLiteral,          // Replace an arbitrary string with another string
    delete,                  // Delete a section from the YAML
    mock                     // Replace a section with a script
}

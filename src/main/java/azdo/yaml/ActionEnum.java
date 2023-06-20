// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.yaml;

public enum ActionEnum {
    replaceValue,            // Replace a 'value' of a 'key' with a new value
    replaceLiteral,          // Replace an arbitrary string with another string
    delete,                  // Delete a section from the YAML
    mock,                    // Replace a section with a script
    insertBefore,            // Insert a section before a given section
    insertAfter              // Insert a section after a given section
}

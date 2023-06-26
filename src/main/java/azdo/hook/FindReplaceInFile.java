// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.hook;

import azdo.utils.Log;
import azdo.utils.Utils;

/* Hook, to find and replace all literals in a given file.
 */
public class FindReplaceInFile extends Hook {
    private String fullQualifiedFileName;
    private String findString;
    private String replaceString;
    private boolean replaceAll;
    private static Log logger = Log.getLogger();
    public FindReplaceInFile(String fullQualifiedFileName,
                             String findString,
                             String replaceString,
                             boolean replaceAll) {
        logger.debug("==> Class: FindReplaceInFile");

        this.fullQualifiedFileName = fullQualifiedFileName;
        this.findString = findString;
        this.replaceString = replaceString;
        this.replaceAll = replaceAll;
    }

    @Override
    public void executeHook (){
        logger.debug("==> Method: FindReplaceInFile.executeHook");
        Utils.findReplaceInFile(fullQualifiedFileName, findString, replaceString, true);
    }
}

// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.hook;

import azdo.junit.AzDoPipeline;
import azdo.utils.Utils;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;

/* Hook, to find and replace all literals in a given file.
 */
public class FindReplaceInFile extends Hook {
    private String fullQualifiedFileName;
    private String findString;
    private String replaceString;
    private boolean replaceAll;
    private static Logger logger = LoggerFactory.getLogger(FindReplaceInFile.class);
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

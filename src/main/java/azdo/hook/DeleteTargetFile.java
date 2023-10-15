// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.hook;

import azdo.utils.Log;
import azdo.utils.Utils;
import java.nio.file.Path;
import java.nio.file.Paths;

/******************************************************************************************
 Hook, to delete a file from the target path.
 *******************************************************************************************/
public class DeleteTargetFile extends Hook {
    private String fullQualifiedFileName;
    private static final Log logger = Log.getLogger();
    public DeleteTargetFile(String fullQualifiedFileName) {
        logger.debug("==> Class: DeleteTargetFile");

        this.fullQualifiedFileName = fullQualifiedFileName;
    }

    @Override
    public void executeHook (){
        logger.debug("==> Method: DeleteTargetFile.executeHook");
        Path path = Paths.get(fullQualifiedFileName);
        path = path.normalize();
        fullQualifiedFileName = path.toString();
        Utils.deleteFile(fullQualifiedFileName);
    }
}

// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.hook;

import azdo.junit.AzDoPipeline;
import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;
import java.nio.file.Paths;

/* Hook, to delete a file from the target path.
 */
public class DeleteTargetFile extends Hook {
    private String fullQualifiedFileName;
    private static Logger logger = LoggerFactory.getLogger(AzDoPipeline.class);
    public DeleteTargetFile(String fullQualifiedFileName) {
        logger.debug("Class: DeleteTargetFile");
        this.fullQualifiedFileName = fullQualifiedFileName;
    }

    @Override
    public void executeHook (){
        logger.debug("==> Method: DeleteTargetFile.executeHook");
        Path path = Paths.get(fullQualifiedFileName);
        path = path.normalize();
        fullQualifiedFileName = path.toString();

        try {
            if (Utils.isLinux()) {
                logger.debug("Deleting on Linux: " + fullQualifiedFileName);
                Runtime.getRuntime().exec("rm -f " + fullQualifiedFileName);
            } else if (Utils.isWindows()) {
                logger.debug("Deleting on Windows: " + "cmd.exe /c del /F /Q " + fullQualifiedFileName);
                Runtime.getRuntime().exec("cmd.exe /c del /F /Q " + fullQualifiedFileName);
                Utils.wait(3000);
            }
        }
        catch (Exception e) {
            logger.debug("Cannot delete" + fullQualifiedFileName);
        }
    }
}

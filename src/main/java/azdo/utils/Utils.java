// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import azdo.junit.TestProperties;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.List;

public class Utils {
    private static Logger logger = LoggerFactory.getLogger(Utils.class);

    public static boolean isLinux(){
        String os = System.getProperty("os.name");
        return os.toLowerCase().indexOf("linux") >= 0;
    }

    public static boolean isWindows(){
        String os = System.getProperty("os.name");
        return os.toLowerCase().indexOf("windows") >= 0;
    }

    public static void makeDirectory (String directoryName) {
        // Create the target path if not existing
        logger.debug("==> Method: Utils.makeDirectory");
        try {
            if (isLinux()) {
                logger.debug("Executing on Linux");
                Runtime.getRuntime().exec("/bin/sh -c mkdir " + directoryName);
            } else if (isWindows()) {
                logger.debug("Executing on Windows");
                Runtime.getRuntime().exec("cmd /c mkdir " + directoryName);
            }
        }
        catch (IOException e) {
            logger.debug("Cannot create the target directory {}; it may already exist. Just continue", directoryName);
        }
    }

    public static boolean deleteDirectory(String directoryName) {
        logger.debug("==> Method: Utils.deleteDirectory");
        String dir = fixPath(directoryName);
        try {
            if (Utils.isLinux()) {
                logger.debug("Executing on Linux");
                Runtime.getRuntime().exec("/bin/sh -c rm -r " + dir);
            }
            else if (Utils.isWindows()) {
                logger.debug("Executing on Windows");
                Runtime.getRuntime().exec("cmd /c rmdir /s /q " + dir);
                logger.debug("Deleted directory: {}", dir);
                wait(1000);
            }
        }
        catch (IOException e)
        {
            logger.debug("Cannot delete directory; does it exist?");
            return false;
        }

        return true;
    }

    /*
       Makes sure that a path resembles the correct path on the file system
       Fortunately, Windows also accepts forward slashes
     */
    public static String fixPath (String path){
        path=path.replaceAll("\\\\","/");
        Path normalized = Paths.get(path);
        normalized = normalized.normalize();
        path = normalized.toString();
        return path;
    }

    public static void wait(int ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch(InterruptedException ex)
        {
            Thread.currentThread().interrupt();
        }
    }
}

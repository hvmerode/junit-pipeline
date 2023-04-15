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
        logger.info("==> Method: Utils.makeDirectory");
        try {
            if (isLinux()) {
                logger.info("Executing on Linux");
                Runtime.getRuntime().exec("/bin/sh -c mkdir " + directoryName);
            } else if (isWindows()) {
                logger.info("Executing on Windows");
                Runtime.getRuntime().exec("cmd /c mkdir " + directoryName);
            }
        }
        catch (IOException e) {
            logger.info("Cannot create the target directory" + directoryName + " ; it may already exist. Just continue");
        }
    }

    public static boolean deleteDirectory(String directoryName) {
        logger.info("==> Method: Utils.deleteDirectory");
        try {
            File directoryToBeDeleted = new File(directoryName);
            if (Utils.isLinux()) {
                logger.info("Executing on Linux");
                Runtime.getRuntime().exec("/bin/sh -c rm -r " + directoryToBeDeleted);
            } else if (Utils.isWindows()) {
                logger.info("Executing on Windows");
                Runtime.getRuntime().exec("cmd /c rmdir " + directoryToBeDeleted);
            }
            return true;
        }
        catch (IOException e)
        {
            logger.info("Cannot delete directory; does it exist?");
            return false;
        }
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

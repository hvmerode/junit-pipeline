// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Utils {
    private static Logger logger = LoggerFactory.getLogger(Utils.class);
    private static final String EXCLUDEFILESLIST = "\\excludedfileslist.txt";

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
        logger.debug("directoryName: {}", directoryName);

        try {
            if (isLinux()) {
                logger.debug("Executing on Linux");
                Runtime.getRuntime().exec("/bin/sh -c mkdir " + directoryName);
            } else if (isWindows()) {
                logger.debug("Executing on Windows");
                Runtime.getRuntime().exec("cmd /c mkdir " + directoryName);
            }
            logger.debug("Directory {} created", directoryName);
        }
        catch (IOException e) {
            logger.debug("Cannot create the target directory {}; it may already exist. Just continue", directoryName);
        }
    }

    public static boolean deleteDirectory(String directoryName) {
        logger.debug("==> Method: Utils.deleteDirectory");
        logger.debug("directoryName: {}", directoryName);

        String dir = fixPath(directoryName);

        // This method makes use of Apache FileUtils, which can be used both on Linux and Windows
        try {
                logger.debug("Executing...");
                FileUtils.deleteDirectory(new File(dir));
                logger.debug("Deleted directory: {}", dir);
                wait(1000);
        }
        catch (IOException e)
        {
            logger.debug("Cannot delete directory {}; does it exist?", dir);
            return false;
        }

        return true;
    }

    public static boolean createDirectory(String directoryName) {
        logger.debug("==> Method: Utils.createDirectory");
        logger.debug("directoryName: {}", directoryName);

        String dir = fixPath(directoryName);
        try {
            if (Utils.isLinux()) {
                logger.debug("Executing on Linux");
                Runtime.getRuntime().exec("/bin/sh -c mkdir " + dir);
            }
            else if (Utils.isWindows()) {
                logger.debug("Executing on Windows");
                Runtime.getRuntime().exec("cmd /c mkdir " + dir);
                wait(1000);
            }
            logger.debug("Created directory: {}", dir);
        }
        catch (IOException e)
        {
            logger.debug("Cannot create directory; it probably exists already?");
            return false;
        }

        return true;
    }

    public static void copyAll(String source,
                               String target,
                               String excludeList) throws IOException{
        logger.debug("==> Method: AzDoUtils.copyAll");
        logger.debug("source: {}", source);
        logger.debug("target: {}", target);
        logger.debug("excludeList: {}", excludeList);

        source = fixPath(source);
        target = fixPath(target);
        if(Utils.isLinux()){
            logger.debug("Executing on Linux: cp {} {}", source, target);
            // TODO: Exclude certain file types and directories
            Runtime.getRuntime().exec("/bin/sh -c cp " + source + " " + target);
        } else if(Utils.isWindows()) {
            if (excludeList == null || excludeList.isEmpty()) {
                logger.debug("Executing on Windows: xcopy {} {}  /E /H /C /I /Y ", source, target);
                Runtime.getRuntime().exec("cmd.exe /c xcopy " + source + " " + target + " /E /H /C /I /Y ");
            }
            else {
                logger.debug("Executing on Windows: xcopy {} {}  /E /H /C /I /Y /exclude:{}", source, target, target + EXCLUDEFILESLIST);
                Runtime.getRuntime().exec("cmd.exe /c " + excludeList + " > " + target + EXCLUDEFILESLIST);
                Runtime.getRuntime().exec("cmd.exe /c xcopy " + source + " " + target + " /E /H /C /I /Y /exclude:" + target + EXCLUDEFILESLIST);
            }
            Utils.wait(3000); // Time needed to unlock files in Windows
        }
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

    /*
       Validate whether a directory is empty
     */
    public static boolean pathIsEmptyOrNotExisting (String path) {
        boolean res = false;
        try {
            // Check whether the directory is empty
            // If res = true the directory is empty, which is enough to check
            // If res = false the directory is not empty, which is also enough to check
            File f = new File(path);
            res = FileUtils.isEmptyDirectory(f);
        }
        catch (IOException e) {
            logger.debug("Cannot validate whether the Path is empty or does not exist; assume it does not exists");
            return true;
        }

        return res;
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

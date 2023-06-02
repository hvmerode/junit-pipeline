// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.commons.io.*;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.codehaus.plexus.util.FileUtils.getFileNames;

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

    public static boolean deleteFile(String fileName) {
        logger.debug("==> Method: Utils.deleteFile");
        logger.debug("fileName: {}", fileName);

        String dir = fixPath(fileName);

        // This method makes use of Apache FileUtils, which can be used both on Linux and Windows
        try {
            logger.debug("Executing...");
            FileUtils.delete(new File(fileName));
            logger.debug("Deleted file: {}", fileName);
            wait(1000);
        }
        catch (IOException e)
        {
            logger.debug("Cannot delete file {}; does it exist?", fileName);
            return false;
        }

        return true;
    }

    public static void createDirectory(String directoryName) {
        logger.debug("==> Method: Utils.createDirectory");
        logger.debug("directoryName: {}", directoryName);
        directoryName = fixPath(directoryName);
        File dir = new File (directoryName);
        dir.mkdirs();
        wait(1000);
    }

    public static void copyAll(String sourceDirectory, String destinationDirectory, String exclusionPattern)
    {
        try {
            copy(new File(sourceDirectory), new File(destinationDirectory), exclusionPattern);
            logger.debug("Directory copied successfully!");
        } catch (IOException e) {
            logger.debug("Failed to copy directory: {}", e.getMessage());
        }
    }

    private static void copy(File source, File target, String exclusionPattern) throws IOException {
        logger.debug("==> Method: Utils.copyAll");
        logger.debug("source: {}", source);
        logger.debug("target: {}", target);
        logger.debug("exclusionPattern: {}", exclusionPattern);

        if (!source.isDirectory()) {
            throw new IllegalArgumentException("Source must be a directory");
        }

        if (!target.exists()) {
            target.mkdirs();
        }

        File[] files = source.listFiles();
        if (files == null) {
            return;
        }

        Pattern pattern = Pattern.compile(exclusionPattern);
        Matcher matcher;
        for (File file : files) {
            matcher = pattern.matcher(file.getName());
            matcher.find();
            if (matcher.matches()) {
                continue; // Exclude directories matching the pattern
            }

            Path sourcePath = file.toPath();
            Path destinationPath = new File(target, file.getName()).toPath();

            if (file.isDirectory()) {
                copy(file, new File(target, file.getName()), exclusionPattern);
            } else {
                Files.copy(sourcePath, destinationPath, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        wait(200); // Time needed to unlock files in Windows
        logger.debug("Copied source {} to target {}", source, target);
    }

    /*
       Makes sure that a path resembles the correct path on the file system
       Fortunately, Windows also accepts forward slashes
     */
    public static String fixPath (String path){
        if (path == null)
            return path;

        path = path.replace("../", ""); // Remove ../
        path = path.replace("..\\", ""); // Remove ..\
        path = path.replace("./", ""); // Remove ./
        path = path.replace(".\\", ""); // Remove .\
        path = path.replaceAll("\\\\","/");
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

    /*
       TODO
     */
    public static String findFullQualifiedFileNameInDirectory (String directory, String fileName) {
        //logger.debug("==> Method: Utils.findFullQualifiedFileNameInDirectory");

        if (directory.isEmpty()) {
            logger.debug("No directory provided; just return the fileName");
            return fileName;
        }

        if (fileName.isEmpty()) {
            logger.debug("No fileName provided; just return an empty string");
            return fileName;
        }

        Path dir = Path.of(directory);
        Path f = Path.of(fileName);
        if (f != null) {
            f = f.normalize();
            fileName = f.toString();
        }
        fileName = Utils.fixPath(fileName); // Remove .. in front of the filename, because the full name is searched on the filesystem anyway
        //fileName = fileName.replace("..", ""); // Remove .. in front of the filename, because the full name is searched on the filesystem anyway
        String fqn = null;
        String compare = null;
        //logger.debug("directory: {}", directory);
        //logger.debug("fileName: {}", fileName);

        try(DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            for (Path path : stream) {
                File pathToFile = path.toFile();
                if (pathToFile.isDirectory()) {
                    // It is a directory; search recursively
                    //logger.debug("Directory: {}", pathToFile.getAbsoluteFile());
                    fqn = findFullQualifiedFileNameInDirectory(pathToFile.toString(), fileName);
                    if (fqn != null)
                        return fqn;
                }
                else {
                    // It is a file
                    //logger.debug("File: {}", pathToFile.getAbsoluteFile());
                    compare = pathToFile.getAbsoluteFile().toString();
                    if (compare.contains(fileName)) {
                        logger.debug("Match: Compared {} with {}", fileName, compare);
                        return compare;
                    }
                }
            }
        }
        catch(IOException e) {}

        return null;
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

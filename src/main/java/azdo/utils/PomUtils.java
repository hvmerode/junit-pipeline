// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

/******************************************************************************************
 Utils to manipulate the pom.xml. This class is not used in the junit-code but added
 for testing purposes only.
 *******************************************************************************************/
public class PomUtils {
    private static final Log logger = Log.getLogger();

    public static boolean checkDependency(String pomFile,
                                          String groupID,
                                          String artifactId,
                                          String version) throws IOException, XmlPullParserException {
        logger.debug("==> Method: PomUtils.checkDependency");

        // Create a MavenXpp3Reader to read the existing pom.xml file
        MavenXpp3Reader reader = new MavenXpp3Reader();
        File file = new File(pomFile);
        InputStream inputStream = new FileInputStream(file);
        Model model = reader.read(inputStream);

        // Create a new dependency node to check for existence
        Dependency dependencyToCheck = new Dependency();
        dependencyToCheck.setGroupId(groupID);
        dependencyToCheck.setArtifactId(artifactId);
        //dependencyToCheck.setVersion(version);

        // Check if the dependency already exists in the model
        List<Dependency> list = model.getDependencies();
        for (Dependency dependency : list) {
            if (artifactId.equals(dependency.getArtifactId()))
                return true;
        }

        return false;
    }

    public static void insertDependency(String pomFile,
                                        String groupID,
                                        String artifactId,
                                        String version) throws IOException, XmlPullParserException {
        logger.debug("==> Method: PomUtils.insertDependency");

        // Create a MavenXpp3Reader to read the existing pom.xml file
        MavenXpp3Reader reader = new MavenXpp3Reader();
        File file = new File(pomFile);
        InputStream inputStream = new FileInputStream(file);
        Model model = reader.read(inputStream);

        // Create a new dependency node
        Dependency newDependency = new Dependency();
        newDependency.setGroupId(groupID);
        newDependency.setArtifactId(artifactId);
        newDependency.setVersion(version);

        // Add the new dependency to the model
        model.addDependency(newDependency);

        // Write the modified model to the pom.xml file
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new java.io.FileWriter(pomFile), model);
    }

    public static void deleteDependency(String pomFile,
                                        String groupID,
                                        String artifactId) throws IOException, XmlPullParserException {
        logger.debug("==> Method: PomUtils.deleteDependency");
        logger.debug("pomFile: {}", pomFile);
        logger.debug("groupID: {}", groupID);
        logger.debug("artifactId: {}", artifactId);

        // Create a MavenXpp3Reader to read the existing pom.xml file
        MavenXpp3Reader reader = new MavenXpp3Reader();
        File file = new File(pomFile);
        InputStream inputStream = new FileInputStream(file);
        Model model = reader.read(inputStream);

        // Use iterator to prevent ConcurrentModificationException
        List<Dependency> list = model.getDependencies();
        Iterator<Dependency> iterator = list.iterator();
        Dependency dependency = null;
        while (iterator.hasNext()) {
            dependency = iterator.next();
            if (artifactId.equals(dependency.getArtifactId()) && groupID.equals(dependency.getGroupId())) {
                logger.debug("Found dependency; remove");
                iterator.remove();
            }
        }

        // Write the modified model to the pom.xml file
        model.setDependencies(list);
        logger.debug("Found dependency; remove");
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new java.io.FileWriter(pomFile), model);
    }
}

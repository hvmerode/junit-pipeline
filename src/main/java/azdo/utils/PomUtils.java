// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.utils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.List;

import azdo.junit.AzDoPipeline;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PomUtils {
    private static Logger logger = LoggerFactory.getLogger(PomUtils.class);

    public static boolean checkDependency(String pomFile,
                                          String groupID,
                                          String artifactId,
                                          String version) throws IOException, XmlPullParserException {
        logger.info("==> Method: PomUtils.checkDependency");

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
        logger.info("==> Method: PomUtils.insertDependency");

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
        logger.info("==> Method: PomUtils.deleteDependency");

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
                iterator.remove();
                break;
            }
        }

        // Write the modified model to the pom.xml file
        MavenXpp3Writer writer = new MavenXpp3Writer();
        writer.write(new java.io.FileWriter(pomFile), model);
    }
}

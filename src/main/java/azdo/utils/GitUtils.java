package azdo.utils;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;

public class GitUtils {
    private static Logger logger = LoggerFactory.getLogger(GitUtils.class);
    private static Git git = null;

    // Clone the repo to local and initialize
    public static Git azdoClone (String targetPath,
                                 String repositoryName,
                                 String userTargetRepository,
                                 String passwordTargetRepository,
                                 String organization,
                                 String project) {
        logger.debug("==> Method: GitUtils.azdoClone");
        logger.debug("targetPath: {}", targetPath);
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("organization: {}", organization);
        logger.debug("project: {}", project);

        // Delete the target path
        Utils.deleteDirectory(targetPath);

        // Create the target path if not existing
        Utils.makeDirectory(targetPath);

        // Create the credentials provider
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(userTargetRepository, passwordTargetRepository);

        // Create the uri
        String azdoBaseUrl = "https://dev.azure.com/" + organization;
        String uriTargetRepository = azdoBaseUrl + "/" + project + "/_git/" + repositoryName;

        // Clone the repo
        try {
            logger.debug("git.clone");
            git = Git.cloneRepository()
                    .setURI(uriTargetRepository)
                    .setCloneAllBranches(true)
                    .setCredentialsProvider(credentialsProvider)
                    .setDirectory(new File(targetPath))
                    .call();
        }
        catch (Exception e) {
            logger.debug("Cannot clone, but just proceed");
        }

        return git;
    }
}

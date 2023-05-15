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

    // Clone an Azure DevOps repo to local and initialize
    public static void cloneAzdoToLocal (String targetPath,
                                        String repositoryName,
                                        String userTargetRepository,
                                        String passwordTargetRepository,
                                        String organization,
                                        String project) {
        logger.debug("==> Method: GitUtils.cloneAzdoToLocal");
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
        logger.debug("uriTargetRepository: {}", uriTargetRepository);

        // Clone the repo
        try {
            logger.debug("git.clone");
            git = Git.cloneRepository()
                    .setURI(uriTargetRepository)
                    .setCloneAllBranches(true)
                    .setCredentialsProvider(credentialsProvider)
                    .setDirectory(new File(targetPath))
                    .call();
            git.close();
        }
        catch (Exception e) {
            logger.debug("Cannot clone, but just proceed");
        }

        Utils.wait(1000);
    }

    // Clone a GitHub repo to local and initialize
    public static void cloneGitHubToLocal (String targetPath,
                                          String repositoryName,
                                          String project) {
        logger.debug("==> Method: GitUtils.cloneGitHubToLocal");
        logger.debug("targetPath: {}", targetPath);
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("project: {}", project);

        // Delete the target path
        Utils.deleteDirectory(targetPath);

        // Create the target path if not existing
        Utils.makeDirectory(targetPath);

        // Create the uri
        String baseUrl = "https://github.com";
        String uriTargetRepository = baseUrl + "/" + project + "/" + repositoryName;
        logger.debug("uriTargetRepository: {}", uriTargetRepository);

        // Clone the repo
        try {
            logger.debug("git.clone");
            git = Git.cloneRepository()
                    .setURI(uriTargetRepository)
                    .setCloneAllBranches(true)
                    .setDirectory(new File(targetPath))
                    .call();
            git.close();
        }
        catch (Exception e) {
            logger.debug("Cannot clone, but just proceed");
        }

        Utils.wait(1000);
    }
}

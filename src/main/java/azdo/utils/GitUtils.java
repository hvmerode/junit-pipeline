package azdo.utils;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class GitUtils {
    private static Logger logger = LoggerFactory.getLogger(GitUtils.class);
    private static Git git = null;
    public static final String BRANCH_MASTER = "master";

    // Clone an Azure DevOps repo to local and initialize
    public static Git cloneAzdoToLocal (String targetPath,
                                        String repositoryName,
                                        String azdoUser,
                                        String azdoPat,
                                        String organization,
                                        String project) {
        logger.debug("==> Method: GitUtils.cloneAzdoToLocal");
        targetPath = Utils.fixPath(targetPath);
        logger.debug("targetPath: {}", targetPath);
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("organization: {}", organization);
        logger.debug("project: {}", project);

        git = null;

        // Delete the target path
        //Utils.deleteDirectory(targetPath);

        // Create the target path if not existing
        Utils.makeDirectory(targetPath);

        // Create the credentials provider
        CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(azdoUser, azdoPat);

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
            //git.close();
        }
        catch (Exception e) {
            logger.debug("Cannot clone, but just proceed");
        }

        Utils.wait(1000);
        return git;
    }

    // Clone a GitHub repo to local and initialize
    public static Git cloneGitHubToLocal (String targetPath,
                                          String repositoryName,
                                          String project) {
        logger.debug("==> Method: GitUtils.cloneGitHubToLocal");
        logger.debug("targetPath: {}", targetPath);
        logger.debug("repositoryName: {}", repositoryName);
        logger.debug("project: {}", project);

        git = null;

        // Delete the target path
        //Utils.deleteDirectory(targetPath);

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
            //git.close();
        }
        catch (Exception e) {
            logger.debug("Cannot clone, but just proceed");
        }

        Utils.wait(1000);
        return git;
    }

    public static boolean containsBranch (Git git,
                                          String branchName) {
        logger.debug("==> Method: GitUtils.containsBranch");
        logger.debug("branchName: {}", branchName);

        if (git == null) {
            logger.debug("Cannot continue; git is null");
            return false;
        }

        try {
            ListBranchCommand command = git.branchList();
            command.setListMode(ListBranchCommand.ListMode.ALL);
            List<Ref> branches = command.call();
            for (Ref ref : branches) {
                if (ref.getName().endsWith("/" + branchName)) {
                    logger.debug("Branch {} exists", branchName);
                    return true;
                }
            }
        }
        catch (Exception e) {
            logger.debug("Cannot check whether the branch is remote");
        }

        logger.debug("Branch {} does not exists", branchName);
        return false;
    }

    public static void commitAndPush (Git git,
                                      String azdoUser,
                                      String azdoPat,
                                      ArrayList<String> commitPatternList) {
        logger.debug("==> Method: GitUtils.commitAndPush");

        if (git == null) {
            logger.debug("Cannot continue; git is null");
            return;
        }

        logger.debug("Repository {}", git.getRepository().getRemoteNames().toString());

        // Push the local repo to remote
        try {
            logger.debug("git.add");
            git.add()
                    .addFilepattern(".")
                    .call();

            // Stage all changed files, including deleted files
            int size = commitPatternList.size();
            AddCommand command = git.add();
            for (int i = 0; i < size; i++) {
                command = command.addFilepattern(commitPatternList.get(i));
                logger.debug("Pattern: {}", commitPatternList.get(i));
            }
            command.call();

            logger.debug("git.commit");
            git.commit()
                    .setAll(true)
                    .setAuthor(azdoUser, "")
                    .setCommitter(azdoUser, "")
                    .setMessage("Init repo")
                    .call();

            // Create the credentials provider
            CredentialsProvider credentialsProvider = new UsernamePasswordCredentialsProvider(azdoUser, azdoPat);

            logger.debug("git.push");
            git.push()
                    .setPushAll()
                    .setCredentialsProvider(credentialsProvider)
                    .setForce(true)
                    .call();
        }

        catch (Exception e) {
            logger.debug("Exception pushing to repo: {}", e);
        }
    }

    public static Git checkout (Git git,
                                String targetPath,
                                String branchName,
                                boolean createRemoteBranch) {
        logger.debug("==> Method: GitUtils.checkout");
        logger.debug("targetPath: {}", targetPath);
        logger.debug("branchName: {}", branchName);
        logger.debug("createRemoteBranch: {}", createRemoteBranch);

        // If git object is invalid recreate it again
        if (git == null) {
            targetPath = Utils.fixPath(targetPath);
            git = createGit(targetPath);
        }

        // Perform a checkout
        if (git != null) {
            try {
                logger.debug("git.checkout");
                git.checkout()
                        .setForced(true)
                        .setCreateBranch(createRemoteBranch)
                        .setName(branchName)
                        .call();
            } catch (Exception e) {
                logger.debug("Exception occurred. Cannot checkout; just continue: {}", e);
            }
        }

        return git;
    }

    public static Git createGit (String targetPath) {
        logger.debug("==> Method: GitUtils.createGit");
        logger.debug("targetPath: {}", targetPath);

        // If git object is invalid recreate it again
        try {
            logger.debug("Recreate git object");
            targetPath = Utils.fixPath(targetPath);
            File f = new File(targetPath);
            git = Git.open(f);
        }
        catch (IOException e) {
            logger.debug("Cannot create a Git object: {}", e);
            return null;
        }

        return git;
    }
}

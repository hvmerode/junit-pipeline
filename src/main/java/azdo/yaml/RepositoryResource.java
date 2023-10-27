package azdo.yaml;

/******************************************************************************************
 Details of a repository.
 *******************************************************************************************/
public class RepositoryResource {
    public String repository;
    public String endpoint;
    public String trigger;
    public String name;
    public String project;
    public String ref;
    public String type;
    public String localBase; // Base location on the local file system
    public static final String LOCAL_SOURCE_POSTFIX = "_source"; // Prefix added to the cloned source directory to prevent mixing up with the local target directory
}

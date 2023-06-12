// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.hook;

import azdo.junit.AzDoPipeline;
import azdo.utils.PomUtils;
import azdo.utils.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/* Hook, to remove the dependency to the 'junit-pipeline' jar, before it is deployed to the AzDo test project.
   Normally, this dependency is stored in a repository or in Azure DevOps artifacts. When building the Maven
   artifact, the location of this dependency is configured and the Maven build will not fail.
   Rhe dependency is removed from the pom.xml to prevent build errors (cannot find library).
 */
public class DeleteJUnitPipelineDependency extends Hook {
    private String pom;
    private String groupId;
    private String artifactId;
    private static Logger logger = LoggerFactory.getLogger(DeleteJUnitPipelineDependency.class);
    public DeleteJUnitPipelineDependency (String pom, String groupId, String artifactId) {
        logger.debug("==> Class: DeleteJUnitPipelineDependency");

        this.pom = Utils.fixPath(pom);
        this.groupId = groupId;
        this.artifactId = artifactId;
    }

    @Override
    public void executeHook (){
        try {
            logger.debug("==> Method: DeleteJUnitPipelineDependency.executeHook");
            PomUtils.deleteDependency(pom, groupId, artifactId);
        }
        catch (Exception e) {}
    }
}

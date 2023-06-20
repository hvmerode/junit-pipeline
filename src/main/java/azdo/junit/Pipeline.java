// Copyright (c) Henry van Merode.
// Licensed under the MIT License.

package azdo.junit;

/*
   The Pipeline interface defines the actions on a pipeline. These actions are called by JUnit tests. In principle,
   multiple implementations of a pipeline are possible (Azure DeVOps or GitHub pipelines), but currently only
   Azure DevOps pipelines are supported.
*/
public interface Pipeline {
    void overrideVariable(String variableName, String value);
    void overrideParameterDefault(String parameterName, String value);
    void overrideTemplateParameter(String parameterName, String value);
    void overrideLiteral(String findLiteral, String replaceLiteral, boolean replaceAll);
    void overrideLiteral(String findLiteral, String replaceLiteral);
    void overrideCurrentBranch(String newBranchName, boolean replaceAll);
    void overrideCurrentBranch(String newBranchName);
    void skipStage(String stageName);
    void skipJob(String jobName);
    void skipStep(String stepName);
    void mockStep(String stepValue, String inlineScript);
//    Step getStepByType (String type);
//    Step getStepByDisplayName (String displayName);
}

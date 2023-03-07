package azdo.junit;

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
}

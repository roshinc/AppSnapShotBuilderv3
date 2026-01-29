package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents usage of a function within the project.
 */
public class FunctionUsage {
    
    private final String functionName;
    private final String fullyQualifiedName;
    private final String dependencyArtifactId;
    private List<FunctionInvocation> invocations;
    
    @JsonCreator
    public FunctionUsage(
            @JsonProperty("functionName") String functionName,
            @JsonProperty("fullyQualifiedName") String fullyQualifiedName,
            @JsonProperty("dependencyArtifactId") String dependencyArtifactId) {
        this.functionName = functionName;
        this.fullyQualifiedName = fullyQualifiedName;
        this.dependencyArtifactId = dependencyArtifactId;
        this.invocations = new ArrayList<>();
    }
    
    public String getFunctionName() {
        return functionName;
    }
    
    public String getFunctionId() {
        return functionName;
    }
    
    public String getFullyQualifiedName() {
        return fullyQualifiedName;
    }
    
    public String getDependencyArtifactId() {
        return dependencyArtifactId;
    }
    
    public List<FunctionInvocation> getInvocations() {
        return invocations;
    }
    
    public void setInvocations(List<FunctionInvocation> invocations) {
        this.invocations = invocations != null ? invocations : new ArrayList<>();
    }
}

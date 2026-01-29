package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the usage information for a function dependency.
 */
public class FunctionUsage {

    @JsonProperty("functionId")
    private String functionId;

    @JsonProperty("functionClass")
    private String functionClass;

    @JsonProperty("dependency")
    private String dependency;

    @JsonProperty("invocations")
    private List<FunctionInvocation> invocations;

    public FunctionUsage() {
    }

    public FunctionUsage(String functionId, String functionClass, String dependency) {
        this.functionId = functionId;
        this.functionClass = functionClass;
        this.dependency = dependency;
    }

    public String getFunctionId() {
        return functionId;
    }

    public void setFunctionId(String functionId) {
        this.functionId = functionId;
    }

    public String getFunctionClass() {
        return functionClass;
    }

    public void setFunctionClass(String functionClass) {
        this.functionClass = functionClass;
    }

    public String getDependency() {
        return dependency;
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    public List<FunctionInvocation> getInvocations() {
        return invocations == null ? null : new ArrayList<>(invocations);
    }

    public void setInvocations(List<FunctionInvocation> invocations) {
        this.invocations = invocations == null ? null : new ArrayList<>(invocations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionUsage that = (FunctionUsage) o;
        return Objects.equals(functionId, that.functionId) &&
                Objects.equals(functionClass, that.functionClass) &&
                Objects.equals(dependency, that.dependency) &&
                Objects.equals(invocations, that.invocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionId, functionClass, dependency, invocations);
    }

    @Override
    public String toString() {
        return "FunctionUsage{" +
                "functionId='" + functionId + '\'' +
                ", functionClass='" + functionClass + '\'' +
                ", dependency='" + dependency + '\'' +
                ", invocations=" + invocations +
                '}';
    }
}

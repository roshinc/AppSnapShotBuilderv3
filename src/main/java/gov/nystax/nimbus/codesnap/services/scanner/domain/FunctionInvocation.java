package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single invocation of a function and its call chain.
 */
public class FunctionInvocation implements UsageInvocation {

    @JsonProperty("invocationSite")
    private String invocationSite;

    @JsonProperty("enclosingMethod")
    private MethodReference enclosingMethod;

    @JsonProperty("callChain")
    private List<MethodReference> callChain;

    @JsonProperty("invocationType")
    private String invocationType;

    public FunctionInvocation() {
    }

    public FunctionInvocation(String invocationSite, MethodReference enclosingMethod) {
        this.invocationSite = invocationSite;
        this.enclosingMethod = enclosingMethod;
    }

    public FunctionInvocation(String invocationSite, MethodReference enclosingMethod, String invocationType) {
        this.invocationSite = invocationSite;
        this.enclosingMethod = enclosingMethod;
        this.invocationType = invocationType;
    }

    public String getInvocationSite() {
        return invocationSite;
    }

    public void setInvocationSite(String invocationSite) {
        this.invocationSite = invocationSite;
    }

    public MethodReference getEnclosingMethod() {
        return enclosingMethod;
    }

    public void setEnclosingMethod(MethodReference enclosingMethod) {
        this.enclosingMethod = enclosingMethod;
    }

    public List<MethodReference> getCallChain() {
        return callChain;
    }

    public void setCallChain(List<MethodReference> callChain) {
        this.callChain = callChain;
    }

    public String getInvocationType() {
        return invocationType;
    }

    public void setInvocationType(String invocationType) {
        this.invocationType = invocationType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionInvocation that = (FunctionInvocation) o;
        return Objects.equals(invocationSite, that.invocationSite) &&
                Objects.equals(enclosingMethod, that.enclosingMethod) &&
                Objects.equals(callChain, that.callChain) &&
                Objects.equals(invocationType, that.invocationType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invocationSite, enclosingMethod, callChain, invocationType);
    }

    @Override
    public String toString() {
        return "FunctionInvocation{" +
                "invocationSite='" + invocationSite + '\'' +
                ", enclosingMethod='" + enclosingMethod + '\'' +
                ", callChain=" + callChain +
                ", invocationType='" + invocationType + '\'' +
                '}';
    }
}

package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single invocation of a CTG client and its call chain.
 * The invocation type is either "invoke" (synchronous) or "invokeAsync" (asynchronous).
 */
public class CtgInvocation implements UsageInvocation {

    @JsonProperty("invocationSite")
    private String invocationSite;

    @JsonProperty("enclosingMethod")
    private MethodReference enclosingMethod;

    @JsonProperty("callChain")
    private List<MethodReference> callChain;

    @JsonProperty("invocationType")
    private String invocationType;

    public CtgInvocation() {
    }

    public CtgInvocation(String invocationSite, MethodReference enclosingMethod) {
        this.invocationSite = invocationSite;
        this.enclosingMethod = enclosingMethod;
    }

    public CtgInvocation(String invocationSite, MethodReference enclosingMethod, String invocationType) {
        this.invocationSite = invocationSite;
        this.enclosingMethod = enclosingMethod;
        this.invocationType = invocationType;
    }

    @Override
    public String getInvocationSite() {
        return invocationSite;
    }

    @Override
    public void setInvocationSite(String invocationSite) {
        this.invocationSite = invocationSite;
    }

    @Override
    public MethodReference getEnclosingMethod() {
        return enclosingMethod;
    }

    @Override
    public void setEnclosingMethod(MethodReference enclosingMethod) {
        this.enclosingMethod = enclosingMethod;
    }

    @Override
    public List<MethodReference> getCallChain() {
        return callChain;
    }

    @Override
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
        CtgInvocation that = (CtgInvocation) o;
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
        return "CtgInvocation{" +
                "invocationSite='" + invocationSite + '\'' +
                ", enclosingMethod='" + enclosingMethod + '\'' +
                ", callChain=" + callChain +
                ", invocationType='" + invocationType + '\'' +
                '}';
    }
}

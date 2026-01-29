package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single invocation of a service's function package and its call chain.
 * Unlike function invocations which have specific patterns, service invocations
 * can be any method call within the <serviceid>.* package.
 */
public class ServiceInvocation implements UsageInvocation {

    @JsonProperty("invocationSite")
    private String invocationSite;

    @JsonProperty("enclosingMethod")
    private MethodReference enclosingMethod;

    @JsonProperty("invokedMethod")
    private String invokedMethod;

    @JsonProperty("callChain")
    private List<MethodReference> callChain;

    public ServiceInvocation() {
    }

    public ServiceInvocation(String invocationSite, MethodReference enclosingMethod, String invokedMethod) {
        this.invocationSite = invocationSite;
        this.enclosingMethod = enclosingMethod;
        this.invokedMethod = invokedMethod;
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

    public String getInvokedMethod() {
        return invokedMethod;
    }

    public void setInvokedMethod(String invokedMethod) {
        this.invokedMethod = invokedMethod;
    }

    public List<MethodReference> getCallChain() {
        return callChain;
    }

    public void setCallChain(List<MethodReference> callChain) {
        this.callChain = callChain;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceInvocation that = (ServiceInvocation) o;
        return Objects.equals(invocationSite, that.invocationSite) &&
                Objects.equals(enclosingMethod, that.enclosingMethod) &&
                Objects.equals(invokedMethod, that.invokedMethod) &&
                Objects.equals(callChain, that.callChain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invocationSite, enclosingMethod, invokedMethod, callChain);
    }

    @Override
    public String toString() {
        return "ServiceInvocation{" +
                "invocationSite='" + invocationSite + '\'' +
                ", enclosingMethod='" + enclosingMethod + '\'' +
                ", invokedMethod='" + invokedMethod + '\'' +
                ", callChain=" + callChain +
                '}';
    }
}

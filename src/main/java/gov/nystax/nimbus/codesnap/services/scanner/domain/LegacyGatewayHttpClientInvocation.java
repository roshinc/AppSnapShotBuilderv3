package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single invocation of an event publisher and its call chain.
 */
public class LegacyGatewayHttpClientInvocation implements UsageInvocation {

    @JsonProperty("invocationSite")
    private String invocationSite;

    @JsonProperty("enclosingMethod")
    private MethodReference enclosingMethod;

    @JsonProperty("callChain")
    private List<MethodReference> callChain;


    public LegacyGatewayHttpClientInvocation() {
    }

    public LegacyGatewayHttpClientInvocation(String invocationSite, MethodReference enclosingMethod) {
        this.invocationSite = invocationSite;
        this.enclosingMethod = enclosingMethod;
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


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LegacyGatewayHttpClientInvocation that = (LegacyGatewayHttpClientInvocation) o;
        return Objects.equals(invocationSite, that.invocationSite) &&
                Objects.equals(enclosingMethod, that.enclosingMethod) &&
                Objects.equals(callChain, that.callChain);
    }

    @Override
    public int hashCode() {
        return Objects.hash(invocationSite, enclosingMethod, callChain);
    }

    @Override
    public String toString() {
        return "EventPublisherInvocation{" +
                "invocationSite='" + invocationSite + '\'' +
                ", enclosingMethod=" + enclosingMethod +
                ", callChain=" + callChain +
                '}';
    }
}

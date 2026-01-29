package gov.nystax.nimbus.codesnap.services.scanner.domain;

import java.util.List;

public interface UsageInvocation {
    public String getInvocationSite();

    public void setInvocationSite(String invocationSite);

    public MethodReference getEnclosingMethod();

    public void setEnclosingMethod(MethodReference enclosingMethod);

    public List<MethodReference> getCallChain();

    public void setCallChain(List<MethodReference> callChain);
}

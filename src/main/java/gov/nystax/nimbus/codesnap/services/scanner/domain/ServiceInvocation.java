package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a service invocation with its calling context.
 */
public class ServiceInvocation {
    
    private final String locationInCode;
    private final MethodReference invokingMethod;
    private final String targetInterfaceMethod;
    private List<MethodReference> callChain;
    
    @JsonCreator
    public ServiceInvocation(
            @JsonProperty("locationInCode") String locationInCode,
            @JsonProperty("invokingMethod") MethodReference invokingMethod,
            @JsonProperty("targetInterfaceMethod") String targetInterfaceMethod) {
        this.locationInCode = locationInCode;
        this.invokingMethod = invokingMethod;
        this.targetInterfaceMethod = targetInterfaceMethod;
        this.callChain = new ArrayList<>();
    }
    
    public String getLocationInCode() {
        return locationInCode;
    }
    
    public String getInvocationSite() {
        return locationInCode;
    }
    
    public String getInvokedMethod() {
        return targetInterfaceMethod;
    }
    
    public MethodReference getInvokingMethod() {
        return invokingMethod;
    }
    
    public String getTargetInterfaceMethod() {
        return targetInterfaceMethod;
    }
    
    public List<MethodReference> getCallChain() {
        return callChain;
    }
    
    public void setCallChain(List<MethodReference> callChain) {
        this.callChain = callChain != null ? callChain : new ArrayList<>();
    }
}

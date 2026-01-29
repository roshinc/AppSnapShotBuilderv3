package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a function invocation with its calling context.
 */
public class FunctionInvocation {
    
    private final String locationInCode;
    private final MethodReference invokingMethod;
    private final String invocationType;
    private List<MethodReference> callChain;
    
    @JsonCreator
    public FunctionInvocation(
            @JsonProperty("locationInCode") String locationInCode,
            @JsonProperty("invokingMethod") MethodReference invokingMethod,
            @JsonProperty("invocationType") String invocationType) {
        this.locationInCode = locationInCode;
        this.invokingMethod = invokingMethod;
        this.invocationType = invocationType;
        this.callChain = new ArrayList<>();
    }
    
    public String getLocationInCode() {
        return locationInCode;
    }
    
    public String getInvocationSite() {
        return locationInCode;
    }
    
    public MethodReference getInvokingMethod() {
        return invokingMethod;
    }
    
    public String getInvocationType() {
        return invocationType;
    }
    
    public List<MethodReference> getCallChain() {
        return callChain;
    }
    
    public void setCallChain(List<MethodReference> callChain) {
        this.callChain = callChain != null ? callChain : new ArrayList<>();
    }
}

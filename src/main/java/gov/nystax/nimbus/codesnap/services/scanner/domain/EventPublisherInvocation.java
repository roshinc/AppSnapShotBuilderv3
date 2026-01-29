package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents an event publisher invocation with topic resolution status.
 */
public class EventPublisherInvocation {
    
    private final String locationInCode;
    private final MethodReference invokingMethod;
    private final String topicName;
    private final TopicResolution topicResolution;
    private List<MethodReference> callChain;
    
    @JsonCreator
    public EventPublisherInvocation(
            @JsonProperty("locationInCode") String locationInCode,
            @JsonProperty("invokingMethod") MethodReference invokingMethod,
            @JsonProperty("topicName") String topicName,
            @JsonProperty("topicResolution") TopicResolution topicResolution) {
        this.locationInCode = locationInCode;
        this.invokingMethod = invokingMethod;
        this.topicName = topicName;
        this.topicResolution = topicResolution;
        this.callChain = new ArrayList<>();
    }
    
    public String getLocationInCode() {
        return locationInCode;
    }
    
    public String getInvocationSite() {
        return locationInCode;
    }
    
    public String getTopic() {
        return topicName;
    }
    
    public MethodReference getInvokingMethod() {
        return invokingMethod;
    }
    
    public String getTopicName() {
        return topicName;
    }
    
    public TopicResolution getTopicResolution() {
        return topicResolution;
    }
    
    public List<MethodReference> getCallChain() {
        return callChain;
    }
    
    public void setCallChain(List<MethodReference> callChain) {
        this.callChain = callChain != null ? callChain : new ArrayList<>();
    }
}

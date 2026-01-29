package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * Represents a single invocation of an event publisher and its call chain.
 */
public class EventPublisherInvocation implements UsageInvocation {

    @JsonProperty("invocationSite")
    private String invocationSite;

    @JsonProperty("enclosingMethod")
    private MethodReference enclosingMethod;

    @JsonProperty("callChain")
    private List<MethodReference> callChain;

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("topicResolution")
    private TopicResolution topicResolution;

    public EventPublisherInvocation() {
    }

    public EventPublisherInvocation(String invocationSite, MethodReference enclosingMethod, String topic, TopicResolution topicResolution) {
        this.invocationSite = invocationSite;
        this.enclosingMethod = enclosingMethod;
        this.topic = topic;
        this.topicResolution = topicResolution;
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

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public TopicResolution getTopicResolution() {
        return topicResolution;
    }

    public void setTopicResolution(TopicResolution topicResolution) {
        this.topicResolution = topicResolution;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventPublisherInvocation that = (EventPublisherInvocation) o;
        return Objects.equals(invocationSite, that.invocationSite) &&
                Objects.equals(enclosingMethod, that.enclosingMethod) &&
                Objects.equals(callChain, that.callChain) &&
                Objects.equals(topic, that.topic) &&
                topicResolution == that.topicResolution;
    }

    @Override
    public int hashCode() {
        return Objects.hash(invocationSite, enclosingMethod, callChain, topic, topicResolution);
    }

    @Override
    public String toString() {
        return "EventPublisherInvocation{" +
                "invocationSite='" + invocationSite + '\'' +
                ", enclosingMethod=" + enclosingMethod +
                ", callChain=" + callChain +
                ", topic='" + topic + '\'' +
                ", topicResolution=" + topicResolution +
                '}';
    }
}

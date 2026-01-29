package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the usage information for an event publisher topic.
 * Groups all invocations that publish to the same topic.
 */
public class EventPublisherUsage {

    @JsonProperty("topic")
    private String topic;

    @JsonProperty("invocations")
    private List<EventPublisherInvocation> invocations;

    public EventPublisherUsage() {
    }

    public EventPublisherUsage(String topic) {
        this.topic = topic;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public List<EventPublisherInvocation> getInvocations() {
        return invocations == null ? null : new ArrayList<>(invocations);
    }

    public void setInvocations(List<EventPublisherInvocation> invocations) {
        this.invocations = invocations == null ? null : new ArrayList<>(invocations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EventPublisherUsage that = (EventPublisherUsage) o;
        return Objects.equals(topic, that.topic) &&
                Objects.equals(invocations, that.invocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, invocations);
    }

    @Override
    public String toString() {
        return "EventPublisherUsage{" +
                "topic='" + topic + '\'' +
                ", invocations=" + invocations +
                '}';
    }
}

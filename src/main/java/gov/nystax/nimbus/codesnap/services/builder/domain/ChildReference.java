package gov.nystax.nimbus.codesnap.services.builder.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a child reference in the function pool or app template.
 * Can be a sync function ref, async function ref, or topic publish ref.
 * 
 * <p>JSON output formats:</p>
 * <ul>
 *   <li>Sync: {"ref": "functionName"}</li>
 *   <li>Async: {"ref": "functionName", "async": true, "queueName": "QUEUE.NAME"}</li>
 *   <li>Topic: {"topicName": "eventName", "topicPublish": true, "queueName": "QUEUE.NAME"}</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ChildReference {

    @JsonProperty("ref")
    private String ref;

    @JsonProperty("async")
    private Boolean async;

    @JsonProperty("topicName")
    private String topicName;

    @JsonProperty("topicPublish")
    private Boolean topicPublish;

    @JsonProperty("queueName")
    private String queueName;

    @JsonProperty("ctgInvoke")
    private Boolean ctgInvoke;

    private ChildReference() {
    }

    /**
     * Creates a synchronous function reference.
     *
     * @param functionName the function name to reference
     * @return a sync function reference
     */
    public static ChildReference syncRef(String functionName) {
        ChildReference ref = new ChildReference();
        ref.ref = functionName;
        return ref;
    }

    /**
     * Creates an asynchronous function reference.
     *
     * @param functionName the function name to reference
     * @param queueName the queue name for async execution
     * @return an async function reference
     */
    public static ChildReference asyncRef(String functionName, String queueName) {
        ChildReference ref = new ChildReference();
        ref.ref = functionName;
        ref.async = true;
        ref.queueName = queueName;
        return ref;
    }

    /**
     * Creates a topic publish reference.
     *
     * @param topicName the topic name to publish to
     * @param queueName the queue name for the topic
     * @return a topic publish reference
     */
    public static ChildReference topicPublishRef(String topicName, String queueName) {
        ChildReference ref = new ChildReference();
        ref.topicName = topicName;
        ref.topicPublish = true;
        ref.queueName = queueName;
        return ref;
    }

    /**
     * Creates a synchronous CTG component reference.
     *
     * @param ctgComponentId the CTG component ID to reference
     * @return a sync CTG reference
     */
    public static ChildReference ctgRef(String ctgComponentId) {
        ChildReference ref = new ChildReference();
        ref.ref = ctgComponentId;
        ref.ctgInvoke = true;
        return ref;
    }

    /**
     * Creates an asynchronous CTG component reference.
     *
     * @param ctgComponentId the CTG component ID to reference
     * @param queueName the queue name for async execution
     * @return an async CTG reference
     */
    public static ChildReference asyncCtgRef(String ctgComponentId, String queueName) {
        ChildReference ref = new ChildReference();
        ref.ref = ctgComponentId;
        ref.ctgInvoke = true;
        ref.async = true;
        ref.queueName = queueName;
        return ref;
    }

    public String getRef() {
        return ref;
    }

    public Boolean getAsync() {
        return async;
    }

    public String getTopicName() {
        return topicName;
    }

    public Boolean getTopicPublish() {
        return topicPublish;
    }

    public String getQueueName() {
        return queueName;
    }

    public boolean isSyncRef() {
        return ref != null && (async == null || !async) && topicPublish == null && (ctgInvoke == null || !ctgInvoke);
    }

    public boolean isAsyncRef() {
        return ref != null && async != null && async && (ctgInvoke == null || !ctgInvoke);
    }

    public boolean isTopicRef() {
        return topicPublish != null && topicPublish;
    }

    public boolean isCtgRef() {
        return ctgInvoke != null && ctgInvoke && (async == null || !async);
    }

    public boolean isAsyncCtgRef() {
        return ctgInvoke != null && ctgInvoke && async != null && async;
    }

    public Boolean getCtgInvoke() {
        return ctgInvoke;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChildReference that = (ChildReference) o;
        return Objects.equals(ref, that.ref) &&
                Objects.equals(async, that.async) &&
                Objects.equals(topicName, that.topicName) &&
                Objects.equals(topicPublish, that.topicPublish) &&
                Objects.equals(queueName, that.queueName) &&
                Objects.equals(ctgInvoke, that.ctgInvoke);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ref, async, topicName, topicPublish, queueName, ctgInvoke);
    }

    @Override
    public String toString() {
        if (isCtgRef()) {
            return "CtgRef{" + ref + "}";
        } else if (isAsyncCtgRef()) {
            return "AsyncCtgRef{" + ref + ", queue=" + queueName + "}";
        } else if (isSyncRef()) {
            return "SyncRef{" + ref + "}";
        } else if (isAsyncRef()) {
            return "AsyncRef{" + ref + ", queue=" + queueName + "}";
        } else if (isTopicRef()) {
            return "TopicRef{" + topicName + ", queue=" + queueName + "}";
        }
        return "ChildReference{unknown}";
    }
}

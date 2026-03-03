package gov.nystax.nimbus.codesnap.services.builder.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Locale;
import java.util.Objects;

/**
 * Represents a child reference in the function pool or app template.
 * Can be a sync function ref, async function ref, topic publish ref, or CTG ref.
 *
 * <p>JSON output formats:</p>
 * <ul>
 *   <li>Sync: {"ref": "functionName"}</li>
 *   <li>Async: {"ref": "functionName", "async": true}</li>
 *   <li>Topic: {"topicName": "eventName", "topicPublish": true, "queueName": "QUEUE.NAME"}</li>
 *   <li>Sync CTG: {"ref": "ctg_tz0001z", "ctg": true}</li>
 *   <li>Async CTG: {"ref": "ctg_tz0001z", "ctg": true, "async": true}</li>
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

    @JsonProperty("ctg")
    private Boolean ctg;

    public static final String CTG_PREFIX = "ctg_";

    private ChildReference() {
    }

    /**
     * Converts a raw CTG component ID (e.g. "TZ0001Z") to its pool key
     * (e.g. "ctg_tz0001z").
     */
    public static String ctgKey(String ctgComponentId) {
        return CTG_PREFIX + ctgComponentId.toLowerCase(Locale.ROOT);
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
     * The queue name is available on the target function's own top-level pool entry.
     *
     * @param functionName the function name to reference
     * @return an async function reference
     */
    public static ChildReference asyncRef(String functionName) {
        ChildReference ref = new ChildReference();
        ref.ref = functionName;
        ref.async = true;
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
     * The ref is stored as the CTG pool key (e.g. "ctg_tz0001z").
     *
     * @param ctgComponentId the raw CTG component ID (e.g. "TZ0001Z")
     * @return a sync CTG reference
     */
    public static ChildReference ctgRef(String ctgComponentId) {
        ChildReference ref = new ChildReference();
        ref.ref = ctgKey(ctgComponentId);
        ref.ctg = true;
        return ref;
    }

    /**
     * Creates an asynchronous CTG component reference.
     * The ref is stored as the CTG pool key (e.g. "ctg_tz0001z").
     * The queue name is available on the target CTG's own top-level pool entry.
     *
     * @param ctgComponentId the raw CTG component ID (e.g. "TZ0001Z")
     * @return an async CTG reference
     */
    public static ChildReference asyncCtgRef(String ctgComponentId) {
        ChildReference ref = new ChildReference();
        ref.ref = ctgKey(ctgComponentId);
        ref.ctg = true;
        ref.async = true;
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
        return ref != null && (async == null || !async) && (topicPublish == null || !topicPublish);
    }

    public boolean isAsyncRef() {
        return ref != null && async != null && async;
    }

    public boolean isTopicRef() {
        return topicPublish != null && topicPublish;
    }

    public boolean isCtg() {
        return ctg != null && ctg;
    }

    public Boolean getCtg() {
        return ctg;
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
                Objects.equals(ctg, that.ctg);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ref, async, topicName, topicPublish, queueName, ctg);
    }

    @Override
    public String toString() {
        if (isTopicRef()) {
            return "TopicRef{" + topicName + ", queue=" + queueName + "}";
        }
        String prefix = isCtg() ? "Ctg" : "";
        if (isAsyncRef()) {
            if (queueName != null) {
                return prefix + "AsyncRef{" + ref + ", queue=" + queueName + "}";
            }
            return prefix + "AsyncRef{" + ref + "}";
        }
        if (isSyncRef()) {
            return prefix + "SyncRef{" + ref + "}";
        }
        return "ChildReference{unknown}";
    }
}

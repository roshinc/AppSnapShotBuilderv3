package gov.nystax.nimbus.codesnap.services.builder.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a function entry in the FunctionPool.
 * Contains the function's children (other functions, async refs, topic refs)
 * and the app this function belongs to.
 *
 * <p>JSON output format:</p>
 * <pre>
 * {
 *   "app": "MyApp",
 *   "children": [
 *     {"ref": "childFunc1"},
 *     {"ref": "asyncFunc", "async": true, "queueName": "QUEUE.NAME"},
 *     {"topicName": "event", "topicPublish": true, "queueName": "TOPIC.Q"}
 *   ]
 * }
 * </pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class FunctionPoolEntry {

    @JsonProperty("app")
    private String app;

    @JsonProperty("children")
    private List<ChildReference> children;

    public FunctionPoolEntry() {
        this.children = new ArrayList<>();
    }

    public FunctionPoolEntry(String app) {
        this.app = app;
        this.children = new ArrayList<>();
    }

    public String getApp() {
        return app;
    }

    public void setApp(String app) {
        this.app = app;
    }

    public void addChild(ChildReference child) {
        if (child != null) {
            this.children.add(child);
        }
    }

    public void addSyncRef(String functionName) {
        this.children.add(ChildReference.syncRef(functionName));
    }

    public void addAsyncRef(String functionName, String queueName) {
        this.children.add(ChildReference.asyncRef(functionName, queueName));
    }

    public void addTopicRef(String topicName, String queueName) {
        this.children.add(ChildReference.topicPublishRef(topicName, queueName));
    }

    /**
     * Checks if this function entry already contains a reference to the given function.
     */
    public boolean containsSyncRef(String functionName) {
        return children.stream()
                .anyMatch(c -> c.isSyncRef() && functionName.equals(c.getRef()));
    }

    /**
     * Checks if this function entry already contains an async reference to the given function.
     */
    public boolean containsAsyncRef(String functionName) {
        return children.stream()
                .anyMatch(c -> c.isAsyncRef() && functionName.equals(c.getRef()));
    }

    /**
     * Checks if this function entry already contains a topic reference.
     */
    public boolean containsTopicRef(String topicName) {
        return children.stream()
                .anyMatch(c -> c.isTopicRef() && topicName.equals(c.getTopicName()));
    }

    public List<ChildReference> getChildren() {
        return children == null ? null : new ArrayList<>(children);
    }

    public void setChildren(List<ChildReference> children) {
        this.children = children == null ? new ArrayList<>() : new ArrayList<>(children);
    }

    public boolean isEmpty() {
        return children == null || children.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FunctionPoolEntry that = (FunctionPoolEntry) o;
        return Objects.equals(app, that.app) && Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(app, children);
    }

    @Override
    public String toString() {
        return "FunctionPoolEntry{" +
                "app='" + app + '\'' +
                ", children=" + children +
                '}';
    }
}

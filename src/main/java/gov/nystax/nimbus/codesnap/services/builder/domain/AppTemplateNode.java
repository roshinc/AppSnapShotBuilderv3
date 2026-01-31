package gov.nystax.nimbus.codesnap.services.builder.domain;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents a node in the AppTemplate tree structure.
 * 
 * <p>Node types:</p>
 * <ul>
 *   <li>app - Root application node</li>
 *   <li>function - Function reference (via ref field)</li>
 *   <li>ui-services - Container for UI service methods</li>
 *   <li>ui-service-method - A method within a UI service</li>
 *   <li>timer - Async queue wrapper</li>
 *   <li>topic - Topic publish wrapper</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppTemplateNode {

    public static final String TYPE_APP = "app";
    public static final String TYPE_FUNCTION = "function";
    public static final String TYPE_UI_SERVICES = "ui-services";
    public static final String TYPE_UI_SERVICE_METHOD = "ui-service-method";
    public static final String TYPE_TIMER = "timer";
    public static final String TYPE_TOPIC = "topic";

    @JsonProperty("name")
    private String name;

    @JsonProperty("type")
    private String type;

    @JsonProperty("ref")
    private String ref;

    @JsonProperty("async")
    private Boolean async;

    @JsonProperty("queueName")
    private String queueName;

    @JsonProperty("topicName")
    private String topicName;

    @JsonProperty("topicPublish")
    private Boolean topicPublish;

    @JsonProperty("children")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<AppTemplateNode> children;

    public AppTemplateNode() {
    }

    /**
     * Creates an app root node.
     */
    public static AppTemplateNode app(String appName) {
        AppTemplateNode node = new AppTemplateNode();
        node.name = appName;
        node.type = TYPE_APP;
        node.children = new ArrayList<>();
        return node;
    }

    /**
     * Creates a sync function reference node.
     */
    public static AppTemplateNode functionRef(String functionName) {
        AppTemplateNode node = new AppTemplateNode();
        node.ref = functionName;
        return node;
    }

    /**
     * Creates an async function reference node (timer wrapper).
     */
    public static AppTemplateNode asyncFunctionRef(String functionName, String queueName) {
        AppTemplateNode node = new AppTemplateNode();
        node.ref = functionName;
        node.async = true;
        node.queueName = queueName;
        return node;
    }

    /**
     * Creates a topic publish reference node.
     */
    public static AppTemplateNode topicPublishRef(String topicName, String queueName) {
        AppTemplateNode node = new AppTemplateNode();
        node.topicName = topicName;
        node.topicPublish = true;
        node.queueName = queueName;
        return node;
    }

    /**
     * Creates a UI services container node.
     */
    public static AppTemplateNode uiServices(String serviceName) {
        AppTemplateNode node = new AppTemplateNode();
        node.name = serviceName;
        node.type = TYPE_UI_SERVICES;
        node.children = new ArrayList<>();
        return node;
    }

    /**
     * Creates a UI service method node.
     */
    public static AppTemplateNode uiServiceMethod(String methodName) {
        AppTemplateNode node = new AppTemplateNode();
        node.name = methodName;
        node.type = TYPE_UI_SERVICE_METHOD;
        node.children = new ArrayList<>();
        return node;
    }

    public void addChild(AppTemplateNode child) {
        if (this.children == null) {
            this.children = new ArrayList<>();
        }
        this.children.add(child);
    }

    public void addFunctionRef(String functionName) {
        addChild(functionRef(functionName));
    }

    public void addAsyncFunctionRef(String functionName, String queueName) {
        addChild(asyncFunctionRef(functionName, queueName));
    }

    public void addTopicPublishRef(String topicName, String queueName) {
        addChild(topicPublishRef(topicName, queueName));
    }

    // Getters and setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Boolean getAsync() {
        return async;
    }

    public void setAsync(Boolean async) {
        this.async = async;
    }

    public String getQueueName() {
        return queueName;
    }

    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }

    public String getTopicName() {
        return topicName;
    }

    public void setTopicName(String topicName) {
        this.topicName = topicName;
    }

    public Boolean getTopicPublish() {
        return topicPublish;
    }

    public void setTopicPublish(Boolean topicPublish) {
        this.topicPublish = topicPublish;
    }

    public List<AppTemplateNode> getChildren() {
        return children == null ? null : new ArrayList<>(children);
    }

    public void setChildren(List<AppTemplateNode> children) {
        this.children = children == null ? null : new ArrayList<>(children);
    }

    public boolean hasChildren() {
        return children != null && !children.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AppTemplateNode that = (AppTemplateNode) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(type, that.type) &&
                Objects.equals(ref, that.ref) &&
                Objects.equals(async, that.async) &&
                Objects.equals(queueName, that.queueName) &&
                Objects.equals(topicName, that.topicName) &&
                Objects.equals(topicPublish, that.topicPublish) &&
                Objects.equals(children, that.children);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, type, ref, async, queueName, topicName, topicPublish, children);
    }

    @Override
    public String toString() {
        if (ref != null) {
            return async != null && async ? 
                    "AsyncRef{" + ref + "}" : 
                    "Ref{" + ref + "}";
        }
        if (topicPublish != null && topicPublish) {
            return "TopicRef{" + topicName + "}";
        }
        return "Node{" + type + ":" + name + "}";
    }
}

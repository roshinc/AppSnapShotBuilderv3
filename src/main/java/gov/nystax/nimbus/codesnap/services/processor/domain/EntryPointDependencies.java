package gov.nystax.nimbus.codesnap.services.processor.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Represents all dependencies for an entry point (function or UI service method).
 * Used in both entryPointChildren and publicMethodDependencies.
 */
public class EntryPointDependencies {

    /**
     * Synchronous function calls (invocationType: "execute").
     * Contains function IDs/names.
     */
    @JsonProperty("functions")
    private Set<String> functions;

    /**
     * Asynchronous function calls (invocationType: "executeAsync").
     * Contains function IDs/names. Queue names are resolved at build time.
     */
    @JsonProperty("asyncFunctions")
    private Set<String> asyncFunctions;

    /**
     * Topic publish events (topicResolution: "RESOLVED").
     * Contains topic names.
     */
    @JsonProperty("topics")
    private Set<String> topics;

    /**
     * Calls to other services. These are resolved transitively at build time.
     */
    @JsonProperty("serviceCalls")
    private List<ServiceCallReference> serviceCalls;

    public EntryPointDependencies() {
        this.functions = new LinkedHashSet<>();
        this.asyncFunctions = new LinkedHashSet<>();
        this.topics = new LinkedHashSet<>();
        this.serviceCalls = new ArrayList<>();
    }

    /**
     * Creates a deep copy of this object.
     */
    public EntryPointDependencies copy() {
        EntryPointDependencies copy = new EntryPointDependencies();
        copy.functions = new LinkedHashSet<>(this.functions);
        copy.asyncFunctions = new LinkedHashSet<>(this.asyncFunctions);
        copy.topics = new LinkedHashSet<>(this.topics);
        copy.serviceCalls = new ArrayList<>();
        for (ServiceCallReference call : this.serviceCalls) {
            copy.serviceCalls.add(call.copy());
        }
        return copy;
    }

    /**
     * Merges another EntryPointDependencies into this one.
     * Used when multiple invocations contribute to the same entry point.
     */
    public void merge(EntryPointDependencies other) {
        if (other == null) {
            return;
        }
        this.functions.addAll(other.functions);
        this.asyncFunctions.addAll(other.asyncFunctions);
        this.topics.addAll(other.topics);
        
        // For service calls, avoid duplicates based on serviceId + interfaceMethod
        for (ServiceCallReference otherCall : other.serviceCalls) {
            boolean exists = this.serviceCalls.stream()
                    .anyMatch(existing -> existing.getServiceId().equals(otherCall.getServiceId()) &&
                            existing.getInterfaceMethod().equals(otherCall.getInterfaceMethod()));
            if (!exists) {
                this.serviceCalls.add(otherCall.copy());
            }
        }
    }

    /**
     * Checks if this dependency set is empty (has no dependencies).
     */
    public boolean isEmpty() {
        return functions.isEmpty() && 
               asyncFunctions.isEmpty() && 
               topics.isEmpty() && 
               serviceCalls.isEmpty();
    }

    public void addFunction(String functionName) {
        this.functions.add(functionName);
    }

    public void addAsyncFunction(String functionName) {
        this.asyncFunctions.add(functionName);
    }

    public void addTopic(String topicName) {
        this.topics.add(topicName);
    }

    public void addServiceCall(String serviceId, String interfaceMethod) {
        ServiceCallReference call = new ServiceCallReference(serviceId, interfaceMethod);
        // Avoid duplicates
        boolean exists = this.serviceCalls.stream()
                .anyMatch(existing -> existing.getServiceId().equals(serviceId) &&
                        existing.getInterfaceMethod().equals(interfaceMethod));
        if (!exists) {
            this.serviceCalls.add(call);
        }
    }

    public Set<String> getFunctions() {
        return functions == null ? null : new LinkedHashSet<>(functions);
    }

    public void setFunctions(Set<String> functions) {
        this.functions = functions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(functions);
    }

    public Set<String> getAsyncFunctions() {
        return asyncFunctions == null ? null : new LinkedHashSet<>(asyncFunctions);
    }

    public void setAsyncFunctions(Set<String> asyncFunctions) {
        this.asyncFunctions = asyncFunctions == null ? new LinkedHashSet<>() : new LinkedHashSet<>(asyncFunctions);
    }

    public Set<String> getTopics() {
        return topics == null ? null : new LinkedHashSet<>(topics);
    }

    public void setTopics(Set<String> topics) {
        this.topics = topics == null ? new LinkedHashSet<>() : new LinkedHashSet<>(topics);
    }

    public List<ServiceCallReference> getServiceCalls() {
        return serviceCalls == null ? null : new ArrayList<>(serviceCalls);
    }

    public void setServiceCalls(List<ServiceCallReference> serviceCalls) {
        this.serviceCalls = serviceCalls == null ? new ArrayList<>() : new ArrayList<>(serviceCalls);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        EntryPointDependencies that = (EntryPointDependencies) o;
        return Objects.equals(functions, that.functions) &&
                Objects.equals(asyncFunctions, that.asyncFunctions) &&
                Objects.equals(topics, that.topics) &&
                Objects.equals(serviceCalls, that.serviceCalls);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functions, asyncFunctions, topics, serviceCalls);
    }

    @Override
    public String toString() {
        return "EntryPointDependencies{" +
                "functions=" + functions +
                ", asyncFunctions=" + asyncFunctions +
                ", topics=" + topics +
                ", serviceCalls=" + serviceCalls +
                '}';
    }
}

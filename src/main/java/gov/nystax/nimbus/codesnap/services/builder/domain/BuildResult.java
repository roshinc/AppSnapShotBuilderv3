package gov.nystax.nimbus.codesnap.services.builder.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Result of building an app snapshot.
 * Contains the AppTemplate tree and the FunctionPool definitions.
 * 
 * <p>The AppTemplate is the hierarchical structure consumed by the frontend tree-builder.js.</p>
 * <p>The FunctionPool contains all function definitions with their children.</p>
 */
public class BuildResult {

    @JsonProperty("appTemplate")
    private AppTemplateNode appTemplate;

    @JsonProperty("functionPool")
    private Map<String, FunctionPoolEntry> functionPool;

    public BuildResult() {
        this.functionPool = new HashMap<>();
    }

    public BuildResult(AppTemplateNode appTemplate, Map<String, FunctionPoolEntry> functionPool) {
        this.appTemplate = appTemplate;
        this.functionPool = functionPool == null ? new HashMap<>() : new HashMap<>(functionPool);
    }

    public AppTemplateNode getAppTemplate() {
        return appTemplate;
    }

    public void setAppTemplate(AppTemplateNode appTemplate) {
        this.appTemplate = appTemplate;
    }

    public Map<String, FunctionPoolEntry> getFunctionPool() {
        return functionPool == null ? null : new HashMap<>(functionPool);
    }

    public void setFunctionPool(Map<String, FunctionPoolEntry> functionPool) {
        this.functionPool = functionPool == null ? new HashMap<>() : new HashMap<>(functionPool);
    }

    /**
     * Adds a function to the pool. If the function already exists, returns the existing entry.
     *
     * @param functionName the function name
     * @return the function pool entry (existing or new)
     */
    public FunctionPoolEntry getOrCreateFunction(String functionName) {
        return functionPool.computeIfAbsent(functionName, k -> new FunctionPoolEntry());
    }

    /**
     * Checks if the function pool contains a function.
     */
    public boolean hasFunction(String functionName) {
        return functionPool.containsKey(functionName);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildResult that = (BuildResult) o;
        return Objects.equals(appTemplate, that.appTemplate) &&
                Objects.equals(functionPool, that.functionPool);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appTemplate, functionPool);
    }

    @Override
    public String toString() {
        return "BuildResult{" +
                "appTemplate=" + (appTemplate != null ? appTemplate.getName() : "null") +
                ", functionPoolSize=" + (functionPool != null ? functionPool.size() : 0) +
                '}';
    }
}

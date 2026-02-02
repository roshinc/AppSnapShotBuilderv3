package gov.nystax.nimbus.codesnap.services.builder.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Result of building an app snapshot.
 * Contains the AppTemplate tree and the FunctionPool definitions.
 *
 * <p>The AppTemplate is the hierarchical structure consumed by the frontend tree-builder.js.</p>
 * <p>The FunctionPool contains all function definitions with their children.</p>
 * <p>The result also includes information about failed scans that may result in an incomplete build.</p>
 */
public class BuildResult {

    @JsonProperty("appTemplate")
    private AppTemplateNode appTemplate;

    @JsonProperty("functionPool")
    private Map<String, FunctionPoolEntry> functionPool;

    @JsonProperty("isComplete")
    private boolean isComplete;

    @JsonProperty("failedServices")
    private List<FailedServiceInfo> failedServices;

    @JsonProperty("warnings")
    private List<String> warnings;

    public BuildResult() {
        this.functionPool = new HashMap<>();
        this.failedServices = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.isComplete = true;
    }

    public BuildResult(AppTemplateNode appTemplate, Map<String, FunctionPoolEntry> functionPool) {
        this.appTemplate = appTemplate;
        this.functionPool = functionPool == null ? new HashMap<>() : new HashMap<>(functionPool);
        this.failedServices = new ArrayList<>();
        this.warnings = new ArrayList<>();
        this.isComplete = true;
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

    /**
     * Returns whether the build is complete (no failed scans).
     */
    public boolean isComplete() {
        return isComplete;
    }

    /**
     * Sets whether the build is complete.
     */
    public void setComplete(boolean complete) {
        isComplete = complete;
    }

    /**
     * Returns the list of failed services.
     */
    public List<FailedServiceInfo> getFailedServices() {
        return failedServices == null ? null : new ArrayList<>(failedServices);
    }

    /**
     * Sets the list of failed services.
     */
    public void setFailedServices(List<FailedServiceInfo> failedServices) {
        this.failedServices = failedServices == null ? new ArrayList<>() : new ArrayList<>(failedServices);
    }

    /**
     * Adds a failed service to the result.
     */
    public void addFailedService(FailedServiceInfo failedService) {
        if (this.failedServices == null) {
            this.failedServices = new ArrayList<>();
        }
        this.failedServices.add(failedService);
        this.isComplete = false;
    }

    /**
     * Returns the list of warnings.
     */
    public List<String> getWarnings() {
        return warnings == null ? null : new ArrayList<>(warnings);
    }

    /**
     * Sets the list of warnings.
     */
    public void setWarnings(List<String> warnings) {
        this.warnings = warnings == null ? new ArrayList<>() : new ArrayList<>(warnings);
    }

    /**
     * Adds a warning message to the result.
     */
    public void addWarning(String warning) {
        if (this.warnings == null) {
            this.warnings = new ArrayList<>();
        }
        this.warnings.add(warning);
    }

    /**
     * Returns whether there are any failed services.
     */
    public boolean hasFailedServices() {
        return failedServices != null && !failedServices.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildResult that = (BuildResult) o;
        return isComplete == that.isComplete &&
                Objects.equals(appTemplate, that.appTemplate) &&
                Objects.equals(functionPool, that.functionPool) &&
                Objects.equals(failedServices, that.failedServices) &&
                Objects.equals(warnings, that.warnings);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appTemplate, functionPool, isComplete, failedServices, warnings);
    }

    @Override
    public String toString() {
        return "BuildResult{" +
                "appTemplate=" + (appTemplate != null ? appTemplate.getName() : "null") +
                ", functionPoolSize=" + (functionPool != null ? functionPool.size() : 0) +
                ", isComplete=" + isComplete +
                ", failedServicesCount=" + (failedServices != null ? failedServices.size() : 0) +
                ", warningsCount=" + (warnings != null ? warnings.size() : 0) +
                '}';
    }

    /**
     * Information about a service that failed to scan.
     */
    public static class FailedServiceInfo {
        @JsonProperty("serviceId")
        private String serviceId;

        @JsonProperty("gitCommitHash")
        private String gitCommitHash;

        @JsonProperty("errorType")
        private String errorType;

        @JsonProperty("errorMessage")
        private String errorMessage;

        public FailedServiceInfo() {
        }

        public FailedServiceInfo(String serviceId, String gitCommitHash, String errorType, String errorMessage) {
            this.serviceId = serviceId;
            this.gitCommitHash = gitCommitHash;
            this.errorType = errorType;
            this.errorMessage = errorMessage;
        }

        public String getServiceId() {
            return serviceId;
        }

        public void setServiceId(String serviceId) {
            this.serviceId = serviceId;
        }

        public String getGitCommitHash() {
            return gitCommitHash;
        }

        public void setGitCommitHash(String gitCommitHash) {
            this.gitCommitHash = gitCommitHash;
        }

        public String getErrorType() {
            return errorType;
        }

        public void setErrorType(String errorType) {
            this.errorType = errorType;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FailedServiceInfo that = (FailedServiceInfo) o;
            return Objects.equals(serviceId, that.serviceId) &&
                    Objects.equals(gitCommitHash, that.gitCommitHash) &&
                    Objects.equals(errorType, that.errorType) &&
                    Objects.equals(errorMessage, that.errorMessage);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceId, gitCommitHash, errorType, errorMessage);
        }

        @Override
        public String toString() {
            return "FailedServiceInfo{" +
                    "serviceId='" + serviceId + '\'' +
                    ", gitCommitHash='" + gitCommitHash + '\'' +
                    ", errorType='" + errorType + '\'' +
                    ", errorMessage='" + errorMessage + '\'' +
                    '}';
        }
    }
}

package gov.nystax.nimbus.codesnap.services.builder.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Request object for building an app snapshot.
 * Contains the app name and list of services with their git commit hashes.
 * 
 * <p>Example JSON:</p>
 * <pre>
 * {
 *   "appName": "nims-wt-wage-process-app",
 *   "services": [
 *     { "serviceId": "WTWAGESUMJ", "gitCommitHash": "abc123" },
 *     { "serviceId": "WT4545J", "gitCommitHash": "def456" },
 *     { "serviceId": "WT0004J", "gitCommitHash": "ghi789" }
 *   ]
 * }
 * </pre>
 */
public class BuildRequest {

    @JsonProperty("appName")
    private String appName;

    @JsonProperty("appType")
    private AppType appType = AppType.NIMBUS;

    @JsonProperty("services")
    private List<ServiceCommitInfo> services;

    public BuildRequest() {
        this.services = new ArrayList<>();
    }

    public BuildRequest(String appName, List<ServiceCommitInfo> services) {
        this.appName = appName;
        this.services = services == null ? new ArrayList<>() : new ArrayList<>(services);
    }

    public String getAppName() {
        return appName;
    }

    public AppType getAppType() {
        return appType;
    }

    public void setAppType(AppType appType) {
        this.appType = appType == null ? AppType.NIMBUS : appType;
    }

    public boolean isNimbaApp() {
        return appType == AppType.NIMBA;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public List<ServiceCommitInfo> getServices() {
        return services == null ? null : new ArrayList<>(services);
    }

    public void setServices(List<ServiceCommitInfo> services) {
        this.services = services == null ? new ArrayList<>() : new ArrayList<>(services);
    }

    public void addService(String serviceId, String gitCommitHash) {
        if (this.services == null) {
            this.services = new ArrayList<>();
        }
        this.services.add(new ServiceCommitInfo(serviceId, gitCommitHash));
    }

    /**
     * Validates that the request has all required fields.
     *
     * @throws IllegalArgumentException if validation fails
     */
    public void validate() {
        if (appName == null || appName.isBlank()) {
            throw new IllegalArgumentException("App name is required");
        }
        if (services == null || services.isEmpty()) {
            throw new IllegalArgumentException("At least one service is required");
        }
        for (ServiceCommitInfo service : services) {
            service.validate();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BuildRequest that = (BuildRequest) o;
        return Objects.equals(appName, that.appName) &&
                Objects.equals(appType, that.appType) &&
                Objects.equals(services, that.services);
    }

    @Override
    public int hashCode() {
        return Objects.hash(appName, appType, services);
    }

    @Override
    public String toString() {
        return "BuildRequest{" +
                "appName='" + appName + '\'' +
                ", appType='" + appType + '\'' +
                ", services=" + services +
                '}';
    }

    /**
     * The type of application being built.
     */
    public enum AppType {
        NIMBUS("nimbus"),
        NIMBA("nimba");

        private final String value;

        AppType(String value) {
            this.value = value;
        }

        @JsonValue
        public String getValue() {
            return value;
        }

        @JsonCreator
        public static AppType fromValue(String value) {
            if (value == null) {
                return NIMBUS;
            }
            for (AppType type : values()) {
                if (type.value.equalsIgnoreCase(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Unknown app type: " + value);
        }
    }

    /**
     * Represents a service with its git commit hash.
     */
    public static class ServiceCommitInfo {

        @JsonProperty("serviceId")
        private String serviceId;

        @JsonProperty("gitCommitHash")
        private String gitCommitHash;

        public ServiceCommitInfo() {
        }

        public ServiceCommitInfo(String serviceId, String gitCommitHash) {
            this.serviceId = serviceId;
            this.gitCommitHash = gitCommitHash;
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

        public void validate() {
            if (serviceId == null || serviceId.isBlank()) {
                throw new IllegalArgumentException("Service ID is required");
            }
            if (gitCommitHash == null || gitCommitHash.isBlank()) {
                throw new IllegalArgumentException("Git commit hash is required for service: " + serviceId);
            }
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ServiceCommitInfo that = (ServiceCommitInfo) o;
            return Objects.equals(serviceId, that.serviceId) &&
                    Objects.equals(gitCommitHash, that.gitCommitHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(serviceId, gitCommitHash);
        }

        @Override
        public String toString() {
            return serviceId + "@" + gitCommitHash;
        }
    }
}

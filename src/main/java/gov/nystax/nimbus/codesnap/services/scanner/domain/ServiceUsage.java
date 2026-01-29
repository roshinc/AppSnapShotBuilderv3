package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Represents the usage information for a service dependency.
 * Tracks how a service dependency (dev.roshin.service:<serviceid>)
 * is used through invocations in the dev.roshin.function.<serviceid>.* package.
 */
public class ServiceUsage {

    @JsonProperty("serviceId")
    private String serviceId;

    @JsonProperty("servicePackage")
    private String servicePackage;

    @JsonProperty("dependency")
    private String dependency;

    @JsonProperty("invocations")
    private List<ServiceInvocation> invocations;

    public ServiceUsage() {
    }

    public ServiceUsage(String serviceId, String servicePackage, String dependency) {
        this.serviceId = serviceId;
        this.servicePackage = servicePackage;
        this.dependency = dependency;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getServicePackage() {
        return servicePackage;
    }

    public void setServicePackage(String servicePackage) {
        this.servicePackage = servicePackage;
    }

    public String getDependency() {
        return dependency;
    }

    public void setDependency(String dependency) {
        this.dependency = dependency;
    }

    public List<ServiceInvocation> getInvocations() {
        return invocations == null ? null : new ArrayList<>(invocations);
    }

    public void setInvocations(List<ServiceInvocation> invocations) {
        this.invocations = invocations == null ? null : new ArrayList<>(invocations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceUsage that = (ServiceUsage) o;
        return Objects.equals(serviceId, that.serviceId) &&
                Objects.equals(servicePackage, that.servicePackage) &&
                Objects.equals(dependency, that.dependency) &&
                Objects.equals(invocations, that.invocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceId, servicePackage, dependency, invocations);
    }

    @Override
    public String toString() {
        return "ServiceUsage{" +
                "serviceId='" + serviceId + '\'' +
                ", servicePackage='" + servicePackage + '\'' +
                ", dependency='" + dependency + '\'' +
                ", invocations=" + invocations +
                '}';
    }
}

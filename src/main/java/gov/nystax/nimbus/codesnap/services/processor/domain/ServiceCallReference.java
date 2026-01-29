package gov.nystax.nimbus.codesnap.services.processor.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

/**
 * Represents a call to another service's method.
 * These calls are resolved transitively at build time.
 */
public class ServiceCallReference {

    /**
     * The service artifact ID being called (e.g., "WT0019J").
     */
    @JsonProperty("serviceId")
    private String serviceId;

    /**
     * The interface method signature being called.
     * Example: "gov.nystax.services.wt0019j.IPreviewEmployeeDBService.retrievePrevEmployee(...)"
     */
    @JsonProperty("interfaceMethod")
    private String interfaceMethod;

    public ServiceCallReference() {
    }

    public ServiceCallReference(String serviceId, String interfaceMethod) {
        this.serviceId = serviceId;
        this.interfaceMethod = interfaceMethod;
    }

    /**
     * Creates a copy of this reference.
     */
    public ServiceCallReference copy() {
        return new ServiceCallReference(this.serviceId, this.interfaceMethod);
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getInterfaceMethod() {
        return interfaceMethod;
    }

    public void setInterfaceMethod(String interfaceMethod) {
        this.interfaceMethod = interfaceMethod;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceCallReference that = (ServiceCallReference) o;
        return Objects.equals(serviceId, that.serviceId) &&
                Objects.equals(interfaceMethod, that.interfaceMethod);
    }

    @Override
    public int hashCode() {
        return Objects.hash(serviceId, interfaceMethod);
    }

    @Override
    public String toString() {
        return "ServiceCallReference{" +
                "serviceId='" + serviceId + '\'' +
                ", interfaceMethod='" + interfaceMethod + '\'' +
                '}';
    }
}

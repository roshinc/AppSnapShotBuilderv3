package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents usage of a service within the project.
 */
public class ServiceUsage {
    
    private final String serviceId;
    private final String groupId;
    private final String dependencyArtifactId;
    private List<ServiceInvocation> invocations;
    
    @JsonCreator
    public ServiceUsage(
            @JsonProperty("serviceId") String serviceId,
            @JsonProperty("groupId") String groupId,
            @JsonProperty("dependencyArtifactId") String dependencyArtifactId) {
        this.serviceId = serviceId;
        this.groupId = groupId;
        this.dependencyArtifactId = dependencyArtifactId;
        this.invocations = new ArrayList<>();
    }
    
    public String getServiceId() {
        return serviceId;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public String getDependencyArtifactId() {
        return dependencyArtifactId;
    }
    
    public List<ServiceInvocation> getInvocations() {
        return invocations;
    }
    
    public void setInvocations(List<ServiceInvocation> invocations) {
        this.invocations = invocations != null ? invocations : new ArrayList<>();
    }
}

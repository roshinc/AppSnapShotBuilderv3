package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents the raw scan data from the project scanner.
 * This is the input to the ScanDataProcessor.
 */
public class ProjectInfo {
    
    @JsonProperty("artifactId")
    private String artifactId;
    
    @JsonProperty("groupId")
    private String groupId;
    
    @JsonProperty("version")
    private String version;
    
    @JsonProperty("isUIService")
    private boolean isUIService;
    
    @JsonProperty("functionMappings")
    private Map<String, String> functionMappings;
    
    @JsonProperty("uiServiceMethodMappings")
    private Map<String, String> uiServiceMethodMappings;
    
    @JsonProperty("methodImplementationMappings")
    private Map<String, String> methodImplementationMappings;
    
    @JsonProperty("functionUsages")
    private List<FunctionUsage> functionUsages;
    
    @JsonProperty("serviceUsages")
    private List<ServiceUsage> serviceUsages;
    
    @JsonProperty("eventPublisherInvocations")
    private List<EventPublisherInvocation> eventPublisherInvocations;
    
    @JsonProperty("serviceDependencies")
    private List<String> serviceDependencies;
    
    public ProjectInfo() {
        this.functionMappings = new HashMap<>();
        this.uiServiceMethodMappings = new HashMap<>();
        this.methodImplementationMappings = new HashMap<>();
        this.functionUsages = new ArrayList<>();
        this.serviceUsages = new ArrayList<>();
        this.eventPublisherInvocations = new ArrayList<>();
        this.serviceDependencies = new ArrayList<>();
    }
    
    public String getArtifactId() {
        return artifactId;
    }
    
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getVersion() {
        return version;
    }
    
    public void setVersion(String version) {
        this.version = version;
    }
    
    public boolean isUIService() {
        return isUIService;
    }
    
    public void setUIService(boolean UIService) {
        isUIService = UIService;
    }
    
    public Map<String, String> getFunctionMappings() {
        return functionMappings;
    }
    
    public void setFunctionMappings(Map<String, String> functionMappings) {
        this.functionMappings = functionMappings != null ? functionMappings : new HashMap<>();
    }
    
    public Map<String, String> getUIServiceMethodMappings() {
        return uiServiceMethodMappings;
    }
    
    public void setUIServiceMethodMappings(Map<String, String> uiServiceMethodMappings) {
        this.uiServiceMethodMappings = uiServiceMethodMappings != null ? uiServiceMethodMappings : new HashMap<>();
    }
    
    public Map<String, String> getMethodImplementationMappings() {
        return methodImplementationMappings;
    }
    
    public void setMethodImplementationMappings(Map<String, String> methodImplementationMappings) {
        this.methodImplementationMappings = methodImplementationMappings != null ? methodImplementationMappings : new HashMap<>();
    }
    
    public List<FunctionUsage> getFunctionUsages() {
        return functionUsages;
    }
    
    public void setFunctionUsages(List<FunctionUsage> functionUsages) {
        this.functionUsages = functionUsages != null ? functionUsages : new ArrayList<>();
    }
    
    public List<ServiceUsage> getServiceUsages() {
        return serviceUsages;
    }
    
    public void setServiceUsages(List<ServiceUsage> serviceUsages) {
        this.serviceUsages = serviceUsages != null ? serviceUsages : new ArrayList<>();
    }
    
    public List<EventPublisherInvocation> getEventPublisherInvocations() {
        return eventPublisherInvocations;
    }
    
    public void setEventPublisherInvocations(List<EventPublisherInvocation> eventPublisherInvocations) {
        this.eventPublisherInvocations = eventPublisherInvocations != null ? eventPublisherInvocations : new ArrayList<>();
    }
    
    public List<String> getServiceDependencies() {
        return serviceDependencies;
    }
    
    public void setServiceDependencies(List<String> serviceDependencies) {
        this.serviceDependencies = serviceDependencies != null ? serviceDependencies : new ArrayList<>();
    }
}

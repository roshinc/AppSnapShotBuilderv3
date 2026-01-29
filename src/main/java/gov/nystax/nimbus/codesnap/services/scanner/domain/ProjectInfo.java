package gov.nystax.nimbus.codesnap.services.scanner.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.*;

/**
 * Represents information about a Maven project.
 */
public class ProjectInfo {


    @JsonProperty("projectPath")
    private String projectPath;
    @JsonProperty("groupId")
    private String groupId;
    @JsonProperty("artifactId")
    private String artifactId;
    @JsonProperty("version")
    private String version;
    @JsonProperty("packaging")
    private String packaging;
    @JsonProperty("sourceFiles")
    private List<String> sourceFiles;
    @JsonProperty("dependencies")
    private List<String> dependencies;
    @JsonProperty("classes")
    private int classCount;
    @JsonProperty("methods")
    private int methodCount;
    @JsonProperty("serviceInterface")
    private String serviceInterface;
    @JsonProperty("serviceImplementation")
    private String serviceImplementation;
    @JsonProperty("isUIService")
    private boolean isUIService;
    @JsonProperty("functionMappings")
    private Map<String, String> functionMappings;
    @JsonProperty("uiServiceMethodMappings")
    private Map<String, String> uiServiceMethodMappings;
    @JsonProperty("methodImplementationMapping")
    private Map<String, String> methodImplementationMappings;
    @JsonProperty("serviceDependencies")
    private List<String> serviceDependencies;

    @JsonProperty("functionDependencies")
    private List<String> functionDependencies;

    @JsonProperty("functionUsages")
    private List<FunctionUsage> functionUsages;

    @JsonProperty("serviceUsages")
    private List<ServiceUsage> serviceUsages;

    @JsonProperty("eventPublisherInvocations")
    private List<EventPublisherInvocation> eventPublisherInvocations;

    public ProjectInfo() {
    }

    public ProjectInfo(String projectPath, String groupId, String artifactId, String version) {
        this.projectPath = projectPath;
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public String getProjectPath() {
        return projectPath;
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getPackaging() {
        return packaging;
    }

    public void setPackaging(String packaging) {
        this.packaging = packaging;
    }

    public List<String> getSourceFiles() {
        return sourceFiles == null ? null : new ArrayList<>(sourceFiles);
    }

    public void setSourceFiles(List<String> sourceFiles) {
        this.sourceFiles = sourceFiles == null ? null : new ArrayList<>(sourceFiles);
    }

    public List<String> getDependencies() {
        return dependencies == null ? null : new ArrayList<>(dependencies);
    }

    public void setDependencies(List<String> dependencies) {
        this.dependencies = dependencies == null ? null : new ArrayList<>(dependencies);
    }

    public int getClassCount() {
        return classCount;
    }

    public void setClassCount(int classCount) {
        this.classCount = classCount;
    }

    public int getMethodCount() {
        return methodCount;
    }

    public void setMethodCount(int methodCount) {
        this.methodCount = methodCount;
    }

    public String getServiceInterface() {
        return serviceInterface;
    }

    public void setServiceInterface(String serviceInterface) {
        this.serviceInterface = serviceInterface;
    }

    public String getServiceImplementation() {
        return serviceImplementation;
    }

    public void setServiceImplementation(String serviceImplementation) {
        this.serviceImplementation = serviceImplementation;
    }

    public boolean isUIService() {
        return isUIService;
    }

    public void setUIService(boolean isUIService) {
        this.isUIService = isUIService;
    }

    public Map<String, String> getFunctionMappings() {
        return functionMappings == null ? null : new HashMap<>(functionMappings);
    }

    public void setFunctionMappings(Map<String, String> functionMappings) {
        this.functionMappings = functionMappings == null ? null : new HashMap<>(functionMappings);
    }

    public Map<String, String> getUIServiceMethodMappings() {
        return uiServiceMethodMappings == null ? null : new HashMap<>(uiServiceMethodMappings);
    }

    public void setUIServiceMethodMappings(Map<String, String> uiServiceMethodMappings) {
        this.uiServiceMethodMappings = uiServiceMethodMappings == null ? null : new HashMap<>(uiServiceMethodMappings);
    }

    public Map<String, String> getMethodImplementationMappings() {
        return methodImplementationMappings == null ? null : new HashMap<>(methodImplementationMappings);
    }

    public void setMethodImplementationMappings(Map<String, String> methodImplementationMappings) {
        this.methodImplementationMappings = methodImplementationMappings == null ? null : new HashMap<>(methodImplementationMappings);
    }

    public List<String> getServiceDependencies() {
        return serviceDependencies == null ? null : new ArrayList<>(serviceDependencies);
    }

    public void setServiceDependencies(List<String> serviceDependencies) {
        this.serviceDependencies = serviceDependencies == null ? null : new ArrayList<>(serviceDependencies);
    }

    public List<String> getFunctionDependencies() {
        return functionDependencies == null ? null : new ArrayList<>(functionDependencies);
    }

    public void setFunctionDependencies(List<String> functionDependencies) {
        this.functionDependencies = functionDependencies == null ? null : new ArrayList<>(functionDependencies);
    }

    public List<FunctionUsage> getFunctionUsages() {
        return functionUsages == null ? null : new ArrayList<>(functionUsages);
    }

    public void setFunctionUsages(List<FunctionUsage> functionUsages) {
        this.functionUsages = functionUsages == null ? null : new ArrayList<>(functionUsages);
    }

    public List<ServiceUsage> getServiceUsages() {
        return serviceUsages == null ? null : new ArrayList<>(serviceUsages);
    }

    public void setServiceUsages(List<ServiceUsage> serviceUsages) {
        this.serviceUsages = serviceUsages == null ? null : new ArrayList<>(serviceUsages);
    }

    public List<EventPublisherInvocation> getEventPublisherInvocations() {
        return eventPublisherInvocations == null ? null : new ArrayList<>(eventPublisherInvocations);
    }

    public void setEventPublisherInvocations(List<EventPublisherInvocation> eventPublisherInvocations) {
        this.eventPublisherInvocations = eventPublisherInvocations == null ? null : new ArrayList<>(eventPublisherInvocations);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProjectInfo that = (ProjectInfo) o;
        return classCount == that.classCount &&
                methodCount == that.methodCount &&
                isUIService == that.isUIService &&
                Objects.equals(projectPath, that.projectPath) &&
                Objects.equals(groupId, that.groupId) &&
                Objects.equals(artifactId, that.artifactId) &&
                Objects.equals(version, that.version) &&
                Objects.equals(packaging, that.packaging) &&
                Objects.equals(sourceFiles, that.sourceFiles) &&
                Objects.equals(dependencies, that.dependencies) &&
                Objects.equals(serviceInterface, that.serviceInterface) &&
                Objects.equals(serviceImplementation, that.serviceImplementation) &&
                Objects.equals(functionMappings, that.functionMappings) &&
                Objects.equals(uiServiceMethodMappings, that.uiServiceMethodMappings) &&
                Objects.equals(methodImplementationMappings, that.methodImplementationMappings) &&
                Objects.equals(serviceDependencies, that.serviceDependencies) &&
                Objects.equals(functionDependencies, that.functionDependencies) &&
                Objects.equals(functionUsages, that.functionUsages) &&
                Objects.equals(serviceUsages, that.serviceUsages) &&
                Objects.equals(eventPublisherInvocations, that.eventPublisherInvocations);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectPath, groupId, artifactId, version, packaging,
                sourceFiles, dependencies, classCount, methodCount,
                serviceInterface, serviceImplementation, isUIService, functionMappings, uiServiceMethodMappings,
                methodImplementationMappings,
                serviceDependencies, functionDependencies, functionUsages, serviceUsages,
                eventPublisherInvocations);
    }

    @Override
    public String toString() {
        return "ProjectInfo{" +
                "projectPath='" + projectPath + '\'' +
                ", groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                ", packaging='" + packaging + '\'' +
                ", classCount=" + classCount +
                ", methodCount=" + methodCount +
                ", serviceInterface='" + serviceInterface + '\'' +
                ", serviceImplementation='" + serviceImplementation + '\'' +
                ", isUIService=" + isUIService +
                ", functionMappings=" + functionMappings +
                ", uiServiceMethodMappings=" + uiServiceMethodMappings +
                ", methodImplementationMapping=" + methodImplementationMappings +
                ", serviceDependencies=" + serviceDependencies +
                ", functionDependencies=" + functionDependencies +
                ", functionUsages=" + functionUsages +
                ", serviceUsages=" + serviceUsages +
                ", eventPublisherUsages=" + eventPublisherInvocations +
                '}';
    }
}

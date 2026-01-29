package gov.nystax.nimbus.codesnap.services.processor.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Root domain class for pre-processed scan data stored in SERVICE_SCAN.SCAN_DATA_JSON.
 * This structure is optimized for build-time tree construction.
 */
public class ScanData {

    /**
     * Maps function short name to interface method signature.
     * Only populated for regular services (isUIService = false).
     * Example: "insertEmployee" -> "gov.nystax.services.wt0004j.IWTEmployeeDBService.insertEmployee(...)"
     */
    @JsonProperty("functionMappings")
    private Map<String, String> functionMappings;

    /**
     * Maps UI method short name to interface method signature.
     * Only populated for UI services (isUIService = true).
     * Example: "retrieveData" -> "gov.nystax.services.wt4545j.IWT4545JWageReportingProcess.retrieveData(...)"
     */
    @JsonProperty("uiServiceMethodMappings")
    private Map<String, String> uiServiceMethodMappings;

    /**
     * Maps interface method signature to implementation method signature.
     * Used for transitive resolution when other services call into this service.
     */
    @JsonProperty("methodImplementationMapping")
    private Map<String, String> methodImplementationMapping;

    /**
     * Pre-processed ownership data. For each exposed function or UI method,
     * lists all direct dependencies (functions, async functions, topics, service calls).
     * Key is the function or UI method short name.
     */
    @JsonProperty("entryPointChildren")
    private Map<String, EntryPointDependencies> entryPointChildren;

    /**
     * Inverted index for transitive resolution. For each public implementation method,
     * lists what it calls. Used when other services call into this service.
     * Key is the implementation method signature.
     */
    @JsonProperty("publicMethodDependencies")
    private Map<String, EntryPointDependencies> publicMethodDependencies;

    public ScanData() {
        this.functionMappings = new HashMap<>();
        this.uiServiceMethodMappings = new HashMap<>();
        this.methodImplementationMapping = new HashMap<>();
        this.entryPointChildren = new HashMap<>();
        this.publicMethodDependencies = new HashMap<>();
    }

    public Map<String, String> getFunctionMappings() {
        return functionMappings == null ? null : new HashMap<>(functionMappings);
    }

    public void setFunctionMappings(Map<String, String> functionMappings) {
        this.functionMappings = functionMappings == null ? null : new HashMap<>(functionMappings);
    }

    public Map<String, String> getUiServiceMethodMappings() {
        return uiServiceMethodMappings == null ? null : new HashMap<>(uiServiceMethodMappings);
    }

    public void setUiServiceMethodMappings(Map<String, String> uiServiceMethodMappings) {
        this.uiServiceMethodMappings = uiServiceMethodMappings == null ? null : new HashMap<>(uiServiceMethodMappings);
    }

    public Map<String, String> getMethodImplementationMapping() {
        return methodImplementationMapping == null ? null : new HashMap<>(methodImplementationMapping);
    }

    public void setMethodImplementationMapping(Map<String, String> methodImplementationMapping) {
        this.methodImplementationMapping = methodImplementationMapping == null ? null : new HashMap<>(methodImplementationMapping);
    }

    public Map<String, EntryPointDependencies> getEntryPointChildren() {
        return entryPointChildren == null ? null : new HashMap<>(entryPointChildren);
    }

    public void setEntryPointChildren(Map<String, EntryPointDependencies> entryPointChildren) {
        this.entryPointChildren = entryPointChildren == null ? null : new HashMap<>(entryPointChildren);
    }

    public Map<String, EntryPointDependencies> getPublicMethodDependencies() {
        return publicMethodDependencies == null ? null : new HashMap<>(publicMethodDependencies);
    }

    public void setPublicMethodDependencies(Map<String, EntryPointDependencies> publicMethodDependencies) {
        this.publicMethodDependencies = publicMethodDependencies == null ? null : new HashMap<>(publicMethodDependencies);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScanData scanData = (ScanData) o;
        return Objects.equals(functionMappings, scanData.functionMappings) &&
                Objects.equals(uiServiceMethodMappings, scanData.uiServiceMethodMappings) &&
                Objects.equals(methodImplementationMapping, scanData.methodImplementationMapping) &&
                Objects.equals(entryPointChildren, scanData.entryPointChildren) &&
                Objects.equals(publicMethodDependencies, scanData.publicMethodDependencies);
    }

    @Override
    public int hashCode() {
        return Objects.hash(functionMappings, uiServiceMethodMappings, methodImplementationMapping,
                entryPointChildren, publicMethodDependencies);
    }

    @Override
    public String toString() {
        return "ScanData{" +
                "functionMappings=" + functionMappings +
                ", uiServiceMethodMappings=" + uiServiceMethodMappings +
                ", methodImplementationMapping=" + methodImplementationMapping +
                ", entryPointChildren=" + entryPointChildren +
                ", publicMethodDependencies=" + publicMethodDependencies +
                '}';
    }
}

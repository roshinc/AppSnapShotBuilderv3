package gov.nystax.nimbus.codesnap.services.processor;

import gov.nystax.nimbus.codesnap.services.processor.domain.EntryPointDependencies;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.scanner.domain.EventPublisherInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionUsage;
import gov.nystax.nimbus.codesnap.services.scanner.domain.MethodReference;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ServiceInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ServiceUsage;
import gov.nystax.nimbus.codesnap.services.scanner.domain.TopicResolution;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Processor that transforms raw scanner output (ProjectInfo) into pre-processed
 * scan data (ScanData) optimized for build-time tree construction.
 * 
 * <p>This processor performs the following transformations:</p>
 * <ul>
 *   <li>Builds reverse lookup maps for method resolution</li>
 *   <li>Determines ownership of each usage by analyzing call chains</li>
 *   <li>Pre-computes entryPointChildren for direct dependency lookup</li>
 *   <li>Builds publicMethodDependencies for transitive resolution</li>
 * </ul>
 */
public class ScanDataProcessor {

    private static final Logger LOGGER = Logger.getLogger(ScanDataProcessor.class.getName());

    private static final String INVOCATION_TYPE_EXECUTE = "execute";
    private static final String INVOCATION_TYPE_EXECUTE_ASYNC = "executeAsync";

    /**
     * Placeholder topic name used when the topic cannot be resolved at scan time.
     * This applies to both UNKNOWN_VARIABLE and UNKNOWN_COMPLEX topic resolutions.
     */
    public static final String UNKNOWN_TOPIC_PLACEHOLDER = "<unknown-topic>";

    /**
     * Processes a ProjectInfo scan result into pre-processed ScanData.
     *
     * @param projectInfo the raw scanner output
     * @return pre-processed scan data ready for database storage
     * @throws IllegalArgumentException if projectInfo is null
     */
    public ScanData process(ProjectInfo projectInfo) {
        if (projectInfo == null) {
            throw new IllegalArgumentException("ProjectInfo cannot be null");
        }

        LOGGER.log(Level.INFO, "Processing scan data for service: {0}", projectInfo.getArtifactId());

        ScanData scanData = new ScanData();

        // Copy basic mappings
        copyBasicMappings(projectInfo, scanData);

        // Build reverse lookup maps
        Map<String, String> implToInterface = buildImplToInterfaceMap(projectInfo);
        Map<String, String> interfaceToEntryPoint = buildInterfaceToEntryPointMap(projectInfo);

        // Initialize entry point children for all exposed entry points
        initializeEntryPointChildren(scanData);

        // Process all usages
        processFunctionUsages(projectInfo, scanData, implToInterface, interfaceToEntryPoint);
        processServiceUsages(projectInfo, scanData, implToInterface, interfaceToEntryPoint);
        processEventPublisherInvocations(projectInfo, scanData, implToInterface, interfaceToEntryPoint);

        LOGGER.log(Level.INFO, "Completed processing scan data for service: {0}. " +
                        "Entry points: {1}, Public methods with deps: {2}",
                new Object[]{
                        projectInfo.getArtifactId(),
                        scanData.getEntryPointChildren().size(),
                        scanData.getPublicMethodDependencies().size()
                });

        return scanData;
    }

    /**
     * Copies basic mappings from ProjectInfo to ScanData.
     */
    private void copyBasicMappings(ProjectInfo projectInfo, ScanData scanData) {
        Map<String, String> functionMappings = projectInfo.getFunctionMappings();
        if (functionMappings != null) {
            scanData.setFunctionMappings(functionMappings);
        }

        Map<String, String> uiServiceMethodMappings = projectInfo.getUIServiceMethodMappings();
        if (uiServiceMethodMappings != null) {
            scanData.setUiServiceMethodMappings(uiServiceMethodMappings);
        }

        Map<String, String> methodImplementationMappings = projectInfo.getMethodImplementationMappings();
        if (methodImplementationMappings != null) {
            scanData.setMethodImplementationMapping(methodImplementationMappings);
        }
    }

    /**
     * Builds a reverse lookup map: implementation method -> interface method.
     */
    private Map<String, String> buildImplToInterfaceMap(ProjectInfo projectInfo) {
        Map<String, String> implToInterface = new HashMap<>();

        Map<String, String> methodImplMapping = projectInfo.getMethodImplementationMappings();
        if (methodImplMapping != null) {
            for (Map.Entry<String, String> entry : methodImplMapping.entrySet()) {
                String interfaceMethod = entry.getKey();
                String implMethod = entry.getValue();
                implToInterface.put(implMethod, interfaceMethod);
            }
        }

        return implToInterface;
    }

    /**
     * Builds a lookup map: interface method -> entry point name.
     * Combines both functionMappings and uiServiceMethodMappings.
     */
    private Map<String, String> buildInterfaceToEntryPointMap(ProjectInfo projectInfo) {
        Map<String, String> interfaceToEntryPoint = new HashMap<>();

        // Add function mappings (for regular services)
        Map<String, String> functionMappings = projectInfo.getFunctionMappings();
        if (functionMappings != null) {
            for (Map.Entry<String, String> entry : functionMappings.entrySet()) {
                String entryPointName = entry.getKey();
                String interfaceMethod = entry.getValue();
                interfaceToEntryPoint.put(interfaceMethod, entryPointName);
            }
        }

        // Add UI service method mappings (for UI services)
        Map<String, String> uiServiceMethodMappings = projectInfo.getUIServiceMethodMappings();
        if (uiServiceMethodMappings != null) {
            for (Map.Entry<String, String> entry : uiServiceMethodMappings.entrySet()) {
                String entryPointName = entry.getKey();
                String interfaceMethod = entry.getValue();
                interfaceToEntryPoint.put(interfaceMethod, entryPointName);
            }
        }

        return interfaceToEntryPoint;
    }

    /**
     * Initializes empty EntryPointDependencies for all exposed entry points.
     */
    private void initializeEntryPointChildren(ScanData scanData) {
        Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();

        // Initialize for all functions
        Map<String, String> functionMappings = scanData.getFunctionMappings();
        if (functionMappings != null) {
            for (String funcName : functionMappings.keySet()) {
                entryPointChildren.put(funcName, new EntryPointDependencies());
            }
        }

        // Initialize for all UI service methods
        Map<String, String> uiServiceMethodMappings = scanData.getUiServiceMethodMappings();
        if (uiServiceMethodMappings != null) {
            for (String methodName : uiServiceMethodMappings.keySet()) {
                entryPointChildren.put(methodName, new EntryPointDependencies());
            }
        }

        scanData.setEntryPointChildren(entryPointChildren);
    }

    /**
     * Processes function usages and updates both entryPointChildren and publicMethodDependencies.
     */
    private void processFunctionUsages(ProjectInfo projectInfo,
                                        ScanData scanData,
                                        Map<String, String> implToInterface,
                                        Map<String, String> interfaceToEntryPoint) {
        List<FunctionUsage> functionUsages = projectInfo.getFunctionUsages();
        if (functionUsages == null) {
            return;
        }

        Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();
        Map<String, EntryPointDependencies> publicMethodDeps = scanData.getPublicMethodDependencies();
        if (publicMethodDeps == null) {
            publicMethodDeps = new HashMap<>();
        }

        for (FunctionUsage usage : functionUsages) {
            String functionId = usage.getFunctionId();
            List<FunctionInvocation> invocations = usage.getInvocations();

            if (invocations == null) {
                continue;
            }

            for (FunctionInvocation invocation : invocations) {
                boolean isAsync = INVOCATION_TYPE_EXECUTE_ASYNC.equals(invocation.getInvocationType());
                List<MethodReference> callChain = invocation.getCallChain();

                if (callChain == null || callChain.isEmpty()) {
                    LOGGER.log(Level.WARNING, "Function usage {0} has empty call chain at {1}",
                            new Object[]{functionId, invocation.getInvocationSite()});
                    continue;
                }

                // Find owners and add to entryPointChildren
                Set<String> owners = findOwners(callChain, implToInterface, interfaceToEntryPoint);
                for (String owner : owners) {
                    EntryPointDependencies deps = entryPointChildren.get(owner);
                    if (deps != null) {
                        if (isAsync) {
                            deps.addAsyncFunction(functionId);
                        } else {
                            deps.addFunction(functionId);
                        }
                    }
                }

                // Add to publicMethodDependencies for all PUBLIC methods in call chain
                for (MethodReference methodRef : callChain) {
                    if (isPublicMethod(methodRef)) {
                        String implMethod = methodRef.getMethodName();
                        EntryPointDependencies methodDeps = publicMethodDeps.computeIfAbsent(
                                implMethod, k -> new EntryPointDependencies());
                        if (isAsync) {
                            methodDeps.addAsyncFunction(functionId);
                        } else {
                            methodDeps.addFunction(functionId);
                        }
                    }
                }
            }
        }

        scanData.setEntryPointChildren(entryPointChildren);
        scanData.setPublicMethodDependencies(publicMethodDeps);
    }

    /**
     * Processes service usages and updates both entryPointChildren and publicMethodDependencies.
     */
    private void processServiceUsages(ProjectInfo projectInfo,
                                       ScanData scanData,
                                       Map<String, String> implToInterface,
                                       Map<String, String> interfaceToEntryPoint) {
        List<ServiceUsage> serviceUsages = projectInfo.getServiceUsages();
        if (serviceUsages == null) {
            return;
        }

        Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();
        Map<String, EntryPointDependencies> publicMethodDeps = scanData.getPublicMethodDependencies();
        if (publicMethodDeps == null) {
            publicMethodDeps = new HashMap<>();
        }

        for (ServiceUsage usage : serviceUsages) {
            String targetServiceId = usage.getServiceId();
            List<ServiceInvocation> invocations = usage.getInvocations();

            if (invocations == null) {
                continue;
            }

            for (ServiceInvocation invocation : invocations) {
                String invokedMethod = invocation.getInvokedMethod();
                List<MethodReference> callChain = invocation.getCallChain();

                if (callChain == null || callChain.isEmpty()) {
                    LOGGER.log(Level.WARNING, "Service usage {0}.{1} has empty call chain at {2}",
                            new Object[]{targetServiceId, invokedMethod, invocation.getInvocationSite()});
                    continue;
                }

                // Find owners and add to entryPointChildren
                Set<String> owners = findOwners(callChain, implToInterface, interfaceToEntryPoint);
                for (String owner : owners) {
                    EntryPointDependencies deps = entryPointChildren.get(owner);
                    if (deps != null) {
                        deps.addServiceCall(targetServiceId, invokedMethod);
                    }
                }

                // Add to publicMethodDependencies for all PUBLIC methods in call chain
                for (MethodReference methodRef : callChain) {
                    if (isPublicMethod(methodRef)) {
                        String implMethod = methodRef.getMethodName();
                        EntryPointDependencies methodDeps = publicMethodDeps.computeIfAbsent(
                                implMethod, k -> new EntryPointDependencies());
                        methodDeps.addServiceCall(targetServiceId, invokedMethod);
                    }
                }
            }
        }

        scanData.setEntryPointChildren(entryPointChildren);
        scanData.setPublicMethodDependencies(publicMethodDeps);
    }

    /**
     * Processes event publisher invocations and updates both entryPointChildren and publicMethodDependencies.
     */
    private void processEventPublisherInvocations(ProjectInfo projectInfo,
                                                   ScanData scanData,
                                                   Map<String, String> implToInterface,
                                                   Map<String, String> interfaceToEntryPoint) {
        List<EventPublisherInvocation> invocations = projectInfo.getEventPublisherInvocations();
        if (invocations == null) {
            return;
        }

        Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();
        Map<String, EntryPointDependencies> publicMethodDeps = scanData.getPublicMethodDependencies();
        if (publicMethodDeps == null) {
            publicMethodDeps = new HashMap<>();
        }

        for (EventPublisherInvocation invocation : invocations) {
            // Determine the topic name based on resolution status
            String topic;
            if (invocation.getTopicResolution() == TopicResolution.RESOLVED) {
                topic = invocation.getTopic();
            } else {
                // For UNKNOWN_VARIABLE and UNKNOWN_COMPLEX, use a placeholder topic name
                // The invocation is still significant for owner detection and should appear in the tree
                topic = UNKNOWN_TOPIC_PLACEHOLDER;
                LOGGER.log(Level.FINE, "Using placeholder for unresolved topic at {0}: resolution={1}",
                        new Object[]{invocation.getInvocationSite(), invocation.getTopicResolution()});
            }

            List<MethodReference> callChain = invocation.getCallChain();

            if (callChain == null || callChain.isEmpty()) {
                LOGGER.log(Level.WARNING, "Event publisher invocation for topic {0} has empty call chain at {1}",
                        new Object[]{topic, invocation.getInvocationSite()});
                continue;
            }

            // Find owners and add to entryPointChildren
            Set<String> owners = findOwners(callChain, implToInterface, interfaceToEntryPoint);
            for (String owner : owners) {
                EntryPointDependencies deps = entryPointChildren.get(owner);
                if (deps != null) {
                    deps.addTopic(topic);
                }
            }

            // Add to publicMethodDependencies for all PUBLIC methods in call chain
            for (MethodReference methodRef : callChain) {
                if (isPublicMethod(methodRef)) {
                    String implMethod = methodRef.getMethodName();
                    EntryPointDependencies methodDeps = publicMethodDeps.computeIfAbsent(
                            implMethod, k -> new EntryPointDependencies());
                    methodDeps.addTopic(topic);
                }
            }
        }

        scanData.setEntryPointChildren(entryPointChildren);
        scanData.setPublicMethodDependencies(publicMethodDeps);
    }

    /**
     * Finds all entry point owners for a given call chain.
     * An owner is an exposed function or UI method that the call chain traces back to.
     *
     * @param callChain the call chain from the invocation
     * @param implToInterface map from implementation method to interface method
     * @param interfaceToEntryPoint map from interface method to entry point name
     * @return set of owner entry point names
     */
    private Set<String> findOwners(List<MethodReference> callChain,
                                    Map<String, String> implToInterface,
                                    Map<String, String> interfaceToEntryPoint) {
        Set<String> owners = new HashSet<>();

        for (MethodReference methodRef : callChain) {
            String implMethod = methodRef.getMethodName();

            // Step 1: impl method -> interface method
            String interfaceMethod = implToInterface.get(implMethod);
            if (interfaceMethod == null) {
                continue;
            }

            // Step 2: interface method -> entry point name
            String entryPoint = interfaceToEntryPoint.get(interfaceMethod);
            if (entryPoint != null) {
                owners.add(entryPoint);
            }
        }

        return owners;
    }

    /**
     * Checks if a method reference is a public method.
     */
    private boolean isPublicMethod(MethodReference methodRef) {
        return methodRef != null &&
                methodRef.getAccessModifier() == MethodReference.MethodAccessModifier.PUBLIC;
    }
}

package gov.nystax.nimbus.codesnap.services.builder;

import gov.nystax.nimbus.codesnap.services.builder.domain.ChildReference;
import gov.nystax.nimbus.codesnap.services.builder.domain.FunctionPoolEntry;
import gov.nystax.nimbus.codesnap.services.processor.domain.EntryPointDependencies;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceCallReference;
import gov.nystax.nimbus.codesnap.services.processor.ServiceScanService.ScanDataWithMetadata;

import java.sql.Connection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles transitive resolution of service calls.
 * When a function calls another service's method, this resolver follows the chain
 * to find the actual leaf dependencies (functions, async functions, topics).
 * 
 * <p>Resolution flow:</p>
 * <ol>
 *   <li>Function A calls ServiceB.methodR()</li>
 *   <li>Look up ServiceB's publicMethodDependencies for methodR</li>
 *   <li>Add ServiceB's direct function/async/topic dependencies to Function A</li>
 *   <li>If ServiceB.methodR calls ServiceC, recursively resolve</li>
 * </ol>
 */
public class TransitiveResolver {

    private static final Logger LOGGER = Logger.getLogger(TransitiveResolver.class.getName());

    private final Map<String, ScanDataWithMetadata> scansByServiceId;
    private final QueueNameResolver queueNameResolver;

    // Maps: serviceId -> (interfaceMethod -> dependencies)
    // Built from publicMethodDependencies during initialization
    private final Map<String, Map<String, EntryPointDependencies>> transitiveResolutionMap;

    public TransitiveResolver(Map<String, ScanDataWithMetadata> scansByServiceId,
                               QueueNameResolver queueNameResolver) {
        this.scansByServiceId = scansByServiceId;
        this.queueNameResolver = queueNameResolver;
        this.transitiveResolutionMap = buildTransitiveResolutionMap();
    }

    /**
     * Builds the transitive resolution map from all loaded scans.
     * Maps: serviceId -> (interfaceMethod -> dependencies)
     */
    private Map<String, Map<String, EntryPointDependencies>> buildTransitiveResolutionMap() {
        Map<String, Map<String, EntryPointDependencies>> result = new java.util.HashMap<>();

        for (Map.Entry<String, ScanDataWithMetadata> entry : scansByServiceId.entrySet()) {
            String serviceId = entry.getKey();
            ScanData scanData = entry.getValue().scanData();

            Map<String, String> methodImplMapping = scanData.getMethodImplementationMapping();
            Map<String, EntryPointDependencies> publicMethodDeps = scanData.getPublicMethodDependencies();

            if (methodImplMapping == null || publicMethodDeps == null) {
                continue;
            }

            Map<String, EntryPointDependencies> serviceMethodDeps = new java.util.HashMap<>();

            // Convert from impl method key to interface method key
            for (Map.Entry<String, String> mapping : methodImplMapping.entrySet()) {
                String interfaceMethod = mapping.getKey();
                String implMethod = mapping.getValue();

                EntryPointDependencies deps = publicMethodDeps.get(implMethod);
                if (deps != null && !deps.isEmpty()) {
                    serviceMethodDeps.put(interfaceMethod, deps);
                }
            }

            if (!serviceMethodDeps.isEmpty()) {
                result.put(serviceId, serviceMethodDeps);
            }
        }

        LOGGER.log(Level.INFO, "Built transitive resolution map for {0} services", result.size());
        return result;
    }

    /**
     * Resolves a service call transitively and adds all leaf dependencies to the function pool entry.
     *
     * @param connection the database connection (for queue name resolution)
     * @param serviceCall the service call to resolve
     * @param targetEntry the function pool entry to add dependencies to
     */
    public void resolveServiceCall(Connection connection,
                                    ServiceCallReference serviceCall,
                                    FunctionPoolEntry targetEntry) {
        resolveServiceCallRecursive(connection, serviceCall.getServiceId(), 
                serviceCall.getInterfaceMethod(), targetEntry, new HashSet<>());
    }

    /**
     * Resolves multiple service calls and adds all leaf dependencies to the function pool entry.
     *
     * @param connection the database connection
     * @param serviceCalls list of service calls to resolve
     * @param targetEntry the function pool entry to add dependencies to
     */
    public void resolveServiceCalls(Connection connection,
                                     List<ServiceCallReference> serviceCalls,
                                     FunctionPoolEntry targetEntry) {
        if (serviceCalls == null || serviceCalls.isEmpty()) {
            return;
        }

        for (ServiceCallReference serviceCall : serviceCalls) {
            resolveServiceCall(connection, serviceCall, targetEntry);
        }
    }

    /**
     * Recursive helper for transitive resolution with cycle detection.
     */
    private void resolveServiceCallRecursive(Connection connection,
                                              String serviceId,
                                              String interfaceMethod,
                                              FunctionPoolEntry targetEntry,
                                              Set<String> visited) {
        // Cycle detection
        String visitKey = serviceId + "::" + interfaceMethod;
        if (visited.contains(visitKey)) {
            LOGGER.log(Level.WARNING, "Cycle detected in transitive resolution: {0}", visitKey);
            return;
        }
        visited.add(visitKey);

        // Get the method dependencies for this service
        Map<String, EntryPointDependencies> serviceMethods = transitiveResolutionMap.get(serviceId);
        if (serviceMethods == null) {
            LOGGER.log(Level.FINE, "No transitive resolution data for service: {0}", serviceId);
            return;
        }

        EntryPointDependencies deps = serviceMethods.get(interfaceMethod);
        if (deps == null) {
            LOGGER.log(Level.FINE, "No dependencies found for {0}.{1}", 
                    new Object[]{serviceId, interfaceMethod});
            return;
        }

        // Add sync function dependencies
        Set<String> functions = deps.getFunctions();
        if (functions != null) {
            for (String funcName : functions) {
                if (!targetEntry.containsSyncRef(funcName)) {
                    targetEntry.addSyncRef(funcName);
                }
            }
        }

        // Add async function dependencies
        Set<String> asyncFunctions = deps.getAsyncFunctions();
        if (asyncFunctions != null) {
            for (String funcName : asyncFunctions) {
                if (!targetEntry.containsAsyncRef(funcName)) {
                    String queueName = queueNameResolver.resolveForFunction(connection, funcName);
                    targetEntry.addAsyncRef(funcName, queueName);
                }
            }
        }

        // Add topic dependencies
        Set<String> topics = deps.getTopics();
        if (topics != null) {
            for (String topicName : topics) {
                if (!targetEntry.containsTopicRef(topicName)) {
                    String queueName = queueNameResolver.resolveForTopic(connection, topicName);
                    targetEntry.addTopicRef(topicName, queueName);
                }
            }
        }

        // Recursively resolve nested service calls
        List<ServiceCallReference> serviceCalls = deps.getServiceCalls();
        if (serviceCalls != null) {
            for (ServiceCallReference nestedCall : serviceCalls) {
                resolveServiceCallRecursive(connection, nestedCall.getServiceId(),
                        nestedCall.getInterfaceMethod(), targetEntry, visited);
            }
        }
    }

    /**
     * Checks if a service has any transitive resolution data.
     */
    public boolean hasResolutionData(String serviceId) {
        return transitiveResolutionMap.containsKey(serviceId);
    }

    /**
     * Gets the number of services with resolution data.
     */
    public int getResolutionDataCount() {
        return transitiveResolutionMap.size();
    }
}

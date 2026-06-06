package gov.nystax.nimbus.codesnap.services.builder;

import gov.nystax.nimbus.codesnap.services.builder.domain.AppTemplateNode;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildRequest;
import gov.nystax.nimbus.codesnap.services.builder.domain.ChildReference;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildRequest.ServiceCommitInfo;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildResult;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildResult.FailedServiceInfo;
import gov.nystax.nimbus.codesnap.services.builder.domain.FunctionPoolEntry;
import gov.nystax.nimbus.codesnap.services.processor.ServiceScanService;
import gov.nystax.nimbus.codesnap.services.processor.ServiceScanService.ScanDataWithMetadata;
import gov.nystax.nimbus.codesnap.services.processor.dao.ServiceScanDAO.ServiceCommitPair;
import gov.nystax.nimbus.codesnap.services.processor.domain.EntryPointDependencies;
import gov.nystax.nimbus.codesnap.services.processor.domain.FailedServiceScanRecord;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceCallReference;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Main builder class for creating AppTemplate and FunctionPool from stored scan data.
 * 
 * <p>Build process:</p>
 * <ol>
 *   <li>Load all relevant scans from the database</li>
 *   <li>Topologically sort services by dependencies</li>
 *   <li>Build the transitive resolution map</li>
 *   <li>For each service (in dependency order):
 *     <ul>
 *       <li>Add functions to FunctionPool with direct dependencies</li>
 *       <li>Resolve service calls transitively</li>
 *       <li>Add UI services to AppTemplate (if applicable)</li>
 *     </ul>
 *   </li>
 *   <li>Build the final AppTemplate tree with function refs</li>
 * </ol>
 */
public class AppSnapshotBuilder {

    private static final Logger LOGGER = LoggerFactory.getLogger(AppSnapshotBuilder.class);

    private final ServiceScanService scanService;
    private final QueueNameResolver queueNameResolver;

    public AppSnapshotBuilder() {
        this.scanService = new ServiceScanService();
        this.queueNameResolver = new QueueNameResolver();
    }

    public AppSnapshotBuilder(ServiceScanService scanService, QueueNameResolver queueNameResolver) {
        this.scanService = scanService;
        this.queueNameResolver = queueNameResolver;
    }

    /**
     * Builds the AppTemplate and FunctionPool for the given request.
     *
     * <p>If any services have failed scans, the build will still proceed with available
     * scans but the result will be marked as incomplete with information about the
     * failed services.</p>
     *
     * @param connection the database connection
     * @param request the build request containing app name and service commits
     * @return the build result containing AppTemplate and FunctionPool
     * @throws SQLException if a database error occurs
     * @throws BuildException if the build fails
     */
    public BuildResult build(Connection connection, BuildRequest request) throws SQLException {
        request.validate();

        LOGGER.info("Starting build for app: {} with {} services",
                request.getAppName(), request.getServices().size());

        // Clear queue name cache for fresh build
        queueNameResolver.clearCache();

        // Step 1: Convert to service commit pairs
        List<ServiceCommitPair> serviceCommitPairs = convertToServiceCommitPairs(request.getServices());

        // Step 1a: Check for failed scans
        List<FailedServiceScanRecord> failedScans = scanService.findFailedScans(connection, serviceCommitPairs);
        Set<String> failedServiceIds = new HashSet<>();
        List<FailedServiceInfo> failedServiceInfoList = new ArrayList<>();

        if (!failedScans.isEmpty()) {
            LOGGER.warn("Found {} failed scans among requested services", failedScans.size());
            for (FailedServiceScanRecord failure : failedScans) {
                failedServiceIds.add(failure.getServiceId());
                failedServiceInfoList.add(new FailedServiceInfo(
                        failure.getServiceId(),
                        failure.getGitCommitHash(),
                        failure.getErrorType(),
                        failure.getErrorMessage()
                ));
            }
        }

        // Step 1b: Filter out failed services from the request
        List<ServiceCommitPair> validServiceCommitPairs = new ArrayList<>();
        for (ServiceCommitPair pair : serviceCommitPairs) {
            if (!failedServiceIds.contains(pair.serviceId())) {
                validServiceCommitPairs.add(pair);
            }
        }

        // Step 2: Load available scans (excluding failed ones)
        Map<String, ScanDataWithMetadata> scansByServiceId;
        if (validServiceCommitPairs.isEmpty()) {
            LOGGER.warn("All services have failed scans, cannot build");
            scansByServiceId = new HashMap<>();
        } else {
            scansByServiceId = scanService.loadScansForBuild(connection, validServiceCommitPairs);
        }

        // Step 3: Topologically sort services
        List<String> sortedServiceIds = scanService.topologicalSort(scansByServiceId);
        LOGGER.info("Services sorted by dependencies: {}", sortedServiceIds);

        // Step 5: Build the result
        BuildResult result = new BuildResult();

        // Step 4: Create transitive resolver (needs result for CTG pool entry creation)
        TransitiveResolver transitiveResolver = new TransitiveResolver(scansByServiceId, queueNameResolver, result);

        // Add failed services information to the result
        for (FailedServiceInfo failedInfo : failedServiceInfoList) {
            result.addFailedService(failedInfo);
            result.addWarning("Service " + failedInfo.getServiceId() + "@" +
                    failedInfo.getGitCommitHash() + " has a failed scan: " +
                    failedInfo.getErrorMessage());
        }

        // Create the app template root
        AppTemplateNode appRoot = AppTemplateNode.app(request.getAppName());

        // Track which functions we've seen (to avoid duplicates in app template)
        Set<String> addedFunctions = new HashSet<>();

        // Process each service in dependency order
        String appName = request.getAppName();
        boolean isNimba = request.isNimbaApp();
        for (String serviceId : sortedServiceIds) {
            ScanDataWithMetadata scanMetadata = scansByServiceId.get(serviceId);
            ScanData scanData = scanMetadata.scanData();

            if (isNimba) {
                // Nimba apps: discard the implicit function, promote its children to app root
                processNimbaService(serviceId, scanData, appRoot, result, transitiveResolver);
            } else if (scanMetadata.isUiService()) {
                // Process UI service
                processUiService(serviceId, scanData, appRoot, result, transitiveResolver);
            } else {
                // Process regular service
                processRegularService(serviceId, scanData, appRoot, result,
                        transitiveResolver, addedFunctions, appName);
            }
        }

        result.setAppTemplate(appRoot);

        if (result.isComplete()) {
            LOGGER.info("Build completed for app: {}. Functions: {}, UI Services: {}",
                    request.getAppName(),
                    result.getFunctionPool().size(),
                    countUiServices(appRoot));
        } else {
            LOGGER.warn("Build completed with warnings for app: {}. " +
                            "Functions: {}, UI Services: {}, Failed Services: {}",
                    request.getAppName(),
                    result.getFunctionPool().size(),
                    countUiServices(appRoot),
                    result.getFailedServices().size());
        }

        return result;
    }

    /**
     * Processes a regular service: adds functions to the pool and app template.
     */
    private void processRegularService(String serviceId,
                                        ScanData scanData,
                                        AppTemplateNode appRoot,
                                        BuildResult result,
                                        TransitiveResolver transitiveResolver,
                                        Set<String> addedFunctions,
                                        String appName) {
        Map<String, String> functionMappings = scanData.getFunctionMappings();
        if (functionMappings == null || functionMappings.isEmpty()) {
            LOGGER.debug("Service {} has no function mappings (dependency-only service)", serviceId);
            return;
        }

        Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();

        for (String functionName : functionMappings.keySet()) {
            // Add to function pool with app name
            FunctionPoolEntry poolEntry = result.getOrCreateFunction(functionName, appName);
            if (poolEntry.getQueueName() == null || poolEntry.getQueueName().isBlank()) {
                String queueName = queueNameResolver.resolveForFunction(functionName);
                poolEntry.setQueueName(queueName);
            }

            // Get direct dependencies
            EntryPointDependencies deps = entryPointChildren != null ? 
                    entryPointChildren.get(functionName) : null;

            if (deps != null) {
                addDependenciesToPoolEntry(deps, poolEntry, transitiveResolver);
                createCtgPoolEntries(deps, result);
            }

            // Add function ref to app template (only once)
            String lowerFunctionName = functionName.toLowerCase(Locale.ROOT);
            if (!addedFunctions.contains(lowerFunctionName)) {
                appRoot.addFunctionRef(functionName);
                addedFunctions.add(lowerFunctionName);
            }
        }

        LOGGER.debug("Processed regular service {}: {} functions",
                serviceId, functionMappings.size());
    }

    /**
     * Processes a nimba service: discards the implicit function and promotes its
     * children directly under the app root. Nothing is added to the function pool.
     */
    private void processNimbaService(String serviceId,
                                      ScanData scanData,
                                      AppTemplateNode appRoot,
                                      BuildResult result,
                                      TransitiveResolver transitiveResolver) {
        Map<String, String> functionMappings = scanData.getFunctionMappings();
        if (functionMappings == null || functionMappings.isEmpty()) {
            LOGGER.debug("Nimba service {} has no function mappings", serviceId);
            return;
        }

        Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();

        // Nimba apps have exactly one implicit function — discard it but promote its children
        for (String implicitFunctionName : functionMappings.keySet()) {
            EntryPointDependencies deps = entryPointChildren != null ?
                    entryPointChildren.get(implicitFunctionName) : null;

            if (deps != null) {
                addDependenciesToMethodNode(deps, appRoot, transitiveResolver);
            }

            LOGGER.debug("Nimba service {}: discarded implicit function '{}', promoted its children to app root",
                    serviceId, implicitFunctionName);
        }
    }

    /**
     * Processes a UI service: adds UI service container and methods to app template.
     */
    private void processUiService(String serviceId,
                                   ScanData scanData,
                                   AppTemplateNode appRoot,
                                   BuildResult result,
                                   TransitiveResolver transitiveResolver) {
        Map<String, String> uiMethodMappings = scanData.getUiServiceMethodMappings();
        if (uiMethodMappings == null || uiMethodMappings.isEmpty()) {
            LOGGER.debug("UI Service {} has no UI method mappings", serviceId);
            return;
        }

        Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();

        // Create UI services container
        AppTemplateNode uiServicesNode = AppTemplateNode.uiServices(serviceId);

        for (String methodName : uiMethodMappings.keySet()) {
            // Create UI service method node
            AppTemplateNode methodNode = AppTemplateNode.uiServiceMethod(methodName);

            // Get dependencies for this method
            EntryPointDependencies deps = entryPointChildren != null ?
                    entryPointChildren.get(methodName) : null;

            if (deps != null) {
                // Create CTG pool entries for direct CTG deps
                createCtgPoolEntries(deps, result);
                // Add direct function refs to the method node
                addDependenciesToMethodNode(deps, methodNode, transitiveResolver);
            }

            uiServicesNode.addChild(methodNode);
        }

        appRoot.addChild(uiServicesNode);

        LOGGER.debug("Processed UI service {}: {} methods",
                serviceId, uiMethodMappings.size());
    }

    /**
     * Adds dependencies to a function pool entry, including transitive resolution of service calls.
     */
    private void addDependenciesToPoolEntry(EntryPointDependencies deps,
                                             FunctionPoolEntry poolEntry,
                                             TransitiveResolver transitiveResolver) {
        // Add sync function dependencies
        Set<String> functions = deps.getFunctions();
        if (functions != null) {
            for (String funcName : functions) {
                if (!poolEntry.containsSyncRef(funcName)) {
                    poolEntry.addSyncRef(funcName);
                }
            }
        }

        // Add async function dependencies
        Set<String> asyncFunctions = deps.getAsyncFunctions();
        if (asyncFunctions != null) {
            for (String funcName : asyncFunctions) {
                if (!poolEntry.containsAsyncRef(funcName)) {
                    poolEntry.addAsyncRef(funcName);
                }
            }
        }

        // Add scheduled async function dependencies
        Set<String> scheduledAsyncFunctions = deps.getScheduledAsyncFunctions();
        if (scheduledAsyncFunctions != null) {
            for (String funcName : scheduledAsyncFunctions) {
                if (!poolEntry.containsScheduledAsyncRef(funcName)) {
                    poolEntry.addScheduledAsyncRef(funcName);
                }
            }
        }

        // Add topic dependencies
        Set<String> topics = deps.getTopics();
        if (topics != null) {
            for (String topicName : topics) {
                if (!poolEntry.containsTopicRef(topicName)) {
                    String queueName = queueNameResolver.resolveForTopic(topicName);
                    poolEntry.addTopicRef(topicName, queueName);
                }
            }
        }

        // Add sync CTG component dependencies
        Set<String> ctgComponents = deps.getCtgComponents();
        if (ctgComponents != null) {
            for (String ctgId : ctgComponents) {
                String ctgKey = ChildReference.ctgKey(ctgId);
                if (!poolEntry.containsSyncRef(ctgKey)) {
                    poolEntry.addCtgRef(ctgId);
                }
            }
        }

        // Add async CTG component dependencies
        Set<String> asyncCtgComponents = deps.getAsyncCtgComponents();
        if (asyncCtgComponents != null) {
            for (String ctgId : asyncCtgComponents) {
                String ctgKey = ChildReference.ctgKey(ctgId);
                if (!poolEntry.containsAsyncRef(ctgKey)) {
                    poolEntry.addAsyncCtgRef(ctgId);
                }
            }
        }

        // Propagate legacy gateway HTTP client flag
        if (deps.isUsesLegacyGatewayHttpClient()) {
            poolEntry.setUsesLegacyGatewayHttpClient(true);
        }

        // Resolve service calls transitively
        List<ServiceCallReference> serviceCalls = deps.getServiceCalls();
        if (serviceCalls != null && !serviceCalls.isEmpty()) {
            transitiveResolver.resolveServiceCalls(serviceCalls, poolEntry);
        }
    }

    /**
     * Adds dependencies to a UI service method node as children in the app template tree.
     */
    private void addDependenciesToMethodNode(EntryPointDependencies deps,
                                              AppTemplateNode methodNode,
                                              TransitiveResolver transitiveResolver) {
        // Add sync function refs
        Set<String> functions = deps.getFunctions();
        if (functions != null) {
            for (String funcName : functions) {
                methodNode.addFunctionRef(funcName);
            }
        }

        // Add async function refs
        Set<String> asyncFunctions = deps.getAsyncFunctions();
        if (asyncFunctions != null) {
            for (String funcName : asyncFunctions) {
                String queueName = queueNameResolver.resolveForFunction(funcName);
                methodNode.addAsyncFunctionRef(funcName, queueName);
            }
        }

        // Add scheduled async function refs
        Set<String> scheduledAsyncFunctions = deps.getScheduledAsyncFunctions();
        if (scheduledAsyncFunctions != null) {
            for (String funcName : scheduledAsyncFunctions) {
                String queueName = queueNameResolver.resolveForFunction(funcName);
                methodNode.addScheduledAsyncFunctionRef(funcName, queueName);
            }
        }

        // Add topic refs
        Set<String> topics = deps.getTopics();
        if (topics != null) {
            for (String topicName : topics) {
                String queueName = queueNameResolver.resolveForTopic(topicName);
                methodNode.addTopicPublishRef(topicName, queueName);
            }
        }

        // Add sync CTG component refs
        Set<String> ctgComponents = deps.getCtgComponents();
        if (ctgComponents != null) {
            for (String ctgId : ctgComponents) {
                methodNode.addCtgRef(ctgId);
            }
        }

        // Add async CTG component refs
        Set<String> asyncCtgComponents = deps.getAsyncCtgComponents();
        if (asyncCtgComponents != null) {
            for (String ctgId : asyncCtgComponents) {
                String queueName = queueNameResolver.resolveForFunction(ctgId);
                methodNode.addAsyncCtgRef(ctgId, queueName);
            }
        }

        // Propagate legacy gateway HTTP client flag from direct dependencies
        if (deps.isUsesLegacyGatewayHttpClient()) {
            methodNode.setUsesLegacyGatewayHttpClient(true);
        }

        // Resolve service calls transitively and add to method node
        List<ServiceCallReference> serviceCalls = deps.getServiceCalls();
        if (serviceCalls != null && !serviceCalls.isEmpty()) {
            // Create a temporary pool entry to collect transitive dependencies
            FunctionPoolEntry transitiveCollector = new FunctionPoolEntry();
            transitiveResolver.resolveServiceCalls(serviceCalls, transitiveCollector);

            // Add collected dependencies to the method node.
            // Async child refs no longer carry queueName (it lives on their top-level pool entry),
            // so we resolve it from queueNameResolver for the AppTemplateNode which still needs it.
            for (var child : transitiveCollector.getChildren()) {
                if (child.isTopicRef()) {
                    methodNode.addTopicPublishRef(child.getTopicName(), child.getQueueName());
                } else if (child.isCtg()) {
                    // CTG ref - child.getRef() already has the ctg_ prefix from ChildReference factory;
                    // construct the AppTemplateNode directly to avoid double-prefixing
                    AppTemplateNode ctgNode = new AppTemplateNode();
                    ctgNode.setRef(child.getRef());
                    ctgNode.setCtg(true);
                    if (child.isAsyncRef()) {
                        ctgNode.setAsync(true);
                        String ctgOriginalId = child.getRef().substring(ChildReference.CTG_PREFIX.length());
                        ctgNode.setQueueName(queueNameResolver.resolveForFunction(ctgOriginalId));
                    }
                    methodNode.addChild(ctgNode);
                } else if (child.isScheduledRef()) {
                    String resolvedQueue = queueNameResolver.resolveForFunction(child.getRef());
                    methodNode.addScheduledAsyncFunctionRef(child.getRef(), resolvedQueue);
                } else if (child.isAsyncRef()) {
                    String resolvedQueue = queueNameResolver.resolveForFunction(child.getRef());
                    methodNode.addAsyncFunctionRef(child.getRef(), resolvedQueue);
                } else if (child.isSyncRef()) {
                    methodNode.addFunctionRef(child.getRef());
                }
            }

            // Propagate legacy gateway HTTP client flag from transitive dependencies
            if (transitiveCollector.isUsesLegacyGatewayHttpClient()) {
                methodNode.setUsesLegacyGatewayHttpClient(true);
            }
        }
    }

    /**
     * Creates top-level CTG pool entries in the build result for all CTG
     * components referenced by the given dependencies.
     */
    private void createCtgPoolEntries(EntryPointDependencies deps, BuildResult result) {
        Set<String> ctgComponents = deps.getCtgComponents();
        if (ctgComponents != null) {
            for (String ctgId : ctgComponents) {
                FunctionPoolEntry ctgEntry = result.getOrCreateCtgEntry(ctgId);
                if (ctgEntry.getQueueName() == null || ctgEntry.getQueueName().isBlank()) {
                    ctgEntry.setQueueName(queueNameResolver.resolveForFunction(ctgId));
                }
            }
        }
        Set<String> asyncCtgComponents = deps.getAsyncCtgComponents();
        if (asyncCtgComponents != null) {
            for (String ctgId : asyncCtgComponents) {
                FunctionPoolEntry ctgEntry = result.getOrCreateCtgEntry(ctgId);
                if (ctgEntry.getQueueName() == null || ctgEntry.getQueueName().isBlank()) {
                    ctgEntry.setQueueName(queueNameResolver.resolveForFunction(ctgId));
                }
            }
        }
    }

    /**
     * Converts ServiceCommitInfo list to ServiceCommitPair list for the scan service.
     */
    private List<ServiceCommitPair> convertToServiceCommitPairs(List<ServiceCommitInfo> services) {
        List<ServiceCommitPair> pairs = new ArrayList<>();
        for (ServiceCommitInfo info : services) {
            pairs.add(new ServiceCommitPair(info.getServiceId(), info.getGitCommitHash()));
        }
        return pairs;
    }

    /**
     * Counts the number of UI service nodes in the app template.
     */
    private int countUiServices(AppTemplateNode appRoot) {
        if (appRoot.getChildren() == null) {
            return 0;
        }
        return (int) appRoot.getChildren().stream()
                .filter(child -> AppTemplateNode.TYPE_UI_SERVICES.equals(child.getType()))
                .count();
    }

    /**
     * Exception thrown when the build process fails.
     */
    public static class BuildException extends RuntimeException {
        public BuildException(String message) {
            super(message);
        }

        public BuildException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

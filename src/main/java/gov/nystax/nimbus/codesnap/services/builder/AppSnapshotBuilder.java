package gov.nystax.nimbus.codesnap.services.builder;

import gov.nystax.nimbus.codesnap.services.builder.domain.AppTemplateNode;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildRequest;
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
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static final Logger LOGGER = Logger.getLogger(AppSnapshotBuilder.class.getName());

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

        LOGGER.log(Level.INFO, "Starting build for app: {0} with {1} services",
                new Object[]{request.getAppName(), request.getServices().size()});

        // Clear queue name cache for fresh build
        queueNameResolver.clearCache();

        // Step 1: Convert to service commit pairs
        List<ServiceCommitPair> serviceCommitPairs = convertToServiceCommitPairs(request.getServices());

        // Step 1a: Check for failed scans
        List<FailedServiceScanRecord> failedScans = scanService.findFailedScans(connection, serviceCommitPairs);
        Set<String> failedServiceIds = new HashSet<>();
        List<FailedServiceInfo> failedServiceInfoList = new ArrayList<>();

        if (!failedScans.isEmpty()) {
            LOGGER.log(Level.WARNING, "Found {0} failed scans among requested services", failedScans.size());
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
            LOGGER.log(Level.WARNING, "All services have failed scans, cannot build");
            scansByServiceId = new HashMap<>();
        } else {
            scansByServiceId = scanService.loadScansForBuild(connection, validServiceCommitPairs);
        }

        // Step 3: Topologically sort services
        List<String> sortedServiceIds = scanService.topologicalSort(scansByServiceId);
        LOGGER.log(Level.INFO, "Services sorted by dependencies: {0}", sortedServiceIds);

        // Step 4: Create transitive resolver
        TransitiveResolver transitiveResolver = new TransitiveResolver(scansByServiceId, queueNameResolver);

        // Step 5: Build the result
        BuildResult result = new BuildResult();

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
        for (String serviceId : sortedServiceIds) {
            ScanDataWithMetadata scanMetadata = scansByServiceId.get(serviceId);
            ScanData scanData = scanMetadata.scanData();

            if (scanMetadata.isUiService()) {
                // Process UI service
                processUiService(connection, serviceId, scanData, appRoot, transitiveResolver);
            } else {
                // Process regular service
                processRegularService(connection, serviceId, scanData, appRoot, result,
                        transitiveResolver, addedFunctions, appName);
            }
        }

        result.setAppTemplate(appRoot);

        if (result.isComplete()) {
            LOGGER.log(Level.INFO, "Build completed for app: {0}. Functions: {1}, UI Services: {2}",
                    new Object[]{
                            request.getAppName(),
                            result.getFunctionPool().size(),
                            countUiServices(appRoot)
                    });
        } else {
            LOGGER.log(Level.WARNING, "Build completed with warnings for app: {0}. " +
                            "Functions: {1}, UI Services: {2}, Failed Services: {3}",
                    new Object[]{
                            request.getAppName(),
                            result.getFunctionPool().size(),
                            countUiServices(appRoot),
                            result.getFailedServices().size()
                    });
        }

        return result;
    }

    /**
     * Processes a regular service: adds functions to the pool and app template.
     */
    private void processRegularService(Connection connection,
                                        String serviceId,
                                        ScanData scanData,
                                        AppTemplateNode appRoot,
                                        BuildResult result,
                                        TransitiveResolver transitiveResolver,
                                        Set<String> addedFunctions,
                                        String appName) {
        Map<String, String> functionMappings = scanData.getFunctionMappings();
        if (functionMappings == null || functionMappings.isEmpty()) {
            LOGGER.log(Level.FINE, "Service {0} has no function mappings (dependency-only service)", serviceId);
            return;
        }

        Map<String, EntryPointDependencies> entryPointChildren = scanData.getEntryPointChildren();

        for (String functionName : functionMappings.keySet()) {
            // Add to function pool with app name
            FunctionPoolEntry poolEntry = result.getOrCreateFunction(functionName, appName);
            if (poolEntry.getQueueName() == null || poolEntry.getQueueName().isBlank()) {
                String queueName = queueNameResolver.resolveForFunction(connection, functionName);
                poolEntry.setQueueName(queueName);
            }

            // Get direct dependencies
            EntryPointDependencies deps = entryPointChildren != null ? 
                    entryPointChildren.get(functionName) : null;

            if (deps != null) {
                addDependenciesToPoolEntry(connection, deps, poolEntry, transitiveResolver);
            }

            // Add function ref to app template (only once)
            String lowerFunctionName = functionName.toLowerCase(Locale.ROOT);
            if (!addedFunctions.contains(lowerFunctionName)) {
                appRoot.addFunctionRef(functionName);
                addedFunctions.add(lowerFunctionName);
            }
        }

        LOGGER.log(Level.FINE, "Processed regular service {0}: {1} functions",
                new Object[]{serviceId, functionMappings.size()});
    }

    /**
     * Processes a UI service: adds UI service container and methods to app template.
     */
    private void processUiService(Connection connection,
                                   String serviceId,
                                   ScanData scanData,
                                   AppTemplateNode appRoot,
                                   TransitiveResolver transitiveResolver) {
        Map<String, String> uiMethodMappings = scanData.getUiServiceMethodMappings();
        if (uiMethodMappings == null || uiMethodMappings.isEmpty()) {
            LOGGER.log(Level.FINE, "UI Service {0} has no UI method mappings", serviceId);
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
                // Add direct function refs to the method node
                addDependenciesToMethodNode(connection, deps, methodNode, transitiveResolver);
            }

            uiServicesNode.addChild(methodNode);
        }

        appRoot.addChild(uiServicesNode);

        LOGGER.log(Level.FINE, "Processed UI service {0}: {1} methods",
                new Object[]{serviceId, uiMethodMappings.size()});
    }

    /**
     * Adds dependencies to a function pool entry, including transitive resolution of service calls.
     */
    private void addDependenciesToPoolEntry(Connection connection,
                                             EntryPointDependencies deps,
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
                    String queueName = queueNameResolver.resolveForFunction(connection, funcName);
                    poolEntry.addAsyncRef(funcName, queueName);
                }
            }
        }

        // Add topic dependencies
        Set<String> topics = deps.getTopics();
        if (topics != null) {
            for (String topicName : topics) {
                if (!poolEntry.containsTopicRef(topicName)) {
                    String queueName = queueNameResolver.resolveForTopic(connection, topicName);
                    poolEntry.addTopicRef(topicName, queueName);
                }
            }
        }

        // Resolve service calls transitively
        List<ServiceCallReference> serviceCalls = deps.getServiceCalls();
        if (serviceCalls != null && !serviceCalls.isEmpty()) {
            transitiveResolver.resolveServiceCalls(connection, serviceCalls, poolEntry);
        }
    }

    /**
     * Adds dependencies to a UI service method node as children in the app template tree.
     */
    private void addDependenciesToMethodNode(Connection connection,
                                              EntryPointDependencies deps,
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
                String queueName = queueNameResolver.resolveForFunction(connection, funcName);
                methodNode.addAsyncFunctionRef(funcName, queueName);
            }
        }

        // Add topic refs
        Set<String> topics = deps.getTopics();
        if (topics != null) {
            for (String topicName : topics) {
                String queueName = queueNameResolver.resolveForTopic(connection, topicName);
                methodNode.addTopicPublishRef(topicName, queueName);
            }
        }

        // Resolve service calls transitively and add to method node
        List<ServiceCallReference> serviceCalls = deps.getServiceCalls();
        if (serviceCalls != null && !serviceCalls.isEmpty()) {
            // Create a temporary pool entry to collect transitive dependencies
            FunctionPoolEntry transitiveCollector = new FunctionPoolEntry();
            transitiveResolver.resolveServiceCalls(connection, serviceCalls, transitiveCollector);

            // Add collected dependencies to the method node
            for (var child : transitiveCollector.getChildren()) {
                if (child.isSyncRef()) {
                    methodNode.addFunctionRef(child.getRef());
                } else if (child.isAsyncRef()) {
                    methodNode.addAsyncFunctionRef(child.getRef(), child.getQueueName());
                } else if (child.isTopicRef()) {
                    methodNode.addTopicPublishRef(child.getTopicName(), child.getQueueName());
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

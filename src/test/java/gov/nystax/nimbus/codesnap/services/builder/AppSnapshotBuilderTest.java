package gov.nystax.nimbus.codesnap.services.builder;

import gov.nystax.nimbus.codesnap.services.builder.domain.AppTemplateNode;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildRequest;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildResult;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildResult.FailedServiceInfo;
import gov.nystax.nimbus.codesnap.services.builder.domain.ChildReference;
import gov.nystax.nimbus.codesnap.services.builder.domain.FunctionPoolEntry;
import gov.nystax.nimbus.codesnap.services.processor.ServiceScanService;
import gov.nystax.nimbus.codesnap.services.processor.ServiceScanService.ScanDataWithMetadata;
import gov.nystax.nimbus.codesnap.services.processor.dao.QueueMappingDAO;
import gov.nystax.nimbus.codesnap.services.processor.dao.ServiceScanDAO.ServiceCommitPair;
import gov.nystax.nimbus.codesnap.services.processor.domain.EntryPointDependencies;
import gov.nystax.nimbus.codesnap.services.processor.domain.FailedServiceScanRecord;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceCallReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AppSnapshotBuilder.
 * Uses mock implementations to test build logic without database.
 */
class AppSnapshotBuilderTest {

    private MockServiceScanService mockScanService;
    private MockQueueMappingDAO mockQueueMappingDAO;
    private AppSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        mockScanService = new MockServiceScanService();
        mockQueueMappingDAO = new MockQueueMappingDAO();
        QueueNameResolver queueNameResolver = new QueueNameResolver(mockQueueMappingDAO);
        
        builder = new AppSnapshotBuilder(mockScanService, queueNameResolver);
    }

    @Nested
    @DisplayName("Basic Build Tests")
    class BasicBuildTests {

        @Test
        @DisplayName("Should build app template with single regular service")
        void buildSingleRegularService() throws SQLException {
            // Setup
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("insertEmployee", "gov.service.IService.insertEmployee(...)");
            functionMappings.put("getWageCount", "gov.service.IService.getWageCount(...)");
            scanData.setFunctionMappings(functionMappings);

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("insertEmployee", new EntryPointDependencies());
            entryPointChildren.put("getWageCount", new EntryPointDependencies());
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("WT0004J", "abc123", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("WT0004J", "abc123");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            assertNotNull(result);
            assertNotNull(result.getAppTemplate());
            assertEquals("test-app", result.getAppTemplate().getName());
            assertEquals(AppTemplateNode.TYPE_APP, result.getAppTemplate().getType());

            // Should have 2 function refs in app template
            assertEquals(2, result.getAppTemplate().getChildren().size());

            // Should have 2 functions in pool
            assertEquals(2, result.getFunctionPool().size());
            assertTrue(result.getFunctionPool().containsKey("insertEmployee"));
            assertTrue(result.getFunctionPool().containsKey("getWageCount"));

            // Verify app property is set on function pool entries
            FunctionPoolEntry insertEmployeeEntry = result.getFunctionPool().get("insertEmployee");
            FunctionPoolEntry getWageCountEntry = result.getFunctionPool().get("getWageCount");
            assertEquals("test-app", insertEmployeeEntry.getApp());
            assertEquals("test-app", getWageCountEntry.getApp());
        }

        @Test
        @DisplayName("Should build app template with UI service")
        void buildUiService() throws SQLException {
            // Setup
            ScanData scanData = new ScanData();
            Map<String, String> uiMethodMappings = new HashMap<>();
            uiMethodMappings.put("retrieveData", "gov.service.IUIService.retrieveData(...)");
            uiMethodMappings.put("saveData", "gov.service.IUIService.saveData(...)");
            scanData.setUiServiceMethodMappings(uiMethodMappings);

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("retrieveData", new EntryPointDependencies());
            entryPointChildren.put("saveData", new EntryPointDependencies());
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("WT4545J", "def456", true, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-ui-app");
            request.addService("WT4545J", "def456");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            assertNotNull(result);
            AppTemplateNode appRoot = result.getAppTemplate();
            assertEquals("test-ui-app", appRoot.getName());

            // Should have 1 ui-services container
            assertEquals(1, appRoot.getChildren().size());
            AppTemplateNode uiServicesNode = appRoot.getChildren().get(0);
            assertEquals(AppTemplateNode.TYPE_UI_SERVICES, uiServicesNode.getType());
            assertEquals("WT4545J", uiServicesNode.getName());

            // Should have 2 ui-service-method children
            assertEquals(2, uiServicesNode.getChildren().size());
        }
    }

    @Nested
    @DisplayName("Dependency Resolution Tests")
    class DependencyResolutionTests {

        @Test
        @DisplayName("Should add sync function dependencies to pool entry")
        void syncFunctionDependencies() throws SQLException {
            // Setup
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("parentFunc", "gov.service.IService.parentFunc(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addFunction("childFunc1");
            deps.addFunction("childFunc2");

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("parentFunc", deps);
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            FunctionPoolEntry entry = result.getFunctionPool().get("parentFunc");
            assertNotNull(entry);
            assertEquals(2, entry.getChildren().size());
            assertTrue(entry.containsSyncRef("childFunc1"));
            assertTrue(entry.containsSyncRef("childFunc2"));
        }

        @Test
        @DisplayName("Should add async function dependencies with queue names")
        void asyncFunctionDependencies() throws SQLException {
            // Setup queue mapping
            mockQueueMappingDAO.addMapping("ASYNC.QUEUE", QueueMappingDAO.TARGET_TYPE_FUNCTION, "asyncFunc");

            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("parentFunc", "gov.service.IService.parentFunc(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addAsyncFunction("asyncFunc");

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("parentFunc", deps);
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            FunctionPoolEntry entry = result.getFunctionPool().get("parentFunc");
            assertNotNull(entry);
            assertTrue(entry.containsAsyncRef("asyncFunc"));

            ChildReference asyncRef = entry.getChildren().stream()
                    .filter(ChildReference::isAsyncRef)
                    .findFirst()
                    .orElse(null);
            assertNotNull(asyncRef);
            assertEquals("ASYNC.QUEUE", asyncRef.getQueueName());
        }

        @Test
        @DisplayName("Should add topic dependencies with queue names")
        void topicDependencies() throws SQLException {
            // Setup queue mapping
            mockQueueMappingDAO.addMapping("TOPIC.QUEUE", QueueMappingDAO.TARGET_TYPE_TOPIC, "PaymentPosting");

            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("publishFunc", "gov.service.IService.publishFunc(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addTopic("PaymentPosting");

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("publishFunc", deps);
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            FunctionPoolEntry entry = result.getFunctionPool().get("publishFunc");
            assertNotNull(entry);
            assertTrue(entry.containsTopicRef("PaymentPosting"));

            ChildReference topicRef = entry.getChildren().stream()
                    .filter(ChildReference::isTopicRef)
                    .findFirst()
                    .orElse(null);
            assertNotNull(topicRef);
            assertEquals("TOPIC.QUEUE", topicRef.getQueueName());
        }
    }

    @Nested
    @DisplayName("Transitive Resolution Tests")
    class TransitiveResolutionTests {

        @Test
        @DisplayName("Should resolve service calls transitively")
        void transitiveServiceCallResolution() throws SQLException {
            // Service A calls Service B's method
            ScanData scanDataA = new ScanData();
            Map<String, String> functionMappingsA = new HashMap<>();
            functionMappingsA.put("funcA", "gov.serviceA.IServiceA.funcA(...)");
            scanDataA.setFunctionMappings(functionMappingsA);

            EntryPointDependencies depsA = new EntryPointDependencies();
            depsA.addServiceCall("SERVICE_B", "gov.serviceB.IServiceB.funcB(...)");

            Map<String, EntryPointDependencies> entryPointChildrenA = new HashMap<>();
            entryPointChildrenA.put("funcA", depsA);
            scanDataA.setEntryPointChildren(entryPointChildrenA);

            // Service B has a function that calls an external function
            ScanData scanDataB = new ScanData();
            scanDataB.setFunctionMappings(new HashMap<>()); // No exposed functions

            Map<String, String> methodImplMappingB = new HashMap<>();
            methodImplMappingB.put("gov.serviceB.IServiceB.funcB(...)", "gov.serviceB.impl.ServiceBImpl.funcB(...)");
            scanDataB.setMethodImplementationMapping(methodImplMappingB);

            EntryPointDependencies publicDepsB = new EntryPointDependencies();
            publicDepsB.addFunction("externalFunc");

            Map<String, EntryPointDependencies> publicMethodDepsB = new HashMap<>();
            publicMethodDepsB.put("gov.serviceB.impl.ServiceBImpl.funcB(...)", publicDepsB);
            scanDataB.setPublicMethodDependencies(publicMethodDepsB);

            mockScanService.addScan("SERVICE_A", "commitA", false, "SERVICE_B", scanDataA);
            mockScanService.addScan("SERVICE_B", "commitB", false, null, scanDataB);

            BuildRequest request = new BuildRequest();
            request.setAppName("transitive-test-app");
            request.addService("SERVICE_A", "commitA");
            request.addService("SERVICE_B", "commitB");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify - funcA should now have externalFunc as a child (resolved transitively)
            FunctionPoolEntry entryA = result.getFunctionPool().get("funcA");
            assertNotNull(entryA);
            assertTrue(entryA.containsSyncRef("externalFunc"),
                    "funcA should have externalFunc as transitive dependency");
        }

        @Test
        @DisplayName("Should handle multi-level transitive chains")
        void multiLevelTransitiveChain() throws SQLException {
            // Service A -> Service B -> Service C -> externalFunc

            // Service A
            ScanData scanDataA = new ScanData();
            Map<String, String> functionMappingsA = new HashMap<>();
            functionMappingsA.put("funcA", "gov.a.IA.funcA(...)");
            scanDataA.setFunctionMappings(functionMappingsA);

            EntryPointDependencies depsA = new EntryPointDependencies();
            depsA.addServiceCall("SERVICE_B", "gov.b.IB.funcB(...)");
            scanDataA.setEntryPointChildren(Map.of("funcA", depsA));

            // Service B
            ScanData scanDataB = new ScanData();
            scanDataB.setFunctionMappings(new HashMap<>());
            scanDataB.setMethodImplementationMapping(Map.of(
                    "gov.b.IB.funcB(...)", "gov.b.impl.BImpl.funcB(...)"));

            EntryPointDependencies publicDepsB = new EntryPointDependencies();
            publicDepsB.addServiceCall("SERVICE_C", "gov.c.IC.funcC(...)");
            scanDataB.setPublicMethodDependencies(Map.of(
                    "gov.b.impl.BImpl.funcB(...)", publicDepsB));

            // Service C
            ScanData scanDataC = new ScanData();
            scanDataC.setFunctionMappings(new HashMap<>());
            scanDataC.setMethodImplementationMapping(Map.of(
                    "gov.c.IC.funcC(...)", "gov.c.impl.CImpl.funcC(...)"));

            EntryPointDependencies publicDepsC = new EntryPointDependencies();
            publicDepsC.addFunction("leafFunction");
            scanDataC.setPublicMethodDependencies(Map.of(
                    "gov.c.impl.CImpl.funcC(...)", publicDepsC));

            mockScanService.addScan("SERVICE_A", "a1", false, "SERVICE_B", scanDataA);
            mockScanService.addScan("SERVICE_B", "b1", false, "SERVICE_C", scanDataB);
            mockScanService.addScan("SERVICE_C", "c1", false, null, scanDataC);

            BuildRequest request = new BuildRequest();
            request.setAppName("multi-level-app");
            request.addService("SERVICE_A", "a1");
            request.addService("SERVICE_B", "b1");
            request.addService("SERVICE_C", "c1");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify - funcA should have leafFunction (resolved through B -> C)
            FunctionPoolEntry entryA = result.getFunctionPool().get("funcA");
            assertNotNull(entryA);
            assertTrue(entryA.containsSyncRef("leafFunction"),
                    "funcA should have leafFunction as multi-level transitive dependency");
        }
    }

    @Nested
    @DisplayName("UI Service Method Tests")
    class UiServiceMethodTests {

        @Test
        @DisplayName("Should add function refs to UI service methods")
        void uiServiceMethodWithFunctionRefs() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> uiMethodMappings = new HashMap<>();
            uiMethodMappings.put("retrieveData", "gov.ui.IUI.retrieveData(...)");
            scanData.setUiServiceMethodMappings(uiMethodMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addFunction("helperFunc1");
            deps.addFunction("helperFunc2");
            scanData.setEntryPointChildren(Map.of("retrieveData", deps));

            mockScanService.addScan("UISERVICE", "ui1", true, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("ui-test-app");
            request.addService("UISERVICE", "ui1");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            AppTemplateNode uiServices = result.getAppTemplate().getChildren().get(0);
            AppTemplateNode methodNode = uiServices.getChildren().get(0);
            
            assertEquals("retrieveData", methodNode.getName());
            assertEquals(2, methodNode.getChildren().size());
            
            // Verify refs in method node
            assertTrue(methodNode.getChildren().stream()
                    .anyMatch(c -> "helperFunc1".equals(c.getRef())));
            assertTrue(methodNode.getChildren().stream()
                    .anyMatch(c -> "helperFunc2".equals(c.getRef())));
        }
    }

    @Nested
    @DisplayName("Failed Scan Handling Tests")
    class FailedScanHandlingTests {

        @Test
        @DisplayName("Should mark build as complete when no failed scans")
        void noFailedScans() throws SQLException {
            // Setup
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("testFunc", "gov.service.IService.testFunc(...)");
            scanData.setFunctionMappings(functionMappings);
            scanData.setEntryPointChildren(Map.of("testFunc", new EntryPointDependencies()));

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            assertTrue(result.isComplete());
            assertFalse(result.hasFailedServices());
            assertTrue(result.getFailedServices().isEmpty());
            assertTrue(result.getWarnings().isEmpty());
        }

        @Test
        @DisplayName("Should mark build as incomplete when service has failed scan")
        void buildWithFailedScan() throws SQLException {
            // Setup - one good service, one failed service
            ScanData goodScanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("goodFunc", "gov.service.IService.goodFunc(...)");
            goodScanData.setFunctionMappings(functionMappings);
            goodScanData.setEntryPointChildren(Map.of("goodFunc", new EntryPointDependencies()));

            mockScanService.addScan("GOOD_SERVICE", "commit1", false, null, goodScanData);
            mockScanService.addFailedScan("FAILED_SERVICE", "commit2", "SCAN_ERROR", "Failed to scan service");

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("GOOD_SERVICE", "commit1");
            request.addService("FAILED_SERVICE", "commit2");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            assertFalse(result.isComplete());
            assertTrue(result.hasFailedServices());
            assertEquals(1, result.getFailedServices().size());

            FailedServiceInfo failedInfo = result.getFailedServices().get(0);
            assertEquals("FAILED_SERVICE", failedInfo.getServiceId());
            assertEquals("commit2", failedInfo.getGitCommitHash());
            assertEquals("SCAN_ERROR", failedInfo.getErrorType());
            assertEquals("Failed to scan service", failedInfo.getErrorMessage());

            // Warnings should be populated
            assertEquals(1, result.getWarnings().size());
            assertTrue(result.getWarnings().get(0).contains("FAILED_SERVICE"));

            // Good service should still be processed
            assertTrue(result.getFunctionPool().containsKey("goodFunc"));
        }

        @Test
        @DisplayName("Should handle all services failing")
        void allServicesFailed() throws SQLException {
            // Setup - all services failed
            mockScanService.addFailedScan("SERVICE1", "commit1", "CODE_VIOLATION", "Compilation error");
            mockScanService.addFailedScan("SERVICE2", "commit2", "PARSE_ERROR", "Invalid syntax");

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE1", "commit1");
            request.addService("SERVICE2", "commit2");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            assertFalse(result.isComplete());
            assertTrue(result.hasFailedServices());
            assertEquals(2, result.getFailedServices().size());

            // No functions should be in the pool
            assertTrue(result.getFunctionPool().isEmpty());

            // Warnings should be populated for both failures
            assertEquals(2, result.getWarnings().size());
        }

        @Test
        @DisplayName("Should include failed service info in result even when other services succeed")
        void mixedSuccessAndFailure() throws SQLException {
            // Setup
            ScanData scanData1 = new ScanData();
            scanData1.setFunctionMappings(Map.of("func1", "gov.service.IService.func1(...)"));
            scanData1.setEntryPointChildren(Map.of("func1", new EntryPointDependencies()));

            ScanData scanData2 = new ScanData();
            scanData2.setFunctionMappings(Map.of("func2", "gov.service.IService.func2(...)"));
            scanData2.setEntryPointChildren(Map.of("func2", new EntryPointDependencies()));

            mockScanService.addScan("SERVICE1", "commit1", false, null, scanData1);
            mockScanService.addFailedScan("SERVICE2", "commit2", "PROCESSING_ERROR", "Processing failed");
            mockScanService.addScan("SERVICE3", "commit3", false, null, scanData2);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE1", "commit1");
            request.addService("SERVICE2", "commit2");
            request.addService("SERVICE3", "commit3");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify
            assertFalse(result.isComplete());
            assertEquals(1, result.getFailedServices().size());
            assertEquals("SERVICE2", result.getFailedServices().get(0).getServiceId());

            // Both successful services should be processed
            assertEquals(2, result.getFunctionPool().size());
            assertTrue(result.getFunctionPool().containsKey("func1"));
            assertTrue(result.getFunctionPool().containsKey("func2"));
        }
    }

    // Mock implementations for testing

    private static class MockServiceScanService extends ServiceScanService {
        private final Map<String, ScanDataWithMetadata> scans = new HashMap<>();
        private final Map<String, String> dependencies = new HashMap<>();
        private final Map<String, FailedServiceScanRecord> failedScans = new HashMap<>();

        void addScan(String serviceId, String gitCommit, boolean isUiService,
                     String serviceDependencies, ScanData scanData) {
            ScanDataWithMetadata metadata = new ScanDataWithMetadata(
                    serviceId, gitCommit, isUiService, serviceDependencies, scanData);
            scans.put(serviceId, metadata);
            if (serviceDependencies != null) {
                dependencies.put(serviceId, serviceDependencies);
            }
        }

        void addFailedScan(String serviceId, String gitCommit, String errorType, String errorMessage) {
            FailedServiceScanRecord record = FailedServiceScanRecord.builder()
                    .failureId("test-failure-" + serviceId)
                    .serviceId(serviceId)
                    .gitCommitHash(gitCommit)
                    .failureTimestamp(new java.sql.Timestamp(System.currentTimeMillis()))
                    .errorType(errorType)
                    .errorMessage(errorMessage)
                    .build();
            failedScans.put(serviceId + "@" + gitCommit, record);
        }

        @Override
        public Map<String, ScanDataWithMetadata> loadScansForBuild(
                Connection connection, List<ServiceCommitPair> serviceCommits) {
            Map<String, ScanDataWithMetadata> result = new HashMap<>();
            for (ServiceCommitPair pair : serviceCommits) {
                ScanDataWithMetadata scan = scans.get(pair.serviceId());
                if (scan != null) {
                    result.put(pair.serviceId(), scan);
                }
            }
            return result;
        }

        @Override
        public List<FailedServiceScanRecord> findFailedScans(
                Connection connection, List<ServiceCommitPair> serviceCommits) {
            List<FailedServiceScanRecord> result = new ArrayList<>();
            for (ServiceCommitPair pair : serviceCommits) {
                String key = pair.serviceId() + "@" + pair.gitCommitHash();
                FailedServiceScanRecord failedRecord = failedScans.get(key);
                if (failedRecord != null) {
                    result.add(failedRecord);
                }
            }
            return result;
        }

        @Override
        public List<String> topologicalSort(Map<String, ScanDataWithMetadata> scansById) {
            // Simple implementation: services with no deps first
            List<String> noDeps = new ArrayList<>();
            List<String> withDeps = new ArrayList<>();

            for (String serviceId : scansById.keySet()) {
                if (dependencies.containsKey(serviceId)) {
                    withDeps.add(serviceId);
                } else {
                    noDeps.add(serviceId);
                }
            }

            List<String> result = new ArrayList<>();
            result.addAll(noDeps);
            result.addAll(withDeps);
            return result;
        }
    }

    private static class MockQueueMappingDAO extends QueueMappingDAO {
        private final Map<String, String> functionQueues = new HashMap<>();
        private final Map<String, String> topicQueues = new HashMap<>();

        void addMapping(String queueName, String targetType, String targetName) {
            if (TARGET_TYPE_FUNCTION.equals(targetType)) {
                functionQueues.put(targetName, queueName);
            } else if (TARGET_TYPE_TOPIC.equals(targetType)) {
                topicQueues.put(targetName, queueName);
            }
        }

        @Override
        public Optional<String> findQueueNameForFunction(Connection connection, String functionName) {
            return Optional.ofNullable(functionQueues.get(functionName));
        }

        @Override
        public Optional<String> findQueueNameForTopic(Connection connection, String topicName) {
            return Optional.ofNullable(topicQueues.get(topicName));
        }
    }
}

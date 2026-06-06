package gov.nystax.nimbus.codesnap.services.builder;

import gov.nystax.nimbus.codesnap.services.builder.domain.AppTemplateNode;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildRequest;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildResult;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildResult.FailedServiceInfo;
import gov.nystax.nimbus.codesnap.services.builder.domain.ChildReference;
import gov.nystax.nimbus.codesnap.services.builder.domain.FunctionPoolEntry;
import gov.nystax.nimbus.codesnap.services.processor.ServiceScanService;
import gov.nystax.nimbus.codesnap.services.processor.ServiceScanService.ScanDataWithMetadata;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for AppSnapshotBuilder.
 * Uses mock implementations to test build logic without database.
 */
class AppSnapshotBuilderTest {

    private MockServiceScanService mockScanService;
    private AppSnapshotBuilder builder;

    @BeforeEach
    void setUp() {
        mockScanService = new MockServiceScanService();
        QueueNameResolver queueNameResolver = new QueueNameResolver();

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
            assertTrue(result.getFunctionPool().containsKey("insertemployee"));
            assertTrue(result.getFunctionPool().containsKey("getwagecount"));

            // Verify app and displayName properties are set on function pool entries
            FunctionPoolEntry insertEmployeeEntry = result.getFunctionPool().get("insertemployee");
            FunctionPoolEntry getWageCountEntry = result.getFunctionPool().get("getwagecount");
            assertEquals("test-app", insertEmployeeEntry.getApp());
            assertEquals("test-app", getWageCountEntry.getApp());
            assertEquals("insertEmployee", insertEmployeeEntry.getDisplayName());
            assertEquals("getWageCount", getWageCountEntry.getDisplayName());
            assertEquals("insertEmployee_queue", insertEmployeeEntry.getQueueName());
            assertEquals("getWageCount_queue", getWageCountEntry.getQueueName());
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
            FunctionPoolEntry entry = result.getFunctionPool().get("parentfunc");
            assertNotNull(entry);
            assertEquals("parentFunc", entry.getDisplayName());
            assertEquals(2, entry.getChildren().size());
            assertTrue(entry.containsSyncRef("childFunc1"));
            assertTrue(entry.containsSyncRef("childFunc2"));
        }

        @Test
        @DisplayName("Should add async function dependencies with queue names")
        void asyncFunctionDependencies() throws SQLException {
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
            FunctionPoolEntry entry = result.getFunctionPool().get("parentfunc");
            assertNotNull(entry);
            assertTrue(entry.containsAsyncRef("asyncFunc"));

            ChildReference asyncRef = entry.getChildren().stream()
                    .filter(ChildReference::isAsyncRef)
                    .findFirst()
                    .orElse(null);
            assertNotNull(asyncRef);
            assertNull(asyncRef.getQueueName(),
                    "Async child ref should not carry queueName (available on top-level pool entry)");
        }

        @Test
        @DisplayName("Should add scheduled async function dependency as a scheduled + async ref")
        void scheduledAsyncFunctionDependencies() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("parentFunc", "gov.service.IService.parentFunc(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addScheduledAsyncFunction("scheduledFunc");

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
            FunctionPoolEntry entry = result.getFunctionPool().get("parentfunc");
            assertNotNull(entry);
            assertTrue(entry.containsScheduledAsyncRef("scheduledFunc"));
            // Scheduled refs are still async, so they are also recognized as async refs
            assertTrue(entry.containsAsyncRef("scheduledFunc"));

            ChildReference scheduledRef = entry.getChildren().stream()
                    .filter(ChildReference::isScheduledRef)
                    .findFirst()
                    .orElse(null);
            assertNotNull(scheduledRef);
            assertEquals(Boolean.TRUE, scheduledRef.getScheduled());
            assertEquals(Boolean.TRUE, scheduledRef.getAsync());
            assertTrue(scheduledRef.isAsyncRef());
            assertNull(scheduledRef.getQueueName(),
                    "Scheduled async child ref should not carry queueName (available on top-level pool entry)");
        }

        @Test
        @DisplayName("Should add topic dependencies with queue names")
        void topicDependencies() throws SQLException {
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
            FunctionPoolEntry entry = result.getFunctionPool().get("publishfunc");
            assertNotNull(entry);
            assertTrue(entry.containsTopicRef("PaymentPosting"));

            ChildReference topicRef = entry.getChildren().stream()
                    .filter(ChildReference::isTopicRef)
                    .findFirst()
                    .orElse(null);
            assertNotNull(topicRef);
            assertEquals("PaymentPosting_queue", topicRef.getQueueName());
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
            FunctionPoolEntry entryA = result.getFunctionPool().get("funca");
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
            FunctionPoolEntry entryA = result.getFunctionPool().get("funca");
            assertNotNull(entryA);
            assertTrue(entryA.containsSyncRef("leafFunction"),
                    "funcA should have leafFunction as multi-level transitive dependency");
        }
    }

    @Nested
    @DisplayName("UI Service Method Tests")
    class UiServiceMethodTests {

        @Test
        @DisplayName("Should add scheduled async function ref (async + scheduled + queueName) to UI service method")
        void uiServiceMethodWithScheduledAsyncFunctionRef() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> uiMethodMappings = new HashMap<>();
            uiMethodMappings.put("retrieveData", "gov.ui.IUI.retrieveData(...)");
            scanData.setUiServiceMethodMappings(uiMethodMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addScheduledAsyncFunction("scheduledFunc");
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

            AppTemplateNode scheduledNode = methodNode.getChildren().stream()
                    .filter(c -> "scheduledFunc".equals(c.getRef()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(scheduledNode);
            assertEquals(Boolean.TRUE, scheduledNode.getAsync());
            assertEquals(Boolean.TRUE, scheduledNode.getScheduled());
            assertNotNull(scheduledNode.getQueueName(),
                    "Scheduled async app-template node should carry a resolved queueName");
        }

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

            // UI service should NOT create function pool entries
            assertTrue(result.getFunctionPool().isEmpty(),
                    "UI service should not create function pool entries for referenced functions");
        }

        @Test
        @DisplayName("Should not create function pool entries from UI service when regular service owns the functions")
        void uiServiceDoesNotDuplicateFunctionPoolEntries() throws SQLException {
            // Setup: Regular service owns helperFunc1 and helperFunc2
            ScanData regularScanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("helperFunc1", "gov.service.IService.helperFunc1(...)");
            functionMappings.put("helperFunc2", "gov.service.IService.helperFunc2(...)");
            regularScanData.setFunctionMappings(functionMappings);

            Map<String, EntryPointDependencies> regularEntryPoints = new HashMap<>();
            regularEntryPoints.put("helperFunc1", new EntryPointDependencies());
            regularEntryPoints.put("helperFunc2", new EntryPointDependencies());
            regularScanData.setEntryPointChildren(regularEntryPoints);

            // Setup: UI service method references helperFunc1 and helperFunc2
            ScanData uiScanData = new ScanData();
            Map<String, String> uiMethodMappings = new HashMap<>();
            uiMethodMappings.put("retrieveData", "gov.ui.IUI.retrieveData(...)");
            uiScanData.setUiServiceMethodMappings(uiMethodMappings);

            EntryPointDependencies uiDeps = new EntryPointDependencies();
            uiDeps.addFunction("helperFunc1");
            uiDeps.addFunction("helperFunc2");
            uiScanData.setEntryPointChildren(Map.of("retrieveData", uiDeps));

            // Add scans: regular service first (no dependency), UI service depends on it
            mockScanService.addScan("REGULAR_SVC", "r1", false, null, regularScanData);
            mockScanService.addScan("UI_SVC", "u1", true, "REGULAR_SVC", uiScanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("mixed-app");
            request.addService("REGULAR_SVC", "r1");
            request.addService("UI_SVC", "u1");

            // Execute
            BuildResult result = builder.build(null, request);

            // Verify function pool: only 2 entries, created by the regular service
            assertEquals(2, result.getFunctionPool().size());
            assertTrue(result.getFunctionPool().containsKey("helperfunc1"));
            assertTrue(result.getFunctionPool().containsKey("helperfunc2"));

            // Verify app property is set correctly (by the regular service)
            assertEquals("mixed-app", result.getFunctionPool().get("helperfunc1").getApp());
            assertEquals("mixed-app", result.getFunctionPool().get("helperfunc2").getApp());

            // Verify UI service method node has function refs as children
            AppTemplateNode appRoot = result.getAppTemplate();
            AppTemplateNode uiServicesNode = appRoot.getChildren().stream()
                    .filter(c -> AppTemplateNode.TYPE_UI_SERVICES.equals(c.getType()))
                    .findFirst()
                    .orElse(null);
            assertNotNull(uiServicesNode);
            assertEquals("UI_SVC", uiServicesNode.getName());

            AppTemplateNode methodNode = uiServicesNode.getChildren().get(0);
            assertEquals("retrieveData", methodNode.getName());
            assertEquals(2, methodNode.getChildren().size());
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
            assertTrue(result.getFunctionPool().containsKey("goodfunc"));
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

    @Nested
    @DisplayName("Legacy Gateway HTTP Client Tests")
    class LegacyGatewayHttpClientTests {

        @Test
        @DisplayName("Should set usesLegacyGatewayHttpClient flag on function pool entry from direct dependency")
        void directDependencyFlag() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("processPayment", "gov.service.IService.processPayment(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.setUsesLegacyGatewayHttpClient(true);

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("processPayment", deps);
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            BuildResult result = builder.build(null, request);

            FunctionPoolEntry entry = result.getFunctionPool().get("processpayment");
            assertNotNull(entry);
            assertTrue(entry.isUsesLegacyGatewayHttpClient());
        }

        @Test
        @DisplayName("Should not set flag when not used")
        void noFlagWhenNotUsed() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("simpleFunc", "gov.service.IService.simpleFunc(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addFunction("childFunc");

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("simpleFunc", deps);
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            BuildResult result = builder.build(null, request);

            FunctionPoolEntry entry = result.getFunctionPool().get("simplefunc");
            assertNotNull(entry);
            assertFalse(entry.isUsesLegacyGatewayHttpClient());
        }

        @Test
        @DisplayName("Should propagate flag transitively through service calls")
        void transitiveResolution() throws SQLException {
            // Service A calls Service B, and Service B uses legacy gateway HTTP client
            ScanData scanDataA = new ScanData();
            Map<String, String> functionMappingsA = new HashMap<>();
            functionMappingsA.put("funcA", "gov.a.IA.funcA(...)");
            scanDataA.setFunctionMappings(functionMappingsA);

            EntryPointDependencies depsA = new EntryPointDependencies();
            depsA.addServiceCall("SERVICE_B", "gov.b.IB.funcB(...)");
            scanDataA.setEntryPointChildren(Map.of("funcA", depsA));

            ScanData scanDataB = new ScanData();
            scanDataB.setFunctionMappings(new HashMap<>());
            scanDataB.setMethodImplementationMapping(Map.of(
                    "gov.b.IB.funcB(...)", "gov.b.impl.BImpl.funcB(...)"));

            EntryPointDependencies publicDepsB = new EntryPointDependencies();
            publicDepsB.setUsesLegacyGatewayHttpClient(true);
            scanDataB.setPublicMethodDependencies(Map.of(
                    "gov.b.impl.BImpl.funcB(...)", publicDepsB));

            mockScanService.addScan("SERVICE_A", "a1", false, "SERVICE_B", scanDataA);
            mockScanService.addScan("SERVICE_B", "b1", false, null, scanDataB);

            BuildRequest request = new BuildRequest();
            request.setAppName("transitive-app");
            request.addService("SERVICE_A", "a1");
            request.addService("SERVICE_B", "b1");

            BuildResult result = builder.build(null, request);

            FunctionPoolEntry entryA = result.getFunctionPool().get("funca");
            assertNotNull(entryA);
            assertTrue(entryA.isUsesLegacyGatewayHttpClient(),
                    "Flag should propagate transitively from Service B to Service A");
        }

        @Test
        @DisplayName("Should propagate flag through multi-level transitive chains")
        void multiLevelTransitive() throws SQLException {
            // A -> B -> C (C uses legacy gateway)
            ScanData scanDataA = new ScanData();
            scanDataA.setFunctionMappings(Map.of("funcA", "gov.a.IA.funcA(...)"));
            EntryPointDependencies depsA = new EntryPointDependencies();
            depsA.addServiceCall("SERVICE_B", "gov.b.IB.funcB(...)");
            scanDataA.setEntryPointChildren(Map.of("funcA", depsA));

            ScanData scanDataB = new ScanData();
            scanDataB.setFunctionMappings(new HashMap<>());
            scanDataB.setMethodImplementationMapping(Map.of(
                    "gov.b.IB.funcB(...)", "gov.b.impl.BImpl.funcB(...)"));
            EntryPointDependencies publicDepsB = new EntryPointDependencies();
            publicDepsB.addServiceCall("SERVICE_C", "gov.c.IC.funcC(...)");
            scanDataB.setPublicMethodDependencies(Map.of(
                    "gov.b.impl.BImpl.funcB(...)", publicDepsB));

            ScanData scanDataC = new ScanData();
            scanDataC.setFunctionMappings(new HashMap<>());
            scanDataC.setMethodImplementationMapping(Map.of(
                    "gov.c.IC.funcC(...)", "gov.c.impl.CImpl.funcC(...)"));
            EntryPointDependencies publicDepsC = new EntryPointDependencies();
            publicDepsC.setUsesLegacyGatewayHttpClient(true);
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

            BuildResult result = builder.build(null, request);

            FunctionPoolEntry entryA = result.getFunctionPool().get("funca");
            assertNotNull(entryA);
            assertTrue(entryA.isUsesLegacyGatewayHttpClient(),
                    "Flag should propagate through A -> B -> C chain");
        }
    }

    @Nested
    @DisplayName("CTG Dependency Tests")
    class CtgDependencyTests {

        @Test
        @DisplayName("Should create top-level CTG pool entry for sync CTG dependency")
        void syncCtgCreatesPoolEntry() throws SQLException {
            ScanData scanData = new ScanData();
            scanData.setFunctionMappings(Map.of(
                    "insertEmployee", "gov.service.IService.insertEmployee(...)"));

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addCtgComponent("TZ0001Z");
            scanData.setEntryPointChildren(Map.of("insertEmployee", deps));

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            BuildResult result = builder.build(null, request);

            // Verify top-level CTG entry exists
            assertTrue(result.getFunctionPool().containsKey("ctg_tz0001z"));
            FunctionPoolEntry ctgEntry = result.getFunctionPool().get("ctg_tz0001z");
            assertEquals("TZ0001Z", ctgEntry.getDisplayName());
            assertTrue(ctgEntry.isCtg());
            assertNull(ctgEntry.getApp());
            assertEquals("TZ0001Z_queue", ctgEntry.getQueueName());

            // Verify child reference on the function
            FunctionPoolEntry funcEntry = result.getFunctionPool().get("insertemployee");
            assertNotNull(funcEntry);
            assertTrue(funcEntry.containsSyncRef("ctg_tz0001z"));

            // Verify the child reference has ctg flag
            ChildReference ctgChild = funcEntry.getChildren().stream()
                    .filter(c -> "ctg_tz0001z".equals(c.getRef()))
                    .findFirst().orElse(null);
            assertNotNull(ctgChild);
            assertTrue(ctgChild.isCtg());
            assertTrue(ctgChild.isSyncRef());
        }

        @Test
        @DisplayName("Should create top-level CTG pool entry for async CTG dependency with queue name")
        void asyncCtgCreatesPoolEntryWithQueueName() throws SQLException {
            ScanData scanData = new ScanData();
            scanData.setFunctionMappings(Map.of(
                    "processPayment", "gov.service.IService.processPayment(...)"));

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addAsyncCtgComponent("TZ0002Z");
            scanData.setEntryPointChildren(Map.of("processPayment", deps));

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            BuildResult result = builder.build(null, request);

            // Verify top-level CTG entry
            assertTrue(result.getFunctionPool().containsKey("ctg_tz0002z"));
            FunctionPoolEntry ctgEntry = result.getFunctionPool().get("ctg_tz0002z");
            assertEquals("TZ0002Z", ctgEntry.getDisplayName());
            assertTrue(ctgEntry.isCtg());
            assertEquals("TZ0002Z_queue", ctgEntry.getQueueName());

            // Verify child reference
            FunctionPoolEntry funcEntry = result.getFunctionPool().get("processpayment");
            assertTrue(funcEntry.containsAsyncRef("ctg_tz0002z"));

            ChildReference asyncCtgChild = funcEntry.getChildren().stream()
                    .filter(c -> "ctg_tz0002z".equals(c.getRef()))
                    .findFirst().orElse(null);
            assertNotNull(asyncCtgChild);
            assertTrue(asyncCtgChild.isCtg());
            assertTrue(asyncCtgChild.isAsyncRef());
            assertNull(asyncCtgChild.getQueueName(),
                    "Async CTG child ref should not carry queueName (available on top-level CTG pool entry)");
        }

        @Test
        @DisplayName("Should resolve CTG dependencies transitively through service calls")
        void transitiveCtgResolution() throws SQLException {
            // Service A calls Service B, Service B has CTG dep
            ScanData scanDataA = new ScanData();
            scanDataA.setFunctionMappings(Map.of(
                    "funcA", "gov.a.IA.funcA(...)"));
            EntryPointDependencies depsA = new EntryPointDependencies();
            depsA.addServiceCall("SERVICE_B", "gov.b.IB.funcB(...)");
            scanDataA.setEntryPointChildren(Map.of("funcA", depsA));

            ScanData scanDataB = new ScanData();
            scanDataB.setFunctionMappings(new HashMap<>());
            scanDataB.setMethodImplementationMapping(Map.of(
                    "gov.b.IB.funcB(...)", "gov.b.impl.BImpl.funcB(...)"));
            EntryPointDependencies publicDepsB = new EntryPointDependencies();
            publicDepsB.addCtgComponent("TZ0003Z");
            scanDataB.setPublicMethodDependencies(Map.of(
                    "gov.b.impl.BImpl.funcB(...)", publicDepsB));

            mockScanService.addScan("SERVICE_A", "a1", false, "SERVICE_B", scanDataA);
            mockScanService.addScan("SERVICE_B", "b1", false, null, scanDataB);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE_A", "a1");
            request.addService("SERVICE_B", "b1");

            BuildResult result = builder.build(null, request);

            // funcA should have CTG ref from transitive resolution
            FunctionPoolEntry entryA = result.getFunctionPool().get("funca");
            assertTrue(entryA.containsSyncRef("ctg_tz0003z"));

            // Top-level CTG entry should exist
            assertTrue(result.getFunctionPool().containsKey("ctg_tz0003z"));
            assertEquals("TZ0003Z", result.getFunctionPool().get("ctg_tz0003z").getDisplayName());
            assertTrue(result.getFunctionPool().get("ctg_tz0003z").isCtg());
            assertEquals("TZ0003Z_queue", result.getFunctionPool().get("ctg_tz0003z").getQueueName());
        }

        @Test
        @DisplayName("Should add CTG refs to UI service method nodes and create pool entries")
        void uiServiceWithCtgDeps() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> uiMethodMappings = new HashMap<>();
            uiMethodMappings.put("retrieveData", "gov.ui.IUI.retrieveData(...)");
            scanData.setUiServiceMethodMappings(uiMethodMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addCtgComponent("TZ0004Z");
            scanData.setEntryPointChildren(Map.of("retrieveData", deps));

            mockScanService.addScan("UISERVICE", "ui1", true, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("ui-ctg-app");
            request.addService("UISERVICE", "ui1");

            BuildResult result = builder.build(null, request);

            // CTG pool entry should exist
            assertTrue(result.getFunctionPool().containsKey("ctg_tz0004z"));
            assertTrue(result.getFunctionPool().get("ctg_tz0004z").isCtg());
            assertEquals("TZ0004Z", result.getFunctionPool().get("ctg_tz0004z").getDisplayName());
            assertEquals("TZ0004Z_queue", result.getFunctionPool().get("ctg_tz0004z").getQueueName());

            // UI method node should have CTG ref
            AppTemplateNode uiServices = result.getAppTemplate().getChildren().get(0);
            AppTemplateNode methodNode = uiServices.getChildren().get(0);
            assertTrue(methodNode.getChildren().stream()
                    .anyMatch(c -> "ctg_tz0004z".equals(c.getRef()) &&
                            c.getCtg() != null && c.getCtg()));
        }

        @Test
        @DisplayName("Should not duplicate CTG pool entries when same CTG referenced by multiple functions")
        void noDuplicateCtgEntries() throws SQLException {
            ScanData scanData = new ScanData();
            scanData.setFunctionMappings(Map.of(
                    "func1", "gov.service.IService.func1(...)",
                    "func2", "gov.service.IService.func2(...)"));

            EntryPointDependencies deps1 = new EntryPointDependencies();
            deps1.addCtgComponent("TZ0001Z");
            EntryPointDependencies deps2 = new EntryPointDependencies();
            deps2.addCtgComponent("TZ0001Z");

            Map<String, EntryPointDependencies> entryPointChildren = new HashMap<>();
            entryPointChildren.put("func1", deps1);
            entryPointChildren.put("func2", deps2);
            scanData.setEntryPointChildren(entryPointChildren);

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            BuildResult result = builder.build(null, request);

            // Only one CTG entry
            long ctgCount = result.getFunctionPool().entrySet().stream()
                    .filter(e -> e.getKey().startsWith("ctg_"))
                    .count();
            assertEquals(1, ctgCount);

            // Queue name should be resolved
            assertEquals("TZ0001Z_queue", result.getFunctionPool().get("ctg_tz0001z").getQueueName());

            // Both functions reference it
            assertTrue(result.getFunctionPool().get("func1").containsSyncRef("ctg_tz0001z"));
            assertTrue(result.getFunctionPool().get("func2").containsSyncRef("ctg_tz0001z"));
        }

        @Test
        @DisplayName("Should handle mixed sync and async CTG dependencies on same function")
        void mixedSyncAsyncCtgDeps() throws SQLException {
            ScanData scanData = new ScanData();
            scanData.setFunctionMappings(Map.of(
                    "mixedFunc", "gov.service.IService.mixedFunc(...)"));

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addCtgComponent("TZ0001Z");
            deps.addAsyncCtgComponent("TZ0002Z");
            scanData.setEntryPointChildren(Map.of("mixedFunc", deps));

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("test-app");
            request.addService("SERVICE", "commit1");

            BuildResult result = builder.build(null, request);

            // Two CTG pool entries
            assertTrue(result.getFunctionPool().containsKey("ctg_tz0001z"));
            assertTrue(result.getFunctionPool().containsKey("ctg_tz0002z"));

            FunctionPoolEntry funcEntry = result.getFunctionPool().get("mixedfunc");
            // Sync CTG ref
            assertTrue(funcEntry.containsSyncRef("ctg_tz0001z"));
            // Async CTG ref
            assertTrue(funcEntry.containsAsyncRef("ctg_tz0002z"));

            // Verify CTG entries have ctg flag
            assertTrue(result.getFunctionPool().get("ctg_tz0001z").isCtg());
            assertTrue(result.getFunctionPool().get("ctg_tz0002z").isCtg());
            // Both CTG entries should have queue names
            assertEquals("TZ0001Z_queue", result.getFunctionPool().get("ctg_tz0001z").getQueueName());
            assertEquals("TZ0002Z_queue", result.getFunctionPool().get("ctg_tz0002z").getQueueName());
        }
    }

    @Nested
    @DisplayName("Nimba App Tests")
    class NimbaAppTests {

        @Test
        @DisplayName("Should promote implicit function children directly under app root and leave function pool empty")
        void nimbaAppPromotesChildrenToAppRoot() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("nimb_xyz_app_implicitfunction", "gov.nimba.INimba.implicitFunction(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addFunction("childFunc1");
            deps.addFunction("childFunc2");
            deps.addAsyncFunction("asyncChild1");
            scanData.setEntryPointChildren(Map.of("nimb_xyz_app_implicitfunction", deps));

            mockScanService.addScan("NIMB-XYZ-APP", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("nimb-xyz-app");
            request.setAppType(BuildRequest.AppType.NIMBA);
            request.addService("NIMB-XYZ-APP", "commit1");

            BuildResult result = builder.build(null, request);

            // Function pool should be empty — nimba apps contribute nothing
            assertTrue(result.getFunctionPool().isEmpty(),
                    "Nimba app should not add anything to the function pool");

            // App root should have the implicit function's children directly
            AppTemplateNode appRoot = result.getAppTemplate();
            assertEquals("nimb-xyz-app", appRoot.getName());
            assertNotNull(appRoot.getChildren());
            assertEquals(3, appRoot.getChildren().size());

            // Verify sync refs
            assertTrue(appRoot.getChildren().stream()
                    .anyMatch(c -> "childFunc1".equals(c.getRef()) && c.getAsync() == null));
            assertTrue(appRoot.getChildren().stream()
                    .anyMatch(c -> "childFunc2".equals(c.getRef()) && c.getAsync() == null));

            // Verify async ref
            assertTrue(appRoot.getChildren().stream()
                    .anyMatch(c -> "asyncChild1".equals(c.getRef()) &&
                            c.getAsync() != null && c.getAsync()));
        }

        @Test
        @DisplayName("Should handle nimba app with no children on implicit function")
        void nimbaAppNoChildren() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("nimb_abc_app_implicitfunction", "gov.nimba.INimba.implicitFunction(...)");
            scanData.setFunctionMappings(functionMappings);

            scanData.setEntryPointChildren(Map.of("nimb_abc_app_implicitfunction", new EntryPointDependencies()));

            mockScanService.addScan("NIMB-ABC-APP", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("nimb-abc-app");
            request.setAppType(BuildRequest.AppType.NIMBA);
            request.addService("NIMB-ABC-APP", "commit1");

            BuildResult result = builder.build(null, request);

            assertTrue(result.getFunctionPool().isEmpty());
            // App root should have no children
            AppTemplateNode appRoot = result.getAppTemplate();
            assertFalse(appRoot.hasChildren() && !appRoot.getChildren().isEmpty(),
                    "Nimba app with no dependencies should have no children on app root");
        }

        @Test
        @DisplayName("Should handle nimba app with topic dependencies")
        void nimbaAppWithTopicDeps() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("nimb_evt_app_implicitfunction", "gov.nimba.INimba.implicitFunction(...)");
            scanData.setFunctionMappings(functionMappings);

            EntryPointDependencies deps = new EntryPointDependencies();
            deps.addFunction("syncFunc");
            deps.addTopic("employeeCreated");
            scanData.setEntryPointChildren(Map.of("nimb_evt_app_implicitfunction", deps));

            mockScanService.addScan("NIMB-EVT-APP", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("nimb-evt-app");
            request.setAppType(BuildRequest.AppType.NIMBA);
            request.addService("NIMB-EVT-APP", "commit1");

            BuildResult result = builder.build(null, request);

            assertTrue(result.getFunctionPool().isEmpty());

            AppTemplateNode appRoot = result.getAppTemplate();
            assertEquals(2, appRoot.getChildren().size());

            // Verify sync function ref
            assertTrue(appRoot.getChildren().stream()
                    .anyMatch(c -> "syncFunc".equals(c.getRef())));

            // Verify topic publish ref
            assertTrue(appRoot.getChildren().stream()
                    .anyMatch(c -> "employeeCreated".equals(c.getTopicName()) &&
                            c.getTopicPublish() != null && c.getTopicPublish()));
        }

        @Test
        @DisplayName("Should not affect regular app builds when appType is not set")
        void regularAppUnaffectedWhenAppTypeNull() throws SQLException {
            ScanData scanData = new ScanData();
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("testFunc", "gov.service.IService.testFunc(...)");
            scanData.setFunctionMappings(functionMappings);
            scanData.setEntryPointChildren(Map.of("testFunc", new EntryPointDependencies()));

            mockScanService.addScan("SERVICE", "commit1", false, null, scanData);

            BuildRequest request = new BuildRequest();
            request.setAppName("regular-app");
            // appType not set — should behave as nimbus (regular)
            request.addService("SERVICE", "commit1");

            BuildResult result = builder.build(null, request);

            // Regular behavior: function in pool
            assertEquals(1, result.getFunctionPool().size());
            assertTrue(result.getFunctionPool().containsKey("testfunc"));
        }

        @Test
        @DisplayName("Should fail when nimba app has no services")
        void nimbaAppRequiresOneService() {
            BuildRequest request = new BuildRequest();
            request.setAppName("nimb-empty-app");
            request.setAppType(BuildRequest.AppType.NIMBA);

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, request::validate);

            assertEquals("Nimba apps must include exactly one service", exception.getMessage());
        }

        @Test
        @DisplayName("Should fail when nimba app has multiple services")
        void nimbaAppRejectsMultipleServices() {
            BuildRequest request = new BuildRequest();
            request.setAppName("nimb-multi-app");
            request.setAppType(BuildRequest.AppType.NIMBA);
            request.addService("NIMB-MULTI-APP", "commit1");
            request.addService("NIMB-MULTI-APP-2", "commit2");

            IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, request::validate);

            assertEquals("Nimba apps must include exactly one service", exception.getMessage());
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
                    .scanId("test-failure-" + serviceId)
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

}

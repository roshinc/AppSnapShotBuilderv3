package gov.nystax.nimbus.codesnap.services.processor;

import gov.nystax.nimbus.codesnap.services.processor.domain.EntryPointDependencies;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceCallReference;
import gov.nystax.nimbus.codesnap.services.scanner.domain.EventPublisherInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.FunctionUsage;
import gov.nystax.nimbus.codesnap.services.scanner.domain.MethodReference;
import gov.nystax.nimbus.codesnap.services.scanner.domain.MethodReference.MethodAccessModifier;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ServiceInvocation;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ServiceUsage;
import gov.nystax.nimbus.codesnap.services.scanner.domain.TopicResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive unit tests for ScanDataProcessor.
 * Tests all transformation logic including ownership detection,
 * transitive resolution data, and edge cases.
 */
class ScanDataProcessorTest {

    private ScanDataProcessor processor;

    @BeforeEach
    void setUp() {
        processor = new ScanDataProcessor();
    }

    @Test
    @DisplayName("Should throw exception for null ProjectInfo")
    void processNullProjectInfo() {
        assertThrows(IllegalArgumentException.class, () -> processor.process(null));
    }

    @Nested
    @DisplayName("Basic Mapping Tests")
    class BasicMappingTests {

        @Test
        @DisplayName("Should copy function mappings for regular service")
        void copyFunctionMappings() {
            ProjectInfo projectInfo = createBasicProjectInfo();
            projectInfo.setUIService(false);

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("insertEmployee", "gov.nystax.services.wt0004j.IWTEmployeeDBService.insertEmployee(...)");
            functionMappings.put("getWageCount", "gov.nystax.services.wt0004j.IWTEmployeeDBService.getWageCount(...)");
            projectInfo.setFunctionMappings(functionMappings);

            ScanData result = processor.process(projectInfo);

            assertEquals(2, result.getFunctionMappings().size());
            assertEquals("gov.nystax.services.wt0004j.IWTEmployeeDBService.insertEmployee(...)",
                    result.getFunctionMappings().get("insertEmployee"));
        }

        @Test
        @DisplayName("Should copy UI service method mappings for UI service")
        void copyUiServiceMethodMappings() {
            ProjectInfo projectInfo = createBasicProjectInfo();
            projectInfo.setUIService(true);

            Map<String, String> uiMappings = new HashMap<>();
            uiMappings.put("retrieveData", "gov.nystax.services.wt4545j.IWT4545JWageReportingProcess.retrieveData(...)");
            projectInfo.setUIServiceMethodMappings(uiMappings);

            ScanData result = processor.process(projectInfo);

            assertEquals(1, result.getUiServiceMethodMappings().size());
            assertEquals("gov.nystax.services.wt4545j.IWT4545JWageReportingProcess.retrieveData(...)",
                    result.getUiServiceMethodMappings().get("retrieveData"));
        }

        @Test
        @DisplayName("Should copy method implementation mappings")
        void copyMethodImplementationMappings() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.nystax.services.IService.method()", "gov.nystax.services.impl.ServiceImpl.method()");
            projectInfo.setMethodImplementationMappings(implMappings);

            ScanData result = processor.process(projectInfo);

            assertEquals(1, result.getMethodImplementationMapping().size());
        }

        @Test
        @DisplayName("Should initialize empty entry point children for all exposed entry points")
        void initializeEntryPointChildren() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("func1", "interface.method1()");
            functionMappings.put("func2", "interface.method2()");
            projectInfo.setFunctionMappings(functionMappings);

            ScanData result = processor.process(projectInfo);

            Map<String, EntryPointDependencies> children = result.getEntryPointChildren();
            assertEquals(2, children.size());
            assertTrue(children.containsKey("func1"));
            assertTrue(children.containsKey("func2"));
            assertTrue(children.get("func1").isEmpty());
        }
    }

    @Nested
    @DisplayName("Function Usage Processing Tests")
    class FunctionUsageTests {

        @Test
        @DisplayName("Should assign sync function dependency to correct owner")
        void syncFunctionOwnership() {
            ProjectInfo projectInfo = createProjectInfoWithFunctionUsage(
                    "insertEmployee",
                    "gov.nystax.services.wt0004j.IWTEmployeeDBService.insertEmployee(...)",
                    "gov.nystax.services.wt0004j.impl.WTEmployeeDBServiceImpl.insertEmployee(...)",
                    "externalFunction",
                    "execute"
            );

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("insertEmployee");
            assertNotNull(deps);
            assertTrue(deps.getFunctions().contains("externalFunction"));
            assertTrue(deps.getAsyncFunctions().isEmpty());
        }

        @Test
        @DisplayName("Should assign async function dependency to correct owner")
        void asyncFunctionOwnership() {
            ProjectInfo projectInfo = createProjectInfoWithFunctionUsage(
                    "insertEmployee",
                    "gov.nystax.services.wt0004j.IWTEmployeeDBService.insertEmployee(...)",
                    "gov.nystax.services.wt0004j.impl.WTEmployeeDBServiceImpl.insertEmployee(...)",
                    "externalAsyncFunction",
                    "executeAsync"
            );

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("insertEmployee");
            assertNotNull(deps);
            assertTrue(deps.getAsyncFunctions().contains("externalAsyncFunction"));
            assertTrue(deps.getFunctions().isEmpty());
        }

        @Test
        @DisplayName("Should assign function to multiple owners when call chain has multiple entry points")
        void multipleOwners() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            // Two entry points
            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("entryPoint1", "gov.service.IService.entryPoint1(...)");
            functionMappings.put("entryPoint2", "gov.service.IService.entryPoint2(...)");
            projectInfo.setFunctionMappings(functionMappings);

            // Both implementations
            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.entryPoint1(...)", "gov.service.impl.ServiceImpl.entryPoint1(...)");
            implMappings.put("gov.service.IService.entryPoint2(...)", "gov.service.impl.ServiceImpl.entryPoint2(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            // Function usage with call chain containing both entry points
            FunctionUsage usage = new FunctionUsage("sharedFunction", "gov.func.sharedFunction", "dep");
            FunctionInvocation invocation = new FunctionInvocation("ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.helper(...)", MethodAccessModifier.PRIVATE),
                    "execute");

            List<MethodReference> callChain = new ArrayList<>();
            callChain.add(new MethodReference("gov.service.impl.ServiceImpl.entryPoint1(...)", MethodAccessModifier.PUBLIC));
            callChain.add(new MethodReference("gov.service.impl.ServiceImpl.helper(...)", MethodAccessModifier.PRIVATE));
            callChain.add(new MethodReference("gov.service.impl.ServiceImpl.entryPoint2(...)", MethodAccessModifier.PUBLIC));
            invocation.setCallChain(callChain);

            usage.setInvocations(List.of(invocation));
            projectInfo.setFunctionUsages(List.of(usage));

            ScanData result = processor.process(projectInfo);

            // Both entry points should own the function
            assertTrue(result.getEntryPointChildren().get("entryPoint1").getFunctions().contains("sharedFunction"));
            assertTrue(result.getEntryPointChildren().get("entryPoint2").getFunctions().contains("sharedFunction"));
        }

        @Test
        @DisplayName("Should populate publicMethodDependencies for all public methods in call chain")
        void publicMethodDependencies() {
            ProjectInfo projectInfo = createProjectInfoWithFunctionUsage(
                    "insertEmployee",
                    "gov.nystax.services.wt0004j.IWTEmployeeDBService.insertEmployee(...)",
                    "gov.nystax.services.wt0004j.impl.WTEmployeeDBServiceImpl.insertEmployee(...)",
                    "externalFunction",
                    "execute"
            );

            ScanData result = processor.process(projectInfo);

            Map<String, EntryPointDependencies> publicDeps = result.getPublicMethodDependencies();
            assertNotNull(publicDeps);
            assertTrue(publicDeps.containsKey("gov.nystax.services.wt0004j.impl.WTEmployeeDBServiceImpl.insertEmployee(...)"));
            assertTrue(publicDeps.get("gov.nystax.services.wt0004j.impl.WTEmployeeDBServiceImpl.insertEmployee(...)").getFunctions().contains("externalFunction"));
        }

        @Test
        @DisplayName("Should not include private methods in publicMethodDependencies")
        void privateMethodsExcludedFromPublicDeps() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("entryPoint", "gov.service.IService.entryPoint(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.entryPoint(...)", "gov.service.impl.ServiceImpl.entryPoint(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            FunctionUsage usage = new FunctionUsage("func", "gov.func.func", "dep");
            FunctionInvocation invocation = new FunctionInvocation("ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.privateHelper(...)", MethodAccessModifier.PRIVATE),
                    "execute");

            List<MethodReference> callChain = new ArrayList<>();
            callChain.add(new MethodReference("gov.service.impl.ServiceImpl.entryPoint(...)", MethodAccessModifier.PUBLIC));
            callChain.add(new MethodReference("gov.service.impl.ServiceImpl.privateHelper(...)", MethodAccessModifier.PRIVATE));
            invocation.setCallChain(callChain);

            usage.setInvocations(List.of(invocation));
            projectInfo.setFunctionUsages(List.of(usage));

            ScanData result = processor.process(projectInfo);

            Map<String, EntryPointDependencies> publicDeps = result.getPublicMethodDependencies();
            assertTrue(publicDeps.containsKey("gov.service.impl.ServiceImpl.entryPoint(...)"));
            assertFalse(publicDeps.containsKey("gov.service.impl.ServiceImpl.privateHelper(...)"));
        }
    }

    @Nested
    @DisplayName("Service Usage Processing Tests")
    class ServiceUsageTests {

        @Test
        @DisplayName("Should create service call reference for service usage")
        void serviceCallReference() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("applyChanges", "gov.service.IService.applyChanges(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.applyChanges(...)", "gov.service.impl.ServiceImpl.applyChanges(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            ServiceUsage serviceUsage = new ServiceUsage("WT0019J", "gov.nystax.services.wt0019j", "dep");
            ServiceInvocation invocation = new ServiceInvocation(
                    "ServiceImpl.java:730",
                    new MethodReference("gov.service.impl.ServiceImpl.applyChanges(...)", MethodAccessModifier.PUBLIC),
                    "gov.nystax.services.wt0019j.IPreviewEmployeeDBService.retrievePrevEmployee(...)"
            );

            List<MethodReference> callChain = new ArrayList<>();
            callChain.add(new MethodReference("gov.service.impl.ServiceImpl.applyChanges(...)", MethodAccessModifier.PUBLIC));
            invocation.setCallChain(callChain);

            serviceUsage.setInvocations(List.of(invocation));
            projectInfo.setServiceUsages(List.of(serviceUsage));

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("applyChanges");
            assertNotNull(deps);
            assertEquals(1, deps.getServiceCalls().size());

            ServiceCallReference call = deps.getServiceCalls().get(0);
            assertEquals("WT0019J", call.getServiceId());
            assertEquals("gov.nystax.services.wt0019j.IPreviewEmployeeDBService.retrievePrevEmployee(...)",
                    call.getInterfaceMethod());
        }

        @Test
        @DisplayName("Should deduplicate identical service calls")
        void deduplicateServiceCalls() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("entryPoint", "gov.service.IService.entryPoint(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.entryPoint(...)", "gov.service.impl.ServiceImpl.entryPoint(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            // Two invocations to the same service method
            ServiceUsage serviceUsage = new ServiceUsage("WT0019J", "gov.nystax.services.wt0019j", "dep");

            ServiceInvocation invocation1 = new ServiceInvocation(
                    "ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.entryPoint(...)", MethodAccessModifier.PUBLIC),
                    "gov.nystax.services.wt0019j.IService.method(...)"
            );
            invocation1.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.entryPoint(...)", MethodAccessModifier.PUBLIC)
            ));

            ServiceInvocation invocation2 = new ServiceInvocation(
                    "ServiceImpl.java:200",
                    new MethodReference("gov.service.impl.ServiceImpl.entryPoint(...)", MethodAccessModifier.PUBLIC),
                    "gov.nystax.services.wt0019j.IService.method(...)"
            );
            invocation2.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.entryPoint(...)", MethodAccessModifier.PUBLIC)
            ));

            serviceUsage.setInvocations(List.of(invocation1, invocation2));
            projectInfo.setServiceUsages(List.of(serviceUsage));

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("entryPoint");
            assertEquals(1, deps.getServiceCalls().size(), "Duplicate service calls should be deduplicated");
        }
    }

    @Nested
    @DisplayName("Event Publisher Processing Tests")
    class EventPublisherTests {

        @Test
        @DisplayName("Should add resolved topic to entry point children")
        void resolvedTopicOwnership() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("publishPayment", "gov.service.IService.publishPayment(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.publishPayment(...)", "gov.service.impl.ServiceImpl.publishPayment(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            EventPublisherInvocation invocation = new EventPublisherInvocation(
                    "ServiceImpl.java:54",
                    new MethodReference("gov.service.impl.ServiceImpl.publishPayment(...)", MethodAccessModifier.PUBLIC),
                    "PaymentPosting",
                    TopicResolution.RESOLVED
            );
            invocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.publishPayment(...)", MethodAccessModifier.PUBLIC)
            ));

            projectInfo.setEventPublisherInvocations(List.of(invocation));

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("publishPayment");
            assertNotNull(deps);
            assertTrue(deps.getTopics().contains("PaymentPosting"));
        }

        @Test
        @DisplayName("Should process UNKNOWN_VARIABLE topic with placeholder")
        void processUnknownVariableTopic() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("publishEvent", "gov.service.IService.publishEvent(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.publishEvent(...)", "gov.service.impl.ServiceImpl.publishEvent(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            EventPublisherInvocation unresolvedInvocation = new EventPublisherInvocation(
                    "ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC),
                    "dynamicTopic",
                    TopicResolution.UNKNOWN_VARIABLE
            );
            unresolvedInvocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC)
            ));

            projectInfo.setEventPublisherInvocations(List.of(unresolvedInvocation));

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("publishEvent");
            assertNotNull(deps);
            // Unresolved topic should appear with the placeholder name
            assertTrue(deps.getTopics().contains(ScanDataProcessor.UNKNOWN_TOPIC_PLACEHOLDER));
        }

        @Test
        @DisplayName("Should process UNKNOWN_COMPLEX topic with placeholder")
        void processUnknownComplexTopic() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("publishEvent", "gov.service.IService.publishEvent(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.publishEvent(...)", "gov.service.impl.ServiceImpl.publishEvent(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            EventPublisherInvocation complexInvocation = new EventPublisherInvocation(
                    "ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC),
                    "complexExpression",
                    TopicResolution.UNKNOWN_COMPLEX
            );
            complexInvocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC)
            ));

            projectInfo.setEventPublisherInvocations(List.of(complexInvocation));

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("publishEvent");
            assertNotNull(deps);
            // Unresolved topic should appear with the placeholder name
            assertTrue(deps.getTopics().contains(ScanDataProcessor.UNKNOWN_TOPIC_PLACEHOLDER));
        }

        @Test
        @DisplayName("Should add unresolved topic to publicMethodDependencies")
        void unresolvedTopicInPublicMethodDeps() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("publishEvent", "gov.service.IService.publishEvent(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.publishEvent(...)", "gov.service.impl.ServiceImpl.publishEvent(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            EventPublisherInvocation unresolvedInvocation = new EventPublisherInvocation(
                    "ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC),
                    "dynamicTopic",
                    TopicResolution.UNKNOWN_VARIABLE
            );
            unresolvedInvocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC)
            ));

            projectInfo.setEventPublisherInvocations(List.of(unresolvedInvocation));

            ScanData result = processor.process(projectInfo);

            Map<String, EntryPointDependencies> publicDeps = result.getPublicMethodDependencies();
            assertTrue(publicDeps.containsKey("gov.service.impl.ServiceImpl.publishEvent(...)"));
            assertTrue(publicDeps.get("gov.service.impl.ServiceImpl.publishEvent(...)").getTopics()
                    .contains(ScanDataProcessor.UNKNOWN_TOPIC_PLACEHOLDER));
        }

        @Test
        @DisplayName("Should process both resolved and unresolved topics together")
        void mixedResolvedAndUnresolvedTopics() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("publishEvent", "gov.service.IService.publishEvent(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.publishEvent(...)", "gov.service.impl.ServiceImpl.publishEvent(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            EventPublisherInvocation resolvedInvocation = new EventPublisherInvocation(
                    "ServiceImpl.java:50",
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC),
                    "PaymentPosting",
                    TopicResolution.RESOLVED
            );
            resolvedInvocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC)
            ));

            EventPublisherInvocation unresolvedInvocation = new EventPublisherInvocation(
                    "ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC),
                    "dynamicTopic",
                    TopicResolution.UNKNOWN_VARIABLE
            );
            unresolvedInvocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.publishEvent(...)", MethodAccessModifier.PUBLIC)
            ));

            projectInfo.setEventPublisherInvocations(List.of(resolvedInvocation, unresolvedInvocation));

            ScanData result = processor.process(projectInfo);

            EntryPointDependencies deps = result.getEntryPointChildren().get("publishEvent");
            assertNotNull(deps);
            // Both the resolved topic and the placeholder should be present
            assertTrue(deps.getTopics().contains("PaymentPosting"));
            assertTrue(deps.getTopics().contains(ScanDataProcessor.UNKNOWN_TOPIC_PLACEHOLDER));
            assertEquals(2, deps.getTopics().size());
        }

        @Test
        @DisplayName("Should add topic to publicMethodDependencies")
        void topicInPublicMethodDeps() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("publishPayment", "gov.service.IService.publishPayment(...)");
            projectInfo.setFunctionMappings(functionMappings);

            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.publishPayment(...)", "gov.service.impl.ServiceImpl.publishPayment(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            EventPublisherInvocation invocation = new EventPublisherInvocation(
                    "ServiceImpl.java:54",
                    new MethodReference("gov.service.impl.ServiceImpl.publishPayment(...)", MethodAccessModifier.PUBLIC),
                    "PaymentPosting",
                    TopicResolution.RESOLVED
            );
            invocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.publishPayment(...)", MethodAccessModifier.PUBLIC)
            ));

            projectInfo.setEventPublisherInvocations(List.of(invocation));

            ScanData result = processor.process(projectInfo);

            Map<String, EntryPointDependencies> publicDeps = result.getPublicMethodDependencies();
            assertTrue(publicDeps.containsKey("gov.service.impl.ServiceImpl.publishPayment(...)"));
            assertTrue(publicDeps.get("gov.service.impl.ServiceImpl.publishPayment(...)").getTopics().contains("PaymentPosting"));
        }
    }

    @Nested
    @DisplayName("Edge Case Tests")
    class EdgeCaseTests {

        @Test
        @DisplayName("Should handle empty usages gracefully")
        void emptyUsages() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("func1", "interface.method1()");
            projectInfo.setFunctionMappings(functionMappings);

            projectInfo.setFunctionUsages(new ArrayList<>());
            projectInfo.setServiceUsages(new ArrayList<>());
            projectInfo.setEventPublisherInvocations(new ArrayList<>());

            ScanData result = processor.process(projectInfo);

            assertNotNull(result);
            assertTrue(result.getEntryPointChildren().get("func1").isEmpty());
            assertTrue(result.getPublicMethodDependencies().isEmpty());
        }

        @Test
        @DisplayName("Should handle null usages gracefully")
        void nullUsages() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("func1", "interface.method1()");
            projectInfo.setFunctionMappings(functionMappings);

            projectInfo.setFunctionUsages(null);
            projectInfo.setServiceUsages(null);
            projectInfo.setEventPublisherInvocations(null);

            ScanData result = processor.process(projectInfo);

            assertNotNull(result);
            assertTrue(result.getEntryPointChildren().get("func1").isEmpty());
        }

        @Test
        @DisplayName("Should handle dependency-only service (no exposed functions)")
        void dependencyOnlyService() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            // No function mappings, no UI service method mappings
            projectInfo.setFunctionMappings(new HashMap<>());
            projectInfo.setUIServiceMethodMappings(new HashMap<>());

            // But has method implementation mappings for transitive resolution
            Map<String, String> implMappings = new HashMap<>();
            implMappings.put("gov.service.IService.helperMethod(...)", "gov.service.impl.ServiceImpl.helperMethod(...)");
            projectInfo.setMethodImplementationMappings(implMappings);

            // And has function usages
            FunctionUsage usage = new FunctionUsage("externalFunc", "gov.func.externalFunc", "dep");
            FunctionInvocation invocation = new FunctionInvocation(
                    "ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.helperMethod(...)", MethodAccessModifier.PUBLIC),
                    "execute"
            );
            invocation.setCallChain(List.of(
                    new MethodReference("gov.service.impl.ServiceImpl.helperMethod(...)", MethodAccessModifier.PUBLIC)
            ));
            usage.setInvocations(List.of(invocation));
            projectInfo.setFunctionUsages(List.of(usage));

            ScanData result = processor.process(projectInfo);

            // No entry point children (no exposed functions)
            assertTrue(result.getEntryPointChildren().isEmpty());

            // But publicMethodDependencies should be populated for transitive resolution
            assertTrue(result.getPublicMethodDependencies().containsKey("gov.service.impl.ServiceImpl.helperMethod(...)"));
            assertTrue(result.getPublicMethodDependencies().get("gov.service.impl.ServiceImpl.helperMethod(...)").getFunctions().contains("externalFunc"));
        }

        @Test
        @DisplayName("Should handle invocation with empty call chain")
        void emptyCallChain() {
            ProjectInfo projectInfo = createBasicProjectInfo();

            Map<String, String> functionMappings = new HashMap<>();
            functionMappings.put("func1", "interface.method1()");
            projectInfo.setFunctionMappings(functionMappings);

            FunctionUsage usage = new FunctionUsage("externalFunc", "gov.func.externalFunc", "dep");
            FunctionInvocation invocation = new FunctionInvocation(
                    "ServiceImpl.java:100",
                    new MethodReference("gov.service.impl.ServiceImpl.method(...)", MethodAccessModifier.PUBLIC),
                    "execute"
            );
            invocation.setCallChain(new ArrayList<>()); // Empty call chain

            usage.setInvocations(List.of(invocation));
            projectInfo.setFunctionUsages(List.of(usage));

            // Should not throw, should just skip the invocation
            ScanData result = processor.process(projectInfo);

            assertNotNull(result);
            assertTrue(result.getEntryPointChildren().get("func1").isEmpty());
        }
    }

    // Helper methods

    private ProjectInfo createBasicProjectInfo() {
        ProjectInfo projectInfo = new ProjectInfo();
        projectInfo.setArtifactId("TEST_SERVICE");
        projectInfo.setGroupId("gov.nystax.services");
        projectInfo.setVersion("1.0.0");
        projectInfo.setUIService(false);
        projectInfo.setFunctionMappings(new HashMap<>());
        projectInfo.setUIServiceMethodMappings(new HashMap<>());
        projectInfo.setMethodImplementationMappings(new HashMap<>());
        return projectInfo;
    }

    private ProjectInfo createProjectInfoWithFunctionUsage(String funcName,
                                                            String interfaceMethod,
                                                            String implMethod,
                                                            String calledFunction,
                                                            String invocationType) {
        ProjectInfo projectInfo = createBasicProjectInfo();

        Map<String, String> functionMappings = new HashMap<>();
        functionMappings.put(funcName, interfaceMethod);
        projectInfo.setFunctionMappings(functionMappings);

        Map<String, String> implMappings = new HashMap<>();
        implMappings.put(interfaceMethod, implMethod);
        projectInfo.setMethodImplementationMappings(implMappings);

        FunctionUsage usage = new FunctionUsage(calledFunction, "gov.func." + calledFunction, "dep");
        FunctionInvocation invocation = new FunctionInvocation(
                "ServiceImpl.java:100",
                new MethodReference(implMethod, MethodAccessModifier.PUBLIC),
                invocationType
        );

        List<MethodReference> callChain = new ArrayList<>();
        callChain.add(new MethodReference(implMethod, MethodAccessModifier.PUBLIC));
        invocation.setCallChain(callChain);

        usage.setInvocations(List.of(invocation));
        projectInfo.setFunctionUsages(List.of(usage));

        return projectInfo;
    }
}

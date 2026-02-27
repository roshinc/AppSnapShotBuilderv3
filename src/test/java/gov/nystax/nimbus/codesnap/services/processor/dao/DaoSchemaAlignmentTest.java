package gov.nystax.nimbus.codesnap.services.processor.dao;

import gov.nystax.nimbus.codesnap.services.processor.domain.FailedServiceScanRecord;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceScanRecord;
import org.junit.jupiter.api.Test;

import javax.sql.rowset.serial.SerialClob;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DaoSchemaAlignmentTest {

    @Test
    void serviceScanDaoSqlUsesFlowQualifiedTablesAndNewColumns() throws Exception {
        String insertSql = getPrivateStaticString(ServiceScanDAO.class, "INSERT_SQL");
        String selectSql = getPrivateStaticString(ServiceScanDAO.class, "SELECT_BY_SERVICE_AND_COMMIT_SQL");

        assertTrue(insertSql.contains("INSERT INTO FLOW.SERVICE_SCAN"));
        assertTrue(insertSql.contains("COMMIT_HASH"));
        assertTrue(insertSql.contains("SCAN_TS"));
        assertTrue(insertSql.contains("UI_SERVICE_IND"));
        assertTrue(insertSql.contains("VERSION_TEXT"));
        assertTrue(insertSql.contains("SERVICE_DEP_TEXT"));
        assertTrue(insertSql.contains("SCANNER_VER_NMBR"));
        assertFalse(insertSql.contains("GIT_COMMIT_HASH"));
        assertFalse(insertSql.contains("SCAN_TIMESTAMP"));
        assertFalse(insertSql.contains("IS_UI_SERVICE"));
        assertFalse(insertSql.contains("SERVICE_DEPENDENCIES"));

        assertTrue(selectSql.contains("FROM FLOW.SERVICE_SCAN"));
        assertTrue(selectSql.contains("WHERE SERVICE_ID = ? AND COMMIT_HASH = ?"));
    }

    @Test
    void failedServiceScanDaoSqlUsesFlowQualifiedTablesAndNewColumns() throws Exception {
        String insertSql = getPrivateStaticString(FailedServiceScanDAO.class, "INSERT_SQL");
        String selectSql = getPrivateStaticString(FailedServiceScanDAO.class, "SELECT_BY_SERVICE_AND_COMMIT_SQL");

        assertTrue(insertSql.contains("INSERT INTO FLOW.FAILED_SERVICE_SCAN"));
        assertTrue(insertSql.contains("SCAN_ID"));
        assertTrue(insertSql.contains("COMMIT_HASH"));
        assertTrue(insertSql.contains("FAILED_TS"));
        assertTrue(insertSql.contains("ERROR_MSG"));
        assertTrue(insertSql.contains("SCANNER_VER_NMBR"));
        assertFalse(insertSql.contains("FAILURE_ID"));
        assertFalse(insertSql.contains("GIT_COMMIT_HASH"));
        assertFalse(insertSql.contains("FAILURE_TIMESTAMP"));
        assertFalse(insertSql.contains("ERROR_MESSAGE"));

        assertTrue(selectSql.contains("FROM FLOW.FAILED_SERVICE_SCAN"));
        assertTrue(selectSql.contains("WHERE SERVICE_ID = ? AND COMMIT_HASH = ?"));
    }

    @Test
    void serviceScanDaoMapsNewColumnNamesToPreservedFieldNames() throws Exception {
        ServiceScanDAO dao = new ServiceScanDAO();
        Timestamp scanTimestamp = Timestamp.valueOf("2026-02-27 10:15:30");

        Map<String, Object> values = new HashMap<>();
        values.put("SCAN_ID", "scan-1");
        values.put("SERVICE_ID", "svc-1");
        values.put("COMMIT_HASH", "abc123");
        values.put("SCAN_TS", scanTimestamp);
        values.put("UI_SERVICE_IND", "Y");
        values.put("VERSION_TEXT", "1.0.0");
        values.put("SERVICE_DEP_TEXT", "A,B");
        values.put("SCANNER_VER_NMBR", 5);
        values.put("SCAN_DATA_JSON", new SerialClob("{\"ok\":true}".toCharArray()));

        ServiceScanRecord record = (ServiceScanRecord) invokePrivateMapMethod(
                dao, "mapResultSetToRecord", createResultSet(values));

        assertEquals("scan-1", record.getScanId());
        assertEquals("svc-1", record.getServiceId());
        assertEquals("abc123", record.getGitCommitHash());
        assertEquals(scanTimestamp, record.getScanTimestamp());
        assertTrue(record.isUiService());
        assertEquals("1.0.0", record.getVersion());
        assertEquals("A,B", record.getServiceDependencies());
        assertEquals(5, record.getScannerVersionNumber());
        assertEquals("{\"ok\":true}", record.getScanDataJson());
    }

    @Test
    void failedServiceScanDaoMapsNewColumnNamesToPreservedFieldNames() throws Exception {
        FailedServiceScanDAO dao = new FailedServiceScanDAO();
        Timestamp failureTimestamp = Timestamp.valueOf("2026-02-27 11:45:00");

        Map<String, Object> values = new HashMap<>();
        values.put("SCAN_ID", "fail-1");
        values.put("SERVICE_ID", "svc-2");
        values.put("COMMIT_HASH", "def456");
        values.put("FAILED_TS", failureTimestamp);
        values.put("ERROR_TYPE", "SCAN_ERROR");
        values.put("ERROR_MSG", "failed");
        values.put("SCANNER_VER_NMBR", 9);
        values.put("STACK_TRACE", new SerialClob("stack".toCharArray()));

        FailedServiceScanRecord record = (FailedServiceScanRecord) invokePrivateMapMethod(
                dao, "mapResultSetToRecord", createResultSet(values));

        assertEquals("fail-1", record.getFailureId());
        assertEquals("svc-2", record.getServiceId());
        assertEquals("def456", record.getGitCommitHash());
        assertEquals(failureTimestamp, record.getFailureTimestamp());
        assertEquals("SCAN_ERROR", record.getErrorType());
        assertEquals("failed", record.getErrorMessage());
        assertEquals(9, record.getScannerVersionNumber());
        assertEquals("stack", record.getStackTrace());
    }

    @Test
    void scannerVersionDefaultsFromConfig() throws Exception {
        ServiceScanDAO serviceDao = new ServiceScanDAO();
        FailedServiceScanDAO failedDao = new FailedServiceScanDAO();

        assertEquals(0, getPrivateInt(serviceDao, "scannerVersionNumber"));
        assertEquals(0, getPrivateInt(failedDao, "scannerVersionNumber"));
    }

    private static String getPrivateStaticString(Class<?> type, String fieldName) throws Exception {
        Field field = type.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (String) field.get(null);
    }

    private static int getPrivateInt(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.getInt(target);
    }

    private static Object invokePrivateMapMethod(Object target, String methodName, ResultSet rs) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName, ResultSet.class);
        method.setAccessible(true);
        return method.invoke(target, rs);
    }

    private static ResultSet createResultSet(Map<String, Object> values) {
        InvocationHandler handler = (proxy, method, args) -> {
            String methodName = method.getName();
            if ("getString".equals(methodName)) {
                Object value = values.get(args[0]);
                return value == null ? null : value.toString();
            }
            if ("getTimestamp".equals(methodName)) {
                return values.get(args[0]);
            }
            if ("getInt".equals(methodName)) {
                Object value = values.get(args[0]);
                return value instanceof Number ? ((Number) value).intValue() : 0;
            }
            if ("getClob".equals(methodName)) {
                return values.get(args[0]);
            }
            if ("wasNull".equals(methodName)) {
                return false;
            }
            if ("close".equals(methodName)) {
                return null;
            }
            throw new UnsupportedOperationException("Method not supported in test ResultSet: " + methodName);
        };
        return (ResultSet) Proxy.newProxyInstance(
                DaoSchemaAlignmentTest.class.getClassLoader(),
                new Class[]{ResultSet.class},
                handler);
    }
}

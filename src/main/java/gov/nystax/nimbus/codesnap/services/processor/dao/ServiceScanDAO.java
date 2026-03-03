package gov.nystax.nimbus.codesnap.services.processor.dao;

import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceScanRecord;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Data Access Object for SERVICE_SCAN table operations.
 * Uses JDBC with prepared statements for DB2 compatibility.
 * 
 * <p>This class does not manage transactions - the caller is responsible
 * for transaction management.</p>
 */
public class ServiceScanDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(ServiceScanDAO.class);

    private static final String INSERT_SQL = """
            INSERT INTO FLOW.SERVICE_SCAN (
                SCAN_ID, SERVICE_ID, COMMIT_HASH, UI_SERVICE_IND, VERSION_TEXT,
                SERVICE_DEP_TEXT, SCAN_DATA_JSON, SCAN_TS, SCANNER_VER_NMBR, JENKINS_REQ_IND
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_SERVICE_AND_COMMIT_SQL = """
            SELECT SCAN_ID, SERVICE_ID, COMMIT_HASH, SCAN_TS,
                   UI_SERVICE_IND, VERSION_TEXT, SERVICE_DEP_TEXT, SCAN_DATA_JSON, SCANNER_VER_NMBR,
                   JENKINS_REQ_IND
            FROM FLOW.SERVICE_SCAN
            WHERE SERVICE_ID = ? AND COMMIT_HASH = ?
            """;

    private static final String SELECT_BY_SCAN_ID_SQL = """
            SELECT SCAN_ID, SERVICE_ID, COMMIT_HASH, SCAN_TS,
                   UI_SERVICE_IND, VERSION_TEXT, SERVICE_DEP_TEXT, SCAN_DATA_JSON, SCANNER_VER_NMBR,
                   JENKINS_REQ_IND
            FROM FLOW.SERVICE_SCAN
            WHERE SCAN_ID = ?
            """;

    private static final String EXISTS_BY_SERVICE_AND_COMMIT_SQL = """
            SELECT 1 FROM FLOW.SERVICE_SCAN
            WHERE SERVICE_ID = ? AND COMMIT_HASH = ?
            FETCH FIRST 1 ROWS ONLY
            """;

    private static final String DELETE_BY_SERVICE_AND_COMMIT_SQL = """
            DELETE FROM FLOW.SERVICE_SCAN
            WHERE SERVICE_ID = ? AND COMMIT_HASH = ?
            """;

    /**
     * Inserts a new service scan record into the database.
     *
     * @param connection the database connection (transaction managed by caller)
     * @param record the record to insert
     * @param scannerVersionNumber the scanner version to stamp on the record
     * @throws SQLException if a database error occurs
     * @throws IllegalArgumentException if required fields are missing
     */
    public void insert(Connection connection, ServiceScanRecord record, int scannerVersionNumber) throws SQLException {
        validateRecord(record);

        LOGGER.debug("Inserting ServiceScanRecord: serviceId={}, gitCommitHash={}",
                record.getServiceId(), record.getGitCommitHash());

        try (PreparedStatement stmt = connection.prepareStatement(INSERT_SQL)) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, record.getScanId());
            stmt.setString(paramIndex++, record.getServiceId());
            stmt.setString(paramIndex++, record.getGitCommitHash());
            stmt.setString(paramIndex++, record.getIsUiServiceDbValue());
            stmt.setString(paramIndex++, record.getVersion());
            stmt.setString(paramIndex++, record.getServiceDependencies());

            // Handle CLOB for scan data JSON
            String scanDataJson = record.getScanDataJson();
            if (scanDataJson != null) {
                Clob clob = connection.createClob();
                clob.setString(1, scanDataJson);
                stmt.setClob(paramIndex++, clob);
            } else {
                stmt.setNull(paramIndex++, java.sql.Types.CLOB);
            }
            stmt.setTimestamp(paramIndex++, record.getScanTimestamp());
            stmt.setInt(paramIndex++, scannerVersionNumber);
            record.setScannerVersionNumber(scannerVersionNumber);
            stmt.setString(paramIndex++, record.getJenkinsRequestDbValue());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException("Expected 1 row affected, but got " + rowsAffected);
            }

            LOGGER.info("Successfully inserted ServiceScanRecord: scanId={}",
                    record.getScanId());
        }
    }

    /**
     * Finds a service scan record by service ID and git commit hash.
     *
     * @param connection the database connection
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return Optional containing the record if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<ServiceScanRecord> findByServiceAndCommit(Connection connection,
                                                               String serviceId,
                                                               String gitCommitHash) throws SQLException {
        LOGGER.debug("Finding ServiceScanRecord: serviceId={}, gitCommitHash={}",
                serviceId, gitCommitHash);

        try (PreparedStatement stmt = connection.prepareStatement(SELECT_BY_SERVICE_AND_COMMIT_SQL)) {
            stmt.setString(1, serviceId);
            stmt.setString(2, gitCommitHash);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToRecord(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds a service scan record by scan ID.
     *
     * @param connection the database connection
     * @param scanId the unique scan ID
     * @return Optional containing the record if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<ServiceScanRecord> findByScanId(Connection connection, String scanId) throws SQLException {
        LOGGER.debug("Finding ServiceScanRecord by scanId={}", scanId);

        try (PreparedStatement stmt = connection.prepareStatement(SELECT_BY_SCAN_ID_SQL)) {
            stmt.setString(1, scanId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapResultSetToRecord(rs));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds multiple service scan records by their service ID and commit hash pairs.
     * This is the main method used during build time to load all relevant scans.
     *
     * @param connection the database connection
     * @param serviceCommitPairs list of service ID and commit hash pairs
     * @return list of found records (order not guaranteed)
     * @throws SQLException if a database error occurs
     */
    public List<ServiceScanRecord> findByServiceCommitPairs(Connection connection,
                                                             List<ServiceCommitPair> serviceCommitPairs) throws SQLException {
        if (serviceCommitPairs == null || serviceCommitPairs.isEmpty()) {
            return new ArrayList<>();
        }

        LOGGER.info("Finding {} ServiceScanRecords by service/commit pairs",
                serviceCommitPairs.size());

        // Build dynamic IN clause for composite key lookup
        // Note: DB2 supports row value expressions, but for compatibility we use individual queries
        // For large batches, consider using a temporary table approach
        
        List<ServiceScanRecord> results = new ArrayList<>();
        
        for (ServiceCommitPair pair : serviceCommitPairs) {
            Optional<ServiceScanRecord> record = findByServiceAndCommit(
                    connection, pair.serviceId(), pair.gitCommitHash());
            record.ifPresent(results::add);
        }

        LOGGER.info("Found {} of {} requested ServiceScanRecords",
                results.size(), serviceCommitPairs.size());

        return results;
    }

    /**
     * Checks if a service scan record exists for the given service ID and commit hash.
     *
     * @param connection the database connection
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return true if a record exists, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean existsByServiceAndCommit(Connection connection,
                                            String serviceId,
                                            String gitCommitHash) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(EXISTS_BY_SERVICE_AND_COMMIT_SQL)) {
            stmt.setString(1, serviceId);
            stmt.setString(2, gitCommitHash);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Deletes a service scan record by service ID and commit hash.
     * Used for re-scanning a service at the same commit.
     *
     * @param connection the database connection
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return true if a record was deleted, false if no matching record found
     * @throws SQLException if a database error occurs
     */
    public boolean deleteByServiceAndCommit(Connection connection,
                                            String serviceId,
                                            String gitCommitHash) throws SQLException {
        LOGGER.debug("Deleting ServiceScanRecord: serviceId={}, gitCommitHash={}",
                serviceId, gitCommitHash);

        try (PreparedStatement stmt = connection.prepareStatement(DELETE_BY_SERVICE_AND_COMMIT_SQL)) {
            stmt.setString(1, serviceId);
            stmt.setString(2, gitCommitHash);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.info("Deleted ServiceScanRecord: serviceId={}, gitCommitHash={}",
                        serviceId, gitCommitHash);
            }
            return rowsAffected > 0;
        }
    }

    /**
     * Maps a ResultSet row to a ServiceScanRecord.
     */
    private ServiceScanRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        ServiceScanRecord record = new ServiceScanRecord();

        record.setScanId(rs.getString("SCAN_ID"));
        record.setServiceId(rs.getString("SERVICE_ID"));
        record.setGitCommitHash(rs.getString("COMMIT_HASH"));
        record.setScanTimestamp(rs.getTimestamp("SCAN_TS"));
        record.setIsUiServiceFromDbValue(rs.getString("UI_SERVICE_IND"));
        record.setVersion(rs.getString("VERSION_TEXT"));
        record.setServiceDependencies(rs.getString("SERVICE_DEP_TEXT"));
        record.setScannerVersionNumber(rs.getInt("SCANNER_VER_NMBR"));
        record.setJenkinsRequestFromDbValue(rs.getString("JENKINS_REQ_IND"));

        // Handle CLOB
        Clob clob = rs.getClob("SCAN_DATA_JSON");
        if (clob != null) {
            record.setScanDataJson(clob.getSubString(1, (int) clob.length()));
        }

        return record;
    }

    /**
     * Validates that a record has all required fields for insertion.
     */
    private void validateRecord(ServiceScanRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("ServiceScanRecord cannot be null");
        }
        if (record.getScanId() == null || record.getScanId().isBlank()) {
            throw new IllegalArgumentException("Scan ID is required");
        }
        if (record.getServiceId() == null || record.getServiceId().isBlank()) {
            throw new IllegalArgumentException("Service ID is required");
        }
        if (record.getGitCommitHash() == null || record.getGitCommitHash().isBlank()) {
            throw new IllegalArgumentException("Git commit hash is required");
        }
        if (record.getScanTimestamp() == null) {
            throw new IllegalArgumentException("Scan timestamp is required");
        }
        if (record.getVersion() == null || record.getVersion().isBlank()) {
            throw new IllegalArgumentException("Version is required");
        }
    }

    /**
     * Record representing a service ID and git commit hash pair for batch lookups.
     */
    public record ServiceCommitPair(String serviceId, String gitCommitHash) {
        public ServiceCommitPair {
            if (serviceId == null || serviceId.isBlank()) {
                throw new IllegalArgumentException("Service ID cannot be null or blank");
            }
            if (gitCommitHash == null || gitCommitHash.isBlank()) {
                throw new IllegalArgumentException("Git commit hash cannot be null or blank");
            }
        }
    }
}

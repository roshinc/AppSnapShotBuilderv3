package gov.nystax.nimbus.codesnap.services.processor.dao;

import gov.nystax.nimbus.codesnap.services.processor.domain.FailedServiceScanRecord;

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
 * Data Access Object for FAILED_SERVICE_SCAN table operations.
 * Uses JDBC with prepared statements for DB2 compatibility.
 *
 * <p>This class does not manage transactions - the caller is responsible
 * for transaction management.</p>
 */
public class FailedServiceScanDAO {

    private static final Logger LOGGER = LoggerFactory.getLogger(FailedServiceScanDAO.class);

    private static final String INSERT_SQL = """
            INSERT INTO FLOW.FAILED_SERVICE_SCAN (
                SCAN_ID, SERVICE_ID, COMMIT_HASH, FAILED_TS,
                ERROR_TYPE, ERROR_MSG, STACK_TRACE, SCANNER_VER_NMBR, JENKINS_REQ_IND
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_SERVICE_AND_COMMIT_SQL = """
            SELECT SCAN_ID, SERVICE_ID, COMMIT_HASH, FAILED_TS,
                   ERROR_TYPE, ERROR_MSG, STACK_TRACE, SCANNER_VER_NMBR,
                   JENKINS_REQ_IND
            FROM FLOW.FAILED_SERVICE_SCAN
            WHERE SERVICE_ID = ? AND COMMIT_HASH = ?
            """;

    private static final String SELECT_BY_SCAN_ID_SQL = """
            SELECT SCAN_ID, SERVICE_ID, COMMIT_HASH, FAILED_TS,
                   ERROR_TYPE, ERROR_MSG, STACK_TRACE, SCANNER_VER_NMBR,
                   JENKINS_REQ_IND
            FROM FLOW.FAILED_SERVICE_SCAN
            WHERE SCAN_ID = ?
            """;

    private static final String EXISTS_BY_SERVICE_AND_COMMIT_SQL = """
            SELECT 1 FROM FLOW.FAILED_SERVICE_SCAN
            WHERE SERVICE_ID = ? AND COMMIT_HASH = ?
            FETCH FIRST 1 ROWS ONLY
            """;

    private static final String DELETE_BY_SERVICE_AND_COMMIT_SQL = """
            DELETE FROM FLOW.FAILED_SERVICE_SCAN
            WHERE SERVICE_ID = ? AND COMMIT_HASH = ?
            """;

    /**
     * Inserts a new failed service scan record into the database.
     *
     * @param connection the database connection (transaction managed by caller)
     * @param record     the record to insert
     * @param scannerVersionNumber the scanner version to stamp on the record
     * @throws SQLException             if a database error occurs
     * @throws IllegalArgumentException if required fields are missing
     */
    public void insert(Connection connection, FailedServiceScanRecord record, int scannerVersionNumber) throws SQLException {
        validateRecord(record);

        LOGGER.debug("Inserting FailedServiceScanRecord: serviceId={}, gitCommitHash={}",
                record.getServiceId(), record.getGitCommitHash());

        try (PreparedStatement stmt = connection.prepareStatement(INSERT_SQL)) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, record.getScanId());
            stmt.setString(paramIndex++, record.getServiceId());
            stmt.setString(paramIndex++, record.getGitCommitHash());
            stmt.setTimestamp(paramIndex++, record.getFailureTimestamp());
            stmt.setString(paramIndex++, record.getErrorType());
            stmt.setString(paramIndex++, record.getErrorMessage());

            // Handle CLOB for stack trace
            String stackTrace = record.getStackTrace();
            if (stackTrace != null) {
                Clob clob = connection.createClob();
                clob.setString(1, stackTrace);
                stmt.setClob(paramIndex++, clob);
            } else {
                stmt.setNull(paramIndex++, java.sql.Types.CLOB);
            }
            stmt.setInt(paramIndex++, scannerVersionNumber);
            record.setScannerVersionNumber(scannerVersionNumber);
            stmt.setString(paramIndex++, record.getJenkinsRequestDbValue());

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException("Expected 1 row affected, but got " + rowsAffected);
            }

            LOGGER.info("Successfully inserted FailedServiceScanRecord: scanId={}",
                    record.getScanId());
        }
    }

    /**
     * Finds a failed service scan record by service ID and git commit hash.
     *
     * @param connection    the database connection
     * @param serviceId     the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return Optional containing the record if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<FailedServiceScanRecord> findByServiceAndCommit(Connection connection,
                                                                     String serviceId,
                                                                     String gitCommitHash) throws SQLException {
        LOGGER.debug("Finding FailedServiceScanRecord: serviceId={}, gitCommitHash={}",
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
     * Finds a failed service scan record by scan ID.
     *
     * @param connection the database connection
     * @param scanId     the unique scan ID
     * @return Optional containing the record if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<FailedServiceScanRecord> findByScanId(Connection connection, String scanId) throws SQLException {
        LOGGER.debug("Finding FailedServiceScanRecord by scanId={}", scanId);

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
     * Finds multiple failed service scan records by their service ID and commit hash pairs.
     * This is used during build time to check for failed scans.
     *
     * @param connection         the database connection
     * @param serviceCommitPairs list of service ID and commit hash pairs
     * @return list of found failed records (order not guaranteed)
     * @throws SQLException if a database error occurs
     */
    public List<FailedServiceScanRecord> findByServiceCommitPairs(Connection connection,
                                                                   List<ServiceScanDAO.ServiceCommitPair> serviceCommitPairs) throws SQLException {
        if (serviceCommitPairs == null || serviceCommitPairs.isEmpty()) {
            return new ArrayList<>();
        }

        LOGGER.info("Finding failed scans for {} service/commit pairs",
                serviceCommitPairs.size());

        List<FailedServiceScanRecord> results = new ArrayList<>();

        for (ServiceScanDAO.ServiceCommitPair pair : serviceCommitPairs) {
            Optional<FailedServiceScanRecord> record = findByServiceAndCommit(
                    connection, pair.serviceId(), pair.gitCommitHash());
            record.ifPresent(results::add);
        }

        LOGGER.info("Found {} failed scans of {} requested",
                results.size(), serviceCommitPairs.size());

        return results;
    }

    /**
     * Checks if a failed service scan record exists for the given service ID and commit hash.
     *
     * @param connection    the database connection
     * @param serviceId     the service artifact ID
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
     * Deletes a failed service scan record by service ID and commit hash.
     * Used when re-scanning a service that previously failed.
     *
     * @param connection    the database connection
     * @param serviceId     the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return true if a record was deleted, false if no matching record found
     * @throws SQLException if a database error occurs
     */
    public boolean deleteByServiceAndCommit(Connection connection,
                                            String serviceId,
                                            String gitCommitHash) throws SQLException {
        LOGGER.debug("Deleting FailedServiceScanRecord: serviceId={}, gitCommitHash={}",
                serviceId, gitCommitHash);

        try (PreparedStatement stmt = connection.prepareStatement(DELETE_BY_SERVICE_AND_COMMIT_SQL)) {
            stmt.setString(1, serviceId);
            stmt.setString(2, gitCommitHash);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.info("Deleted FailedServiceScanRecord: serviceId={}, gitCommitHash={}",
                        serviceId, gitCommitHash);
            }
            return rowsAffected > 0;
        }
    }

    /**
     * Maps a ResultSet row to a FailedServiceScanRecord.
     */
    private FailedServiceScanRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        FailedServiceScanRecord record = new FailedServiceScanRecord();

        record.setScanId(rs.getString("SCAN_ID"));
        record.setServiceId(rs.getString("SERVICE_ID"));
        record.setGitCommitHash(rs.getString("COMMIT_HASH"));
        record.setFailureTimestamp(rs.getTimestamp("FAILED_TS"));
        record.setErrorType(rs.getString("ERROR_TYPE"));
        record.setErrorMessage(rs.getString("ERROR_MSG"));
        record.setScannerVersionNumber(rs.getInt("SCANNER_VER_NMBR"));
        record.setJenkinsRequestFromDbValue(rs.getString("JENKINS_REQ_IND"));

        // Handle CLOB
        Clob clob = rs.getClob("STACK_TRACE");
        if (clob != null) {
            record.setStackTrace(clob.getSubString(1, (int) clob.length()));
        }

        return record;
    }

    /**
     * Validates that a record has all required fields for insertion.
     */
    private void validateRecord(FailedServiceScanRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("FailedServiceScanRecord cannot be null");
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
        if (record.getFailureTimestamp() == null) {
            throw new IllegalArgumentException("Failure timestamp is required");
        }
        if (record.getErrorType() == null || record.getErrorType().isBlank()) {
            throw new IllegalArgumentException("Error type is required");
        }
    }
}

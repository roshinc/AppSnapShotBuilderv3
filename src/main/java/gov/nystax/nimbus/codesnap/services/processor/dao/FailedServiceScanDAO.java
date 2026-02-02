package gov.nystax.nimbus.codesnap.services.processor.dao;

import gov.nystax.nimbus.codesnap.services.processor.domain.FailedServiceScanRecord;

import java.sql.Clob;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for FAILED_SERVICE_SCAN table operations.
 * Uses JDBC with prepared statements for DB2 compatibility.
 *
 * <p>This class does not manage transactions - the caller is responsible
 * for transaction management.</p>
 */
public class FailedServiceScanDAO {

    private static final Logger LOGGER = Logger.getLogger(FailedServiceScanDAO.class.getName());

    private static final String INSERT_SQL = """
            INSERT INTO FAILED_SERVICE_SCAN (
                FAILURE_ID, SERVICE_ID, GIT_COMMIT_HASH, FAILURE_TIMESTAMP,
                GROUP_ID, VERSION, ERROR_TYPE, ERROR_MESSAGE, STACK_TRACE
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SELECT_BY_SERVICE_AND_COMMIT_SQL = """
            SELECT FAILURE_ID, SERVICE_ID, GIT_COMMIT_HASH, FAILURE_TIMESTAMP,
                   GROUP_ID, VERSION, ERROR_TYPE, ERROR_MESSAGE, STACK_TRACE
            FROM FAILED_SERVICE_SCAN
            WHERE SERVICE_ID = ? AND GIT_COMMIT_HASH = ?
            """;

    private static final String SELECT_BY_FAILURE_ID_SQL = """
            SELECT FAILURE_ID, SERVICE_ID, GIT_COMMIT_HASH, FAILURE_TIMESTAMP,
                   GROUP_ID, VERSION, ERROR_TYPE, ERROR_MESSAGE, STACK_TRACE
            FROM FAILED_SERVICE_SCAN
            WHERE FAILURE_ID = ?
            """;

    private static final String EXISTS_BY_SERVICE_AND_COMMIT_SQL = """
            SELECT 1 FROM FAILED_SERVICE_SCAN
            WHERE SERVICE_ID = ? AND GIT_COMMIT_HASH = ?
            FETCH FIRST 1 ROWS ONLY
            """;

    private static final String DELETE_BY_SERVICE_AND_COMMIT_SQL = """
            DELETE FROM FAILED_SERVICE_SCAN
            WHERE SERVICE_ID = ? AND GIT_COMMIT_HASH = ?
            """;

    private static final String SELECT_BY_SERVICE_COMMIT_PAIRS_SQL_PREFIX = """
            SELECT FAILURE_ID, SERVICE_ID, GIT_COMMIT_HASH, FAILURE_TIMESTAMP,
                   GROUP_ID, VERSION, ERROR_TYPE, ERROR_MESSAGE, STACK_TRACE
            FROM FAILED_SERVICE_SCAN
            WHERE (SERVICE_ID, GIT_COMMIT_HASH) IN (
            """;

    /**
     * Inserts a new failed service scan record into the database.
     *
     * @param connection the database connection (transaction managed by caller)
     * @param record     the record to insert
     * @throws SQLException             if a database error occurs
     * @throws IllegalArgumentException if required fields are missing
     */
    public void insert(Connection connection, FailedServiceScanRecord record) throws SQLException {
        validateRecord(record);

        LOGGER.log(Level.FINE, "Inserting FailedServiceScanRecord: serviceId={0}, gitCommitHash={1}",
                new Object[]{record.getServiceId(), record.getGitCommitHash()});

        try (PreparedStatement stmt = connection.prepareStatement(INSERT_SQL)) {
            int paramIndex = 1;
            stmt.setString(paramIndex++, record.getFailureId());
            stmt.setString(paramIndex++, record.getServiceId());
            stmt.setString(paramIndex++, record.getGitCommitHash());
            stmt.setTimestamp(paramIndex++, record.getFailureTimestamp());
            stmt.setString(paramIndex++, record.getGroupId());
            stmt.setString(paramIndex++, record.getVersion());
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

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException("Expected 1 row affected, but got " + rowsAffected);
            }

            LOGGER.log(Level.INFO, "Successfully inserted FailedServiceScanRecord: failureId={0}",
                    record.getFailureId());
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
        LOGGER.log(Level.FINE, "Finding FailedServiceScanRecord: serviceId={0}, gitCommitHash={1}",
                new Object[]{serviceId, gitCommitHash});

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
     * Finds a failed service scan record by failure ID.
     *
     * @param connection the database connection
     * @param failureId  the unique failure ID
     * @return Optional containing the record if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<FailedServiceScanRecord> findByFailureId(Connection connection, String failureId) throws SQLException {
        LOGGER.log(Level.FINE, "Finding FailedServiceScanRecord by failureId={0}", failureId);

        try (PreparedStatement stmt = connection.prepareStatement(SELECT_BY_FAILURE_ID_SQL)) {
            stmt.setString(1, failureId);

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

        LOGGER.log(Level.INFO, "Finding failed scans for {0} service/commit pairs",
                serviceCommitPairs.size());

        List<FailedServiceScanRecord> results = new ArrayList<>();

        for (ServiceScanDAO.ServiceCommitPair pair : serviceCommitPairs) {
            Optional<FailedServiceScanRecord> record = findByServiceAndCommit(
                    connection, pair.serviceId(), pair.gitCommitHash());
            record.ifPresent(results::add);
        }

        LOGGER.log(Level.INFO, "Found {0} failed scans of {1} requested",
                new Object[]{results.size(), serviceCommitPairs.size()});

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
        LOGGER.log(Level.FINE, "Deleting FailedServiceScanRecord: serviceId={0}, gitCommitHash={1}",
                new Object[]{serviceId, gitCommitHash});

        try (PreparedStatement stmt = connection.prepareStatement(DELETE_BY_SERVICE_AND_COMMIT_SQL)) {
            stmt.setString(1, serviceId);
            stmt.setString(2, gitCommitHash);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.log(Level.INFO, "Deleted FailedServiceScanRecord: serviceId={0}, gitCommitHash={1}",
                        new Object[]{serviceId, gitCommitHash});
            }
            return rowsAffected > 0;
        }
    }

    /**
     * Maps a ResultSet row to a FailedServiceScanRecord.
     */
    private FailedServiceScanRecord mapResultSetToRecord(ResultSet rs) throws SQLException {
        FailedServiceScanRecord record = new FailedServiceScanRecord();

        record.setFailureId(rs.getString("FAILURE_ID"));
        record.setServiceId(rs.getString("SERVICE_ID"));
        record.setGitCommitHash(rs.getString("GIT_COMMIT_HASH"));
        record.setFailureTimestamp(rs.getTimestamp("FAILURE_TIMESTAMP"));
        record.setGroupId(rs.getString("GROUP_ID"));
        record.setVersion(rs.getString("VERSION"));
        record.setErrorType(rs.getString("ERROR_TYPE"));
        record.setErrorMessage(rs.getString("ERROR_MESSAGE"));

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
        if (record.getFailureId() == null || record.getFailureId().isBlank()) {
            throw new IllegalArgumentException("Failure ID is required");
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

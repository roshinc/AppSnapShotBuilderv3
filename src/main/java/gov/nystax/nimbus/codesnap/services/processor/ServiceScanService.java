package gov.nystax.nimbus.codesnap.services.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nystax.nimbus.codesnap.services.processor.dao.FailedServiceScanDAO;
import gov.nystax.nimbus.codesnap.services.processor.dao.ServiceScanDAO;
import gov.nystax.nimbus.codesnap.services.processor.dao.ServiceScanDAO.ServiceCommitPair;
import gov.nystax.nimbus.codesnap.services.processor.domain.FailedServiceScanRecord;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceScanRecord;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class that orchestrates the scan processing workflow.
 * Provides high-level operations for:
 * <ul>
 *   <li>Processing and storing new service scans</li>
 *   <li>Recording and retrieving failed scans</li>
 *   <li>Retrieving scans for build-time tree construction</li>
 *   <li>Topological sorting of services by dependencies</li>
 * </ul>
 */
public class ServiceScanService {

    private static final Logger LOGGER = Logger.getLogger(ServiceScanService.class.getName());

    private final ServiceScanRecordFactory recordFactory;
    private final ServiceScanDAO serviceScanDAO;
    private final FailedServiceScanDAO failedServiceScanDAO;
    private final ObjectMapper objectMapper;

    public ServiceScanService() {
        this.recordFactory = new ServiceScanRecordFactory();
        this.serviceScanDAO = new ServiceScanDAO();
        this.failedServiceScanDAO = new FailedServiceScanDAO();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor for dependency injection.
     */
    public ServiceScanService(ServiceScanRecordFactory recordFactory,
                               ServiceScanDAO serviceScanDAO,
                               FailedServiceScanDAO failedServiceScanDAO,
                               ObjectMapper objectMapper) {
        this.recordFactory = recordFactory;
        this.serviceScanDAO = serviceScanDAO;
        this.failedServiceScanDAO = failedServiceScanDAO;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes a ProjectInfo scan and stores it in the database.
     * If a scan already exists for the service/commit pair, it will be replaced.
     *
     * @param connection the database connection (transaction managed by caller)
     * @param projectInfo the scanner output
     * @param gitCommitHash the git commit hash of the scanned code
     * @return the created ServiceScanRecord
     * @throws SQLException if a database error occurs
     * @throws ServiceScanRecordFactory.ScanDataProcessingException if processing fails
     */
    public ServiceScanRecord processAndStore(Connection connection,
                                              ProjectInfo projectInfo,
                                              String gitCommitHash) throws SQLException {
        LOGGER.log(Level.INFO, "Processing and storing scan for service: {0}, commit: {1}",
                new Object[]{projectInfo.getArtifactId(), gitCommitHash});

        // Check if a scan already exists
        boolean exists = serviceScanDAO.existsByServiceAndCommit(
                connection, projectInfo.getArtifactId(), gitCommitHash);

        if (exists) {
            LOGGER.log(Level.INFO, "Scan already exists for {0}@{1}, replacing",
                    new Object[]{projectInfo.getArtifactId(), gitCommitHash});
            serviceScanDAO.deleteByServiceAndCommit(
                    connection, projectInfo.getArtifactId(), gitCommitHash);
        }

        // Clear any previous failure record for this service/commit since scan is now successful
        if (failedServiceScanDAO.existsByServiceAndCommit(
                connection, projectInfo.getArtifactId(), gitCommitHash)) {
            LOGGER.log(Level.INFO, "Clearing previous failure record for {0}@{1}",
                    new Object[]{projectInfo.getArtifactId(), gitCommitHash});
            failedServiceScanDAO.deleteByServiceAndCommit(
                    connection, projectInfo.getArtifactId(), gitCommitHash);
        }

        // Create and store the new record
        ServiceScanRecord record = recordFactory.createRecord(projectInfo, gitCommitHash);
        serviceScanDAO.insert(connection, record);

        return record;
    }

    /**
     * Records a failed scan attempt in the database.
     * If a failure record already exists for the service/commit pair, it will be replaced.
     * Also clears any successful scan record for this service/commit pair.
     *
     * @param connection the database connection (transaction managed by caller)
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash of the scanned code
     * @param groupId the Maven group ID (may be null)
     * @param version the Maven version (may be null)
     * @param errorType the type of error (use constants from FailedServiceScanRecord.ErrorType)
     * @param errorMessage brief error message
     * @param exception the exception that caused the failure (may be null)
     * @return the created FailedServiceScanRecord
     * @throws SQLException if a database error occurs
     */
    public FailedServiceScanRecord recordFailure(Connection connection,
                                                  String serviceId,
                                                  String gitCommitHash,
                                                  String groupId,
                                                  String version,
                                                  String errorType,
                                                  String errorMessage,
                                                  Throwable exception) throws SQLException {
        LOGGER.log(Level.WARNING, "Recording scan failure for service: {0}, commit: {1}, error: {2}",
                new Object[]{serviceId, gitCommitHash, errorMessage});

        // Clear any existing successful scan for this service/commit
        serviceScanDAO.deleteByServiceAndCommit(connection, serviceId, gitCommitHash);

        // Clear any existing failure record
        if (failedServiceScanDAO.existsByServiceAndCommit(connection, serviceId, gitCommitHash)) {
            LOGGER.log(Level.INFO, "Failure record already exists for {0}@{1}, replacing",
                    new Object[]{serviceId, gitCommitHash});
            failedServiceScanDAO.deleteByServiceAndCommit(connection, serviceId, gitCommitHash);
        }

        // Create the failure record
        FailedServiceScanRecord record = FailedServiceScanRecord.builder()
                .failureId(UUID.randomUUID().toString())
                .serviceId(serviceId)
                .gitCommitHash(gitCommitHash)
                .failureTimestamp(new Timestamp(System.currentTimeMillis()))
                .groupId(groupId)
                .version(version)
                .errorType(errorType != null ? errorType : FailedServiceScanRecord.ErrorType.UNKNOWN)
                .errorMessage(errorMessage)
                .stackTrace(exception != null ? getStackTraceString(exception) : null)
                .build();

        failedServiceScanDAO.insert(connection, record);

        return record;
    }

    /**
     * Checks if a scan failure exists for the given service and commit.
     *
     * @param connection the database connection
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return true if a failure record exists, false otherwise
     * @throws SQLException if a database error occurs
     */
    public boolean hasFailedScan(Connection connection,
                                  String serviceId,
                                  String gitCommitHash) throws SQLException {
        return failedServiceScanDAO.existsByServiceAndCommit(connection, serviceId, gitCommitHash);
    }

    /**
     * Retrieves a failed scan record for the given service and commit.
     *
     * @param connection the database connection
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return Optional containing the failure record if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<FailedServiceScanRecord> getFailedScan(Connection connection,
                                                            String serviceId,
                                                            String gitCommitHash) throws SQLException {
        return failedServiceScanDAO.findByServiceAndCommit(connection, serviceId, gitCommitHash);
    }

    /**
     * Finds all failed scans from a list of service/commit pairs.
     * This is used during build time to identify which services cannot be built.
     *
     * @param connection the database connection
     * @param serviceCommits list of service ID and commit hash pairs to check
     * @return list of failed scan records for services that have failures
     * @throws SQLException if a database error occurs
     */
    public List<FailedServiceScanRecord> findFailedScans(Connection connection,
                                                          List<ServiceCommitPair> serviceCommits) throws SQLException {
        LOGGER.log(Level.INFO, "Checking for failed scans among {0} services", serviceCommits.size());
        return failedServiceScanDAO.findByServiceCommitPairs(connection, serviceCommits);
    }

    /**
     * Clears a failure record for a service/commit pair.
     * This should be called when a scan succeeds after previously failing.
     *
     * @param connection the database connection
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return true if a failure record was deleted, false if none existed
     * @throws SQLException if a database error occurs
     */
    public boolean clearFailure(Connection connection,
                                 String serviceId,
                                 String gitCommitHash) throws SQLException {
        LOGGER.log(Level.INFO, "Clearing failure record for service: {0}, commit: {1}",
                new Object[]{serviceId, gitCommitHash});
        return failedServiceScanDAO.deleteByServiceAndCommit(connection, serviceId, gitCommitHash);
    }

    /**
     * Converts an exception to a stack trace string.
     */
    private String getStackTraceString(Throwable exception) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        exception.printStackTrace(pw);
        return sw.toString();
    }

    /**
     * Retrieves scan data for multiple services by their service/commit pairs.
     * This is the main method used during build time.
     *
     * @param connection the database connection
     * @param serviceCommits list of service ID and commit hash pairs
     * @return map of service ID to parsed ScanData
     * @throws SQLException if a database error occurs
     * @throws ScanDataParseException if JSON parsing fails
     */
    public Map<String, ScanDataWithMetadata> loadScansForBuild(
            Connection connection,
            List<ServiceCommitPair> serviceCommits) throws SQLException {

        LOGGER.log(Level.INFO, "Loading {0} scans for build", serviceCommits.size());

        List<ServiceScanRecord> records = serviceScanDAO.findByServiceCommitPairs(
                connection, serviceCommits);

        // Check for missing scans
        Set<String> requestedKeys = new HashSet<>();
        for (ServiceCommitPair pair : serviceCommits) {
            requestedKeys.add(pair.serviceId() + "@" + pair.gitCommitHash());
        }

        Set<String> foundKeys = new HashSet<>();
        for (ServiceScanRecord record : records) {
            foundKeys.add(record.getServiceId() + "@" + record.getGitCommitHash());
        }

        Set<String> missingKeys = new HashSet<>(requestedKeys);
        missingKeys.removeAll(foundKeys);

        if (!missingKeys.isEmpty()) {
            LOGGER.log(Level.WARNING, "Missing scans for: {0}", missingKeys);
            throw new MissingScanException("Missing scans for services: " + missingKeys);
        }

        // Parse and return
        Map<String, ScanDataWithMetadata> result = new HashMap<>();
        for (ServiceScanRecord record : records) {
            ScanData scanData = parseScanDataJson(record.getScanDataJson());
            ScanDataWithMetadata metadata = new ScanDataWithMetadata(
                    record.getServiceId(),
                    record.getGitCommitHash(),
                    record.isUiService(),
                    record.getServiceDependencies(),
                    scanData
            );
            result.put(record.getServiceId(), metadata);
        }

        return result;
    }

    /**
     * Performs topological sort of services based on their dependencies.
     * Services with no dependencies come first, dependent services come after their dependencies.
     *
     * @param scansById map of service ID to scan metadata
     * @return list of service IDs in dependency order
     * @throws CyclicDependencyException if a dependency cycle is detected
     */
    public List<String> topologicalSort(Map<String, ScanDataWithMetadata> scansById) {
        LOGGER.log(Level.FINE, "Performing topological sort on {0} services", scansById.size());

        // Build adjacency list (service -> services it depends on)
        Map<String, Set<String>> dependencies = new HashMap<>();
        for (Map.Entry<String, ScanDataWithMetadata> entry : scansById.entrySet()) {
            String serviceId = entry.getKey();
            Set<String> deps = parseServiceDependencies(entry.getValue().serviceDependencies());

            // Filter to only include dependencies that are in the current build set
            Set<String> relevantDeps = new HashSet<>();
            for (String dep : deps) {
                if (scansById.containsKey(dep)) {
                    relevantDeps.add(dep);
                }
            }

            dependencies.put(serviceId, relevantDeps);
        }

        // Kahn's algorithm for topological sort
        List<String> sorted = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String serviceId : scansById.keySet()) {
            if (!visited.contains(serviceId)) {
                topologicalSortDFS(serviceId, dependencies, visited, visiting, sorted);
            }
        }

        LOGGER.log(Level.INFO, "Topological sort result: {0}", sorted);
        return sorted;
    }

    /**
     * DFS helper for topological sort.
     */
    private void topologicalSortDFS(String serviceId,
                                     Map<String, Set<String>> dependencies,
                                     Set<String> visited,
                                     Set<String> visiting,
                                     List<String> sorted) {
        if (visiting.contains(serviceId)) {
            throw new CyclicDependencyException(
                    "Cyclic dependency detected involving service: " + serviceId);
        }

        if (visited.contains(serviceId)) {
            return;
        }

        visiting.add(serviceId);

        Set<String> deps = dependencies.get(serviceId);
        if (deps != null) {
            for (String dep : deps) {
                topologicalSortDFS(dep, dependencies, visited, visiting, sorted);
            }
        }

        visiting.remove(serviceId);
        visited.add(serviceId);
        sorted.add(serviceId);
    }

    /**
     * Parses the SERVICE_DEPENDENCIES column value into a set of service IDs.
     *
     * @param serviceDependencies comma-separated service IDs (may be null)
     * @return set of service IDs
     */
    private Set<String> parseServiceDependencies(String serviceDependencies) {
        Set<String> result = new HashSet<>();
        if (serviceDependencies == null || serviceDependencies.isBlank()) {
            return result;
        }

        String[] parts = serviceDependencies.split(",");
        for (String part : parts) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                result.add(trimmed);
            }
        }

        return result;
    }

    /**
     * Parses the SCAN_DATA_JSON CLOB into a ScanData object.
     */
    private ScanData parseScanDataJson(String json) {
        if (json == null || json.isBlank()) {
            throw new ScanDataParseException("Scan data JSON is null or blank");
        }

        try {
            return objectMapper.readValue(json, ScanData.class);
        } catch (JsonProcessingException e) {
            throw new ScanDataParseException("Failed to parse scan data JSON", e);
        }
    }

    /**
     * Finds a service scan by service ID and commit hash, checking both successful
     * scans and failed scans tables.
     *
     * <p>The method first checks the SERVICE_SCAN table for successful scans. If not found,
     * it checks the FAILED_SERVICE_SCAN table. If not found in either, returns NotFound.
     *
     * @param connection the database connection
     * @param serviceId the service artifact ID
     * @param gitCommitHash the git commit hash
     * @return a ServiceScanResult indicating where the service was found (or not found)
     * @throws SQLException if a database error occurs
     */
    public ServiceScanResult findByServiceAndCommit(Connection connection,
                                                     String serviceId,
                                                     String gitCommitHash) throws SQLException {
        LOGGER.log(Level.FINE, "Finding service scan for {0}@{1}",
                new Object[]{serviceId, gitCommitHash});

        // First check for successful scan
        Optional<ServiceScanRecord> successfulScan = serviceScanDAO.findByServiceAndCommit(
                connection, serviceId, gitCommitHash);

        if (successfulScan.isPresent()) {
            LOGGER.log(Level.FINE, "Found successful scan for {0}@{1}",
                    new Object[]{serviceId, gitCommitHash});
            return new ServiceScanResult.SuccessfulScan(successfulScan.get());
        }

        // Check for failed scan
        Optional<FailedServiceScanRecord> failedScan = failedServiceScanDAO.findByServiceAndCommit(
                connection, serviceId, gitCommitHash);

        if (failedScan.isPresent()) {
            LOGGER.log(Level.FINE, "Found failed scan for {0}@{1}",
                    new Object[]{serviceId, gitCommitHash});
            return new ServiceScanResult.FailedScan(failedScan.get());
        }

        LOGGER.log(Level.FINE, "No scan found for {0}@{1}",
                new Object[]{serviceId, gitCommitHash});
        return new ServiceScanResult.NotFound(serviceId, gitCommitHash);
    }

    /**
     * Result type for findByServiceAndCommit operation.
     * Uses a sealed interface to represent three possible outcomes:
     * <ul>
     *   <li>{@link SuccessfulScan} - the service was found in the SERVICE_SCAN table</li>
     *   <li>{@link FailedScan} - the service was found in the FAILED_SERVICE_SCAN table</li>
     *   <li>{@link NotFound} - the service was not found in either table</li>
     * </ul>
     *
     * <p>Example usage with pattern matching:
     * <pre>{@code
     * ServiceScanResult result = service.findByServiceAndCommit(conn, "my-service", "abc123");
     * switch (result) {
     *     case ServiceScanResult.SuccessfulScan s -> System.out.println("Found successful scan: " + s.record().getScanId());
     *     case ServiceScanResult.FailedScan f -> System.out.println("Found failed scan: " + f.record().getErrorMessage());
     *     case ServiceScanResult.NotFound n -> System.out.println("Not found: " + n.serviceId() + "@" + n.gitCommitHash());
     * }
     * }</pre>
     */
    public sealed interface ServiceScanResult
            permits ServiceScanResult.SuccessfulScan,
                    ServiceScanResult.FailedScan,
                    ServiceScanResult.NotFound {

        /**
         * Represents a service found in the SERVICE_SCAN table (successful scan).
         */
        record SuccessfulScan(ServiceScanRecord record) implements ServiceScanResult {
            public SuccessfulScan {
                if (record == null) {
                    throw new IllegalArgumentException("ServiceScanRecord cannot be null");
                }
            }
        }

        /**
         * Represents a service found in the FAILED_SERVICE_SCAN table (failed scan).
         */
        record FailedScan(FailedServiceScanRecord record) implements ServiceScanResult {
            public FailedScan {
                if (record == null) {
                    throw new IllegalArgumentException("FailedServiceScanRecord cannot be null");
                }
            }
        }

        /**
         * Represents a service not found in either table.
         */
        record NotFound(String serviceId, String gitCommitHash) implements ServiceScanResult {
            public NotFound {
                if (serviceId == null || serviceId.isBlank()) {
                    throw new IllegalArgumentException("Service ID cannot be null or blank");
                }
                if (gitCommitHash == null || gitCommitHash.isBlank()) {
                    throw new IllegalArgumentException("Git commit hash cannot be null or blank");
                }
            }
        }

        /**
         * Returns true if this result represents a successful scan.
         */
        default boolean isSuccessful() {
            return this instanceof SuccessfulScan;
        }

        /**
         * Returns true if this result represents a failed scan.
         */
        default boolean isFailed() {
            return this instanceof FailedScan;
        }

        /**
         * Returns true if the service was not found in either table.
         */
        default boolean isNotFound() {
            return this instanceof NotFound;
        }

        /**
         * Returns true if the service was found (either successful or failed).
         */
        default boolean isFound() {
            return !isNotFound();
        }
    }

    /**
     * Container for scan data with associated metadata.
     */
    public record ScanDataWithMetadata(
            String serviceId,
            String gitCommitHash,
            boolean isUiService,
            String serviceDependencies,
            ScanData scanData
    ) {
        public ScanDataWithMetadata {
            if (serviceId == null || serviceId.isBlank()) {
                throw new IllegalArgumentException("Service ID cannot be null or blank");
            }
            if (scanData == null) {
                throw new IllegalArgumentException("ScanData cannot be null");
            }
        }
    }

    /**
     * Exception thrown when requested scans are not found in the database.
     */
    public static class MissingScanException extends RuntimeException {
        public MissingScanException(String message) {
            super(message);
        }
    }

    /**
     * Exception thrown when scan data JSON parsing fails.
     */
    public static class ScanDataParseException extends RuntimeException {
        public ScanDataParseException(String message) {
            super(message);
        }

        public ScanDataParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when a cyclic dependency is detected during topological sort.
     */
    public static class CyclicDependencyException extends RuntimeException {
        public CyclicDependencyException(String message) {
            super(message);
        }
    }
}

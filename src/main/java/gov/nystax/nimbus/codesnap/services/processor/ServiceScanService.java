package gov.nystax.nimbus.codesnap.services.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nystax.nimbus.codesnap.services.processor.dao.ServiceScanDAO;
import gov.nystax.nimbus.codesnap.services.processor.dao.ServiceScanDAO.ServiceCommitPair;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceScanRecord;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class that orchestrates the scan processing workflow.
 * Provides high-level operations for:
 * <ul>
 *   <li>Processing and storing new service scans</li>
 *   <li>Retrieving scans for build-time tree construction</li>
 *   <li>Topological sorting of services by dependencies</li>
 * </ul>
 */
public class ServiceScanService {

    private static final Logger LOGGER = Logger.getLogger(ServiceScanService.class.getName());

    private final ServiceScanRecordFactory recordFactory;
    private final ServiceScanDAO serviceScanDAO;
    private final ObjectMapper objectMapper;

    public ServiceScanService() {
        this.recordFactory = new ServiceScanRecordFactory();
        this.serviceScanDAO = new ServiceScanDAO();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor for dependency injection.
     */
    public ServiceScanService(ServiceScanRecordFactory recordFactory,
                               ServiceScanDAO serviceScanDAO,
                               ObjectMapper objectMapper) {
        this.recordFactory = recordFactory;
        this.serviceScanDAO = serviceScanDAO;
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

        // Create and store the new record
        ServiceScanRecord record = recordFactory.createRecord(projectInfo, gitCommitHash);
        serviceScanDAO.insert(connection, record);

        return record;
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

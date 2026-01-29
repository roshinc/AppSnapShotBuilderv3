package gov.nystax.nimbus.codesnap.services.processor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.nystax.nimbus.codesnap.services.processor.domain.ScanData;
import gov.nystax.nimbus.codesnap.services.processor.domain.ServiceScanRecord;
import gov.nystax.nimbus.codesnap.services.scanner.domain.ProjectInfo;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Factory class for creating ServiceScanRecord instances from ProjectInfo.
 * Handles all transformations including:
 * <ul>
 *   <li>UUID generation for scan ID</li>
 *   <li>Service dependency extraction from Maven coordinates</li>
 *   <li>ScanData processing and JSON serialization</li>
 * </ul>
 */
public class ServiceScanRecordFactory {

    private static final Logger LOGGER = Logger.getLogger(ServiceScanRecordFactory.class.getName());

    /**
     * Pattern to extract artifact ID from Maven dependency coordinates.
     * Matches: "gov.nystax.services:WT0004J:[1.0.0,)" -> captures "WT0004J"
     */
    private static final Pattern DEPENDENCY_ARTIFACT_PATTERN = 
            Pattern.compile("^[^:]+:([^:]+):");

    private final ScanDataProcessor scanDataProcessor;
    private final ObjectMapper objectMapper;

    public ServiceScanRecordFactory() {
        this.scanDataProcessor = new ScanDataProcessor();
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Constructor for dependency injection.
     *
     * @param scanDataProcessor the processor for transforming ProjectInfo to ScanData
     * @param objectMapper the Jackson ObjectMapper for JSON serialization
     */
    public ServiceScanRecordFactory(ScanDataProcessor scanDataProcessor, ObjectMapper objectMapper) {
        this.scanDataProcessor = scanDataProcessor;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a ServiceScanRecord from a ProjectInfo and git commit hash.
     *
     * @param projectInfo the scanner output
     * @param gitCommitHash the git commit hash of the scanned code
     * @return a fully populated ServiceScanRecord ready for database insertion
     * @throws ScanDataProcessingException if processing or serialization fails
     */
    public ServiceScanRecord createRecord(ProjectInfo projectInfo, String gitCommitHash) {
        if (projectInfo == null) {
            throw new IllegalArgumentException("ProjectInfo cannot be null");
        }
        if (gitCommitHash == null || gitCommitHash.isBlank()) {
            throw new IllegalArgumentException("Git commit hash cannot be null or blank");
        }

        LOGGER.log(Level.INFO, "Creating ServiceScanRecord for {0} at commit {1}",
                new Object[]{projectInfo.getArtifactId(), gitCommitHash});

        // Process the scan data
        ScanData scanData = scanDataProcessor.process(projectInfo);

        // Serialize to JSON
        String scanDataJson = serializeScanData(scanData);

        // Extract service dependencies
        String serviceDependencies = extractServiceDependencies(projectInfo.getServiceDependencies());

        // Build the record
        return ServiceScanRecord.builder()
                .scanId(UUID.randomUUID().toString())
                .serviceId(projectInfo.getArtifactId())
                .gitCommitHash(gitCommitHash)
                .scanTimestamp(Timestamp.from(Instant.now()))
                .isUiService(projectInfo.isUIService())
                .groupId(projectInfo.getGroupId())
                .version(projectInfo.getVersion())
                .serviceDependencies(serviceDependencies)
                .scanDataJson(scanDataJson)
                .build();
    }

    /**
     * Serializes ScanData to JSON string.
     *
     * @param scanData the scan data to serialize
     * @return JSON string representation
     * @throws ScanDataProcessingException if serialization fails
     */
    private String serializeScanData(ScanData scanData) {
        try {
            return objectMapper.writeValueAsString(scanData);
        } catch (JsonProcessingException e) {
            throw new ScanDataProcessingException("Failed to serialize ScanData to JSON", e);
        }
    }

    /**
     * Extracts service artifact IDs from Maven dependency coordinates and joins them
     * as a comma-separated string.
     *
     * <p>Example input: ["gov.nystax.services:WT0004J:[1.0.0,)", "gov.nystax.services:WT0019J:[1.0.0,)"]</p>
     * <p>Example output: "WT0004J,WT0019J"</p>
     *
     * @param serviceDependencies list of Maven dependency coordinates
     * @return comma-separated list of service artifact IDs, or null if empty
     */
    private String extractServiceDependencies(List<String> serviceDependencies) {
        if (serviceDependencies == null || serviceDependencies.isEmpty()) {
            return null;
        }

        List<String> serviceIds = new ArrayList<>();
        for (String dependency : serviceDependencies) {
            String serviceId = extractArtifactId(dependency);
            if (serviceId != null) {
                serviceIds.add(serviceId);
            }
        }

        if (serviceIds.isEmpty()) {
            return null;
        }

        return String.join(",", serviceIds);
    }

    /**
     * Extracts the artifact ID from a Maven dependency coordinate string.
     *
     * @param dependency Maven dependency coordinate (e.g., "gov.nystax.services:WT0004J:[1.0.0,)")
     * @return the artifact ID (e.g., "WT0004J"), or null if extraction fails
     */
    private String extractArtifactId(String dependency) {
        if (dependency == null || dependency.isBlank()) {
            return null;
        }

        Matcher matcher = DEPENDENCY_ARTIFACT_PATTERN.matcher(dependency);
        if (matcher.find()) {
            return matcher.group(1);
        }

        LOGGER.log(Level.WARNING, "Failed to extract artifact ID from dependency: {0}", dependency);
        return null;
    }

    /**
     * Exception thrown when scan data processing fails.
     */
    public static class ScanDataProcessingException extends RuntimeException {
        public ScanDataProcessingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

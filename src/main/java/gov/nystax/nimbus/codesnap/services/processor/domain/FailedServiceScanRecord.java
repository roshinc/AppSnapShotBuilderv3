package gov.nystax.nimbus.codesnap.services.processor.domain;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Database entity representing a row in the FAILED_SERVICE_SCAN table.
 * This class stores information about service scans that failed due to:
 * <ul>
 *   <li>Scanning logic errors</li>
 *   <li>Code violations in the scanned repository</li>
 *   <li>Parsing or processing failures</li>
 * </ul>
 *
 * <p>This information is used during the AppSnapshot build process to determine
 * if a complete build is possible or if the result will be incomplete.</p>
 */
public class FailedServiceScanRecord {

    private String failureId;
    private String serviceId;
    private String gitCommitHash;
    private Timestamp failureTimestamp;
    private String groupId;
    private String version;
    private String errorType;      // e.g., "SCAN_ERROR", "PARSE_ERROR", "CODE_VIOLATION"
    private String errorMessage;   // Brief error message
    private String stackTrace;     // Full stack trace for debugging (CLOB)

    public FailedServiceScanRecord() {
    }

    /**
     * Builder pattern for creating FailedServiceScanRecord instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getFailureId() {
        return failureId;
    }

    public void setFailureId(String failureId) {
        this.failureId = failureId;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

    public String getGitCommitHash() {
        return gitCommitHash;
    }

    public void setGitCommitHash(String gitCommitHash) {
        this.gitCommitHash = gitCommitHash;
    }

    public Timestamp getFailureTimestamp() {
        return failureTimestamp == null ? null : new Timestamp(failureTimestamp.getTime());
    }

    public void setFailureTimestamp(Timestamp failureTimestamp) {
        this.failureTimestamp = failureTimestamp == null ? null : new Timestamp(failureTimestamp.getTime());
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getStackTrace() {
        return stackTrace;
    }

    public void setStackTrace(String stackTrace) {
        this.stackTrace = stackTrace;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FailedServiceScanRecord that = (FailedServiceScanRecord) o;
        return Objects.equals(failureId, that.failureId) &&
                Objects.equals(serviceId, that.serviceId) &&
                Objects.equals(gitCommitHash, that.gitCommitHash) &&
                Objects.equals(failureTimestamp, that.failureTimestamp) &&
                Objects.equals(groupId, that.groupId) &&
                Objects.equals(version, that.version) &&
                Objects.equals(errorType, that.errorType) &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(stackTrace, that.stackTrace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(failureId, serviceId, gitCommitHash, failureTimestamp,
                groupId, version, errorType, errorMessage, stackTrace);
    }

    @Override
    public String toString() {
        return "FailedServiceScanRecord{" +
                "failureId='" + failureId + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", gitCommitHash='" + gitCommitHash + '\'' +
                ", failureTimestamp=" + failureTimestamp +
                ", groupId='" + groupId + '\'' +
                ", version='" + version + '\'' +
                ", errorType='" + errorType + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", stackTrace='" + (stackTrace != null ? "[" + stackTrace.length() + " chars]" : "null") + '\'' +
                '}';
    }

    /**
     * Builder for FailedServiceScanRecord.
     */
    public static class Builder {
        private final FailedServiceScanRecord record = new FailedServiceScanRecord();

        public Builder failureId(String failureId) {
            record.setFailureId(failureId);
            return this;
        }

        public Builder serviceId(String serviceId) {
            record.setServiceId(serviceId);
            return this;
        }

        public Builder gitCommitHash(String gitCommitHash) {
            record.setGitCommitHash(gitCommitHash);
            return this;
        }

        public Builder failureTimestamp(Timestamp failureTimestamp) {
            record.setFailureTimestamp(failureTimestamp);
            return this;
        }

        public Builder groupId(String groupId) {
            record.setGroupId(groupId);
            return this;
        }

        public Builder version(String version) {
            record.setVersion(version);
            return this;
        }

        public Builder errorType(String errorType) {
            record.setErrorType(errorType);
            return this;
        }

        public Builder errorMessage(String errorMessage) {
            record.setErrorMessage(errorMessage);
            return this;
        }

        public Builder stackTrace(String stackTrace) {
            record.setStackTrace(stackTrace);
            return this;
        }

        public FailedServiceScanRecord build() {
            return record;
        }
    }

    /**
     * Constants for common error types.
     */
    public static final class ErrorType {
        public static final String SCAN_ERROR = "SCAN_ERROR";
        public static final String PARSE_ERROR = "PARSE_ERROR";
        public static final String CODE_VIOLATION = "CODE_VIOLATION";
        public static final String PROCESSING_ERROR = "PROCESSING_ERROR";
        public static final String UNKNOWN = "UNKNOWN";

        private ErrorType() {
            // Prevent instantiation
        }
    }
}

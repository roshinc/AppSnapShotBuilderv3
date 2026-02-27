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

    private String scanId;
    private String serviceId;
    private String gitCommitHash;
    private Timestamp failureTimestamp;
    private String errorType;      // e.g., "SCAN_ERROR", "PARSE_ERROR", "CODE_VIOLATION"
    private String errorMessage;   // Brief error message
    private String stackTrace;     // Full stack trace for debugging (CLOB)
    private int scannerVersionNumber;

    public FailedServiceScanRecord() {
    }

    /**
     * Builder pattern for creating FailedServiceScanRecord instances.
     */
    public static Builder builder() {
        return new Builder();
    }

    public String getScanId() {
        return scanId;
    }

    public void setScanId(String scanId) {
        this.scanId = scanId;
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

    public int getScannerVersionNumber() {
        return scannerVersionNumber;
    }

    public void setScannerVersionNumber(int scannerVersionNumber) {
        this.scannerVersionNumber = scannerVersionNumber;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FailedServiceScanRecord that = (FailedServiceScanRecord) o;
        return scannerVersionNumber == that.scannerVersionNumber &&
                Objects.equals(scanId, that.scanId) &&
                Objects.equals(serviceId, that.serviceId) &&
                Objects.equals(gitCommitHash, that.gitCommitHash) &&
                Objects.equals(failureTimestamp, that.failureTimestamp) &&
                Objects.equals(errorType, that.errorType) &&
                Objects.equals(errorMessage, that.errorMessage) &&
                Objects.equals(stackTrace, that.stackTrace);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scanId, serviceId, gitCommitHash, failureTimestamp,
                errorType, errorMessage, stackTrace, scannerVersionNumber);
    }

    @Override
    public String toString() {
        return "FailedServiceScanRecord{" +
                "scanId='" + scanId + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", gitCommitHash='" + gitCommitHash + '\'' +
                ", failureTimestamp=" + failureTimestamp +
                ", errorType='" + errorType + '\'' +
                ", errorMessage='" + errorMessage + '\'' +
                ", scannerVersionNumber=" + scannerVersionNumber +
                ", stackTrace='" + (stackTrace != null ? "[" + stackTrace.length() + " chars]" : "null") + '\'' +
                '}';
    }

    /**
     * Builder for FailedServiceScanRecord.
     */
    public static class Builder {
        private final FailedServiceScanRecord record = new FailedServiceScanRecord();

        public Builder scanId(String scanId) {
            record.setScanId(scanId);
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

        public Builder scannerVersionNumber(int scannerVersionNumber) {
            record.setScannerVersionNumber(scannerVersionNumber);
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

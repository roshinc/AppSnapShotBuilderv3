package gov.nystax.nimbus.codesnap.services.processor.domain;

import java.sql.Timestamp;
import java.util.Objects;

/**
 * Database entity representing a row in the SERVICE_SCAN table.
 * This class is used for JDBC operations (insert/select) and does not use JPA.
 */
public class ServiceScanRecord {

    private String scanId;
    private String serviceId;
    private String gitCommitHash;
    private Timestamp scanTimestamp;
    private boolean isUiService;
    private String groupId;
    private String version;
    private String serviceDependencies;  // Comma-separated service IDs
    private String scanDataJson;         // CLOB content as String

    public ServiceScanRecord() {
    }

    /**
     * Builder pattern for creating ServiceScanRecord instances.
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

    public Timestamp getScanTimestamp() {
        return scanTimestamp == null ? null : new Timestamp(scanTimestamp.getTime());
    }

    public void setScanTimestamp(Timestamp scanTimestamp) {
        this.scanTimestamp = scanTimestamp == null ? null : new Timestamp(scanTimestamp.getTime());
    }

    public boolean isUiService() {
        return isUiService;
    }

    public void setUiService(boolean uiService) {
        isUiService = uiService;
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

    public String getServiceDependencies() {
        return serviceDependencies;
    }

    public void setServiceDependencies(String serviceDependencies) {
        this.serviceDependencies = serviceDependencies;
    }

    public String getScanDataJson() {
        return scanDataJson;
    }

    public void setScanDataJson(String scanDataJson) {
        this.scanDataJson = scanDataJson;
    }

    /**
     * Returns the IS_UI_SERVICE column value ('Y' or 'N').
     */
    public String getIsUiServiceDbValue() {
        return isUiService ? "Y" : "N";
    }

    /**
     * Sets the isUiService field from the database column value ('Y' or 'N').
     */
    public void setIsUiServiceFromDbValue(String dbValue) {
        this.isUiService = "Y".equalsIgnoreCase(dbValue);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ServiceScanRecord that = (ServiceScanRecord) o;
        return isUiService == that.isUiService &&
                Objects.equals(scanId, that.scanId) &&
                Objects.equals(serviceId, that.serviceId) &&
                Objects.equals(gitCommitHash, that.gitCommitHash) &&
                Objects.equals(scanTimestamp, that.scanTimestamp) &&
                Objects.equals(groupId, that.groupId) &&
                Objects.equals(version, that.version) &&
                Objects.equals(serviceDependencies, that.serviceDependencies) &&
                Objects.equals(scanDataJson, that.scanDataJson);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scanId, serviceId, gitCommitHash, scanTimestamp,
                isUiService, groupId, version, serviceDependencies, scanDataJson);
    }

    @Override
    public String toString() {
        return "ServiceScanRecord{" +
                "scanId='" + scanId + '\'' +
                ", serviceId='" + serviceId + '\'' +
                ", gitCommitHash='" + gitCommitHash + '\'' +
                ", scanTimestamp=" + scanTimestamp +
                ", isUiService=" + isUiService +
                ", groupId='" + groupId + '\'' +
                ", version='" + version + '\'' +
                ", serviceDependencies='" + serviceDependencies + '\'' +
                ", scanDataJson='" + (scanDataJson != null ? "[" + scanDataJson.length() + " chars]" : "null") + '\'' +
                '}';
    }

    /**
     * Builder for ServiceScanRecord.
     */
    public static class Builder {
        private final ServiceScanRecord record = new ServiceScanRecord();

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

        public Builder scanTimestamp(Timestamp scanTimestamp) {
            record.setScanTimestamp(scanTimestamp);
            return this;
        }

        public Builder isUiService(boolean isUiService) {
            record.setUiService(isUiService);
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

        public Builder serviceDependencies(String serviceDependencies) {
            record.setServiceDependencies(serviceDependencies);
            return this;
        }

        public Builder scanDataJson(String scanDataJson) {
            record.setScanDataJson(scanDataJson);
            return this;
        }

        public ServiceScanRecord build() {
            return record;
        }
    }
}

-- ============================================================================
-- SERVICE SCAN DATABASE SCHEMA (DB2)
-- Schema: FLOW
-- Tables: FLOW.SERVICE_SCAN, FLOW.FAILED_SERVICE_SCAN
-- ============================================================================

-- Create schema if it does not already exist.
-- Note: If your DB2 environment doesn't support IF NOT EXISTS, remove it and
-- create the schema once manually.
BEGIN
    DECLARE CONTINUE HANDLER FOR SQLSTATE '42710' BEGIN END; -- object exists
    EXECUTE IMMEDIATE 'CREATE SCHEMA FLOW';
END
@

-- Option A: set current schema for the session/script
SET CURRENT SCHEMA FLOW
@

-- ============================================================================
-- TABLE: SERVICE_SCAN
-- Stores scan data for each service at a specific commit.
-- ============================================================================

CREATE TABLE SERVICE_SCAN (
    SCAN_ID           VARCHAR(50)    NOT NULL,
    SERVICE_ID        VARCHAR(100)   NOT NULL,
    COMMIT_HASH       VARCHAR(64)    NOT NULL,
    UI_SERVICE_IND    CHAR(1)        NOT NULL,
    VERSION_TEXT      VARCHAR(50)    NOT NULL,
    JENKINS_REQ_IND   CHAR(1)        NOT NULL DEFAULT 'N',
    SERVICE_DEP_TEXT  VARCHAR(2000),
    QUEUE_DEP_JSON    VARCHAR(5000),
    SCAN_DATA_JSON    CLOB(10M),
    SCAN_TS           TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    SCANNER_VER_NMBR  INTEGER        NOT NULL DEFAULT 0,

    CONSTRAINT PK_SERVICE_SCAN
        PRIMARY KEY (SCAN_ID),

    CONSTRAINT CHK_SERVICE_SCAN_UI_IND
        CHECK (UI_SERVICE_IND IN ('Y', 'N')),

    CONSTRAINT CHK_SERVICE_SCAN_JENKINS_IND
        CHECK (JENKINS_REQ_IND IN ('Y', 'N'))
)
@

-- Common lookup: service + commit
CREATE INDEX IDX_SERVICE_SCAN_SERVICE_COMMIT
    ON SERVICE_SCAN (SERVICE_ID, COMMIT_HASH)
@

-- List scans by time
CREATE INDEX IDX_SERVICE_SCAN_TS
    ON SERVICE_SCAN (SCAN_TS DESC)
@

COMMENT ON TABLE SERVICE_SCAN IS
    'Stores scan metadata and scan payload per service/commit.'
@

COMMENT ON COLUMN SERVICE_SCAN.SCAN_ID IS
    'Primary key for this scan record (string id / UUID)'
@

COMMENT ON COLUMN SERVICE_SCAN.SERVICE_ID IS
    'Service identifier'
@

COMMENT ON COLUMN SERVICE_SCAN.COMMIT_HASH IS
    'Source control commit hash for the scan'
@

COMMENT ON COLUMN SERVICE_SCAN.UI_SERVICE_IND IS
    'Y/N indicator if service is UI'
@

COMMENT ON COLUMN SERVICE_SCAN.VERSION_TEXT IS
    'Version text for the scanned artifact'
@

COMMENT ON COLUMN SERVICE_SCAN.JENKINS_REQ_IND IS
    'Y/N indicator if Jenkins request is required'
@

COMMENT ON COLUMN SERVICE_SCAN.SERVICE_DEP_TEXT IS
    'Service dependency text (optional)'
@

COMMENT ON COLUMN SERVICE_SCAN.QUEUE_DEP_JSON IS
    'Queue dependency JSON (optional)'
@

COMMENT ON COLUMN SERVICE_SCAN.SCAN_DATA_JSON IS
    'Scan payload JSON (optional)'
@

COMMENT ON COLUMN SERVICE_SCAN.SCAN_TS IS
    'Timestamp when the scan was recorded'
@

COMMENT ON COLUMN SERVICE_SCAN.SCANNER_VER_NMBR IS
    'Scanner version number'
@

-- ============================================================================
-- TABLE: FAILED_SERVICE_SCAN
-- Stores failures for scans so downstream can detect missing/incomplete data.
-- ============================================================================

CREATE TABLE FAILED_SERVICE_SCAN (
    SCAN_ID           VARCHAR(50)    NOT NULL,
    SERVICE_ID        VARCHAR(100)   NOT NULL,
    COMMIT_HASH       VARCHAR(64)    NOT NULL,
    JENKINS_REQ_IND   CHAR(1)        NOT NULL DEFAULT 'N',
    ERROR_TYPE        VARCHAR(50)    NOT NULL,
    ERROR_MSG         VARCHAR(1000),
    STACK_TRACE       CLOB(1M),
    FAILED_TS         TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    SCANNER_VER_NMBR  INTEGER        NOT NULL DEFAULT 0,

    CONSTRAINT PK_FAILED_SERVICE_SCAN
        PRIMARY KEY (SCAN_ID),

    CONSTRAINT CHK_FAILED_SCAN_JENKINS_IND
        CHECK (JENKINS_REQ_IND IN ('Y', 'N'))
)
@

CREATE INDEX IDX_FAILED_SCAN_SERVICE_COMMIT
    ON FAILED_SERVICE_SCAN (SERVICE_ID, COMMIT_HASH)
@

CREATE INDEX IDX_FAILED_SCAN_ERROR_TYPE
    ON FAILED_SERVICE_SCAN (ERROR_TYPE)
@

CREATE INDEX IDX_FAILED_SCAN_TS
    ON FAILED_SERVICE_SCAN (FAILED_TS DESC)
@

COMMENT ON TABLE FAILED_SERVICE_SCAN IS
    'Stores scan failures for a service/commit.'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.SCAN_ID IS
    'Primary key for this failed scan record (string id / UUID)'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.SERVICE_ID IS
    'Service identifier'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.COMMIT_HASH IS
    'Source control commit hash for the failed scan'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.JENKINS_REQ_IND IS
    'Y/N indicator if Jenkins request is required'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.ERROR_TYPE IS
    'Failure classification'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.ERROR_MSG IS
    'Failure message (optional)'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.STACK_TRACE IS
    'Failure stack trace (optional)'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.FAILED_TS IS
    'Timestamp when the failure was recorded'
@

COMMENT ON COLUMN FAILED_SERVICE_SCAN.SCANNER_VER_NMBR IS
    'Scanner version number'
@

-- Notes:
-- 1) The script uses @ as a statement terminator because of the BEGIN...END block.
--    In CLP, run:   db2 -td@ -vf schema_flow_updated.sql
-- 2) If you prefer fully qualified names instead of SET CURRENT SCHEMA, remove
--    SET CURRENT SCHEMA and prefix objects with FLOW. (e.g., CREATE TABLE FLOW.SERVICE_SCAN ...)

-- ============================================================================
-- SERVICE SCAN DATABASE SCHEMA
-- DB2 DDL for Application Dependency Tree System
-- ============================================================================

-- ============================================================================
-- TABLE: SERVICE_SCAN
-- Stores pre-processed scan data for each service at a specific git commit.
-- One row per service scan with all scan data as JSON in CLOB.
-- ============================================================================

CREATE TABLE SERVICE_SCAN (
    SCAN_ID              VARCHAR(36)     NOT NULL,
    SERVICE_ID           VARCHAR(100)    NOT NULL,
    GIT_COMMIT_HASH      VARCHAR(64)     NOT NULL,
    SCAN_TIMESTAMP       TIMESTAMP       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    IS_UI_SERVICE        CHAR(1)         NOT NULL DEFAULT 'N',
    GROUP_ID             VARCHAR(200),
    VERSION              VARCHAR(50),
    SERVICE_DEPENDENCIES VARCHAR(2000),
    SCAN_DATA_JSON       CLOB(10M),
    
    CONSTRAINT PK_SERVICE_SCAN 
        PRIMARY KEY (SCAN_ID),
    
    CONSTRAINT UQ_SERVICE_COMMIT 
        UNIQUE (SERVICE_ID, GIT_COMMIT_HASH),
    
    CONSTRAINT CHK_UI_SERVICE 
        CHECK (IS_UI_SERVICE IN ('Y', 'N'))
);

-- Index for the primary lookup pattern: service ID + git commit hash
CREATE INDEX IDX_SERVICE_SCAN_LOOKUP 
    ON SERVICE_SCAN (SERVICE_ID, GIT_COMMIT_HASH);

-- Index for listing scans by service
CREATE INDEX IDX_SERVICE_SCAN_SERVICE 
    ON SERVICE_SCAN (SERVICE_ID, SCAN_TIMESTAMP DESC);

-- Comment on table and columns
COMMENT ON TABLE SERVICE_SCAN IS 
    'Stores pre-processed scan data for each service scan. Used by the build service to construct app dependency trees.';

COMMENT ON COLUMN SERVICE_SCAN.SCAN_ID IS 
    'UUID primary key for this scan record';

COMMENT ON COLUMN SERVICE_SCAN.SERVICE_ID IS 
    'Service artifact ID (e.g., "WTWAGESUMJ", "WT0004J")';

COMMENT ON COLUMN SERVICE_SCAN.GIT_COMMIT_HASH IS 
    'Git commit hash of the scanned source code';

COMMENT ON COLUMN SERVICE_SCAN.SCAN_TIMESTAMP IS 
    'Timestamp when the scan was performed';

COMMENT ON COLUMN SERVICE_SCAN.IS_UI_SERVICE IS 
    'Y if this is a UI service exposing UI service methods, N otherwise';

COMMENT ON COLUMN SERVICE_SCAN.GROUP_ID IS 
    'Maven group ID (e.g., "gov.nystax.services")';

COMMENT ON COLUMN SERVICE_SCAN.VERSION IS 
    'Maven version (e.g., "1.0.0")';

COMMENT ON COLUMN SERVICE_SCAN.SERVICE_DEPENDENCIES IS 
    'Comma-separated list of service artifact IDs this service depends on (e.g., "WT0004J,WT0019J")';

COMMENT ON COLUMN SERVICE_SCAN.SCAN_DATA_JSON IS 
    'Pre-processed scan data as JSON (entryPointChildren, publicMethodDependencies, etc.)';


-- ============================================================================
-- TABLE: QUEUE_MAPPING
-- Global mapping of queue names to their target functions or topics.
-- Used at build time to resolve queue names for async calls and topic publishes.
-- ============================================================================

CREATE TABLE QUEUE_MAPPING (
    QUEUE_NAME    VARCHAR(100)    NOT NULL,
    TARGET_TYPE   VARCHAR(10)     NOT NULL,
    TARGET_NAME   VARCHAR(200)    NOT NULL,
    
    CONSTRAINT PK_QUEUE_MAPPING 
        PRIMARY KEY (QUEUE_NAME),
    
    CONSTRAINT CHK_TARGET_TYPE 
        CHECK (TARGET_TYPE IN ('FUNCTION', 'TOPIC'))
);

-- Reverse lookup index: find queue by target function or topic name
CREATE INDEX IDX_QUEUE_BY_TARGET 
    ON QUEUE_MAPPING (TARGET_TYPE, TARGET_NAME);

-- Comment on table and columns
COMMENT ON TABLE QUEUE_MAPPING IS 
    'Global mapping of queue names to their target functions or topics. Used at build time for async resolution.';

COMMENT ON COLUMN QUEUE_MAPPING.QUEUE_NAME IS 
    'Queue name (e.g., "RPWTWR.PFQ")';

COMMENT ON COLUMN QUEUE_MAPPING.TARGET_TYPE IS 
    'Type of target: FUNCTION or TOPIC';

COMMENT ON COLUMN QUEUE_MAPPING.TARGET_NAME IS 
    'Name of the target function or topic (e.g., "processWTPayments" or "PaymentPosting")';


-- ============================================================================
-- SAMPLE DATA (for testing/reference)
-- ============================================================================

-- Sample queue mappings
INSERT INTO QUEUE_MAPPING (QUEUE_NAME, TARGET_TYPE, TARGET_NAME) VALUES
    ('RPWTWR.PFQ', 'FUNCTION', 'processWTPayments'),
    ('PAYMENT.POSTING.Q', 'TOPIC', 'PaymentPosting'),
    ('FIN.TRXN.REVERSAL.Q', 'TOPIC', 'FinancialTrxnReversal'),
    ('TRXN.HOLDS.Q', 'TOPIC', 'ReleaseIASTrxnHolds'),
    ('EXCEPTIONS.CREATE.Q', 'TOPIC', 'exceptionsCreateEvent'),
    ('EXCEPTIONS.EXPIRE.Q', 'TOPIC', 'exceptionsExpireEvent');


-- ============================================================================
-- GRANTS (adjust schema/role names as needed)
-- ============================================================================

-- Grant to application role
-- GRANT SELECT, INSERT, UPDATE, DELETE ON SERVICE_SCAN TO APP_ROLE;
-- GRANT SELECT, INSERT, UPDATE, DELETE ON QUEUE_MAPPING TO APP_ROLE;

-- Read-only role for monitoring/reporting
-- GRANT SELECT ON SERVICE_SCAN TO READONLY_ROLE;
-- GRANT SELECT ON QUEUE_MAPPING TO READONLY_ROLE;

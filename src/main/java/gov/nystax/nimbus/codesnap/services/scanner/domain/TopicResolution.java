package gov.nystax.nimbus.codesnap.services.scanner.domain;

/**
 * Enum representing the resolution status of a topic name in event publisher invocations.
 */
public enum TopicResolution {
    RESOLVED,
    UNKNOWN_VARIABLE,
    UNKNOWN_CONSTANT
}

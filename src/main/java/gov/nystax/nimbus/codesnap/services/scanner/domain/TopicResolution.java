package gov.nystax.nimbus.codesnap.services.scanner.domain;

/**
 * Represents the resolution status of an event publisher topic.
 */
public enum TopicResolution {
    /**
     * Topic was resolved to a concrete string value.
     */
    RESOLVED,

    /**
     * Topic is a variable reference that could not be resolved.
     */
    UNKNOWN_VARIABLE,

    /**
     * Topic is a complex expression that could not be resolved.
     */
    UNKNOWN_COMPLEX
}

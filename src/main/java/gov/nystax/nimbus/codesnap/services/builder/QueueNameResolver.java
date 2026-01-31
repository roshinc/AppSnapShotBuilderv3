package gov.nystax.nimbus.codesnap.services.builder;

import gov.nystax.nimbus.codesnap.services.processor.dao.QueueMappingDAO;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves queue names for functions and topics.
 * Uses the QUEUE_MAPPING table to look up queue names, with caching to avoid repeated queries.
 * Falls back to a generated default queue name if no mapping exists.
 */
public class QueueNameResolver {

    private static final Logger LOGGER = Logger.getLogger(QueueNameResolver.class.getName());

    private static final String DEFAULT_QUEUE_SUFFIX = "_queue";

    private final QueueMappingDAO queueMappingDAO;
    
    // Cache for resolved queue names
    private final Map<String, String> functionQueueCache;
    private final Map<String, String> topicQueueCache;

    public QueueNameResolver() {
        this.queueMappingDAO = new QueueMappingDAO();
        this.functionQueueCache = new HashMap<>();
        this.topicQueueCache = new HashMap<>();
    }

    public QueueNameResolver(QueueMappingDAO queueMappingDAO) {
        this.queueMappingDAO = queueMappingDAO;
        this.functionQueueCache = new HashMap<>();
        this.topicQueueCache = new HashMap<>();
    }

    /**
     * Resolves the queue name for an async function call.
     * Checks the database first, then falls back to a generated default.
     *
     * @param connection the database connection
     * @param functionName the function name
     * @return the resolved queue name
     */
    public String resolveForFunction(Connection connection, String functionName) {
        // Check cache first
        if (functionQueueCache.containsKey(functionName)) {
            return functionQueueCache.get(functionName);
        }

        String queueName;
        try {
            Optional<String> dbQueueName = queueMappingDAO.findQueueNameForFunction(connection, functionName);
            queueName = dbQueueName.orElseGet(() -> generateDefaultQueueName(functionName));
            
            if (dbQueueName.isEmpty()) {
                LOGGER.log(Level.FINE, "No queue mapping found for function {0}, using default: {1}",
                        new Object[]{functionName, queueName});
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error looking up queue for function " + functionName + 
                    ", using default", e);
            queueName = generateDefaultQueueName(functionName);
        }

        functionQueueCache.put(functionName, queueName);
        return queueName;
    }

    /**
     * Resolves the queue name for a topic publish.
     * Checks the database first, then falls back to a generated default.
     *
     * @param connection the database connection
     * @param topicName the topic name
     * @return the resolved queue name
     */
    public String resolveForTopic(Connection connection, String topicName) {
        // Check cache first
        if (topicQueueCache.containsKey(topicName)) {
            return topicQueueCache.get(topicName);
        }

        String queueName;
        try {
            Optional<String> dbQueueName = queueMappingDAO.findQueueNameForTopic(connection, topicName);
            queueName = dbQueueName.orElseGet(() -> generateDefaultQueueName(topicName));
            
            if (dbQueueName.isEmpty()) {
                LOGGER.log(Level.FINE, "No queue mapping found for topic {0}, using default: {1}",
                        new Object[]{topicName, queueName});
            }
        } catch (SQLException e) {
            LOGGER.log(Level.WARNING, "Error looking up queue for topic " + topicName + 
                    ", using default", e);
            queueName = generateDefaultQueueName(topicName);
        }

        topicQueueCache.put(topicName, queueName);
        return queueName;
    }

    /**
     * Pre-loads queue mappings for a batch of functions and topics to reduce database round trips.
     * Call this before the main build loop for better performance.
     *
     * @param connection the database connection
     * @param functionNames collection of function names to pre-load
     * @param topicNames collection of topic names to pre-load
     */
    public void preloadMappings(Connection connection, 
                                 Iterable<String> functionNames, 
                                 Iterable<String> topicNames) {
        LOGGER.log(Level.FINE, "Pre-loading queue mappings");

        // Pre-load function mappings
        for (String functionName : functionNames) {
            if (!functionQueueCache.containsKey(functionName)) {
                resolveForFunction(connection, functionName);
            }
        }

        // Pre-load topic mappings
        for (String topicName : topicNames) {
            if (!topicQueueCache.containsKey(topicName)) {
                resolveForTopic(connection, topicName);
            }
        }
    }

    /**
     * Clears the cache. Call this when starting a new build to ensure fresh data.
     */
    public void clearCache() {
        functionQueueCache.clear();
        topicQueueCache.clear();
    }

    /**
     * Generates a default queue name when no mapping exists.
     */
    private String generateDefaultQueueName(String targetName) {
        return targetName + DEFAULT_QUEUE_SUFFIX;
    }
}

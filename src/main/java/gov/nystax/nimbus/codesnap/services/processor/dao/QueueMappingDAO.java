package gov.nystax.nimbus.codesnap.services.processor.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data Access Object for QUEUE_MAPPING table operations.
 * This table stores global mappings from queue names to their target functions or topics.
 */
public class QueueMappingDAO {

    private static final Logger LOGGER = Logger.getLogger(QueueMappingDAO.class.getName());

    public static final String TARGET_TYPE_FUNCTION = "FUNCTION";
    public static final String TARGET_TYPE_TOPIC = "TOPIC";

    private static final String SELECT_BY_TARGET_SQL = """
            SELECT QUEUE_NAME FROM QUEUE_MAPPING
            WHERE TARGET_TYPE = ? AND TARGET_NAME = ?
            """;

    private static final String SELECT_BY_QUEUE_NAME_SQL = """
            SELECT QUEUE_NAME, TARGET_TYPE, TARGET_NAME FROM QUEUE_MAPPING
            WHERE QUEUE_NAME = ?
            """;

    private static final String INSERT_SQL = """
            INSERT INTO QUEUE_MAPPING (QUEUE_NAME, TARGET_TYPE, TARGET_NAME)
            VALUES (?, ?, ?)
            """;

    private static final String UPDATE_SQL = """
            UPDATE QUEUE_MAPPING
            SET TARGET_TYPE = ?, TARGET_NAME = ?
            WHERE QUEUE_NAME = ?
            """;

    private static final String DELETE_SQL = """
            DELETE FROM QUEUE_MAPPING WHERE QUEUE_NAME = ?
            """;

    /**
     * Finds the queue name for a given target (function or topic).
     *
     * @param connection the database connection
     * @param targetType the type of target (FUNCTION or TOPIC)
     * @param targetName the name of the target
     * @return Optional containing the queue name if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<String> findQueueNameByTarget(Connection connection,
                                                   String targetType,
                                                   String targetName) throws SQLException {
        validateTargetType(targetType);

        try (PreparedStatement stmt = connection.prepareStatement(SELECT_BY_TARGET_SQL)) {
            stmt.setString(1, targetType);
            stmt.setString(2, targetName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(rs.getString("QUEUE_NAME"));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Finds the queue name for a function.
     *
     * @param connection the database connection
     * @param functionName the function name
     * @return Optional containing the queue name if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<String> findQueueNameForFunction(Connection connection,
                                                      String functionName) throws SQLException {
        return findQueueNameByTarget(connection, TARGET_TYPE_FUNCTION, functionName);
    }

    /**
     * Finds the queue name for a topic.
     *
     * @param connection the database connection
     * @param topicName the topic name
     * @return Optional containing the queue name if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<String> findQueueNameForTopic(Connection connection,
                                                   String topicName) throws SQLException {
        return findQueueNameByTarget(connection, TARGET_TYPE_TOPIC, topicName);
    }

    /**
     * Finds a queue mapping by queue name.
     *
     * @param connection the database connection
     * @param queueName the queue name
     * @return Optional containing the mapping if found, empty otherwise
     * @throws SQLException if a database error occurs
     */
    public Optional<QueueMapping> findByQueueName(Connection connection,
                                                   String queueName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(SELECT_BY_QUEUE_NAME_SQL)) {
            stmt.setString(1, queueName);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(new QueueMapping(
                            rs.getString("QUEUE_NAME"),
                            rs.getString("TARGET_TYPE"),
                            rs.getString("TARGET_NAME")
                    ));
                }
                return Optional.empty();
            }
        }
    }

    /**
     * Inserts a new queue mapping.
     *
     * @param connection the database connection
     * @param queueName the queue name
     * @param targetType the type of target (FUNCTION or TOPIC)
     * @param targetName the name of the target
     * @throws SQLException if a database error occurs or the queue already exists
     */
    public void insert(Connection connection,
                       String queueName,
                       String targetType,
                       String targetName) throws SQLException {
        validateTargetType(targetType);
        validateNotBlank(queueName, "Queue name");
        validateNotBlank(targetName, "Target name");

        LOGGER.log(Level.FINE, "Inserting QueueMapping: queueName={0}, targetType={1}, targetName={2}",
                new Object[]{queueName, targetType, targetName});

        try (PreparedStatement stmt = connection.prepareStatement(INSERT_SQL)) {
            stmt.setString(1, queueName);
            stmt.setString(2, targetType);
            stmt.setString(3, targetName);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected != 1) {
                throw new SQLException("Expected 1 row affected, but got " + rowsAffected);
            }

            LOGGER.log(Level.INFO, "Inserted QueueMapping: queueName={0}", queueName);
        }
    }

    /**
     * Updates an existing queue mapping.
     *
     * @param connection the database connection
     * @param queueName the queue name to update
     * @param targetType the new type of target
     * @param targetName the new name of the target
     * @return true if a record was updated, false if no matching record found
     * @throws SQLException if a database error occurs
     */
    public boolean update(Connection connection,
                          String queueName,
                          String targetType,
                          String targetName) throws SQLException {
        validateTargetType(targetType);
        validateNotBlank(queueName, "Queue name");
        validateNotBlank(targetName, "Target name");

        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_SQL)) {
            stmt.setString(1, targetType);
            stmt.setString(2, targetName);
            stmt.setString(3, queueName);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.log(Level.INFO, "Updated QueueMapping: queueName={0}", queueName);
            }
            return rowsAffected > 0;
        }
    }

    /**
     * Inserts a queue mapping, or updates it if it already exists.
     *
     * @param connection the database connection
     * @param queueName the queue name
     * @param targetType the type of target (FUNCTION or TOPIC)
     * @param targetName the name of the target
     * @throws SQLException if a database error occurs
     */
    public void upsert(Connection connection,
                       String queueName,
                       String targetType,
                       String targetName) throws SQLException {
        Optional<QueueMapping> existing = findByQueueName(connection, queueName);
        if (existing.isPresent()) {
            update(connection, queueName, targetType, targetName);
        } else {
            insert(connection, queueName, targetType, targetName);
        }
    }

    /**
     * Deletes a queue mapping.
     *
     * @param connection the database connection
     * @param queueName the queue name to delete
     * @return true if a record was deleted, false if no matching record found
     * @throws SQLException if a database error occurs
     */
    public boolean delete(Connection connection, String queueName) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement(DELETE_SQL)) {
            stmt.setString(1, queueName);

            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                LOGGER.log(Level.INFO, "Deleted QueueMapping: queueName={0}", queueName);
            }
            return rowsAffected > 0;
        }
    }

    private void validateTargetType(String targetType) {
        if (!TARGET_TYPE_FUNCTION.equals(targetType) && !TARGET_TYPE_TOPIC.equals(targetType)) {
            throw new IllegalArgumentException(
                    "Target type must be '" + TARGET_TYPE_FUNCTION + "' or '" + TARGET_TYPE_TOPIC + "'");
        }
    }

    private void validateNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be null or blank");
        }
    }

    /**
     * Record representing a queue mapping entry.
     */
    public record QueueMapping(String queueName, String targetType, String targetName) {
        public QueueMapping {
            if (queueName == null || queueName.isBlank()) {
                throw new IllegalArgumentException("Queue name cannot be null or blank");
            }
            if (targetType == null || targetType.isBlank()) {
                throw new IllegalArgumentException("Target type cannot be null or blank");
            }
            if (targetName == null || targetName.isBlank()) {
                throw new IllegalArgumentException("Target name cannot be null or blank");
            }
        }

        public boolean isFunction() {
            return TARGET_TYPE_FUNCTION.equals(targetType);
        }

        public boolean isTopic() {
            return TARGET_TYPE_TOPIC.equals(targetType);
        }
    }
}

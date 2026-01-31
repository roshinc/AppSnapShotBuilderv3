package gov.nystax.nimbus.codesnap.services.builder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import gov.nystax.nimbus.codesnap.services.builder.domain.AppTemplateNode;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildRequest;
import gov.nystax.nimbus.codesnap.services.builder.domain.BuildResult;
import gov.nystax.nimbus.codesnap.services.builder.domain.FunctionPoolEntry;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service class for building app snapshots.
 * Provides JSON serialization of the build results for use by the frontend tree-builder.js.
 */
public class AppSnapshotService {

    private static final Logger LOGGER = Logger.getLogger(AppSnapshotService.class.getName());

    private final AppSnapshotBuilder builder;
    private final ObjectMapper objectMapper;

    public AppSnapshotService() {
        this.builder = new AppSnapshotBuilder();
        this.objectMapper = createObjectMapper();
    }

    public AppSnapshotService(AppSnapshotBuilder builder) {
        this.builder = builder;
        this.objectMapper = createObjectMapper();
    }

    public AppSnapshotService(AppSnapshotBuilder builder, ObjectMapper objectMapper) {
        this.builder = builder;
        this.objectMapper = objectMapper;
    }

    private ObjectMapper createObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        return mapper;
    }

    /**
     * Builds the app snapshot and returns the result object.
     *
     * @param connection the database connection
     * @param request the build request
     * @return the build result containing AppTemplate and FunctionPool
     * @throws SQLException if a database error occurs
     */
    public BuildResult build(Connection connection, BuildRequest request) throws SQLException {
        return builder.build(connection, request);
    }

    /**
     * Builds the app snapshot and returns the result as JSON strings.
     *
     * @param connection the database connection
     * @param request the build request
     * @return the build result as JSON
     * @throws SQLException if a database error occurs
     * @throws JsonSerializationException if JSON serialization fails
     */
    public BuildResultJson buildAsJson(Connection connection, BuildRequest request) throws SQLException {
        BuildResult result = builder.build(connection, request);
        return serializeToJson(result);
    }

    /**
     * Serializes a BuildResult to JSON strings.
     *
     * @param result the build result
     * @return JSON representation
     * @throws JsonSerializationException if serialization fails
     */
    public BuildResultJson serializeToJson(BuildResult result) {
        try {
            String appTemplateJson = objectMapper.writeValueAsString(result.getAppTemplate());
            String functionPoolJson = objectMapper.writeValueAsString(result.getFunctionPool());
            return new BuildResultJson(appTemplateJson, functionPoolJson);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize build result to JSON", e);
        }
    }

    /**
     * Serializes just the AppTemplate to JSON.
     *
     * @param appTemplate the app template node
     * @return JSON string
     * @throws JsonSerializationException if serialization fails
     */
    public String serializeAppTemplate(AppTemplateNode appTemplate) {
        try {
            return objectMapper.writeValueAsString(appTemplate);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize AppTemplate to JSON", e);
        }
    }

    /**
     * Serializes just the FunctionPool to JSON.
     *
     * @param functionPool the function pool
     * @return JSON string
     * @throws JsonSerializationException if serialization fails
     */
    public String serializeFunctionPool(Map<String, FunctionPoolEntry> functionPool) {
        try {
            return objectMapper.writeValueAsString(functionPool);
        } catch (JsonProcessingException e) {
            throw new JsonSerializationException("Failed to serialize FunctionPool to JSON", e);
        }
    }

    /**
     * Parses a BuildRequest from JSON.
     *
     * @param json the JSON string
     * @return the parsed request
     * @throws JsonParseException if parsing fails
     */
    public BuildRequest parseRequest(String json) {
        try {
            return objectMapper.readValue(json, BuildRequest.class);
        } catch (JsonProcessingException e) {
            throw new JsonParseException("Failed to parse BuildRequest from JSON", e);
        }
    }

    /**
     * Container for JSON-serialized build results.
     */
    public record BuildResultJson(String appTemplateJson, String functionPoolJson) {
        public BuildResultJson {
            if (appTemplateJson == null) {
                throw new IllegalArgumentException("appTemplateJson cannot be null");
            }
            if (functionPoolJson == null) {
                throw new IllegalArgumentException("functionPoolJson cannot be null");
            }
        }
    }

    /**
     * Exception thrown when JSON serialization fails.
     */
    public static class JsonSerializationException extends RuntimeException {
        public JsonSerializationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * Exception thrown when JSON parsing fails.
     */
    public static class JsonParseException extends RuntimeException {
        public JsonParseException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

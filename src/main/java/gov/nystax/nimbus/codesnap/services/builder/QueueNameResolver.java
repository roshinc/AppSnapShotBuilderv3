package gov.nystax.nimbus.codesnap.services.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves queue names for functions and topics through REST endpoints.
 * Uses in-memory caching and bounded retries with backoff to avoid overloading endpoints.
 */
public class QueueNameResolver {

    private static final Logger LOGGER = Logger.getLogger(QueueNameResolver.class.getName());

    private static final String DEFAULT_QUEUE_SUFFIX = "_queue";
    private static final String FUNCTION_ENDPOINT_SYSTEM_PROPERTY = "codesnap.queue.function.resolver.url";
    private static final String FUNCTION_ENDPOINT_ENV_VAR = "CODESNAP_QUEUE_FUNCTION_RESOLVER_URL";
    private static final String TOPIC_ENDPOINT_SYSTEM_PROPERTY = "codesnap.queue.topic.resolver.url";
    private static final String TOPIC_ENDPOINT_ENV_VAR = "CODESNAP_QUEUE_TOPIC_RESOLVER_URL";
    private static final String FUNCTION_PARAM_NAME = "functionName";
    private static final String TOPIC_PARAM_NAME = "topicName";
    private static final String QUEUE_NAME_KEY = "QUEUE.NAME";
    private static final int MAX_ENDPOINT_ATTEMPTS = 3;
    private static final long INITIAL_BACKOFF_MS = 200L;
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(2);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI functionResolverEndpoint;
    private final URI topicResolverEndpoint;

    private final Map<String, String> functionQueueCache;
    private final Map<String, String> topicQueueCache;

    public QueueNameResolver() {
        this(HttpClient.newBuilder().connectTimeout(HTTP_TIMEOUT).build(),
                resolveConfiguredEndpoint(FUNCTION_ENDPOINT_SYSTEM_PROPERTY, FUNCTION_ENDPOINT_ENV_VAR),
                resolveConfiguredEndpoint(TOPIC_ENDPOINT_SYSTEM_PROPERTY, TOPIC_ENDPOINT_ENV_VAR));
    }

    public QueueNameResolver(HttpClient httpClient, URI functionResolverEndpoint, URI topicResolverEndpoint) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.objectMapper = new ObjectMapper();
        this.functionResolverEndpoint = functionResolverEndpoint;
        this.topicResolverEndpoint = topicResolverEndpoint;
        this.functionQueueCache = new HashMap<>();
        this.topicQueueCache = new HashMap<>();
    }

    /**
     * Resolves the queue name for an async function call.
     *
     * @param connection unused, kept for interface compatibility
     * @param functionName the function name
     * @return resolved queue name, or generated default when unresolved
     */
    public String resolveForFunction(Connection connection, String functionName) {
        if (functionQueueCache.containsKey(functionName)) {
            return functionQueueCache.get(functionName);
        }

        String queueName = resolveFromEndpointWithRetry(
                functionResolverEndpoint, FUNCTION_PARAM_NAME, functionName, "function")
                .orElseGet(() -> generateDefaultQueueName(functionName));

        functionQueueCache.put(functionName, queueName);
        return queueName;
    }

    /**
     * Resolves the queue name for a topic publish.
     *
     * @param connection unused, kept for interface compatibility
     * @param topicName the topic name
     * @return resolved queue name, or generated default when unresolved
     */
    public String resolveForTopic(Connection connection, String topicName) {
        if (topicQueueCache.containsKey(topicName)) {
            return topicQueueCache.get(topicName);
        }

        String queueName = resolveFromEndpointWithRetry(
                topicResolverEndpoint, TOPIC_PARAM_NAME, topicName, "topic")
                .orElseGet(() -> generateDefaultQueueName(topicName));

        topicQueueCache.put(topicName, queueName);
        return queueName;
    }

    /**
     * Pre-loads queue names for a batch of functions and topics.
     */
    public void preloadMappings(Connection connection,
                                Iterable<String> functionNames,
                                Iterable<String> topicNames) {
        for (String functionName : functionNames) {
            if (!functionQueueCache.containsKey(functionName)) {
                resolveForFunction(connection, functionName);
            }
        }

        for (String topicName : topicNames) {
            if (!topicQueueCache.containsKey(topicName)) {
                resolveForTopic(connection, topicName);
            }
        }
    }

    /**
     * Clears in-memory queue name cache.
     */
    public void clearCache() {
        functionQueueCache.clear();
        topicQueueCache.clear();
    }

    private Optional<String> resolveFromEndpointWithRetry(URI endpoint,
                                                          String queryParamName,
                                                          String targetName,
                                                          String targetType) {
        if (endpoint == null) {
            LOGGER.log(Level.FINE,
                    "No {0} queue resolver endpoint configured for target {1}",
                    new Object[]{targetType, targetName});
            return Optional.empty();
        }

        for (int attempt = 1; attempt <= MAX_ENDPOINT_ATTEMPTS; attempt++) {
            EndpointLookupResult result = callResolverEndpoint(endpoint, queryParamName, targetName, targetType);
            if (result.queueName() != null) {
                return Optional.of(result.queueName());
            }

            if (!result.retryable() || attempt == MAX_ENDPOINT_ATTEMPTS) {
                break;
            }

            if (!sleepBeforeRetry(targetType, targetName, attempt)) {
                break;
            }
        }

        return Optional.empty();
    }

    private EndpointLookupResult callResolverEndpoint(URI endpoint,
                                                      String queryParamName,
                                                      String targetName,
                                                      String targetType) {
        try {
            URI requestUri = buildRequestUri(endpoint, queryParamName, targetName);
            HttpRequest request = HttpRequest.newBuilder(requestUri)
                    .GET()
                    .timeout(HTTP_TIMEOUT)
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                Optional<String> queueName = parseQueueName(response.body());
                if (queueName.isPresent()) {
                    return EndpointLookupResult.success(queueName.get());
                }
                LOGGER.log(Level.WARNING,
                        "Queue resolver response missing key {0} for {1} {2}",
                        new Object[]{QUEUE_NAME_KEY, targetType, targetName});
                return EndpointLookupResult.nonRetryableFailure();
            }

            if (statusCode == 429 || statusCode >= 500) {
                LOGGER.log(Level.WARNING,
                        "Transient {0} queue resolver status={1} for {2}",
                        new Object[]{targetType, statusCode, targetName});
                return EndpointLookupResult.retryableFailure();
            }

            LOGGER.log(Level.WARNING,
                    "Non-retryable {0} queue resolver status={1} for {2}",
                    new Object[]{targetType, statusCode, targetName});
            return EndpointLookupResult.nonRetryableFailure();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING,
                    "Error calling " + targetType + " queue resolver for " + targetName, e);
            return EndpointLookupResult.retryableFailure();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING,
                    "Interrupted while calling " + targetType + " queue resolver for " + targetName, e);
            return EndpointLookupResult.nonRetryableFailure();
        } catch (URISyntaxException e) {
            LOGGER.log(Level.WARNING,
                    "Invalid " + targetType + " queue resolver request URI for " + targetName, e);
            return EndpointLookupResult.nonRetryableFailure();
        }
    }

    private URI buildRequestUri(URI baseEndpoint, String paramName, String paramValue) throws URISyntaxException {
        String encodedValue = URLEncoder.encode(paramValue, StandardCharsets.UTF_8);
        String queryParam = paramName + "=" + encodedValue;
        String existingQuery = baseEndpoint.getQuery();
        String mergedQuery = (existingQuery == null || existingQuery.isBlank())
                ? queryParam
                : existingQuery + "&" + queryParam;

        return new URI(baseEndpoint.getScheme(),
                baseEndpoint.getAuthority(),
                baseEndpoint.getPath(),
                mergedQuery,
                baseEndpoint.getFragment());
    }

    private Optional<String> parseQueueName(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode queueNode = jsonNode.get(QUEUE_NAME_KEY);
            if (queueNode == null || queueNode.isNull()) {
                return Optional.empty();
            }

            String queueName = queueNode.asText();
            if (queueName == null || queueName.isBlank()) {
                return Optional.empty();
            }

            return Optional.of(queueName);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Invalid JSON from queue resolver endpoint", e);
            return Optional.empty();
        }
    }

    private boolean sleepBeforeRetry(String targetType, String targetName, int attempt) {
        long exponentialDelay = INITIAL_BACKOFF_MS * (1L << (attempt - 1));
        long jitterMs = ThreadLocalRandom.current().nextLong(50L);
        long totalDelayMs = exponentialDelay + jitterMs;

        LOGGER.log(Level.FINE,
                "Retrying {0} queue resolver for {1} in {2}ms (attempt {3}/{4})",
                new Object[]{targetType, targetName, totalDelayMs, attempt + 1, MAX_ENDPOINT_ATTEMPTS});

        try {
            Thread.sleep(totalDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Retry sleep interrupted for " + targetType + " " + targetName, e);
            return false;
        }
    }

    private static URI resolveConfiguredEndpoint(String systemPropertyName, String envVarName) {
        String endpoint = System.getProperty(systemPropertyName);
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = System.getenv(envVarName);
        }

        if (endpoint == null || endpoint.isBlank()) {
            return null;
        }

        try {
            return URI.create(endpoint.trim());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Ignoring invalid endpoint URL: " + endpoint, e);
            return null;
        }
    }

    private String generateDefaultQueueName(String targetName) {
        return targetName + DEFAULT_QUEUE_SUFFIX;
    }

    private record EndpointLookupResult(String queueName, boolean retryable) {
        private static EndpointLookupResult success(String queueName) {
            return new EndpointLookupResult(queueName, false);
        }

        private static EndpointLookupResult retryableFailure() {
            return new EndpointLookupResult(null, true);
        }

        private static EndpointLookupResult nonRetryableFailure() {
            return new EndpointLookupResult(null, false);
        }
    }
}

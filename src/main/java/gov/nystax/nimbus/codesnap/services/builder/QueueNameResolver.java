package gov.nystax.nimbus.codesnap.services.builder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Resolves queue names for functions and topics through REST endpoints.
 * Uses in-memory caching and bounded retries with backoff to avoid overloading endpoints.
 */
public class QueueNameResolver {

    private static final Logger LOGGER = Logger.getLogger(QueueNameResolver.class.getName());

    private static final String FUNCTION_QUEUE_NAME_KEY = "async_url";
    private static final String TOPIC_QUEUE_NAME_KEY = "MQ_QUEUE";

    private final String defaultQueueSuffix;
    private final String queuePrefixToRemove;
    private final int maxEndpointAttempts;
    private final long initialBackoffMs;
    private final Duration httpTimeout;

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final URI functionResolverEndpoint;
    private final URI topicResolverEndpoint;

    private final Cache<String, String> functionQueueCache;
    private final Cache<String, String> topicQueueCache;

    public QueueNameResolver() {
        Config config = ConfigProvider.getConfig();

        this.defaultQueueSuffix = config.getOptionalValue(
                "codesnap.queue.default.suffix", String.class).orElse("_queue");
        this.queuePrefixToRemove = config.getOptionalValue(
                "codesnap.queue.prefix.to.remove", String.class).orElse("OCP.DEV.");
        this.maxEndpointAttempts = config.getOptionalValue(
                "codesnap.queue.max.endpoint.attempts", Integer.class).orElse(3);
        this.initialBackoffMs = config.getOptionalValue(
                "codesnap.queue.initial.backoff.ms", Long.class).orElse(200L);
        int timeoutSeconds = config.getOptionalValue(
                "codesnap.queue.http.timeout.seconds", Integer.class).orElse(2);
        this.httpTimeout = Duration.ofSeconds(timeoutSeconds);

        this.httpClient = HttpClient.newBuilder().connectTimeout(this.httpTimeout).build();
        this.objectMapper = new ObjectMapper();
        this.functionResolverEndpoint = resolveEndpointFromConfig(config,
                "codesnap.queue.function.resolver.url");
        this.topicResolverEndpoint = resolveEndpointFromConfig(config,
                "codesnap.queue.topic.resolver.url");
        this.functionQueueCache = Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(10)).build();
        this.topicQueueCache = Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(10)).build();
    }

    public QueueNameResolver(HttpClient httpClient, URI functionResolverEndpoint, URI topicResolverEndpoint) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient cannot be null");
        this.objectMapper = new ObjectMapper();
        this.functionResolverEndpoint = functionResolverEndpoint;
        this.topicResolverEndpoint = topicResolverEndpoint;
        this.functionQueueCache = Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(10)).build();
        this.topicQueueCache = Caffeine.newBuilder().maximumSize(500).expireAfterWrite(Duration.ofMinutes(10)).build();

        this.defaultQueueSuffix = "_queue";
        this.queuePrefixToRemove = "OCP.DEV.";
        this.maxEndpointAttempts = 3;
        this.initialBackoffMs = 200L;
        this.httpTimeout = Duration.ofSeconds(2);
    }

    /**
     * Resolves the queue name for an async function call.
     *
     * @param functionName the function name
     * @return resolved queue name, or generated default when unresolved
     */
    public String resolveForFunction(String functionName) {
        String cacheKey = normalizeCacheKey(functionName);
        String cached = functionQueueCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        String queueName = resolveFromEndpointWithRetry(
                functionResolverEndpoint,
                functionName,
                "function",
                FUNCTION_QUEUE_NAME_KEY,
                HttpMethod.POST)
                .orElseGet(() -> generateDefaultQueueName(functionName));

        functionQueueCache.put(cacheKey, queueName);
        return queueName;
    }

    /**
     * Resolves the queue name for a topic publish.
     *
     * @param topicName the topic name
     * @return resolved queue name, or generated default when unresolved
     */
    public String resolveForTopic(String topicName) {
        String cacheKey = normalizeCacheKey(topicName);
        String cached = topicQueueCache.getIfPresent(cacheKey);
        if (cached != null) {
            return cached;
        }

        String queueName = resolveFromEndpointWithRetry(
                topicResolverEndpoint,
                topicName,
                "topic",
                TOPIC_QUEUE_NAME_KEY,
                HttpMethod.GET)
                .orElseGet(() -> generateDefaultQueueName(topicName));

        topicQueueCache.put(cacheKey, queueName);
        return queueName;
    }

    /**
     * Pre-loads queue names for a batch of functions and topics.
     */
    public void preloadMappings(Iterable<String> functionNames,
                                Iterable<String> topicNames) {
        for (String functionName : functionNames) {
            resolveForFunction(functionName);
        }

        for (String topicName : topicNames) {
            resolveForTopic(topicName);
        }
    }

    /**
     * Clears in-memory queue name cache.
     */
    public void clearCache() {
        functionQueueCache.invalidateAll();
        topicQueueCache.invalidateAll();
    }

    private Optional<String> resolveFromEndpointWithRetry(URI endpoint,
                                                          String targetName,
                                                          String targetType,
                                                          String queueNameKey,
                                                          HttpMethod httpMethod) {
        if (endpoint == null) {
            LOGGER.log(Level.FINE,
                    "No {0} queue resolver endpoint configured for target {1}",
                    new Object[]{targetType, targetName});
            return Optional.empty();
        }

        for (int attempt = 1; attempt <= maxEndpointAttempts; attempt++) {
            EndpointLookupResult result = callResolverEndpoint(
                    endpoint, targetName, targetType, queueNameKey, httpMethod);
            if (result.queueName() != null) {
                return Optional.of(result.queueName());
            }

            if (!result.retryable() || attempt == maxEndpointAttempts) {
                break;
            }

            if (!sleepBeforeRetry(targetType, targetName, attempt)) {
                break;
            }
        }

        return Optional.empty();
    }

    private EndpointLookupResult callResolverEndpoint(URI endpoint,
                                                      String targetName,
                                                      String targetType,
                                                      String queueNameKey,
                                                      HttpMethod httpMethod) {
        try {
            URI requestUri = buildRequestUri(endpoint, targetName);
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder(requestUri)
                    .timeout(httpTimeout);

            if (httpMethod == HttpMethod.POST) {
                requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
            } else {
                requestBuilder.GET();
            }

            HttpRequest request = requestBuilder.build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            int statusCode = response.statusCode();

            if (statusCode >= 200 && statusCode < 300) {
                Optional<String> queueName = parseQueueName(response.body(), queueNameKey);
                if (queueName.isPresent()) {
                    return EndpointLookupResult.success(normalizeResolvedQueueName(queueName.get()));
                }
                LOGGER.log(Level.WARNING,
                        "Queue resolver response missing key {0} for {1} {2}",
                        new Object[]{queueNameKey, targetType, targetName});
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

    private URI buildRequestUri(URI baseEndpoint, String targetName) throws URISyntaxException {
        String normalizedTarget = normalizeCacheKey(targetName);
        String encodedTarget = URLEncoder.encode(normalizedTarget, StandardCharsets.UTF_8).replace("+", "%20");

        String basePath = baseEndpoint.getPath();
        if (basePath == null) {
            basePath = "";
        }
        String normalizedBasePath = basePath.endsWith("/")
                ? basePath.substring(0, basePath.length() - 1)
                : basePath;
        String requestPath = normalizedBasePath + "/" + encodedTarget;

        return new URI(baseEndpoint.getScheme(),
                baseEndpoint.getAuthority(),
                requestPath,
                baseEndpoint.getQuery(),
                baseEndpoint.getFragment());
    }

    private Optional<String> parseQueueName(String responseBody, String queueNameKey) {
        if (responseBody == null || responseBody.isBlank()) {
            return Optional.empty();
        }

        try {
            JsonNode jsonNode = objectMapper.readTree(responseBody);
            JsonNode queueNode = jsonNode.get(queueNameKey);
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
        long exponentialDelay = initialBackoffMs * (1L << (attempt - 1));
        long jitterMs = ThreadLocalRandom.current().nextLong(50L);
        long totalDelayMs = exponentialDelay + jitterMs;

        LOGGER.log(Level.FINE,
                "Retrying {0} queue resolver for {1} in {2}ms (attempt {3}/{4})",
                new Object[]{targetType, targetName, totalDelayMs, attempt + 1, maxEndpointAttempts});

        try {
            Thread.sleep(totalDelayMs);
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LOGGER.log(Level.WARNING, "Retry sleep interrupted for " + targetType + " " + targetName, e);
            return false;
        }
    }

    private static URI resolveEndpointFromConfig(Config config, String propertyName) {
        Optional<String> endpoint = config.getOptionalValue(propertyName, String.class);
        if (endpoint.isEmpty() || endpoint.get().isBlank()) {
            return null;
        }

        try {
            return URI.create(endpoint.get().trim());
        } catch (IllegalArgumentException e) {
            LOGGER.log(Level.WARNING,
                    "Ignoring invalid endpoint URL for " + propertyName + ": " + endpoint.get(), e);
            return null;
        }
    }

    private String generateDefaultQueueName(String targetName) {
        return targetName + defaultQueueSuffix;
    }

    private String normalizeResolvedQueueName(String queueName) {
        String normalizedQueueName = queueName.trim();
        if (normalizedQueueName.regionMatches(true, 0, queuePrefixToRemove, 0, queuePrefixToRemove.length())) {
            return normalizedQueueName.substring(queuePrefixToRemove.length());
        }
        return normalizedQueueName;
    }

    private String normalizeCacheKey(String targetName) {
        if (targetName == null) {
            return "";
        }
        return targetName.toLowerCase(Locale.ROOT);
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

    private enum HttpMethod {
        GET,
        POST
    }
}

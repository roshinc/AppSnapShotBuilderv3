package gov.nystax.nimbus.codesnap.services.builder;

import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueueNameResolverTest {

    @Test
    void resolveForFunction_usesEndpointAndCache() {
        ScriptedHttpClient httpClient = new ScriptedHttpClient(List.of(
                new ScriptedHttpClient.ScriptedResponseData(200, "{\"async_url\":\"OCP.DEV.FUNC.Q\"}")
        ));

        QueueNameResolver resolver = new QueueNameResolver(
                httpClient,
                URI.create("http://resolver.local/function-queue"),
                URI.create("http://resolver.local/topic-queue")
        );

        String first = resolver.resolveForFunction(null, "myFunc");
        String second = resolver.resolveForFunction(null, "MYFUNC");

        assertEquals("FUNC.Q", first);
        assertEquals("FUNC.Q", second);
        assertEquals(1, httpClient.getCallCount(), "Expected cached second lookup");
        assertTrue(httpClient.getRequestedUris().get(0).toString().endsWith("/function-queue/myfunc"));
        assertEquals("POST", httpClient.getRequestedMethods().get(0));
    }

    @Test
    void resolveForTopic_usesTopicEndpoint() {
        ScriptedHttpClient httpClient = new ScriptedHttpClient(List.of(
                new ScriptedHttpClient.ScriptedResponseData(200, "{\"MQ_QUEUE\":\"OCP.DEV.TOPIC.Q\"}")
        ));

        QueueNameResolver resolver = new QueueNameResolver(
                httpClient,
                URI.create("http://resolver.local/function-queue"),
                URI.create("http://resolver.local/topic-queue")
        );

        String queueName = resolver.resolveForTopic(null, "PaymentPosting");

        assertEquals("TOPIC.Q", queueName);
        assertEquals(1, httpClient.getCallCount());
        assertTrue(httpClient.getRequestedUris().get(0).toString().endsWith("/topic-queue/paymentposting"));
        assertEquals("GET", httpClient.getRequestedMethods().get(0));
    }

    @Test
    void resolveForFunction_retriesOnTransientError() {
        ScriptedHttpClient httpClient = new ScriptedHttpClient(List.of(
                new ScriptedHttpClient.ScriptedResponseData(500, "{\"error\":\"temporary\"}"),
                new ScriptedHttpClient.ScriptedResponseData(503, "{\"error\":\"temporary\"}"),
                new ScriptedHttpClient.ScriptedResponseData(200, "{\"async_url\":\"RETRY.Q\"}")
        ));

        QueueNameResolver resolver = new QueueNameResolver(
                httpClient,
                URI.create("http://resolver.local/function-queue"),
                URI.create("http://resolver.local/topic-queue")
        );

        String queueName = resolver.resolveForFunction(null, "retryFunc");

        assertEquals("RETRY.Q", queueName);
        assertEquals(3, httpClient.getCallCount());
    }

    @Test
    void resolveForTopic_fallsBackToDefaultWhenEndpointFails() {
        ScriptedHttpClient httpClient = new ScriptedHttpClient(List.of(
                new ScriptedHttpClient.ScriptedResponseData(404, "{\"error\":\"not found\"}")
        ));

        QueueNameResolver resolver = new QueueNameResolver(
                httpClient,
                URI.create("http://resolver.local/function-queue"),
                URI.create("http://resolver.local/topic-queue")
        );

        String queueName = resolver.resolveForTopic(null, "UnknownTopic");

        assertEquals("UnknownTopic_queue", queueName);
        assertEquals(1, httpClient.getCallCount());
    }

    private static final class ScriptedHttpClient extends HttpClient {

        private final List<ScriptedResponseData> scriptedResponses;
        private final AtomicInteger callCounter;
        private final List<URI> requestedUris;
        private final List<String> requestedMethods;

        private ScriptedHttpClient(List<ScriptedResponseData> scriptedResponses) {
            this.scriptedResponses = new ArrayList<>(scriptedResponses);
            this.callCounter = new AtomicInteger(0);
            this.requestedUris = new ArrayList<>();
            this.requestedMethods = new ArrayList<>();
        }

        int getCallCount() {
            return callCounter.get();
        }

        List<URI> getRequestedUris() {
            return requestedUris;
        }

        List<String> getRequestedMethods() {
            return requestedMethods;
        }

        @Override
        public Optional<CookieHandler> cookieHandler() {
            return Optional.empty();
        }

        @Override
        public Optional<Duration> connectTimeout() {
            return Optional.empty();
        }

        @Override
        public Redirect followRedirects() {
            return Redirect.NEVER;
        }

        @Override
        public Optional<ProxySelector> proxy() {
            return Optional.empty();
        }

        @Override
        public javax.net.ssl.SSLContext sslContext() {
            return null;
        }

        @Override
        public javax.net.ssl.SSLParameters sslParameters() {
            return null;
        }

        @Override
        public Optional<Authenticator> authenticator() {
            return Optional.empty();
        }

        @Override
        public Version version() {
            return Version.HTTP_1_1;
        }

        @Override
        public Optional<Executor> executor() {
            return Optional.empty();
        }

        @Override
        public java.net.http.WebSocket.Builder newWebSocketBuilder() {
            throw new UnsupportedOperationException("newWebSocketBuilder not used in tests");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException {
            int index = callCounter.getAndIncrement();
            requestedUris.add(request.uri());
            requestedMethods.add(request.method());

            if (scriptedResponses.isEmpty()) {
                throw new IOException("No scripted responses configured");
            }

            ScriptedResponseData data = scriptedResponses.get(Math.min(index, scriptedResponses.size() - 1));
            return (HttpResponse<T>) new ScriptedHttpResponse(request, data.statusCode(), data.body());
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException("sendAsync not used in tests");
        }

        @Override
        public <T> CompletableFuture<HttpResponse<T>> sendAsync(
                HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException("sendAsync not used in tests");
        }

        private record ScriptedResponseData(int statusCode, String body) {
        }
    }

    private record ScriptedHttpResponse(HttpRequest request, int statusCode, String body)
            implements HttpResponse<String> {
        @Override
        public Optional<HttpResponse<String>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(java.util.Map.of(), (k, v) -> true);
        }

        @Override
        public URI uri() {
            return request.uri();
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }
    }
}

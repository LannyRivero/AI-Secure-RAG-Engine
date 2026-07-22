package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.lanny.ailab.rag.domain.exception.SourceResolutionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.ProxySelector;
import java.net.URI;
import java.net.Authenticator;
import java.net.CookieHandler;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for remote fetch safety controls.
 */
@Tag("unit")
class SourceHttpClientTest {

    @Test
    @DisplayName("Rejects non HTTP schemes")
    void rejects_non_http_schemes() {
        SourceHttpClient client = new SourceHttpClient(new FakeHttpClient(List.of()));

        assertThatThrownBy(() -> client.fetch("file:///etc/passwd", null, "text/plain", Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Remote source URI must use http or https");
    }

    @Test
    @DisplayName("Rejects loopback destinations to block SSRF")
    void rejects_loopback_destinations() {
        SourceHttpClient client = new SourceHttpClient(new FakeHttpClient(List.of()));

        assertThatThrownBy(() -> client.fetch("http://127.0.0.1:8080/private", null, "text/plain", Map.of()))
                .isInstanceOf(SourceResolutionException.class)
                .hasMessage("Remote source URI points to a restricted network destination");
    }

    @Test
    @DisplayName("Rejects oversized responses before buffering them entirely")
    void rejects_oversized_responses() {
        SourceHttpClient client = new SourceHttpClient(new FakeHttpClient(List.of(
                new FakeResponse(200, URI.create("https://example.com/huge"), "text/plain", 11L * 1024 * 1024,
                        new ByteArrayInputStream(new byte[0])))));

        assertThatThrownBy(() -> client.fetch("https://example.com/huge", null, "text/plain", Map.of()))
                .isInstanceOf(SourceResolutionException.class)
                .hasMessage("Remote source response exceeds the maximum supported size");
    }

    @Test
    @DisplayName("Rejects redirects to restricted destinations")
    void rejects_redirects_to_restricted_destinations() {
        SourceHttpClient client = new SourceHttpClient(new FakeHttpClient(List.of(
                new FakeResponse(302, URI.create("https://example.com/start"), "text/plain", 0,
                        new ByteArrayInputStream(new byte[0]), Map.of("Location", List.of("http://127.0.0.1/internal"))))));

        assertThatThrownBy(() -> client.fetch("https://example.com/start", null, "text/plain", Map.of()))
                .isInstanceOf(SourceResolutionException.class)
                .hasMessage("Remote source URI points to a restricted network destination");
    }

    @Test
    @DisplayName("Returns remote payloads when the destination is allowed")
    void returns_remote_payloads_when_destination_is_allowed() {
        SourceHttpClient client = new SourceHttpClient(new FakeHttpClient(List.of(
                new FakeResponse(200, URI.create("https://example.com/document"), "text/plain", 4,
                        new ByteArrayInputStream("test".getBytes())))));

        SourceHttpClient.SourceResponse response = client.fetch("https://example.com/document", null, "text/plain", Map.of());

        assertThat(new String(response.body())).isEqualTo("test");
        assertThat(response.contentType()).isEqualTo("text/plain");
    }

    private static final class FakeHttpClient extends HttpClient {

        private final List<FakeResponse> responses;
        private int index;

        private FakeHttpClient(List<FakeResponse> responses) {
            this.responses = responses;
        }

        @Override
        public <T> HttpResponse<T> send(HttpRequest request, HttpResponse.BodyHandler<T> responseBodyHandler) {
            @SuppressWarnings("unchecked")
            HttpResponse<T> response = (HttpResponse<T>) responses.get(index++);
            return response;
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> java.util.concurrent.CompletableFuture<HttpResponse<T>> sendAsync(HttpRequest request,
                HttpResponse.BodyHandler<T> responseBodyHandler,
                HttpResponse.PushPromiseHandler<T> pushPromiseHandler) {
            throw new UnsupportedOperationException();
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
    }

    private static final class FakeResponse implements HttpResponse<InputStream> {

        private final int statusCode;
        private final URI uri;
        private final String contentType;
        private final long contentLength;
        private final InputStream body;
        private final Map<String, List<String>> headersMap;

        private FakeResponse(int statusCode, URI uri, String contentType, long contentLength, InputStream body) {
            this(statusCode, uri, contentType, contentLength, body,
                    Map.of("Content-Type", List.of(contentType), "Content-Length", List.of(String.valueOf(contentLength))));
        }

        private FakeResponse(int statusCode, URI uri, String contentType, long contentLength, InputStream body,
                Map<String, List<String>> headersMap) {
            this.statusCode = statusCode;
            this.uri = uri;
            this.contentType = contentType;
            this.contentLength = contentLength;
            this.body = body;
            this.headersMap = headersMap;
        }

        @Override
        public int statusCode() {
            return statusCode;
        }

        @Override
        public HttpRequest request() {
            return null;
        }

        @Override
        public Optional<HttpResponse<InputStream>> previousResponse() {
            return Optional.empty();
        }

        @Override
        public HttpHeaders headers() {
            return HttpHeaders.of(headersMap, (name, value) -> true);
        }

        @Override
        public InputStream body() {
            return body;
        }

        @Override
        public Optional<SSLSession> sslSession() {
            return Optional.empty();
        }

        @Override
        public URI uri() {
            return uri;
        }

        @Override
        public HttpClient.Version version() {
            return HttpClient.Version.HTTP_1_1;
        }
    }
}

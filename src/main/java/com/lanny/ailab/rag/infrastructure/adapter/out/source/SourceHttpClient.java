package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.lanny.ailab.rag.domain.exception.SourceUnavailableException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * Small HTTP adapter for remote source fetches.
 */
@Component
class SourceHttpClient {

    private final HttpClient httpClient;

    SourceHttpClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    SourceHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    SourceResponse fetch(String uri, String accessToken, String accept, Map<String, String> extraHeaders) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofSeconds(30))
                    .GET();
            if (hasText(accept)) {
                builder.header("Accept", accept);
            }
            if (hasText(accessToken)) {
                builder.header("Authorization", "Bearer " + accessToken);
            }
            extraHeaders.forEach(builder::header);

            HttpResponse<byte[]> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofByteArray());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new SourceUnavailableException(
                        "Remote source request failed with status " + response.statusCode() + " for uri " + uri);
            }

            return new SourceResponse(
                    response.body(),
                    response.headers().firstValue("Content-Type").orElse("application/octet-stream"));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("Failed to fetch remote source: " + uri, ex);
        } catch (IOException ex) {
            throw new SourceUnavailableException("Failed to fetch remote source: " + uri, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record SourceResponse(byte[] body, String contentType) {
    }
}

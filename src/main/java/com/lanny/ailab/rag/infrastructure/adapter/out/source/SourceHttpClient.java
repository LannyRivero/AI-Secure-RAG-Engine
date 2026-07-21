package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.lanny.ailab.rag.domain.exception.SourceResolutionException;
import com.lanny.ailab.rag.domain.exception.SourceUnavailableException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
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

    private static final int MAX_REDIRECTS = 5;
    private static final int MAX_RESPONSE_BYTES = 10 * 1024 * 1024;

    private final HttpClient httpClient;

    SourceHttpClient() {
        this(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    }

    SourceHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    SourceResponse fetch(String uri, String accessToken, String accept, Map<String, String> extraHeaders) {
        try {
            URI currentUri = validateExternalUri(URI.create(uri));

            for (int redirectCount = 0; redirectCount <= MAX_REDIRECTS; redirectCount++) {
                HttpResponse<InputStream> response = httpClient.send(
                        buildRequest(currentUri, accessToken, accept, extraHeaders),
                        HttpResponse.BodyHandlers.ofInputStream());

                if (isRedirect(response.statusCode())) {
                    String location = response.headers().firstValue("Location").orElse(null);
                    if (!hasText(location)) {
                        throw new SourceUnavailableException(
                                "Remote source redirect is missing Location header for uri " + currentUri);
                    }
                    currentUri = validateExternalUri(currentUri.resolve(location));
                    closeQuietly(response.body());
                    continue;
                }

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    closeQuietly(response.body());
                    throw new SourceUnavailableException(
                            "Remote source request failed with status " + response.statusCode() + " for uri " + currentUri);
                }

                String contentType = response.headers().firstValue("Content-Type").orElse("application/octet-stream");
                byte[] body = readBodyWithLimit(response.body(), response.headers().firstValueAsLong("Content-Length").orElse(-1L));
                return new SourceResponse(body, contentType);
            }

            throw new SourceUnavailableException("Remote source exceeded the maximum redirect limit for uri " + uri);
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new SourceUnavailableException("Failed to fetch remote source: " + uri, ex);
        } catch (IOException ex) {
            throw new SourceUnavailableException("Failed to fetch remote source: " + uri, ex);
        }
    }

    private HttpRequest buildRequest(URI uri, String accessToken, String accept, Map<String, String> extraHeaders) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(30))
                .GET();
        if (hasText(accept)) {
            builder.header("Accept", accept);
        }
        if (hasText(accessToken)) {
            builder.header("Authorization", "Bearer " + accessToken);
        }
        extraHeaders.forEach(builder::header);
        return builder.build();
    }

    private URI validateExternalUri(URI uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("Remote source URI must use http or https");
        }

        String host = uri.getHost();
        if (!hasText(host)) {
            throw new IllegalArgumentException("Remote source URI must include a valid host");
        }

        try {
            for (InetAddress address : InetAddress.getAllByName(host)) {
                if (isRestricted(address)) {
                    throw new SourceResolutionException("Remote source URI points to a restricted network destination");
                }
            }
        } catch (IOException ex) {
            throw new SourceUnavailableException("Failed to resolve remote source host: " + host, ex);
        }

        return uri;
    }

    private boolean isRestricted(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return true;
        }

        if (address instanceof Inet6Address inet6Address) {
            byte firstByte = inet6Address.getAddress()[0];
            return (firstByte & (byte) 0xfe) == (byte) 0xfc;
        }

        return false;
    }

    private boolean isRedirect(int statusCode) {
        return statusCode == 301 || statusCode == 302 || statusCode == 303 || statusCode == 307 || statusCode == 308;
    }

    private byte[] readBodyWithLimit(InputStream bodyStream, long contentLength) throws IOException {
        try (InputStream input = bodyStream; ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (contentLength > MAX_RESPONSE_BYTES) {
                throw new SourceResolutionException("Remote source response exceeds the maximum supported size");
            }

            byte[] buffer = new byte[8192];
            int totalRead = 0;
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                totalRead += bytesRead;
                if (totalRead > MAX_RESPONSE_BYTES) {
                    throw new SourceResolutionException("Remote source response exceeds the maximum supported size");
                }
                output.write(buffer, 0, bytesRead);
            }
            return output.toByteArray();
        }
    }

    private void closeQuietly(InputStream inputStream) {
        try {
            inputStream.close();
        } catch (IOException ignored) {
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record SourceResponse(byte[] body, String contentType) {
    }
}

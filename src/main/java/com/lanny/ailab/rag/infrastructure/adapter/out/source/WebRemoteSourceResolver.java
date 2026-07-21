package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.lanny.ailab.rag.application.command.IngestionSourceCommand;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Resolves HTML and crawl-based remote sources.
 */
@Component
class WebRemoteSourceResolver {

    private static final int DEFAULT_CRAWL_MAX_PAGES = 5;

    private final SourceHttpClient sourceHttpClient;
    private final SourceTextExtractor sourceTextExtractor;

    WebRemoteSourceResolver(SourceHttpClient sourceHttpClient, SourceTextExtractor sourceTextExtractor) {
        this.sourceHttpClient = sourceHttpClient;
        this.sourceTextExtractor = sourceTextExtractor;
    }

    String resolveHtml(String inlineContent, IngestionSourceCommand source) {
        if (sourceTextExtractor.hasText(source.uri())) {
            SourceHttpClient.SourceResponse response = sourceHttpClient.fetch(
                    source.uri(), source.accessToken(), "text/html, text/plain;q=0.9", Map.of());
            return sourceTextExtractor.htmlToText(new String(response.body(), StandardCharsets.UTF_8));
        }
        return sourceTextExtractor.htmlToText(
                sourceTextExtractor.normalizePlainText(inlineContent, "content is required for HTML source when uri is absent"));
    }

    String resolveWebCrawl(IngestionSourceCommand source) {
        String rootUri = requireUri(source, "WEB_CRAWL source requires uri");
        int maxPages = source.maxPages() != null ? source.maxPages() : DEFAULT_CRAWL_MAX_PAGES;

        try {
            URI root = new URI(rootUri);
            String rootHost = root.getHost();
            if (!sourceTextExtractor.hasText(rootHost)) {
                throw new IllegalArgumentException("WEB_CRAWL source uri must contain a valid host");
            }

            Set<String> visited = new HashSet<>();
            ArrayDeque<String> queue = new ArrayDeque<>();
            StringBuilder builder = new StringBuilder();
            queue.add(root.toString());

            while (!queue.isEmpty() && visited.size() < maxPages) {
                String next = queue.removeFirst();
                if (!visited.add(next)) {
                    continue;
                }

                SourceHttpClient.SourceResponse response = sourceHttpClient.fetch(
                        next, source.accessToken(), "text/html, text/plain;q=0.9", Map.of());
                String html = new String(response.body(), StandardCharsets.UTF_8);
                Document document = Jsoup.parse(html, next);
                sourceTextExtractor.appendSeparated(builder, document.text());

                for (Element link : document.select("a[href]")) {
                    String candidate = link.absUrl("href");
                    if (!sourceTextExtractor.hasText(candidate)) {
                        continue;
                    }
                    URI candidateUri = new URI(candidate);
                    if (Objects.equals(rootHost, candidateUri.getHost()) && !visited.contains(candidate)) {
                        queue.addLast(candidate);
                    }
                }
            }

            return builder.toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("WEB_CRAWL source uri is invalid", ex);
        }
    }

    private String requireUri(IngestionSourceCommand source, String message) {
        if (!sourceTextExtractor.hasText(source.uri())) {
            throw new IllegalArgumentException(message);
        }
        return source.uri();
    }
}

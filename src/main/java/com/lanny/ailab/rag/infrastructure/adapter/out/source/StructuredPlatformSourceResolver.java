package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanny.ailab.rag.application.command.IngestionSourceCommand;
import com.lanny.ailab.rag.domain.exception.SourceResolutionException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Map;

/**
 * Resolves structured document platforms such as Google Drive, Confluence, and
 * Notion.
 */
@Component
class StructuredPlatformSourceResolver {

    private static final String NOTION_VERSION = "2022-06-28";

    private final ObjectMapper objectMapper;
    private final SourceHttpClient sourceHttpClient;
    private final SourceTextExtractor sourceTextExtractor;

    StructuredPlatformSourceResolver(
            ObjectMapper objectMapper,
            SourceHttpClient sourceHttpClient,
            SourceTextExtractor sourceTextExtractor) {
        this.objectMapper = objectMapper;
        this.sourceHttpClient = sourceHttpClient;
        this.sourceTextExtractor = sourceTextExtractor;
    }

    IngestionSourceCommand resolveGoogleDriveSource(IngestionSourceCommand source) {
        String uri = requireUri(source, "GOOGLE_DRIVE source requires uri");
        if (!uri.contains("google")) {
            return source;
        }

        String fileId = googleDriveFileId(uri);
        if (fileId == null) {
            return source;
        }

        String exportedUri = uri.contains("/document/d/")
                ? "https://www.googleapis.com/drive/v3/files/" + fileId + "/export?mimeType=text/plain"
                : "https://www.googleapis.com/drive/v3/files/" + fileId + "?alt=media";

        return new IngestionSourceCommand(
                source.type(),
                exportedUri,
                source.base64Content(),
                source.accessToken(),
                source.maxPages());
    }

    String resolveConfluence(IngestionSourceCommand source) {
        String uri = requireUri(source, "CONFLUENCE source requires uri");
        SourceHttpClient.SourceResponse response = sourceHttpClient.fetch(
                resolveConfluenceApiUri(uri), source.accessToken(), "application/json", Map.of());
        JsonNode root = parseJson(response.body(), "Unable to parse Confluence response");
        StringBuilder html = new StringBuilder();
        collectConfluenceHtml(root, html);
        return sourceTextExtractor.htmlToText(html.toString());
    }

    String resolveNotion(IngestionSourceCommand source) {
        if (!sourceTextExtractor.hasText(source.accessToken())) {
            throw new IllegalArgumentException("NOTION source requires accessToken");
        }

        String uri = requireUri(source, "NOTION source requires uri");
        StringBuilder builder = new StringBuilder();
        collectNotionBlocks(resolveNotionApiUri(uri), source.accessToken(), builder);
        return builder.toString();
    }

    private void collectNotionBlocks(String uri, String accessToken, StringBuilder builder) {
        String pageUri = uri;
        while (pageUri != null) {
            SourceHttpClient.SourceResponse response = sourceHttpClient.fetch(
                    pageUri, accessToken, "application/json", Map.of("Notion-Version", NOTION_VERSION));
            JsonNode root = parseJson(response.body(), "Unable to parse Notion response");
            JsonNode results = root.path("results");
            if (!results.isArray()) {
                throw new SourceResolutionException("Notion response does not contain a results array");
            }

            for (JsonNode block : results) {
                sourceTextExtractor.appendSeparated(builder, collectPlainText(block));
                if (block.path("has_children").asBoolean(false) && sourceTextExtractor.hasText(block.path("id").asText())) {
                    collectNotionBlocks(notionChildrenUri(uri, block.path("id").asText()), accessToken, builder);
                }
            }

            pageUri = root.path("has_more").asBoolean(false)
                    ? notionPageCursorUri(uri, root.path("next_cursor").asText())
                    : null;
        }
    }

    private void collectConfluenceHtml(JsonNode node, StringBuilder html) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            if (node.has("value") && node.get("value").isTextual()) {
                sourceTextExtractor.appendSeparated(html, node.get("value").asText());
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collectConfluenceHtml(field.getValue(), html);
            }
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                collectConfluenceHtml(item, html);
            }
        }
    }

    private String collectPlainText(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        collectPlainText(node, builder);
        return builder.toString();
    }

    private void collectPlainText(JsonNode node, StringBuilder builder) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return;
        }

        if (node.isObject()) {
            if (node.has("plain_text") && node.get("plain_text").isTextual()) {
                sourceTextExtractor.appendSeparated(builder, node.get("plain_text").asText());
            }

            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                collectPlainText(field.getValue(), builder);
            }
            return;
        }

        if (node.isArray()) {
            for (JsonNode item : node) {
                collectPlainText(item, builder);
            }
        }
    }

    private JsonNode parseJson(byte[] body, String message) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException ex) {
            throw new SourceResolutionException(message, ex);
        }
    }

    private String resolveConfluenceApiUri(String uri) {
        if (uri.contains("/rest/api/") || uri.contains("/wiki/api/v2/")) {
            return uri;
        }

        String pageId = confluencePageId(uri);
        if (pageId == null) {
            return uri;
        }

        int pathStart = uri.indexOf("/wiki/");
        String base = pathStart > 0 ? uri.substring(0, pathStart)
                : uri.substring(0, uri.indexOf('/', uri.indexOf("//") + 2));
        return base + "/wiki/rest/api/content/" + pageId + "?expand=body.storage";
    }

    private String resolveNotionApiUri(String uri) {
        if (uri.contains("/v1/blocks/")) {
            return uri;
        }

        String pageId = notionPageId(uri);
        if (pageId == null) {
            throw new IllegalArgumentException("NOTION source uri must be a Notion page URL or blocks API URL");
        }

        return "https://api.notion.com/v1/blocks/" + pageId + "/children?page_size=100";
    }

    private String notionChildrenUri(String currentUri, String blockId) {
        if (currentUri.contains("/v1/blocks/")) {
            int schemeEnd = currentUri.indexOf("//");
            int pathStart = currentUri.indexOf('/', schemeEnd + 2);
            String base = pathStart > 0 ? currentUri.substring(0, pathStart) : currentUri;
            return base + "/v1/blocks/" + blockId + "/children?page_size=100";
        }
        return "https://api.notion.com/v1/blocks/" + blockId + "/children?page_size=100";
    }

    private String notionPageCursorUri(String uri, String cursor) {
        if (!sourceTextExtractor.hasText(cursor)) {
            return null;
        }

        String separator = uri.contains("?") ? "&" : "?";
        return uri + separator + "start_cursor=" + URLEncoder.encode(cursor, StandardCharsets.UTF_8);
    }

    private String googleDriveFileId(String uri) {
        int marker = uri.indexOf("/d/");
        if (marker < 0) {
            return null;
        }

        String tail = uri.substring(marker + 3);
        int nextSlash = tail.indexOf('/');
        return nextSlash >= 0 ? tail.substring(0, nextSlash) : tail;
    }

    private String confluencePageId(String uri) {
        if (uri.contains("pageId=")) {
            String tail = uri.substring(uri.indexOf("pageId=") + 7);
            int ampersand = tail.indexOf('&');
            return ampersand >= 0 ? tail.substring(0, ampersand) : tail;
        }

        String marker = "/pages/";
        int index = uri.indexOf(marker);
        if (index < 0) {
            return null;
        }

        String tail = uri.substring(index + marker.length());
        int nextSlash = tail.indexOf('/');
        return nextSlash >= 0 ? tail.substring(0, nextSlash) : tail;
    }

    private String notionPageId(String uri) {
        String compact = uri.replace("-", "");
        for (int i = compact.length() - 32; i >= 0; i--) {
            String candidate = compact.substring(i, i + 32);
            if (candidate.matches("[0-9a-fA-F]{32}")) {
                return candidate.substring(0, 8) + "-"
                        + candidate.substring(8, 12) + "-"
                        + candidate.substring(12, 16) + "-"
                        + candidate.substring(16, 20) + "-"
                        + candidate.substring(20);
            }
        }
        return null;
    }

    private String requireUri(IngestionSourceCommand source, String message) {
        if (!sourceTextExtractor.hasText(source.uri())) {
            throw new IllegalArgumentException(message);
        }
        return source.uri();
    }
}

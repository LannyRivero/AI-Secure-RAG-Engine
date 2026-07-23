package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.command.IngestionSourceCommand;
import com.lanny.ailab.rag.domain.model.DocumentMetadata;
import com.lanny.ailab.rag.domain.model.SourceType;
import com.lanny.ailab.rag.domain.valueobject.TenantId;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for connector-based source resolution before durable enqueueing.
 */
@Tag("unit")
class HttpIngestionSourceResolverTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SourceTextExtractor sourceTextExtractor = new SourceTextExtractor();
    private final SourceHttpClient sourceHttpClient = new SourceHttpClient();
    private final WebRemoteSourceResolver webRemoteSourceResolver = new WebRemoteSourceResolver(
            sourceHttpClient,
            sourceTextExtractor);
    private final StructuredPlatformSourceResolver structuredPlatformSourceResolver = new StructuredPlatformSourceResolver(
            objectMapper,
            sourceHttpClient,
            sourceTextExtractor);
    private final RemoteStructuredSourceResolver remoteStructuredSourceResolver = new RemoteStructuredSourceResolver(
            webRemoteSourceResolver,
            structuredPlatformSourceResolver);
    private final HttpIngestionSourceResolver resolver = new HttpIngestionSourceResolver(
            sourceHttpClient,
            sourceTextExtractor,
            remoteStructuredSourceResolver);
    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("Resolves legacy raw text requests without a source descriptor")
    void resolves_legacy_raw_text_requests_without_source_descriptor() {
        var resolved = resolver.resolve(new IngestDocumentCommand(
                "doc-1",
                TenantId.from("org-test"),
                "hello knowledge base",
                null,
                DocumentMetadata.empty()));

        assertThat(resolved.sourceType()).isEqualTo(SourceType.RAW_TEXT);
        assertThat(resolved.content()).isEqualTo("hello knowledge base");
    }

    @Test
    @DisplayName("Extracts text from inline PDF payloads")
    void extracts_text_from_inline_pdf_payloads() throws IOException {
        var resolved = resolver.resolve(command(
                null,
                source(SourceType.PDF, null, base64(pdfBytes("PDF connector content")), null, null)));

        assertThat(resolved.sourceType()).isEqualTo(SourceType.PDF);
        assertThat(resolved.content()).contains("PDF connector content");
    }

    @Test
    @DisplayName("Extracts text from inline DOCX payloads")
    void extracts_text_from_inline_docx_payloads() throws IOException {
        var resolved = resolver.resolve(command(
                null,
                source(SourceType.DOCX, null, base64(docxBytes("DOCX connector content")), null, null)));

        assertThat(resolved.sourceType()).isEqualTo(SourceType.DOCX);
        assertThat(resolved.content()).contains("DOCX connector content");
    }

    @Test
    @DisplayName("Strips markup from inline HTML payloads")
    void strips_markup_from_inline_html_payloads() {
        var resolved = resolver.resolve(command(
                "<h1>Quarterly</h1><p>Revenue up</p>",
                source(SourceType.HTML, null, null, null, null)));

        assertThat(resolved.sourceType()).isEqualTo(SourceType.HTML);
        assertThat(resolved.content()).isEqualTo("Quarterly Revenue up");
    }

    @Test
    @DisplayName("Crawls multiple pages on the same host into one normalized payload")
    void crawls_multiple_pages_on_the_same_host() {
        HttpIngestionSourceResolver routedResolver = resolverWithRoutes(Map.of(
                "https://example.com/root",
                html("<html><body><p>Root page</p><a href=\"/child\">child</a><a href=\"https://outside.test\">external link</a></body></html>"),
                "https://example.com/child", html("<html><body><p>Child page</p></body></html>")));

        var resolved = routedResolver.resolve(command(
                null,
                source(SourceType.WEB_CRAWL, "https://example.com/root", null, null, 3)));

        assertThat(resolved.content()).contains("Root page");
        assertThat(resolved.content()).contains("Child page");
    }

    @Test
    @DisplayName("Fetches remote text objects for cloud storage connectors")
    void fetches_remote_text_objects_for_cloud_storage_connectors() {
        HttpIngestionSourceResolver routedResolver = resolverWithRoutes(Map.of(
                "https://example.com/object.txt", text("bucket knowledge object")));

        var resolved = routedResolver.resolve(command(
                null,
                source(SourceType.S3_OBJECT, "https://example.com/object.txt", null, null, null)));

        assertThat(resolved.sourceType()).isEqualTo(SourceType.S3_OBJECT);
        assertThat(resolved.content()).isEqualTo("bucket knowledge object");
    }

    @Test
    @DisplayName("Parses Confluence storage HTML from API responses")
    void parses_confluence_storage_html_from_api_responses() {
        HttpIngestionSourceResolver routedResolver = resolverWithRoutes(Map.of(
                "https://example.com/wiki/rest/api/content/123?expand=body.storage",
                json("""
                        {"body":{"storage":{"value":"<p>Confluence knowledge page</p>"}}}
                        """)));

        var resolved = routedResolver.resolve(command(
                null,
                source(SourceType.CONFLUENCE, "https://example.com/wiki/rest/api/content/123?expand=body.storage", null, "token-123",
                        null)));

        assertThat(resolved.sourceType()).isEqualTo(SourceType.CONFLUENCE);
        assertThat(resolved.content()).isEqualTo("Confluence knowledge page");
    }

    @Test
    @DisplayName("Parses Notion block text from API responses")
    void parses_notion_block_text_from_api_responses() {
        HttpIngestionSourceResolver routedResolver = resolverWithRoutes(Map.of(
                "https://example.com/v1/blocks/page-1/children?page_size=100",
                json("""
                        {
                          "has_more": true,
                          "next_cursor": "cursor-2",
                          "results": [
                            {
                              "id": "block-1",
                              "has_children": false,
                              "paragraph": {
                                "rich_text": [
                                  {"plain_text": "Notion connector page"}
                                ]
                              }
                            }
                          ]
                        }
                        """),
                "https://example.com/v1/blocks/page-1/children?page_size=100&start_cursor=cursor-2",
                json("""
                        {
                          "has_more": false,
                          "results": [
                            {
                              "id": "block-2",
                              "has_children": false,
                              "paragraph": {
                                "rich_text": [
                                  {"plain_text": "Notion second page"}
                                ]
                              }
                            }
                          ]
                        }
                        """)));

        var resolved = routedResolver.resolve(command(
                null,
                source(SourceType.NOTION, "https://example.com/v1/blocks/page-1/children?page_size=100", null, "secret", null)));

        assertThat(resolved.sourceType()).isEqualTo(SourceType.NOTION);
        assertThat(resolved.content()).contains("Notion connector page");
        assertThat(resolved.content()).contains("Notion second page");
    }

    @Test
    @DisplayName("Preserves the original Google Drive URI in source metadata")
    void preserves_original_google_drive_uri_in_source_metadata() {
        SourceHttpClient fakeHttpClient = new SourceHttpClient() {
            @Override
            SourceResponse fetch(String uri, String accessToken, String accept, Map<String, String> extraHeaders) {
                return new SourceResponse("drive body".getBytes(StandardCharsets.UTF_8), "text/plain");
            }
        };

        WebRemoteSourceResolver fakeWebResolver = new WebRemoteSourceResolver(fakeHttpClient, sourceTextExtractor);
        StructuredPlatformSourceResolver fakePlatformResolver = new StructuredPlatformSourceResolver(
                objectMapper,
                fakeHttpClient,
                sourceTextExtractor);
        RemoteStructuredSourceResolver fakeRemoteResolver = new RemoteStructuredSourceResolver(
                fakeWebResolver,
                fakePlatformResolver);
        HttpIngestionSourceResolver fakeResolver = new HttpIngestionSourceResolver(
                fakeHttpClient,
                sourceTextExtractor,
                fakeRemoteResolver);

        String originalUri = "https://docs.google.test/document/d/abc123/edit";
        var resolved = fakeResolver.resolve(command(
                null,
                source(SourceType.GOOGLE_DRIVE, originalUri, null, "secret", null)));

        assertThat(resolved.sourceUri()).isEqualTo(originalUri);
    }

    @Test
    @DisplayName("Rejects RAW_TEXT sources when content is missing")
    void rejects_raw_text_sources_when_content_is_missing() {
        assertThatThrownBy(() -> resolver.resolve(command(
                null,
                source(SourceType.RAW_TEXT, null, null, null, null))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("content is required for RAW_TEXT source");
    }

    private IngestDocumentCommand command(String content, IngestionSourceCommand source) {
        return new IngestDocumentCommand("doc-1", TenantId.from("org-test"), content, source,
                DocumentMetadata.empty());
    }

    private HttpIngestionSourceResolver resolverWithRoutes(Map<String, ResponseSpec> routes) {
        SourceHttpClient routedHttpClient = new SourceHttpClient() {
            @Override
            SourceResponse fetch(String uri, String accessToken, String accept, Map<String, String> extraHeaders) {
                ResponseSpec response = routes.get(uri);
                if (response == null) {
                    throw new AssertionError("Unexpected fetch URI: " + uri);
                }
                return new SourceResponse(response.body().getBytes(StandardCharsets.UTF_8), response.contentType());
            }
        };

        WebRemoteSourceResolver webResolver = new WebRemoteSourceResolver(routedHttpClient, sourceTextExtractor);
        StructuredPlatformSourceResolver platformResolver = new StructuredPlatformSourceResolver(
                objectMapper,
                routedHttpClient,
                sourceTextExtractor);
        RemoteStructuredSourceResolver remoteResolver = new RemoteStructuredSourceResolver(webResolver, platformResolver);
        return new HttpIngestionSourceResolver(routedHttpClient, sourceTextExtractor, remoteResolver);
    }

    private IngestionSourceCommand source(SourceType type, String uri, String base64Content, String accessToken,
            Integer maxPages) {
        return new IngestionSourceCommand(type, uri, base64Content, accessToken, maxPages);
    }

    private void startServer(Map<String, ResponseSpec> responses) throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        for (Map.Entry<String, ResponseSpec> entry : responses.entrySet()) {
            server.createContext(entry.getKey(), exchange -> write(exchange, entry.getValue()));
        }
        server.start();
    }

    private void write(HttpExchange exchange, ResponseSpec response) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", response.contentType());
        byte[] body = response.body().getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(200, body.length);
        exchange.getResponseBody().write(body);
        exchange.close();
    }

    private String url(String pathAndQuery) {
        return "http://localhost:" + server.getAddress().getPort() + pathAndQuery;
    }

    private String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private byte[] pdfBytes(String text) throws IOException {
        try (PDDocument document = new PDDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.addPage(new PDPage());
            try (PDPageContentStream contentStream = new PDPageContentStream(document, document.getPage(0))) {
                contentStream.beginText();
                contentStream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                contentStream.newLineAtOffset(50, 700);
                contentStream.showText(text);
                contentStream.endText();
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] docxBytes(String text) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
                ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            XWPFParagraph paragraph = document.createParagraph();
            paragraph.createRun().setText(text);
            document.write(output);
            return output.toByteArray();
        }
    }

    private ResponseSpec html(String body) {
        return new ResponseSpec(body, "text/html");
    }

    private ResponseSpec text(String body) {
        return new ResponseSpec(body, "text/plain");
    }

    private ResponseSpec json(String body) {
        return new ResponseSpec(body, "application/json");
    }

    private record ResponseSpec(String body, String contentType) {
    }
}

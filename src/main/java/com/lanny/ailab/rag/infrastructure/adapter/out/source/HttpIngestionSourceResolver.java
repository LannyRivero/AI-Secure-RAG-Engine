package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.command.IngestionSourceCommand;
import com.lanny.ailab.rag.application.model.ResolvedIngestionSource;
import com.lanny.ailab.rag.application.port.out.IngestionSourceResolverPort;
import com.lanny.ailab.rag.domain.model.SourceType;
import org.springframework.stereotype.Component;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Infrastructure adapter that resolves connector-specific sources into
 * normalized text before the existing durable ingestion pipeline runs.
 */
@Component
public class HttpIngestionSourceResolver implements IngestionSourceResolverPort {

    private final SourceHttpClient sourceHttpClient;
    private final SourceTextExtractor sourceTextExtractor;
    private final RemoteStructuredSourceResolver remoteStructuredSourceResolver;

    public HttpIngestionSourceResolver(
            SourceHttpClient sourceHttpClient,
            SourceTextExtractor sourceTextExtractor,
            RemoteStructuredSourceResolver remoteStructuredSourceResolver) {
        this.sourceHttpClient = sourceHttpClient;
        this.sourceTextExtractor = sourceTextExtractor;
        this.remoteStructuredSourceResolver = remoteStructuredSourceResolver;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ResolvedIngestionSource resolve(IngestDocumentCommand command) {
        IngestionSourceCommand source = command.source();
        if (source == null) {
            return resolved(
                    sourceTextExtractor.normalizePlainText(command.content(),
                            "content is required when source is not provided"),
                    SourceType.RAW_TEXT, null, command);
        }

        return switch (source.type()) {
            case RAW_TEXT -> resolved(
                    sourceTextExtractor.normalizePlainText(command.content(),
                            "content is required for RAW_TEXT source"),
                    SourceType.RAW_TEXT,
                    source.uri(), command);
            case PDF -> resolved(sourceTextExtractor.extractPdf(loadBinarySource(command)), SourceType.PDF,
                    source.uri(), command);
            case DOCX -> resolved(sourceTextExtractor.extractDocx(loadBinarySource(command)), SourceType.DOCX,
                    source.uri(), command);
            case HTML -> resolved(remoteStructuredSourceResolver.resolveHtml(command.content(), source),
                    SourceType.HTML, source.uri(), command);
            case WEB_CRAWL -> resolved(remoteStructuredSourceResolver.resolveWebCrawl(source), SourceType.WEB_CRAWL,
                    source.uri(), command);
            case S3_OBJECT -> resolveDownloadedRemoteSource(command, source, SourceType.S3_OBJECT, source.uri());
            case AZURE_BLOB -> resolveDownloadedRemoteSource(command, source, SourceType.AZURE_BLOB, source.uri());
            case GOOGLE_DRIVE -> resolveDownloadedRemoteSource(
                    command,
                    remoteStructuredSourceResolver.resolveGoogleDriveSource(source),
                    SourceType.GOOGLE_DRIVE,
                    source.uri());
            case CONFLUENCE -> resolved(remoteStructuredSourceResolver.resolveConfluence(source), SourceType.CONFLUENCE,
                    source.uri(), command);
            case NOTION -> resolved(remoteStructuredSourceResolver.resolveNotion(source), SourceType.NOTION,
                    source.uri(), command);
        };
    }

    private ResolvedIngestionSource resolveDownloadedRemoteSource(IngestDocumentCommand command,
            IngestionSourceCommand source, SourceType type,
            String originalSourceUri) {
        String uri = requireUri(source, type + " source requires uri");
        SourceHttpClient.SourceResponse response = sourceHttpClient.fetch(
                uri, source.accessToken(), acceptHeader(type), defaultHeaders(type));
        return resolved(sourceTextExtractor.extractDownloadedText(type, uri, response.contentType(), response.body()),
                type,
                originalSourceUri, command);
    }

    private byte[] loadBinarySource(IngestDocumentCommand command) {
        IngestionSourceCommand source = command.source();
        if (sourceTextExtractor.hasText(source.base64Content())) {
            try {
                return Base64.getDecoder().decode(source.base64Content());
            } catch (IllegalArgumentException ex) {
                throw new IllegalArgumentException("source.base64Content is not valid base64", ex);
            }
        }

        if (sourceTextExtractor.hasText(source.uri())) {
            return sourceHttpClient.fetch(source.uri(), source.accessToken(), acceptHeader(source.type()), Map.of())
                    .body();
        }

        throw new IllegalArgumentException(source.type() + " source requires uri or base64Content");
    }

    private ResolvedIngestionSource resolved(String content, SourceType sourceType, String sourceUri,
            IngestDocumentCommand command) {
        return new ResolvedIngestionSource(sourceTextExtractor.limitContent(content), sourceType, sourceUri,
                command != null ? command.metadata() : null);
    }

    private String requireUri(IngestionSourceCommand source, String message) {
        if (!sourceTextExtractor.hasText(source.uri())) {
            throw new IllegalArgumentException(message);
        }
        return source.uri();
    }

    private String acceptHeader(SourceType sourceType) {
        return switch (sourceType) {
            case PDF -> "application/pdf, application/octet-stream;q=0.9";
            case DOCX ->
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document, application/octet-stream;q=0.9";
            case HTML, WEB_CRAWL -> "text/html, text/plain;q=0.9";
            case GOOGLE_DRIVE, S3_OBJECT, AZURE_BLOB -> "*/*";
            case CONFLUENCE, NOTION -> "application/json";
            case RAW_TEXT -> "text/plain";
        };
    }

    private Map<String, String> defaultHeaders(SourceType sourceType) {
        Map<String, String> headers = new HashMap<>();
        if (sourceType == SourceType.NOTION) {
            headers.put("Notion-Version", "2022-06-28");
        }
        return headers;
    }
}

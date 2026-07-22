package com.lanny.ailab.rag.infrastructure.adapter.out.source;

import com.lanny.ailab.rag.application.command.IngestionSourceCommand;
import org.springframework.stereotype.Component;

/**
 * Facade that orchestrates remote structured source resolution.
 */
@Component
class RemoteStructuredSourceResolver {

    private final WebRemoteSourceResolver webRemoteSourceResolver;
    private final StructuredPlatformSourceResolver structuredPlatformSourceResolver;

    RemoteStructuredSourceResolver(
            WebRemoteSourceResolver webRemoteSourceResolver,
            StructuredPlatformSourceResolver structuredPlatformSourceResolver) {
        this.webRemoteSourceResolver = webRemoteSourceResolver;
        this.structuredPlatformSourceResolver = structuredPlatformSourceResolver;
    }

    String resolveHtml(String inlineContent, IngestionSourceCommand source) {
        return webRemoteSourceResolver.resolveHtml(inlineContent, source);
    }

    String resolveWebCrawl(IngestionSourceCommand source) {
        return webRemoteSourceResolver.resolveWebCrawl(source);
    }

    String resolveConfluence(IngestionSourceCommand source) {
        return structuredPlatformSourceResolver.resolveConfluence(source);
    }

    String resolveNotion(IngestionSourceCommand source) {
        return structuredPlatformSourceResolver.resolveNotion(source);
    }

    IngestionSourceCommand resolveGoogleDriveSource(IngestionSourceCommand source) {
        return structuredPlatformSourceResolver.resolveGoogleDriveSource(source);
    }
}

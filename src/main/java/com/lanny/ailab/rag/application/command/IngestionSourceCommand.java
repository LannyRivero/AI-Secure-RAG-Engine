package com.lanny.ailab.rag.application.command;

import com.lanny.ailab.rag.domain.model.SourceType;

/**
 * Source descriptor received by the application layer before source resolution.
 *
 * @param type          connector kind to use
 * @param uri           remote locator when the connector fetches external data
 * @param base64Content inline binary payload encoded as base64 when the source
 *                      is uploaded in-band
 * @param accessToken   optional bearer token used by protected connectors
 * @param maxPages      optional page limit used by crawling connectors
 */
public record IngestionSourceCommand(
        SourceType type,
        String uri,
        String base64Content,
        String accessToken,
        Integer maxPages) {
}

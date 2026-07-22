package com.lanny.ailab.rag.domain.model;

/**
 * Supported source kinds that can be resolved into normalized text before
 * entering the durable ingestion queue.
 */
public enum SourceType {
    RAW_TEXT,
    PDF,
    DOCX,
    HTML,
    WEB_CRAWL,
    S3_OBJECT,
    AZURE_BLOB,
    GOOGLE_DRIVE,
    CONFLUENCE,
    NOTION
}

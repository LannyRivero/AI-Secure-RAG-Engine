-- V10: Backfill chunk metadata from durable ingestion rows when available

UPDATE document_chunks dc
SET metadata_document_type = COALESCE(dc.metadata_document_type, di.metadata_document_type),
    metadata_document_date = COALESCE(dc.metadata_document_date, di.metadata_document_date),
    metadata_source = COALESCE(dc.metadata_source, di.metadata_source),
    metadata_tags = CASE
        WHEN cardinality(dc.metadata_tags) = 0 THEN di.metadata_tags
        ELSE dc.metadata_tags
    END,
    metadata_owner = COALESCE(dc.metadata_owner, di.metadata_owner),
    metadata_classification = COALESCE(dc.metadata_classification, di.metadata_classification)
FROM document_ingestions di
WHERE dc.tenant_id = di.tenant_id
  AND dc.document_id = di.document_id
  AND di.status = 'COMPLETED'
  AND (
      dc.metadata_document_type IS NULL
      OR dc.metadata_document_date IS NULL
      OR dc.metadata_source IS NULL
      OR cardinality(dc.metadata_tags) = 0
      OR dc.metadata_owner IS NULL
      OR dc.metadata_classification IS NULL
  );

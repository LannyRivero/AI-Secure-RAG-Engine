# Enterprise Observability Guide

This service now emits correlated logs, business metrics, and distributed traces so latency, failures, and degradation can be diagnosed without guesswork.

## Quick path

1. Enable trace export in the target environment with `MANAGEMENT_TRACING_EXPORT_ENABLED=true`.
2. Point the service to your collector with `OTEL_EXPORTER_OTLP_TRACES_ENDPOINT=http://localhost:4318/v1/traces` when using the local monitoring stack.
3. Scrape `/actuator/prometheus` and import `monitoring/grafana/dashboards/ai-secure-rag-engine-observability.json`.
4. Load `monitoring/prometheus/rules/ai-secure-rag-engine-alerts.yml` into Prometheus or your rule-evaluation stack.

## Local monitoring stack

The repo now includes a local monitoring stack for the normal development flow of this project: infrastructure in Docker, Spring Boot running on the host.

### Start it

```bash
docker compose -f docker-compose.monitoring.yml up -d
```

### Endpoints

- Grafana: `http://localhost:3000` (`admin` / `admin`)
- Prometheus: `http://localhost:9090`
- OTLP HTTP ingest: `http://localhost:4318/v1/traces`
- OTLP gRPC ingest: `localhost:4317`

### Run the app against it

PowerShell:

```powershell
$env:MANAGEMENT_TRACING_EXPORT_ENABLED="true"
$env:OTEL_EXPORTER_OTLP_TRACES_ENDPOINT="http://localhost:4318/v1/traces"
.\mvnw.cmd spring-boot:run -Dspring-boot.run.profiles=dev
```

The collector uses a `debug` exporter on purpose for local validation. That keeps the setup simple: you can prove traces are arriving before deciding whether to add Jaeger, Tempo, or another trace backend.

## What is emitted

| Signal | What you get |
|---|---|
| Logs | `traceId`, `spanId`, `queryId`, `tenantId` in the console pattern |
| HTTP tracing | Spring Boot request spans plus nested business/provider spans |
| Business metrics | `rag.operation.requests`, `rag.operation.latency` by `operation` and `outcome` |
| Retrieval metrics | `rag.retrieval.requests`, `rag.retrieval.latency`, `rag.retrieval.results` by `retriever`, `phase`, `outcome` |
| Provider metrics | `rag.provider.calls`, `rag.provider.latency` by `provider`, `operation`, `outcome` |
| Existing counters | `rag.requests.*`, `rag.ingestion.*`, `rag.rate_limit.*` remain available |

## Correlation model

| Field | Source | Why it matters |
|---|---|---|
| `traceId` / `spanId` | Micrometer Tracing | Follow one request across HTTP, retrieval, and provider calls |
| `queryId` | `X-Query-Id` header or generated server-side | Client-visible request correlation |
| `tenantId` | JWT `attributes.tenant_id` claim | Isolate tenant-specific degradation |
| `queryHash` | SHA-256 fingerprint of the user query | Correlate repeated problematic prompts without logging raw content |
| `documentId` | Ingest/delete/status operations | Track document-specific ingestion issues |

## Suggested dashboards

Ready-to-import assets now live in the repo:

- Grafana dashboard: `monitoring/grafana/dashboards/ai-secure-rag-engine-observability.json`
- Prometheus alerts: `monitoring/prometheus/rules/ai-secure-rag-engine-alerts.yml`

The dashboard is opinionated on purpose: it starts with service health, then retrieval, then provider dependency health, and finally ingestion backlog/failures. That ordering matters. When latency rises, you want to answer "is it the API, retrieval, the provider, or the worker queue?" in that exact sequence.

### 1. API health

- `rate(rag_operation_requests_total{operation="query",outcome="error"}[5m])`
- `histogram_quantile(0.95, sum by (le, operation) (rate(rag_operation_latency_seconds_bucket[5m])))`
- `rate(http_server_requests_seconds_count{uri=~"/rag/.*",status=~"5.."}[5m])`

### 2. Retrieval quality and speed

- `histogram_quantile(0.95, sum by (le, retriever) (rate(rag_retrieval_latency_seconds_bucket[5m])))`
- `sum(rate(rag_retrieval_requests_total{outcome="error"}[5m])) by (retriever, phase)`
- `avg_over_time(rag_retrieval_results_chunks_sum[15m]) / clamp_min(avg_over_time(rag_retrieval_results_chunks_count[15m]), 1)`

### 3. Provider dependency health

- `histogram_quantile(0.95, sum by (le, provider, operation) (rate(rag_provider_latency_seconds_bucket[5m])))`
- `sum(rate(rag_provider_calls_total{outcome="error"}[5m])) by (provider, operation)`
- `sum(rate(rag_rate_limit_backend_unavailable_total[5m]))`

### 4. Ingestion operations

- `rag_ingestion_queued`
- `rag_ingestion_processing`
- `sum(rate(rag_operation_requests_total{operation="ingestion_worker",outcome="failed"}[5m]))`
- `histogram_quantile(0.95, sum by (le) (rate(rag_ingestion_processing_latency_seconds_bucket[5m])))`

## Suggested SLOs

| SLI | Target | Why |
|---|---|---|
| Query success rate | `>= 99.0%` over 30d | Protect end-user answerability |
| Query p95 latency | `<= 2.5s` over 1h | Catch retrieval/provider slowdowns early |
| Ingestion completion rate | `>= 99.5%` over 30d | Detect stuck or failing async processing |
| Provider error rate | `<= 1.0%` over 1h | Surface external dependency instability |

## Notes

- Keep `tenantId`, `queryHash`, and `documentId` in traces/logs, not metric tags. High-cardinality labels destroy Prometheus usefulness.
- The default trace sampling is `1.0` to make this branch easy to validate. Reduce it per environment if volume becomes too high.
- Raw query text is intentionally not logged or tagged.
- The Grafana dashboard uses `${DS_PROMETHEUS}` so Grafana prompts for the Prometheus datasource on import instead of hardcoding an environment-specific UID.

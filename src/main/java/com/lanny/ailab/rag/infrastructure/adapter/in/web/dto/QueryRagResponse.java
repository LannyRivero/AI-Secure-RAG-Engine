package com.lanny.ailab.rag.infrastructure.adapter.in.web.dto;

import java.util.List;

public record QueryRagResponse(
                String answer,
                List<EvidenceDto> evidence,
                boolean hasEvidence) {
}
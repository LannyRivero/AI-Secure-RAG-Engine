package com.lanny.ailab.rag.application.command;

public record QueryRagCommand(
                String query,
                String tenantId,
                String conversationId,
                Integer topK) {
}

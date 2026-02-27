package com.lanny.ailab.rag.application.service;

import java.util.List;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;

public class PromptBuilder {

    public String build(String query, List<DocumentChunk> chunks) {

        StringBuilder context = new StringBuilder();

        for (DocumentChunk chunk : chunks) {
            context.append("- ")
                    .append(chunk.content())
                    .append("\n");
        }

        return """
                Usa únicamente el siguiente contexto para responder.
                Si no hay suficiente información, responde: "No tengo evidencia suficiente."

                Contexto:
                %s

                Pregunta:
                %s
                """.formatted(context, query);
    }
}
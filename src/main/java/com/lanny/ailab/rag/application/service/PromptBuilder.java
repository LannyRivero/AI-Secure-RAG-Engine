package com.lanny.ailab.rag.application.service;

import java.util.List;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import org.springframework.stereotype.Component;

@Component
public class PromptBuilder {

    public String build(String userQuery, List<DocumentChunk> chunks) {

        String context = chunks.stream()
                .map(chunk -> "- " + chunk.content())
                .reduce("", (a, b) -> a + "\n" + b);

        return """
                Eres un asistente que SOLO puede responder usando el contexto proporcionado.

                REGLAS OBLIGATORIAS:
                - Responde únicamente usando información contenida en el contexto.
                - Si la respuesta no está explícitamente en el contexto,
                  responde exactamente: NO_EVIDENCE
                - No añadas información externa.

                CONTEXTO:
                %s

                PREGUNTA:
                %s
                """.formatted(context, userQuery);
    }
}
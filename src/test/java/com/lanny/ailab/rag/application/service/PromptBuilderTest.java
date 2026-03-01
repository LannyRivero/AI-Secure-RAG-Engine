package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.domain.valueobject.DocumentChunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PromptBuilderTest {

    private PromptBuilder promptBuilder;

    @BeforeEach
    void setUp() {
        promptBuilder = new PromptBuilder();
    }

    @Test
    void prompt_contains_user_query() {
        var chunks = List.of(chunk("doc-1", "contenido cualquiera"));
        String query = "¿qué servicios ofrece UNADA?";

        String prompt = promptBuilder.build(query, chunks);

        assertThat(prompt).contains(query);
    }

    @Test
    void prompt_contains_all_chunk_contents() {
        var chunks = List.of(
                chunk("doc-1", "UNADA ofrece recursos sociales"),
                chunk("doc-2", "Las técnicas pueden buscar documentos"));

        String prompt = promptBuilder.build("cualquier query", chunks);

        assertThat(prompt).contains("UNADA ofrece recursos sociales");
        assertThat(prompt).contains("Las técnicas pueden buscar documentos");
    }

    @Test
    void prompt_contains_no_evidence_instruction() {
        var chunks = List.of(chunk("doc-1", "contenido"));

        String prompt = promptBuilder.build("query", chunks);

        assertThat(prompt).contains("NO_EVIDENCE");
    }

    @Test
    void prompt_contains_no_external_information_instruction() {
        var chunks = List.of(chunk("doc-1", "contenido"));

        String prompt = promptBuilder.build("query", chunks);

        assertThat(prompt).containsIgnoringCase("no añadas información externa");
    }

    @Test
    void prompt_with_multiple_chunks_contains_all_contents_in_order() {
        var chunks = List.of(
                chunk("doc-1", "primer fragmento"),
                chunk("doc-2", "segundo fragmento"),
                chunk("doc-3", "tercer fragmento"));

        String prompt = promptBuilder.build("query", chunks);

        int pos1 = prompt.indexOf("primer fragmento");
        int pos2 = prompt.indexOf("segundo fragmento");
        int pos3 = prompt.indexOf("tercer fragmento");

        assertThat(pos1).isLessThan(pos2);
        assertThat(pos2).isLessThan(pos3);
    }

    // helper
    private DocumentChunk chunk(String documentId, String content) {
        return new DocumentChunk(documentId, "org-test", content, 0.9);
    }
}

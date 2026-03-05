package com.lanny.ailab.rag.application.service;

import com.lanny.ailab.rag.application.command.IngestDocumentCommand;
import com.lanny.ailab.rag.application.port.out.DocumentRepositoryPort;
import com.lanny.ailab.rag.application.port.out.EmbeddingPort;
import com.lanny.ailab.rag.application.port.out.VectorStorePort;
import com.lanny.ailab.rag.domain.service.ChunkingService;
import com.lanny.ailab.rag.domain.valueobject.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IngestDocumentServiceTest {

    @Mock
    private EmbeddingPort embeddingPort;
    @Mock
    private VectorStorePort vectorStorePort;
    @Mock
    private DocumentRepositoryPort documentRepositoryPort;

    private IngestDocumentService service;

    private static final float[] FAKE_EMBEDDING = new float[] { 0.1f, 0.2f, 0.3f };

    @BeforeEach
    void setUp() {
        ChunkingService chunkingService = new ChunkingService(3, 1);
        service = new IngestDocumentService(
                chunkingService,
                embeddingPort,
                vectorStorePort,
                documentRepositoryPort);
    }

    @Test
    void deletes_existing_chunks_before_indexing() {
        when(embeddingPort.embed(anyString())).thenReturn(FAKE_EMBEDDING);
        String content = "word1 word2 word3";

        service.execute(command("doc-1", content));

        verify(documentRepositoryPort).deleteByTenantAndDocument("org-test", "doc-1");
    }

    @Test
    void delete_happens_before_store() {
        when(embeddingPort.embed(anyString())).thenReturn(FAKE_EMBEDDING);

        var order = inOrder(documentRepositoryPort, vectorStorePort);

        service.execute(command("doc-1", "word1 word2 word3"));

        order.verify(documentRepositoryPort).deleteByTenantAndDocument(anyString(), anyString());
        order.verify(vectorStorePort, atLeastOnce()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    void returns_correct_chunks_indexed_count() {
        when(embeddingPort.embed(anyString())).thenReturn(FAKE_EMBEDDING);
        String content = "w1 w2 w3 w4 w5";

        var result = service.execute(command("doc-1", content));

        assertThat(result.chunksIndexed()).isEqualTo(2);
        assertThat(result.documentId()).isEqualTo("doc-1");
    }

    @Test
    void embeds_and_stores_each_chunk_with_correct_tenant_and_document() {
        when(embeddingPort.embed(anyString())).thenReturn(FAKE_EMBEDDING);
        String content = "w1 w2 w3";

        service.execute(command("doc-42", content));

        verify(embeddingPort, times(1)).embed("w1 w2 w3");
        verify(vectorStorePort, times(1)).store(
                eq("org-test"),
                eq("doc-42"),
                eq("w1 w2 w3"),
                eq(FAKE_EMBEDDING));
    }

    @Test
    void returns_zero_chunks_when_content_is_blank() {
        var result = service.execute(command("doc-1", "   "));

        assertThat(result.chunksIndexed()).isEqualTo(0);
        verify(embeddingPort, never()).embed(anyString());
        verify(vectorStorePort, never()).store(anyString(), anyString(), anyString(), any());
    }

    @Test
    void still_deletes_existing_chunks_even_when_content_is_blank() {
        service.execute(command("doc-1", "   "));

        verify(documentRepositoryPort).deleteByTenantAndDocument("org-test", "doc-1");
    }

    @Test
    void calls_embedding_once_per_chunk() {
        when(embeddingPort.embed(anyString())).thenReturn(FAKE_EMBEDDING);
        String content = "w1 w2 w3 w4 w5";

        service.execute(command("doc-1", content));

        verify(embeddingPort, times(2)).embed(anyString());
        verify(vectorStorePort, times(2)).store(anyString(), anyString(), anyString(), any());
    }

    private IngestDocumentCommand command(String documentId, String content) {
        return new IngestDocumentCommand(documentId, TenantId.from("org-test"), content);
    }
}
package com.lanny.ailab.rag.domain.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChunkingService {

    private static final int DEFAULT_CHUNK_SIZE = 512;
    private static final int DEFAULT_OVERLAP = 50;

    private final int chunkSize;
    private final int overlap;

    public ChunkingService() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public ChunkingService(int chunkSize, int overlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be > 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap must be >= 0 and < chunkSize");
        }
        this.chunkSize = chunkSize;
        this.overlap = overlap;
    }

    public List<String> chunk(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }

        String[] words = text.trim().split("\\s+");

        if (words.length == 0) {
            return List.of();
        }

        List<String> chunks = new ArrayList<>();
        int step = chunkSize - overlap;
        int start = 0;

        while (start < words.length) {
            int end = Math.min(start + chunkSize, words.length);
            chunks.add(String.join(" ", Arrays.copyOfRange(words, start, end)));
            if (end == words.length)
                break;
            start += step;
        }

        return List.copyOf(chunks);
    }
}

package com.lanny.ailab.rag.domain.exception;

public class LlmProviderException extends RuntimeException {

    public LlmProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

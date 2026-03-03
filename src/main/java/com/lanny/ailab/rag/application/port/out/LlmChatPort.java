package com.lanny.ailab.rag.application.port.out;

public interface LlmChatPort {

    String generateAnswer(String prompt);
}

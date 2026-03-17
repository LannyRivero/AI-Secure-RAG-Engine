package com.lanny.ailab.shared.error;

import com.lanny.ailab.rag.domain.exception.LlmProviderException;
import com.lanny.ailab.rag.domain.exception.RateLimitExceededException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handle_validation_returns_400_with_field_errors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "query", "must not be blank"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail result = handler.handleValidation(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Validation failed");
        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) result.getProperties().get("errors");
        assertThat(errors).containsEntry("query", "must not be blank");
    }

    @Test
    void handle_validation_collects_all_field_errors() throws Exception {
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "query", "must not be blank"));
        bindingResult.addError(new FieldError("request", "topK", "must be less than or equal to 20"));
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail result = handler.handleValidation(ex);

        @SuppressWarnings("unchecked")
        Map<String, String> errors = (Map<String, String>) result.getProperties().get("errors");
        assertThat(errors).hasSize(2).containsKey("query").containsKey("topK");
    }

    @Test
    void handle_illegal_argument_returns_400_with_message() {
        IllegalArgumentException ex = new IllegalArgumentException("Invalid tenant format");

        ProblemDetail result = handler.handleIllegalArgument(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(result.getTitle()).isEqualTo("Bad request");
        assertThat(result.getDetail()).isEqualTo("Invalid tenant format");
    }

    @Test
    void handle_llm_provider_error_returns_502_with_generic_message() {
        LlmProviderException ex = new LlmProviderException("OpenAI timeout", new RuntimeException("connection refused"));

        ProblemDetail result = handler.handleLlmProviderError(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY.value());
        assertThat(result.getTitle()).isEqualTo("AI service unavailable");
        assertThat(result.getDetail()).doesNotContain("connection refused");
    }

    @Test
    void handle_rate_limit_exceeded_returns_429_with_generic_message() {
        RateLimitExceededException ex = new RateLimitExceededException("org-test");

        ProblemDetail result = handler.handleRateLimitExceeded(ex);

        assertThat(result.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
        assertThat(result.getTitle()).isEqualTo("Rate limit exceeded");
        assertThat(result.getDetail()).doesNotContain("org-test");
    }
}

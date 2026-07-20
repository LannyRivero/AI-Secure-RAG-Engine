package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import jakarta.servlet.FilterChain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

@Tag("unit")
class QueryIdFilterTest {

    private final QueryIdFilter filter = new QueryIdFilter();

    @Test
    @DisplayName("When request provides a query ID, the filter reuses it for response")
    void reuses_incoming_query_id_header_for_request_correlation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        request.addHeader("X-Query-Id", "client-correlation-id");

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Query-Id")).isEqualTo("client-correlation-id");
    }

    @Test
    @DisplayName("When request does not provide a query ID, the filter generates one")
    void generates_query_id_when_request_does_not_provide_one() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("X-Query-Id")).isNotBlank();
    }
}

package com.lanny.ailab.rag.infrastructure.adapter.in.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Assigns a unique queryId to every incoming request via MDC.
 * All log statements within the request will automatically include queryId.
 * The queryId is also returned in the response header X-Query-Id
 * for client-side correlation.
 */
@Component
public class QueryIdFilter extends OncePerRequestFilter {

    private static final String QUERY_ID_KEY = "queryId";
    private static final String QUERY_ID_HEADER = "X-Query-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String queryId = UUID.randomUUID().toString();

        try {
            MDC.put(QUERY_ID_KEY, queryId);
            response.setHeader(QUERY_ID_HEADER, queryId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(QUERY_ID_KEY);
        }
    }
}
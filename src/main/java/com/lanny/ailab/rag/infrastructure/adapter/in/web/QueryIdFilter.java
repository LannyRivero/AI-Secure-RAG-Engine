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
    private static final String HTTP_METHOD_KEY = "httpMethod";
    private static final String HTTP_PATH_KEY = "httpPath";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain)
            throws ServletException, IOException {

        String queryId = resolveQueryId(request);

        try {
            MDC.put(QUERY_ID_KEY, queryId);
            MDC.put(HTTP_METHOD_KEY, request.getMethod());
            MDC.put(HTTP_PATH_KEY, request.getRequestURI());
            response.setHeader(QUERY_ID_HEADER, queryId);
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(QUERY_ID_KEY);
            MDC.remove("tenantId");
            MDC.remove("documentId");
            MDC.remove("queryHash");
            MDC.remove("operation");
            MDC.remove(HTTP_METHOD_KEY);
            MDC.remove(HTTP_PATH_KEY);
        }
    }

    private String resolveQueryId(HttpServletRequest request) {
        String incoming = request.getHeader(QUERY_ID_HEADER);
        if (incoming == null || incoming.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return incoming.trim();
    }
}

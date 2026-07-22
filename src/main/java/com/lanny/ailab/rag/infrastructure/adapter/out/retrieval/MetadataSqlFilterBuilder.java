package com.lanny.ailab.rag.infrastructure.adapter.out.retrieval;

import com.lanny.ailab.rag.application.model.RetrievalFilter;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.support.SqlValue;

/**
 * Builds SQL predicates for retrieval metadata filters.
 */
final class MetadataSqlFilterBuilder {

    private MetadataSqlFilterBuilder() {
    }

    static SqlFilterClause build(RetrievalFilter filters) {
        RetrievalFilter effectiveFilters = filters == null ? RetrievalFilter.empty() : filters;

        StringBuilder where = new StringBuilder();
        List<Object> args = new ArrayList<>();

        if (effectiveFilters.documentType() != null) {
            where.append(" AND metadata_document_type = ?");
            args.add(effectiveFilters.documentType());
        }
        if (effectiveFilters.source() != null) {
            where.append(" AND metadata_source = ?");
            args.add(effectiveFilters.source());
        }
        if (!effectiveFilters.tags().isEmpty()) {
            where.append(" AND metadata_tags @> ?::text[]");
            args.add(textArray(effectiveFilters.tags()));
        }
        if (effectiveFilters.owner() != null) {
            where.append(" AND metadata_owner = ?");
            args.add(effectiveFilters.owner());
        }
        if (effectiveFilters.classification() != null) {
            where.append(" AND metadata_classification = ?");
            args.add(effectiveFilters.classification());
        }
        if (effectiveFilters.dateFrom() != null) {
            where.append(" AND metadata_document_date >= ?");
            args.add(effectiveFilters.dateFrom());
        }
        if (effectiveFilters.dateTo() != null) {
            where.append(" AND metadata_document_date <= ?");
            args.add(effectiveFilters.dateTo());
        }

        return new SqlFilterClause(where.toString(), List.copyOf(args));
    }

    private static SqlValue textArray(List<String> values) {
        return new SqlValue() {
            @Override
            public void setValue(java.sql.PreparedStatement ps, int paramIndex) throws SQLException {
                Array array = ps.getConnection().createArrayOf("text", values.toArray(String[]::new));
                ps.setArray(paramIndex, array);
            }

            @Override
            public void cleanup() {
            }
        };
    }

    record SqlFilterClause(String whereClause, List<Object> args) {
    }
}

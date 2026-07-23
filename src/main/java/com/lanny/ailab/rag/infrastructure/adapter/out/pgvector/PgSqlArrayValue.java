package com.lanny.ailab.rag.infrastructure.adapter.out.pgvector;

import org.springframework.jdbc.support.SqlValue;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

/**
 * Helper for binding PostgreSQL text arrays through JdbcTemplate.
 */
final class PgSqlArrayValue {

    private PgSqlArrayValue() {
    }

    static SqlValue textArray(List<String> values) {
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
}

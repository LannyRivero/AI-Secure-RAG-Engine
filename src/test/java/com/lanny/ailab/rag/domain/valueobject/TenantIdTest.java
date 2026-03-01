package com.lanny.ailab.rag.domain.valueobject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TenantIdTest {

    @Test
    void accepts_valid_value() {
        var tenantId = new TenantId("org-abc");

        assertThat(tenantId.value()).isEqualTo("org-abc");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "abc",
            "org-123",
            "UNADA",
            "a1b2c3d4e5"
    })
    void accepts_valid_formats(String value) {
        var tenantId = new TenantId(value);

        assertThat(tenantId.value()).isEqualTo(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ab",
            "  ",
            "org@abc",
            "org.abc",
            "org abc"
    })
    void rejects_invalid_formats(String value) {
        assertThatThrownBy(() -> new TenantId(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> new TenantId(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> new TenantId(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void two_tenant_ids_with_same_value_are_equal() {
        var a = new TenantId("org-abc");
        var b = new TenantId("org-abc");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void two_tenant_ids_with_different_values_are_not_equal() {
        var a = new TenantId("org-abc");
        var b = new TenantId("org-xyz");

        assertThat(a).isNotEqualTo(b);
    }
}

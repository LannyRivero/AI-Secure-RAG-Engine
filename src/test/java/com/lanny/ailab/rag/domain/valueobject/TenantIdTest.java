package com.lanny.ailab.rag.domain.valueobject;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
class TenantIdTest {

    @Test
    void accepts_valid_value() {
        var tenantId = TenantId.from("org-abc");

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
        var tenantId = TenantId.from(value);

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
        assertThatThrownBy(() -> TenantId.from(value))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_null() {
        assertThatThrownBy(() -> TenantId.from(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejects_blank() {
        assertThatThrownBy(() -> TenantId.from(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void two_tenant_ids_with_same_value_are_equal() {
        var a = TenantId.from("org-abc");
        var b = TenantId.from("org-abc");

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }

    @Test
    void two_tenant_ids_with_different_values_are_not_equal() {
        var a = TenantId.from("org-abc");
        var b = TenantId.from("org-xyz");

        assertThat(a).isNotEqualTo(b);
    }
}

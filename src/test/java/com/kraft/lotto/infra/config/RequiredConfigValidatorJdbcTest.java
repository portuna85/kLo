package com.kraft.lotto.infra.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RequiredConfigValidator JDBC parsing test")
class RequiredConfigValidatorJdbcTest {

    @Test
    @DisplayName("uses default port 3306")
    void extractsJdbcEndpointWithDefaultPort() {
        var endpoint = RequiredConfigValidator.extractJdbcEndpoint("jdbc:mariadb://localhost/kraft_lotto");
        assertThat(endpoint).isNotNull();
        assertThat(endpoint.host()).isEqualTo("localhost");
        assertThat(endpoint.port()).isEqualTo(3306);
    }

    @Test
    @DisplayName("uses explicit port")
    void extractsJdbcEndpointWithExplicitPort() {
        var endpoint = RequiredConfigValidator.extractJdbcEndpoint("jdbc:mariadb://db.internal:3307/kraft_lotto");
        assertThat(endpoint).isNotNull();
        assertThat(endpoint.host()).isEqualTo("db.internal");
        assertThat(endpoint.port()).isEqualTo(3307);
    }
}
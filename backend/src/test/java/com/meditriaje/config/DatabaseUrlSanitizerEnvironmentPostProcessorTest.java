package com.meditriaje.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class DatabaseUrlSanitizerEnvironmentPostProcessorTest {

    private final DatabaseUrlSanitizerEnvironmentPostProcessor processor =
            new DatabaseUrlSanitizerEnvironmentPostProcessor();

    @Test
    @DisplayName("Debe sanitizar URL con saltos de linea y espacios en DB_URL")
    void debeSanitizarUrlConSaltosDeLinea() {
        MockEnvironment environment = new MockEnvironment();
        String dirtyUrl = "jdbc:oracle:thin:@(description=(address=(protocol=tcps)(port=1522)(host=adb\n  .sa-bogota-1.oraclecloud.com)))";
        environment.setProperty("DB_URL", dirtyUrl);

        processor.postProcessEnvironment(environment, null);

        String cleanUrl = "jdbc:oracle:thin:@(description=(address=(protocol=tcps)(port=1522)(host=adb.sa-bogota-1.oraclecloud.com)))";
        assertThat(environment.getProperty("DB_URL")).isEqualTo(cleanUrl);
        assertThat(environment.getProperty("spring.datasource.url")).isEqualTo(cleanUrl);
        assertThat(environment.getProperty("spring.flyway.url")).isEqualTo(cleanUrl);
    }

    @Test
    @DisplayName("No debe alterar una URL que ya esta limpia")
    void noDebeAlterarUrlLimpia() {
        MockEnvironment environment = new MockEnvironment();
        String cleanUrl = "jdbc:oracle:thin:@adb.sa-bogota-1.oraclecloud.com:1522/service";
        environment.setProperty("DB_URL", cleanUrl);

        processor.postProcessEnvironment(environment, null);

        assertThat(environment.getProperty("DB_URL")).isEqualTo(cleanUrl);
    }
}

package com.meditriaje.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

/**
 * Configuración del Clock del sistema para MediTriaje 2.0.
 * Fija la zona horaria institucional America/Bogota según ADR-005.
 */
@Configuration
public class ClockConfig {

    public static final ZoneId ZONA_BOGOTA = ZoneId.of("America/Bogota");

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    public Clock clock() {
        return Clock.system(ZONA_BOGOTA);
    }
}

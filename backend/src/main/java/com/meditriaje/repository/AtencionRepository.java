package com.meditriaje.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/**
 * Repositorio JDBC para la entidad {@code ATENCION}.
 * Implementa persistencia y consultas clínicas con SQL 100% parametrizado (ADR-001, ADR-007, ADR-008).
 */
@Repository
public class AtencionRepository {

    private final JdbcTemplate jdbcTemplate;

    public AtencionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Verifica si existe una atención médica previa entre el profesional y el paciente
     * realizada dentro de la ventana temporal especificada (ADR-007).
     *
     * @param profesionalId Identificador interno del profesional
     * @param pacienteId    Identificador interno del paciente
     * @param fechaLimite   Límite inferior temporal de la ventana
     * @return true si existe al menos una atención en la ventana
     */
    public boolean existeAtencionPreviaEnVentana(Long profesionalId, Long pacienteId, Instant fechaLimite) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(fechaLimite, "fechaLimite no puede ser nulo");

        String sql = """
            SELECT COUNT(*)
            FROM ATENCION a
            WHERE a.PACIENTE_ID = ?
              AND a.PROFESIONAL_ID = ?
              AND a.CREATED_AT >= ?
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                pacienteId,
                profesionalId,
                Timestamp.from(fechaLimite)
        );
        return count != null && count > 0;
    }
}

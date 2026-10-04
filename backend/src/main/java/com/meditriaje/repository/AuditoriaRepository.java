package com.meditriaje.repository;

import com.meditriaje.model.EventoAuditoria;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Objects;

/**
 * Repositorio para la tabla {@code AUDITORIA}.
 *
 * <p><strong>Restricción de diseño (ADR-011, ADR-012):</strong>
 * Este repositorio es estrictamente <em>insert-only</em>. No define ni expone métodos
 * de actualización ni eliminación, garantizando la inmutabilidad de la bitácora desde la capa de datos.</p>
 */
@Repository
public class AuditoriaRepository {

    private final JdbcTemplate jdbcTemplate;

    public AuditoriaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    /**
     * Inserta un nuevo registro de auditoría utilizando SQL parametrizado.
     *
     * @param evento Metadatos del evento a registrar.
     */
    public void registrar(EventoAuditoria evento) {
        Objects.requireNonNull(evento, "El evento de auditoria no puede ser nulo");

        final String sql = """
            INSERT INTO AUDITORIA (USUARIO_ID, ACCION, TIPO_RECURSO, RECURSO_PUBLIC_ID, RESULTADO, IP_ORIGEN)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(
            sql,
            evento.usuarioId(),
            evento.accion().name(),
            evento.tipoRecurso(),
            evento.recursoPublicId(),
            evento.resultado().name(),
            evento.ipOrigen()
        );
    }
}

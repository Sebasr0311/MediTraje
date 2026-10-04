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

    /**
     * Consulta paginada de eventos de auditoría para el visor de seguridad (ADR-019).
     */
    public java.util.List<com.meditriaje.dto.audit.RegistroAuditoriaResponse> listarEventos(
            java.time.Instant fechaDesde,
            java.time.Instant fechaHasta,
            String accion,
            String resultado,
            int page,
            int size
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT a.ID,
                   u.EMAIL AS USUARIO_EMAIL,
                   a.ACCION,
                   a.TIPO_RECURSO,
                   a.RECURSO_PUBLIC_ID,
                   a.RESULTADO,
                   a.IP_ORIGEN,
                   a.FECHA_HORA
            FROM AUDITORIA a
            LEFT JOIN USUARIO u ON a.USUARIO_ID = u.ID
            WHERE 1=1
            """);
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND a.FECHA_HORA >= ?");
            params.add(java.sql.Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND a.FECHA_HORA <= ?");
            params.add(java.sql.Timestamp.from(fechaHasta));
        }
        if (accion != null && !accion.isBlank()) {
            sql.append(" AND a.ACCION = ?");
            params.add(accion.trim().toUpperCase());
        }
        if (resultado != null && !resultado.isBlank()) {
            sql.append(" AND a.RESULTADO = ?");
            params.add(resultado.trim().toUpperCase());
        }

        sql.append(" ORDER BY a.FECHA_HORA DESC, a.ID DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, size);
        params.add(safePage * safeSize);
        params.add(safeSize);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> new com.meditriaje.dto.audit.RegistroAuditoriaResponse(
                rs.getLong("ID"),
                rs.getString("USUARIO_EMAIL") != null ? rs.getString("USUARIO_EMAIL") : "Sistema / Anónimo",
                rs.getString("ACCION"),
                rs.getString("TIPO_RECURSO"),
                rs.getString("RECURSO_PUBLIC_ID"),
                rs.getString("RESULTADO"),
                rs.getString("IP_ORIGEN"),
                rs.getTimestamp("FECHA_HORA").toInstant()
        ), params.toArray());
    }

    /**
     * Conteo total de eventos para cálculo de paginación en el visor de auditoría (ADR-019).
     */
    public long contarEventos(
            java.time.Instant fechaDesde,
            java.time.Instant fechaHasta,
            String accion,
            String resultado
    ) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM AUDITORIA a WHERE 1=1");
        java.util.List<Object> params = new java.util.ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND a.FECHA_HORA >= ?");
            params.add(java.sql.Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND a.FECHA_HORA <= ?");
            params.add(java.sql.Timestamp.from(fechaHasta));
        }
        if (accion != null && !accion.isBlank()) {
            sql.append(" AND a.ACCION = ?");
            params.add(accion.trim().toUpperCase());
        }
        if (resultado != null && !resultado.isBlank()) {
            sql.append(" AND a.RESULTADO = ?");
            params.add(resultado.trim().toUpperCase());
        }

        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }
}

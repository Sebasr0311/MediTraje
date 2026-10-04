package com.meditriaje.repository;

import com.meditriaje.model.AccesoTemporalQr;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code ACCESO_TEMPORAL_QR} (ADR-010, F2.3).
 * Maneja persistencia de tokens hasheados, control atómico de lecturas y revocación.
 */
@Repository
public class AccesoTemporalQrRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<AccesoTemporalQr> rowMapper = (rs, rowNum) -> {
        Timestamp tsExpira = rs.getTimestamp("EXPIRA_AT");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");

        return new AccesoTemporalQr(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("TOKEN_HASH"),
                rs.getString("PIN_HASH"),
                rs.getInt("INCLUIR_ALERGIAS") == 1,
                rs.getInt("INCLUIR_MEDICAMENTOS") == 1,
                rs.getInt("INCLUIR_ATENCIONES") == 1,
                rs.getInt("INCLUIR_CONTACTO") == 1,
                rs.getInt("MAX_ACCESOS"),
                rs.getInt("ACCESOS_REALIZADOS"),
                rs.getInt("REVOCADO") == 1,
                tsExpira != null ? tsExpira.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    public AccesoTemporalQrRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Persiste un nuevo token de acceso temporal QR.
     *
     * @param acceso Entidad a persistir
     * @return ID numérico generado
     */
    public Long crear(AccesoTemporalQr acceso) {
        String sql = """
                INSERT INTO ACCESO_TEMPORAL_QR (
                    PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
                    INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
                    MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, EXPIRA_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, acceso.publicId());
            ps.setLong(2, acceso.pacienteId());
            ps.setString(3, acceso.tokenHash());
            ps.setString(4, acceso.pinHash());
            ps.setInt(5, acceso.incluirAlergias() ? 1 : 0);
            ps.setInt(6, acceso.incluirMedicamentos() ? 1 : 0);
            ps.setInt(7, acceso.incluirAtenciones() ? 1 : 0);
            ps.setInt(8, acceso.incluirContacto() ? 1 : 0);
            ps.setInt(9, acceso.maxAccesos());
            ps.setInt(10, acceso.accesosRealizados());
            ps.setInt(11, acceso.revocado() ? 1 : 0);
            ps.setTimestamp(12, Timestamp.from(acceso.expiraAt()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        if (!keyHolder.getKeyList().isEmpty() && keyHolder.getKeyList().get(0).containsKey("ID")) {
            return ((Number) keyHolder.getKeyList().get(0).get("ID")).longValue();
        }
        throw new IllegalStateException("No se pudo obtener la clave primaria generada para ACCESO_TEMPORAL_QR");
    }

    /**
     * Busca un acceso temporal por su token hash SHA-256.
     */
    public Optional<AccesoTemporalQr> buscarPorTokenHash(String tokenHash) {
        String sql = """
                SELECT ID, PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
                       INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
                       MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, EXPIRA_AT, CREATED_AT, UPDATED_AT
                FROM ACCESO_TEMPORAL_QR
                WHERE TOKEN_HASH = ?
                """;
        try {
            AccesoTemporalQr acceso = jdbcTemplate.queryForObject(sql, rowMapper, tokenHash);
            return Optional.ofNullable(acceso);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Busca un acceso temporal por su UUID público.
     */
    public Optional<AccesoTemporalQr> buscarPorPublicId(String publicId) {
        String sql = """
                SELECT ID, PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
                       INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
                       MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, EXPIRA_AT, CREATED_AT, UPDATED_AT
                FROM ACCESO_TEMPORAL_QR
                WHERE PUBLIC_ID = ?
                """;
        try {
            AccesoTemporalQr acceso = jdbcTemplate.queryForObject(sql, rowMapper, publicId);
            return Optional.ofNullable(acceso);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Lista los accesos temporales generados por un paciente específico, ordenados cronológicamente descendente.
     */
    public List<AccesoTemporalQr> listarPorPacienteId(Long pacienteId) {
        String sql = """
                SELECT ID, PUBLIC_ID, PACIENTE_ID, TOKEN_HASH, PIN_HASH,
                       INCLUIR_ALERGIAS, INCLUIR_MEDICAMENTOS, INCLUIR_ATENCIONES, INCLUIR_CONTACTO,
                       MAX_ACCESOS, ACCESOS_REALIZADOS, REVOCADO, EXPIRA_AT, CREATED_AT, UPDATED_AT
                FROM ACCESO_TEMPORAL_QR
                WHERE PACIENTE_ID = ?
                ORDER BY CREATED_AT DESC
                """;
        return jdbcTemplate.query(sql, rowMapper, pacienteId);
    }

    /**
     * Registra un acceso/lectura incrementando de manera atómica el contador {@code ACCESOS_REALIZADOS}.
     * Solo tiene éxito si el token no está revocado, no ha expirado y no ha alcanzado el límite máximo.
     *
     * @param id Identificador numérico del acceso
     * @param now Instante actual de lectura
     * @return 1 si se incrementó exitosamente, 0 si ya no era válido
     */
    public int registrarAcceso(Long id, Instant now) {
        String sql = """
                UPDATE ACCESO_TEMPORAL_QR
                SET ACCESOS_REALIZADOS = ACCESOS_REALIZADOS + 1,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE ID = ?
                  AND REVOCADO = 0
                  AND ACCESOS_REALIZADOS < MAX_ACCESOS
                  AND EXPIRA_AT > ?
                """;
        return jdbcTemplate.update(sql, id, Timestamp.from(now));
    }

    /**
     * Revoca un token de acceso temporal por solicitud del paciente.
     *
     * @param publicId Identificador público del acceso
     * @param pacienteId ID numérico del paciente dueño
     * @return 1 si se revocó, 0 si no existía o ya estaba revocado
     */
    public int revocar(String publicId, Long pacienteId) {
        String sql = """
                UPDATE ACCESO_TEMPORAL_QR
                SET REVOCADO = 1,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE PUBLIC_ID = ?
                  AND PACIENTE_ID = ?
                  AND REVOCADO = 0
                """;
        return jdbcTemplate.update(sql, publicId, pacienteId);
    }
}

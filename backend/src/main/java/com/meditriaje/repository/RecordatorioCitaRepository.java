package com.meditriaje.repository;

import com.meditriaje.model.RecordatorioCita;
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

/**
 * Repositorio JDBC parametrizado para la tabla RECORDATORIO_CITA.
 */
@Repository
public class RecordatorioCitaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<RecordatorioCita> rowMapper = (rs, rowNum) -> {
        Timestamp enviadoTs = rs.getTimestamp("ENVIADO_AT");
        Instant enviadoAt = enviadoTs != null ? enviadoTs.toInstant() : null;

        return new RecordatorioCita(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("CITA_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("TIPO"),
                rs.getString("CANAL"),
                rs.getString("DESTINATARIO"),
                rs.getString("ESTADO_ENVIO"),
                rs.getString("ERROR_MENSAJE"),
                enviadoAt
        );
    };

    public RecordatorioCitaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Inserta un nuevo registro de recordatorio/notificación de cita.
     *
     * @param recordatorio Registro a persistir
     * @return ID numérico autogenerado
     */
    public Long guardar(RecordatorioCita recordatorio) {
        String sql = """
                INSERT INTO RECORDATORIO_CITA (
                    PUBLIC_ID, CITA_ID, PACIENTE_ID, TIPO, CANAL,
                    DESTINATARIO, ESTADO_ENVIO, ERROR_MENSAJE, ENVIADO_AT
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, recordatorio.publicId());
            ps.setLong(2, recordatorio.citaId());
            ps.setLong(3, recordatorio.pacienteId());
            ps.setString(4, recordatorio.tipo());
            ps.setString(5, recordatorio.canal() != null ? recordatorio.canal() : "EMAIL");
            ps.setString(6, recordatorio.destinatario());
            ps.setString(7, recordatorio.estadoEnvio() != null ? recordatorio.estadoEnvio() : "ENVIADO");
            ps.setString(8, recordatorio.errorMensaje());
            ps.setTimestamp(9, Timestamp.from(recordatorio.enviadoAt() != null ? recordatorio.enviadoAt() : Instant.now()));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        return key != null ? key.longValue() : null;
    }

    /**
     * Lista los recordatorios asociados a una cita.
     */
    public List<RecordatorioCita> listarPorCitaId(Long citaId) {
        String sql = """
                SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, TIPO, CANAL,
                       DESTINATARIO, ESTADO_ENVIO, ERROR_MENSAJE, ENVIADO_AT
                FROM RECORDATORIO_CITA
                WHERE CITA_ID = ?
                ORDER BY ID ASC
                """;
        return jdbcTemplate.query(sql, rowMapper, citaId);
    }

    /**
     * Cuenta cuántos recordatorios de un tipo específico se han emitido para una cita.
     */
    public int contarPorCitaIdYTipo(Long citaId, String tipo) {
        String sql = "SELECT COUNT(*) FROM RECORDATORIO_CITA WHERE CITA_ID = ? AND TIPO = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, citaId, tipo);
        return count != null ? count : 0;
    }
}

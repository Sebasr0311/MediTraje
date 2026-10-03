package com.meditriaje.repository;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.model.Cita;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code CITA}.
 * Implementa persistencia 100% parametrizada, consultas optimizadas con JOINs y verificación
 * estricta de unicidad de citas activas por slot (ADR-001, ADR-003, ADR-006, HU-04).
 */
@Repository
public class CitaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Cita> citaRowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        Long triajeId = rs.getObject("TRIAJE_ID") != null ? rs.getLong("TRIAJE_ID") : null;
        Long citaOrigenId = rs.getObject("CITA_ORIGEN_ID") != null ? rs.getLong("CITA_ORIGEN_ID") : null;
        return new Cita(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("SLOT_ID"),
                rs.getLong("PACIENTE_ID"),
                triajeId,
                citaOrigenId,
                rs.getString("ESTADO"),
                rs.getString("MOTIVO_CANCELACION"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    private final RowMapper<CitaResponse> citaResponseRowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");

        return new CitaResponse(
                rs.getString("CITA_PUBLIC_ID"),
                rs.getString("SLOT_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("SEDE_DIRECCION"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO"),
                null, // triajePublicId (se incorporará en M5)
                rs.getString("CITA_ORIGEN_PUBLIC_ID"),
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public CitaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    /**
     * Inserta una nueva cita asistencial y retorna el ID interno autogenerado.
     */
    public Long crear(Cita cita) {
        Objects.requireNonNull(cita, "La cita no puede ser nula");
        final String sql = """
            INSERT INTO CITA (PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID, CITA_ORIGEN_ID, ESTADO, MOTIVO_CANCELACION)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[] { "ID" });
            ps.setString(1, cita.publicId());
            ps.setLong(2, cita.slotId());
            ps.setLong(3, cita.pacienteId());
            if (cita.triajeId() != null) {
                ps.setLong(4, cita.triajeId());
            } else {
                ps.setNull(4, Types.NUMERIC);
            }
            if (cita.citaOrigenId() != null) {
                ps.setLong(5, cita.citaOrigenId());
            } else {
                ps.setNull(5, Types.NUMERIC);
            }
            ps.setString(6, cita.estado());
            ps.setString(7, cita.motivoCancelacion());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la cita.");
        }
        return key.longValue();
    }

    /**
     * Busca los detalles consolidados de una cita por su clave pública UUID (ADR-003, HU-04).
     */
    public Optional<CitaResponse> buscarPorPublicId(String publicId) {
        final String sql = """
            SELECT c.PUBLIC_ID AS CITA_PUBLIC_ID,
                   s.PUBLIC_ID AS SLOT_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.NOMBRES || ' ' || p.APELLIDOS AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   pr.NOMBRES || ' ' || pr.APELLIDOS AS PROFESIONAL_NOMBRE,
                   e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   sd.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   sd.NOMBRE AS SEDE_NOMBRE,
                   sd.DIRECCION AS SEDE_DIRECCION,
                   s.FECHA_HORA_INICIO,
                   s.FECHA_HORA_FIN,
                   s.MODALIDAD,
                   c.ESTADO,
                   co.PUBLIC_ID AS CITA_ORIGEN_PUBLIC_ID,
                   c.CREATED_AT
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            LEFT JOIN CITA co ON c.CITA_ORIGEN_ID = co.ID
            WHERE c.PUBLIC_ID = ?
            """;
        List<CitaResponse> resultados = jdbcTemplate.query(sql, citaResponseRowMapper, publicId);
        return resultados.stream().findFirst();
    }

    /**
     * Busca la entidad Cita por su clave pública UUID.
     */
    public Optional<Cita> buscarEntidadPorPublicId(String publicId) {
        final String sql = """
            SELECT ID, PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID, CITA_ORIGEN_ID,
                   ESTADO, MOTIVO_CANCELACION, CREATED_AT, UPDATED_AT
            FROM CITA
            WHERE PUBLIC_ID = ?
            """;
        List<Cita> resultados = jdbcTemplate.query(sql, citaRowMapper, publicId);
        return resultados.stream().findFirst();
    }

    /**
     * Busca la entidad Cita por su ID interno.
     */
    public Optional<Cita> buscarEntidadPorId(Long id) {
        final String sql = """
            SELECT ID, PUBLIC_ID, SLOT_ID, PACIENTE_ID, TRIAJE_ID, CITA_ORIGEN_ID,
                   ESTADO, MOTIVO_CANCELACION, CREATED_AT, UPDATED_AT
            FROM CITA
            WHERE ID = ?
            """;
        List<Cita> resultados = jdbcTemplate.query(sql, citaRowMapper, id);
        return resultados.stream().findFirst();
    }

    /**
     * Actualiza el estado y motivo de cancelación de una cita asistencial.
     */
    public void actualizarEstado(Long id, String nuevoEstado, String motivoCancelacion) {
        final String sql = """
            UPDATE CITA
            SET ESTADO = ?, MOTIVO_CANCELACION = ?, UPDATED_AT = CURRENT_TIMESTAMP
            WHERE ID = ?
            """;
        jdbcTemplate.update(sql, nuevoEstado, motivoCancelacion, id);
    }

    /**
     * Determina si ya existe una cita activa (PROGRAMADA o CONFIRMADA) para el slot dado.
     */
    public boolean existeCitaActivaEnSlot(Long slotId) {
        final String sql = """
            SELECT COUNT(*)
            FROM CITA
            WHERE SLOT_ID = ?
              AND ESTADO IN ('PROGRAMADA', 'CONFIRMADA')
            """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, slotId);
        return count != null && count > 0;
    }
}

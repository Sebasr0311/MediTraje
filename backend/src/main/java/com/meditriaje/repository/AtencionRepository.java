package com.meditriaje.repository;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.SignosVitalesDto;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.SignoVital;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code ATENCION} y {@code SIGNO_VITAL}.
 * Implementa persistencia y consultas clínicas con SQL 100% parametrizado (ADR-001, ADR-007, ADR-008, HU-07).
 */
@Repository
public class AtencionRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Atencion> atencionRowMapper = (rs, rowNum) -> {
        Timestamp tsCierre = rs.getTimestamp("FECHA_CIERRE");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Long diagId = rs.getObject("DIAGNOSTICO_PRINCIPAL_ID") != null ? rs.getLong("DIAGNOSTICO_PRINCIPAL_ID") : null;

        return new Atencion(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("CITA_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getLong("PROFESIONAL_ID"),
                diagId,
                rs.getString("MOTIVO_CONSULTA"),
                rs.getString("EVOLUCION"),
                rs.getString("INDICACIONES"),
                rs.getString("ESTADO"),
                tsCierre != null ? tsCierre.toInstant() : null,
                tsCreated != null ? tsCreated.toInstant() : null
        );
    };

    public AtencionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Inserta una nueva atención médica en estado inicial ABIERTA.
     *
     * @param atencion Entidad de atención a persistir
     * @return Identificador interno autogenerado
     */
    public Long crear(Atencion atencion) {
        Objects.requireNonNull(atencion, "atencion no puede ser nula");

        String sql = """
            INSERT INTO ATENCION (PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, ESTADO)
            VALUES (?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, atencion.publicId());
            ps.setLong(2, atencion.citaId());
            ps.setLong(3, atencion.pacienteId());
            ps.setLong(4, atencion.profesionalId());
            ps.setString(5, atencion.estado() != null ? atencion.estado() : "ABIERTA");
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la atencion clinica.");
        }
        return key.longValue();
    }

    public Optional<Atencion> buscarEntidadPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        String sql = """
            SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, DIAGNOSTICO_PRINCIPAL_ID,
                   MOTIVO_CONSULTA, EVOLUCION, INDICACIONES, ESTADO, FECHA_CIERRE, CREATED_AT
            FROM ATENCION
            WHERE PUBLIC_ID = ?
            """;
        List<Atencion> lista = jdbcTemplate.query(sql, atencionRowMapper, publicId);
        return lista.stream().findFirst();
    }

    public Optional<Atencion> buscarEntidadPorId(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, DIAGNOSTICO_PRINCIPAL_ID,
                   MOTIVO_CONSULTA, EVOLUCION, INDICACIONES, ESTADO, FECHA_CIERRE, CREATED_AT
            FROM ATENCION
            WHERE ID = ?
            """;
        List<Atencion> lista = jdbcTemplate.query(sql, atencionRowMapper, id);
        return lista.stream().findFirst();
    }

    public Optional<Atencion> buscarEntidadPorCitaId(Long citaId) {
        if (citaId == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT ID, PUBLIC_ID, CITA_ID, PACIENTE_ID, PROFESIONAL_ID, DIAGNOSTICO_PRINCIPAL_ID,
                   MOTIVO_CONSULTA, EVOLUCION, INDICACIONES, ESTADO, FECHA_CIERRE, CREATED_AT
            FROM ATENCION
            WHERE CITA_ID = ?
            """;
        List<Atencion> lista = jdbcTemplate.query(sql, atencionRowMapper, citaId);
        return lista.stream().findFirst();
    }

    public boolean existePorCitaId(Long citaId) {
        if (citaId == null) {
            return false;
        }
        String sql = "SELECT COUNT(*) FROM ATENCION WHERE CITA_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, citaId);
        return count != null && count > 0;
    }

    /**
     * Cierra y consolida una atención médica haciéndola inmutable (ADR-008).
     */
    public void cerrarAtencion(
            Long atencionId,
            Long diagnosticoId,
            String motivoConsulta,
            String evolucion,
            String indicaciones,
            Instant fechaCierre
    ) {
        Objects.requireNonNull(atencionId, "atencionId no puede ser nulo");
        Objects.requireNonNull(diagnosticoId, "diagnosticoId no puede ser nulo");
        Objects.requireNonNull(motivoConsulta, "motivoConsulta no puede ser nulo");
        Objects.requireNonNull(evolucion, "evolucion no puede ser nula");
        Objects.requireNonNull(indicaciones, "indicaciones no puede ser nulo");
        Objects.requireNonNull(fechaCierre, "fechaCierre no puede ser nula");

        String sql = """
            UPDATE ATENCION
            SET ESTADO = 'CERRADA',
                FECHA_CIERRE = ?,
                DIAGNOSTICO_PRINCIPAL_ID = ?,
                MOTIVO_CONSULTA = ?,
                EVOLUCION = ?,
                INDICACIONES = ?
            WHERE ID = ? AND ESTADO = 'ABIERTA'
            """;

        int filas = jdbcTemplate.update(
                sql,
                Timestamp.from(fechaCierre),
                diagnosticoId,
                motivoConsulta,
                evolucion,
                indicaciones,
                atencionId
        );

        if (filas == 0) {
            throw new IllegalStateException("No fue posible cerrar la atencion: no existe o ya se encuentra cerrada.");
        }
    }

    /**
     * Persiste los signos vitales medidos durante la atención médica.
     */
    public void guardarSignosVitales(SignoVital sv) {
        Objects.requireNonNull(sv, "SignoVital no puede ser nulo");
        Objects.requireNonNull(sv.atencionId(), "atencionId no puede ser nulo");

        String sql = """
            INSERT INTO SIGNO_VITAL (
                ATENCION_ID, PRESION_SISTOLICA, PRESION_DIASTOLICA, FRECUENCIA_CARDIACA,
                FRECUENCIA_RESPIRATORIA, TEMPERATURA, SATURACION_OXIGENO, PESO_KG, TALLA_CM
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql);
            ps.setLong(1, sv.atencionId());
            setNullableInt(ps, 2, sv.presionSistolica());
            setNullableInt(ps, 3, sv.presionDiastolica());
            setNullableInt(ps, 4, sv.frecuenciaCardiaca());
            setNullableInt(ps, 5, sv.frecuenciaRespiratoria());
            setNullableBigDecimal(ps, 6, sv.temperatura());
            setNullableInt(ps, 7, sv.saturacionOxigeno());
            setNullableBigDecimal(ps, 8, sv.pesoKg());
            setNullableBigDecimal(ps, 9, sv.tallaCm());
            return ps;
        });
    }

    public Optional<SignosVitalesDto> buscarSignosVitalesPorAtencionId(Long atencionId) {
        if (atencionId == null) {
            return Optional.empty();
        }
        String sql = """
            SELECT PRESION_SISTOLICA, PRESION_DIASTOLICA, FRECUENCIA_CARDIACA,
                   FRECUENCIA_RESPIRATORIA, TEMPERATURA, SATURACION_OXIGENO, PESO_KG, TALLA_CM
            FROM SIGNO_VITAL
            WHERE ATENCION_ID = ?
            """;

        List<SignosVitalesDto> lista = jdbcTemplate.query(sql, (rs, rowNum) -> new SignosVitalesDto(
                getNullableInt(rs, "PRESION_SISTOLICA"),
                getNullableInt(rs, "PRESION_DIASTOLICA"),
                getNullableInt(rs, "FRECUENCIA_CARDIACA"),
                getNullableInt(rs, "FRECUENCIA_RESPIRATORIA"),
                rs.getBigDecimal("TEMPERATURA"),
                getNullableInt(rs, "SATURACION_OXIGENO"),
                rs.getBigDecimal("PESO_KG"),
                rs.getBigDecimal("TALLA_CM")
        ), atencionId);

        return lista.stream().findFirst();
    }

    /**
     * Consulta detallada de una atención médica con JOINs relacionales y signos vitales (HU-07, HU-09).
     */
    public Optional<AtencionResponse> buscarDetallePorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT a.ID AS ATENCION_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   c.PUBLIC_ID AS CITA_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   a.ESTADO,
                   a.CREATED_AT,
                   a.FECHA_CIERRE,
                   d.CODIGO AS DIAGNOSTICO_CODIGO,
                   d.DESCRIPCION AS DIAGNOSTICO_DESCRIPCION,
                   a.MOTIVO_CONSULTA,
                   a.EVOLUCION,
                   a.INDICACIONES
            FROM ATENCION a
            JOIN CITA c ON a.CITA_ID = c.ID
            JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON a.PROFESIONAL_ID = pr.ID
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            LEFT JOIN DIAGNOSTICO_CIE10 d ON a.DIAGNOSTICO_PRINCIPAL_ID = d.ID
            WHERE a.PUBLIC_ID = ?
            """;

        List<AtencionResponse> lista = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long atencionId = rs.getLong("ATENCION_ID");
            SignosVitalesDto signos = buscarSignosVitalesPorAtencionId(atencionId).orElse(null);

            Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
            Timestamp tsCierre = rs.getTimestamp("FECHA_CIERRE");

            return new AtencionResponse(
                    rs.getString("ATENCION_PUBLIC_ID"),
                    rs.getString("CITA_PUBLIC_ID"),
                    rs.getString("PACIENTE_PUBLIC_ID"),
                    rs.getString("PACIENTE_NOMBRE"),
                    rs.getString("PROFESIONAL_PUBLIC_ID"),
                    rs.getString("PROFESIONAL_NOMBRE"),
                    rs.getString("ESPECIALIDAD_NOMBRE"),
                    rs.getString("ESTADO"),
                    tsCreated != null ? tsCreated.toInstant() : null,
                    tsCierre != null ? tsCierre.toInstant() : null,
                    rs.getString("DIAGNOSTICO_CODIGO"),
                    rs.getString("DIAGNOSTICO_DESCRIPCION"),
                    rs.getString("MOTIVO_CONSULTA"),
                    rs.getString("EVOLUCION"),
                    rs.getString("INDICACIONES"),
                    signos
            );
        }, publicId);

        return lista.stream().findFirst();
    }

    /**
     * Verifica si existe una atención médica previa entre el profesional y el paciente
     * realizada dentro de la ventana temporal especificada (ADR-007).
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

    private static void setNullableInt(PreparedStatement ps, int paramIndex, Integer value) throws SQLException {
        if (value != null) {
            ps.setInt(paramIndex, value);
        } else {
            ps.setNull(paramIndex, Types.INTEGER);
        }
    }

    private static void setNullableBigDecimal(PreparedStatement ps, int paramIndex, BigDecimal value) throws SQLException {
        if (value != null) {
            ps.setBigDecimal(paramIndex, value);
        } else {
            ps.setNull(paramIndex, Types.DECIMAL);
        }
    }

    private static Integer getNullableInt(ResultSet rs, String colName) throws SQLException {
        int v = rs.getInt(colName);
        return rs.wasNull() ? null : v;
    }
}

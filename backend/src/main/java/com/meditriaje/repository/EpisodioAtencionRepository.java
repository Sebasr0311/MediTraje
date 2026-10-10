package com.meditriaje.repository;

import com.meditriaje.dto.emergency.EpisodioUrgenciaResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.model.EpisodioAtencion;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para {@code EPISODIO_ATENCION} (Fase U, ADR-022, U02, U05).
 */
@Repository
public class EpisodioAtencionRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<EpisodioAtencion> rowMapper = (rs, rowNum) -> {
        Timestamp tsIngreso = rs.getTimestamp("INGRESO_AT");
        Timestamp tsEgreso = rs.getTimestamp("EGRESO_AT");
        Timestamp tsCreated = rs.getTimestamp("CREADO_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        long pacId = rs.getLong("PACIENTE_ID");

        return new EpisodioAtencion(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.wasNull() ? null : pacId,
                rs.getString("TIPO"),
                rs.getLong("SEDE_ID"),
                rs.getString("ESTADO"),
                tsIngreso != null ? tsIngreso.toInstant() : null,
                tsEgreso != null ? tsEgreso.toInstant() : null,
                rs.getString("MOTIVO_INGRESO"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    public EpisodioAtencionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    public EpisodioAtencion guardar(EpisodioAtencion episodio) {
        String sql = """
                INSERT INTO EPISODIO_ATENCION (
                    PUBLIC_ID, PACIENTE_ID, TIPO, SEDE_ID, ESTADO, INGRESO_AT, MOTIVO_INGRESO
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, episodio.publicId());
            if (episodio.pacienteId() != null) {
                ps.setLong(2, episodio.pacienteId());
            } else {
                ps.setNull(2, java.sql.Types.NUMERIC);
            }
            ps.setString(3, episodio.tipo());
            ps.setLong(4, episodio.sedeId());
            ps.setString(5, episodio.estado());
            ps.setTimestamp(6, episodio.ingresoAt() != null ? Timestamp.from(episodio.ingresoAt()) : Timestamp.from(Instant.now()));
            ps.setString(7, episodio.motivoIngreso());
            return ps;
        }, keyHolder);

        Number id = keyHolder.getKey();
        Long generatedId = id != null ? id.longValue() : null;

        return new EpisodioAtencion(
                generatedId,
                episodio.publicId(),
                episodio.pacienteId(),
                episodio.tipo(),
                episodio.sedeId(),
                episodio.estado(),
                episodio.ingresoAt() != null ? episodio.ingresoAt() : Instant.now(),
                episodio.egresoAt(),
                episodio.motivoIngreso(),
                Instant.now(),
                Instant.now()
        );
    }

    public Optional<EpisodioAtencion> buscarPorId(Long id) {
        String sql = "SELECT * FROM EPISODIO_ATENCION WHERE ID = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public Optional<EpisodioAtencion> buscarPorPublicId(String publicId) {
        String sql = "SELECT * FROM EPISODIO_ATENCION WHERE PUBLIC_ID = ?";
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, rowMapper, publicId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    public int actualizarEstado(Long id, String nuevoEstado) {
        String sql = "UPDATE EPISODIO_ATENCION SET ESTADO = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = ?";
        return jdbcTemplate.update(sql, nuevoEstado, id);
    }

    public int vincularPaciente(Long id, Long pacienteId) {
        String sql = "UPDATE EPISODIO_ATENCION SET PACIENTE_ID = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = ?";
        return jdbcTemplate.update(sql, pacienteId, id);
    }

    public int cerrarEpisodio(Long id, Instant egresoAt) {
        String sql = "UPDATE EPISODIO_ATENCION SET ESTADO = 'EGRESADO', EGRESO_AT = ?, UPDATED_AT = CURRENT_TIMESTAMP WHERE ID = ?";
        return jdbcTemplate.update(sql, Timestamp.from(egresoAt != null ? egresoAt : Instant.now()), id);
    }

    /**
     * Consulta detallada del episodio con joins a paciente, sede, triaje actual y equipo asistencial.
     */
    public Optional<EpisodioUrgenciaResponse> buscarDetallePorPublicId(String publicId) {
        String sql = """
                SELECT e.PUBLIC_ID AS EPISODIO_PUB_ID,
                       p.PUBLIC_ID AS PACIENTE_PUB_ID,
                       CASE WHEN p.ID IS NOT NULL THEN (u.NOMBRE || ' ' || u.APELLIDO) ELSE NULL END AS PACIENTE_NOMBRE,
                       s.PUBLIC_ID AS SEDE_PUB_ID,
                       s.NOMBRE AS SEDE_NOMBRE,
                       e.TIPO,
                       e.ESTADO,
                       ip.CODIGO_PROVISIONAL,
                       ip.ESTADO AS IDENTIDAD_ESTADO,
                       iu.MEDIO_LLEGADA,
                       iu.MOTIVO_RESUMIDO,
                       (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) AS NIVEL_TRIAJE,
                       (SELECT up.NOMBRE || ' ' || up.APELLIDO FROM ASIGNACION_ASISTENCIAL aa
                        JOIN PROFESIONAL prof ON aa.PROFESIONAL_ID = prof.ID
                        JOIN USUARIO up ON prof.USUARIO_ID = up.ID
                        WHERE aa.EPISODIO_ID = e.ID AND aa.ACTIVO = 1 AND aa.FUNCION = 'MEDICO_TRATANTE'
                        FETCH FIRST 1 ROWS ONLY) AS MEDICO_TRATANTE_NOMBRE,
                       e.INGRESO_AT,
                       e.EGRESO_AT,
                       e.CREADO_AT
                FROM EPISODIO_ATENCION e
                JOIN SEDE s ON e.SEDE_ID = s.ID
                LEFT JOIN PACIENTE p ON e.PACIENTE_ID = p.ID
                LEFT JOIN USUARIO u ON p.USUARIO_ID = u.ID
                LEFT JOIN INGRESO_URGENCIA iu ON e.ID = iu.EPISODIO_ID
                LEFT JOIN IDENTIDAD_PROVISIONAL ip ON e.ID = ip.EPISODIO_ID
                WHERE e.PUBLIC_ID = ?
                """;

        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(sql, (rs, rowNum) -> {
                Timestamp tsIngreso = rs.getTimestamp("INGRESO_AT");
                Timestamp tsEgreso = rs.getTimestamp("EGRESO_AT");
                Timestamp tsCreado = rs.getTimestamp("CREADO_AT");
                String codProv = rs.getString("CODIGO_PROVISIONAL");

                return new EpisodioUrgenciaResponse(
                        rs.getString("EPISODIO_PUB_ID"),
                        rs.getString("PACIENTE_PUB_ID"),
                        rs.getString("PACIENTE_NOMBRE"),
                        rs.getString("SEDE_PUB_ID"),
                        rs.getString("SEDE_NOMBRE"),
                        rs.getString("TIPO"),
                        rs.getString("ESTADO"),
                        codProv != null,
                        codProv,
                        rs.getString("IDENTIDAD_ESTADO"),
                        rs.getString("MEDIO_LLEGADA"),
                        rs.getString("MOTIVO_RESUMIDO"),
                        rs.getString("NIVEL_TRIAJE") != null ? rs.getString("NIVEL_TRIAJE") : "PENDIENTE_VALORACION",
                        rs.getString("MEDICO_TRATANTE_NOMBRE"),
                        tsIngreso != null ? tsIngreso.toInstant() : null,
                        tsEgreso != null ? tsEgreso.toInstant() : null,
                        tsCreado != null ? tsCreado.toInstant() : null
                );
            }, publicId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    /**
     * Consulta la cola priorizada de urgencias por sede (U05).
     * Orden: Prioridad I > II > III > IV > V > PENDIENTE_VALORACION, y luego por menor tiempo de llegada.
     */
    public List<ItemColaUrgenciaResponse> listarColaUrgenciaPorSede(Long sedeId, String estadoFiltro) {
        StringBuilder sql = new StringBuilder("""
                SELECT e.PUBLIC_ID AS EPISODIO_PUB_ID,
                       p.PUBLIC_ID AS PACIENTE_PUB_ID,
                       CASE 
                         WHEN p.ID IS NOT NULL THEN (u.NOMBRE || ' ' || u.APELLIDO)
                         WHEN ip.CODIGO_PROVISIONAL IS NOT NULL THEN ('NN (' || ip.CODIGO_PROVISIONAL || ')')
                         ELSE 'Paciente no identificado'
                       END AS IDENTIFICADOR_VISIBLE,
                       ip.CODIGO_PROVISIONAL,
                       s.PUBLIC_ID AS SEDE_PUB_ID,
                       s.NOMBRE AS SEDE_NOMBRE,
                       e.TIPO,
                       e.ESTADO,
                       COALESCE((SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY), 'PENDIENTE_VALORACION') AS NIVEL_TRIAJE,
                       COALESCE(iu.MOTIVO_RESUMIDO, e.MOTIVO_INGRESO) AS MOTIVO_LLEGADA,
                       ROUND((CAST(CURRENT_TIMESTAMP AS DATE) - CAST(e.INGRESO_AT AS DATE)) * 1440) AS MINUTOS_ESPERA,
                       e.INGRESO_AT,
                       (SELECT up.NOMBRE || ' ' || up.APELLIDO FROM ASIGNACION_ASISTENCIAL aa
                        JOIN PROFESIONAL prof ON aa.PROFESIONAL_ID = prof.ID
                        JOIN USUARIO up ON prof.USUARIO_ID = up.ID
                        WHERE aa.EPISODIO_ID = e.ID AND aa.ACTIVO = 1 AND aa.FUNCION = 'MEDICO_TRATANTE'
                        FETCH FIRST 1 ROWS ONLY) AS MEDICO_TRATANTE_NOMBRE
                FROM EPISODIO_ATENCION e
                JOIN SEDE s ON e.SEDE_ID = s.ID
                LEFT JOIN PACIENTE p ON e.PACIENTE_ID = p.ID
                LEFT JOIN USUARIO u ON p.USUARIO_ID = u.ID
                LEFT JOIN INGRESO_URGENCIA iu ON e.ID = iu.EPISODIO_ID
                LEFT JOIN IDENTIDAD_PROVISIONAL ip ON e.ID = ip.EPISODIO_ID
                WHERE e.TIPO = 'URGENCIA'
                  AND e.ESTADO NOT IN ('EGRESADO', 'CANCELADO')
                """);

        List<Object> params = new ArrayList<>();
        if (sedeId != null) {
            sql.append(" AND e.SEDE_ID = ? ");
            params.add(sedeId);
        }
        if (estadoFiltro != null && !estadoFiltro.isBlank()) {
            sql.append(" AND e.ESTADO = ? ");
            params.add(estadoFiltro);
        }

        // Orden de clasificación clínica según Resolución 5596/2015
        sql.append("""
                ORDER BY 
                  CASE 
                    WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'I' THEN 1
                    WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'II' THEN 2
                    WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'III' THEN 3
                    WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'IV' THEN 4
                    WHEN (SELECT vt.NIVEL FROM VALORACION_TRIAJE vt WHERE vt.EPISODIO_ID = e.ID ORDER BY vt.VERSION DESC FETCH FIRST 1 ROWS ONLY) = 'V' THEN 5
                    ELSE 6
                  END ASC,
                  e.INGRESO_AT ASC
                """);

        return jdbcTemplate.query(sql.toString(), (rs, rowNum) -> {
            Timestamp tsIngreso = rs.getTimestamp("INGRESO_AT");
            long minutos = rs.getLong("MINUTOS_ESPERA");
            if (minutos < 0) minutos = 0;

            return new ItemColaUrgenciaResponse(
                    rs.getString("EPISODIO_PUB_ID"),
                    rs.getString("PACIENTE_PUB_ID"),
                    rs.getString("IDENTIFICADOR_VISIBLE"),
                    rs.getString("CODIGO_PROVISIONAL"),
                    rs.getString("SEDE_PUB_ID"),
                    rs.getString("SEDE_NOMBRE"),
                    rs.getString("TIPO"),
                    rs.getString("ESTADO"),
                    rs.getString("NIVEL_TRIAJE"),
                    rs.getString("MOTIVO_LLEGADA"),
                    minutos,
                    tsIngreso != null ? tsIngreso.toInstant() : null,
                    rs.getString("MEDICO_TRATANTE_NOMBRE")
            );
        }, params.toArray());
    }
}

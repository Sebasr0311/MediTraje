package com.meditriaje.repository;

import com.meditriaje.dto.allergy.AlergiaResponse;
import com.meditriaje.model.Alergia;
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
import java.util.UUID;

/**
 * Repositorio JDBC para la entidad {@code ALERGIA} (ADR-008, V008, V016, T4).
 * Consultas e inserciones con SQL 100% parametrizado.
 */
@Repository
public class AlergiaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Alergia> rowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsUpdated = rs.getTimestamp("UPDATED_AT");
        Timestamp tsInact = rs.getTimestamp("FECHA_INACTIVACION");
        long regPor = rs.getLong("REGISTRADA_POR_USUARIO_ID");
        long inactPor = rs.getLong("INACTIVADA_POR_USUARIO_ID");
        long atencionId = rs.getLong("ATENCION_ID");

        return new Alergia(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getString("SUSTANCIA"),
                rs.getString("REACCION"),
                rs.getString("SEVERIDAD"),
                rs.getString("ESTADO"),
                rs.getString("ORIGEN"),
                rs.wasNull() ? null : regPor,
                rs.wasNull() ? null : atencionId,
                tsInact != null ? tsInact.toInstant() : null,
                rs.wasNull() ? null : inactPor,
                rs.getString("MOTIVO_INACTIVACION"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsUpdated != null ? tsUpdated.toInstant() : null
        );
    };

    private final RowMapper<AlergiaResponse> responseRowMapper = (rs, rowNum) -> {
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Timestamp tsInact = rs.getTimestamp("FECHA_INACTIVACION");
        String origen = rs.getString("ORIGEN");

        return new AlergiaResponse(
                rs.getString("PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("SUSTANCIA"),
                rs.getString("REACCION"),
                rs.getString("SEVERIDAD"),
                rs.getString("ESTADO"),
                origen,
                "PACIENTE".equalsIgnoreCase(origen),
                rs.getString("ATENCION_PUBLIC_ID"),
                tsCreated != null ? tsCreated.toInstant() : null,
                tsInact != null ? tsInact.toInstant() : null,
                rs.getString("MOTIVO_INACTIVACION")
        );
    };

    public AlergiaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Lista alergias de un paciente por su ID numérico interno (solo activas por defecto).
     */
    public List<Alergia> listarPorPacienteId(Long pacienteId) {
        return listarPorPacienteId(pacienteId, true);
    }

    /**
     * Lista alergias de un paciente por su ID numérico interno.
     */
    public List<Alergia> listarPorPacienteId(Long pacienteId, boolean soloActivas) {
        if (pacienteId == null) {
            return List.of();
        }
        String sql = """
                SELECT ID, PUBLIC_ID, PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD, ESTADO, ORIGEN,
                       REGISTRADA_POR_USUARIO_ID, ATENCION_ID, FECHA_INACTIVACION, INACTIVADA_POR_USUARIO_ID,
                       MOTIVO_INACTIVACION, CREATED_AT, UPDATED_AT
                FROM ALERGIA
                WHERE PACIENTE_ID = ?
                  AND (? = 0 OR ESTADO = 'ACTIVA')
                ORDER BY CREATED_AT DESC
                """;
        return jdbcTemplate.query(sql, rowMapper, pacienteId, soloActivas ? 1 : 0);
    }

    /**
     * Lista alergias en formato DTO para un paciente identificado por su UUID público.
     */
    public List<AlergiaResponse> listarPorPacientePublicId(String pacientePublicId, boolean incluirInactivas) {
        if (pacientePublicId == null || pacientePublicId.isBlank()) {
            return List.of();
        }
        String sql = """
                SELECT a.PUBLIC_ID, p.PUBLIC_ID AS PACIENTE_PUBLIC_ID, a.SUSTANCIA, a.REACCION, a.SEVERIDAD,
                       a.ESTADO, a.ORIGEN, at.PUBLIC_ID AS ATENCION_PUBLIC_ID, a.CREATED_AT,
                       a.FECHA_INACTIVACION, a.MOTIVO_INACTIVACION
                FROM ALERGIA a
                JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
                LEFT JOIN ATENCION at ON a.ATENCION_ID = at.ID
                WHERE p.PUBLIC_ID = ?
                  AND (? = 1 OR a.ESTADO = 'ACTIVA')
                ORDER BY CASE WHEN a.ESTADO = 'ACTIVA' THEN 1 ELSE 2 END, a.CREATED_AT DESC
                """;
        return jdbcTemplate.query(sql, responseRowMapper, pacientePublicId, incluirInactivas ? 1 : 0);
    }

    /**
     * Busca la entidad Alergia por su UUID público.
     */
    public Optional<Alergia> buscarEntidadPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT ID, PUBLIC_ID, PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD, ESTADO, ORIGEN,
                       REGISTRADA_POR_USUARIO_ID, ATENCION_ID, FECHA_INACTIVACION, INACTIVADA_POR_USUARIO_ID,
                       MOTIVO_INACTIVACION, CREATED_AT, UPDATED_AT
                FROM ALERGIA
                WHERE PUBLIC_ID = ?
                """;
        List<Alergia> lista = jdbcTemplate.query(sql, rowMapper, publicId);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    /**
     * Busca la alergia y retorna su DTO AlergiaResponse.
     */
    public Optional<AlergiaResponse> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }
        String sql = """
                SELECT a.PUBLIC_ID, p.PUBLIC_ID AS PACIENTE_PUBLIC_ID, a.SUSTANCIA, a.REACCION, a.SEVERIDAD,
                       a.ESTADO, a.ORIGEN, at.PUBLIC_ID AS ATENCION_PUBLIC_ID, a.CREATED_AT,
                       a.FECHA_INACTIVACION, a.MOTIVO_INACTIVACION
                FROM ALERGIA a
                JOIN PACIENTE p ON a.PACIENTE_ID = p.ID
                LEFT JOIN ATENCION at ON a.ATENCION_ID = at.ID
                WHERE a.PUBLIC_ID = ?
                """;
        List<AlergiaResponse> lista = jdbcTemplate.query(sql, responseRowMapper, publicId);
        return lista.isEmpty() ? Optional.empty() : Optional.of(lista.get(0));
    }

    /**
     * Verifica si existe una alergia ACTIVA para el paciente y la sustancia dada.
     */
    public boolean existeActivaPorSustancia(Long pacienteId, String sustancia) {
        if (pacienteId == null || sustancia == null || sustancia.isBlank()) {
            return false;
        }
        String sql = """
                SELECT COUNT(*)
                FROM ALERGIA
                WHERE PACIENTE_ID = ?
                  AND UPPER(TRIM(SUSTANCIA)) = UPPER(TRIM(?))
                  AND ESTADO = 'ACTIVA'
                """;
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, pacienteId, sustancia);
        return count != null && count > 0;
    }

    /**
     * Registra una nueva alergia para un paciente.
     */
    public Long crear(Alergia alergia) {
        Objects.requireNonNull(alergia, "alergia no puede ser nula");
        String publicId = alergia.publicId() != null && !alergia.publicId().isBlank()
                ? alergia.publicId()
                : UUID.randomUUID().toString();
        String estado = alergia.estado() != null ? alergia.estado() : "ACTIVA";
        String origen = alergia.origen() != null ? alergia.origen() : "PROFESIONAL";
        Instant createdAt = alergia.createdAt() != null ? alergia.createdAt() : Instant.now();
        Instant updatedAt = alergia.updatedAt() != null ? alergia.updatedAt() : createdAt;

        String sql = """
                INSERT INTO ALERGIA (
                    PUBLIC_ID, PACIENTE_ID, SUSTANCIA, REACCION, SEVERIDAD, ESTADO, ORIGEN,
                    REGISTRADA_POR_USUARIO_ID, ATENCION_ID, CREATED_AT, UPDATED_AT
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, publicId);
            ps.setLong(2, alergia.pacienteId());
            ps.setString(3, alergia.sustancia().trim());
            ps.setString(4, alergia.reaccion() != null ? alergia.reaccion().trim() : null);
            ps.setString(5, alergia.severidad());
            ps.setString(6, estado);
            ps.setString(7, origen);

            if (alergia.registradaPorUsuarioId() != null) {
                ps.setLong(8, alergia.registradaPorUsuarioId());
            } else {
                ps.setNull(8, java.sql.Types.NUMERIC);
            }

            if (alergia.atencionId() != null) {
                ps.setLong(9, alergia.atencionId());
            } else {
                ps.setNull(9, java.sql.Types.NUMERIC);
            }

            ps.setTimestamp(10, Timestamp.from(createdAt));
            ps.setTimestamp(11, Timestamp.from(updatedAt));
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key != null) {
            return key.longValue();
        }
        if (!keyHolder.getKeyList().isEmpty() && keyHolder.getKeyList().get(0).containsKey("ID")) {
            return ((Number) keyHolder.getKeyList().get(0).get("ID")).longValue();
        }
        throw new IllegalStateException("No se pudo obtener el ID autogenerado para ALERGIA.");
    }

    /**
     * Inactiva una alergia activa estableciendo su motivo, usuario y fecha de inactivación.
     */
    public int inactivar(Long alergiaId, Long inactivadaPorUsuarioId, String motivo, Instant fechaInactivacion) {
        Objects.requireNonNull(alergiaId, "alergiaId no puede ser nulo");
        Objects.requireNonNull(inactivadaPorUsuarioId, "inactivadaPorUsuarioId no puede ser nulo");
        Objects.requireNonNull(motivo, "motivo no puede ser nulo");

        Instant fecha = fechaInactivacion != null ? fechaInactivacion : Instant.now();

        String sql = """
                UPDATE ALERGIA
                SET ESTADO = 'INACTIVA',
                    INACTIVADA_POR_USUARIO_ID = ?,
                    MOTIVO_INACTIVACION = ?,
                    FECHA_INACTIVACION = ?,
                    UPDATED_AT = CURRENT_TIMESTAMP
                WHERE ID = ? AND ESTADO = 'ACTIVA'
                """;

        return jdbcTemplate.update(
                sql,
                inactivadaPorUsuarioId,
                motivo.trim(),
                Timestamp.from(fecha),
                alergiaId
        );
    }
}

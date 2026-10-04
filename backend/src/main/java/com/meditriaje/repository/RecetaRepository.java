package com.meditriaje.repository;

import com.meditriaje.dto.prescription.RecetaDetalleResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.model.Receta;
import com.meditriaje.model.RecetaDetalle;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para la entidad {@code RECETA} y sus ítems {@code RECETA_DETALLE}.
 * Persistencia y consultas inmutables con SQL 100% parametrizado (ADR-001, ADR-007, ADR-008, HU-08).
 */
@Repository
public class RecetaRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Receta> recetaRowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new Receta(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("ATENCION_ID"),
                rs.getLong("PACIENTE_ID"),
                rs.getLong("PROFESIONAL_ID"),
                rs.getInt("VIGENCIA_DIAS"),
                ts != null ? ts.toInstant() : null
        );
    };

    public RecetaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Persiste una nueva receta médica. Inmutable desde su inserción (ADR-008).
     *
     * @param receta Entidad receta a persistir
     * @return Identificador numérico generado en base de datos
     */
    public Long crearReceta(Receta receta) {
        Objects.requireNonNull(receta, "receta no puede ser nula");

        String sql = """
            INSERT INTO RECETA (PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, VIGENCIA_DIAS)
            VALUES (?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, receta.publicId());
            ps.setLong(2, receta.atencionId());
            ps.setLong(3, receta.pacienteId());
            ps.setLong(4, receta.profesionalId());
            ps.setInt(5, receta.vigenciaDias() > 0 ? receta.vigenciaDias() : 30);
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la receta.");
        }
        return key.longValue();
    }

    /**
     * Guarda por lotes los ítems prescritos en RECETA_DETALLE congelando los snapshots de fármaco.
     *
     * @param recetaId Identificador interno de la receta cabecera
     * @param detalles Lista de detalles con datos históricos
     */
    public void guardarDetalles(Long recetaId, List<RecetaDetalle> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO RECETA_DETALLE (
                RECETA_ID, MEDICAMENTO_ID, SNAPSHOT_NOMBRE, SNAPSHOT_PRINCIPIO_ACTIVO,
                SNAPSHOT_PRESENTACION, SNAPSHOT_CONCENTRACION, DOSIS, FRECUENCIA,
                DURACION_DIAS, CANTIDAD, INDICACIONES
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                RecetaDetalle d = detalles.get(i);
                ps.setLong(1, recetaId != null ? recetaId : d.recetaId());
                ps.setLong(2, d.medicamentoId());
                ps.setString(3, d.snapshotNombre());
                ps.setString(4, d.snapshotPrincipioActivo());
                ps.setString(5, d.snapshotPresentacion());
                ps.setString(6, d.snapshotConcentracion());
                ps.setString(7, d.dosis());
                ps.setString(8, d.frecuencia());
                ps.setInt(9, d.duracionDias());
                ps.setInt(10, d.cantidad());
                ps.setString(11, d.indicaciones());
            }

            @Override
            public int getBatchSize() {
                return detalles.size();
            }
        });
    }

    /**
     * Consulta detallada de una receta médica con JOINs relacionales y detalles de prescripción (HU-08).
     *
     * @param publicId UUID público de la receta
     * @return RecetaResponse con detalles si existe
     */
    public Optional<RecetaResponse> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   r.VIGENCIA_DIAS,
                   r.CREATED_AT
            FROM RECETA r
            JOIN ATENCION a ON r.ATENCION_ID = a.ID
            JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON r.PROFESIONAL_ID = pr.ID
            JOIN CITA c ON a.CITA_ID = c.ID
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            WHERE r.PUBLIC_ID = ?
            """;

        List<RecetaResponse> lista = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long recetaId = rs.getLong("RECETA_ID");
            List<RecetaDetalleResponse> detalles = buscarDetallesPorRecetaId(recetaId);
            Timestamp tsCreated = rs.getTimestamp("CREATED_AT");

            return new RecetaResponse(
                    rs.getString("RECETA_PUBLIC_ID"),
                    rs.getString("ATENCION_PUBLIC_ID"),
                    rs.getString("PACIENTE_PUBLIC_ID"),
                    rs.getString("PACIENTE_NOMBRE"),
                    rs.getString("PROFESIONAL_PUBLIC_ID"),
                    rs.getString("PROFESIONAL_NOMBRE"),
                    rs.getString("ESPECIALIDAD_NOMBRE"),
                    rs.getInt("VIGENCIA_DIAS"),
                    tsCreated != null ? tsCreated.toInstant() : null,
                    detalles
            );
        }, publicId.trim());

        return lista.stream().findFirst();
    }

    /**
     * Consulta la entidad interna inmutable {@link Receta} por su UUID público.
     *
     * @param publicId UUID público de la receta
     * @return Entidad Receta si existe
     */
    public Optional<Receta> buscarEntidadPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT ID, PUBLIC_ID, ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, VIGENCIA_DIAS, CREATED_AT
            FROM RECETA
            WHERE PUBLIC_ID = ?
            """;

        List<Receta> lista = jdbcTemplate.query(sql, recetaRowMapper, publicId.trim());
        return lista.stream().findFirst();
    }

    /**
     * Consulta los detalles de una receta médica asegurando la inmutabilidad histórica
     * a través de los campos SNAPSHOT_*.
     *
     * @param recetaId Identificador interno de la receta
     * @return Lista de detalles con datos históricos
     */
    public List<RecetaDetalleResponse> buscarDetallesPorRecetaId(Long recetaId) {
        if (recetaId == null) {
            return List.of();
        }

        String sql = """
            SELECT m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   m.CODIGO AS MEDICAMENTO_CODIGO,
                   rd.SNAPSHOT_NOMBRE,
                   rd.SNAPSHOT_PRINCIPIO_ACTIVO,
                   rd.SNAPSHOT_PRESENTACION,
                   rd.SNAPSHOT_CONCENTRACION,
                   rd.DOSIS,
                   rd.FRECUENCIA,
                   rd.DURACION_DIAS,
                   rd.CANTIDAD,
                   rd.INDICACIONES
            FROM RECETA_DETALLE rd
            JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
            WHERE rd.RECETA_ID = ?
            ORDER BY rd.ID ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new RecetaDetalleResponse(
                rs.getString("MEDICAMENTO_PUBLIC_ID"),
                rs.getString("MEDICAMENTO_CODIGO"),
                rs.getString("SNAPSHOT_NOMBRE"),
                rs.getString("SNAPSHOT_PRINCIPIO_ACTIVO"),
                rs.getString("SNAPSHOT_PRESENTACION"),
                rs.getString("SNAPSHOT_CONCENTRACION"),
                rs.getString("DOSIS"),
                rs.getString("FRECUENCIA"),
                rs.getInt("DURACION_DIAS"),
                rs.getInt("CANTIDAD"),
                rs.getString("INDICACIONES")
        ), recetaId);
    }

    /**
     * Verifica si ya existe una receta médica emitida para una atención determinada.
     *
     * @param atencionId Identificador interno de la atención
     * @return true si existe al menos una receta para la atención
     */
    public boolean existePorAtencionId(Long atencionId) {
        if (atencionId == null) {
            return false;
        }

        String sql = "SELECT COUNT(*) FROM RECETA WHERE ATENCION_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, atencionId);
        return count != null && count > 0;
    }

    /**
     * Consulta paginada de recetas médicas emitidas para un paciente específico (HU-08, HU-09).
     *
     * @param pacienteId Identificador interno del paciente
     * @param page Número de página (0-indexed)
     * @param size Cantidad de elementos por página
     * @return Lista de recetas médicas del paciente con sus respectivos detalles
     */
    public List<RecetaResponse> listarPorPacienteId(Long pacienteId, int page, int size) {
        if (pacienteId == null) {
            return List.of();
        }

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));
        int offset = safePage * safeSize;

        String sql = """
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   TRIM(p.NOMBRES || ' ' || p.APELLIDOS) AS PACIENTE_NOMBRE,
                   pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                   TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS) AS PROFESIONAL_NOMBRE,
                   e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                   r.VIGENCIA_DIAS,
                   r.CREATED_AT
            FROM RECETA r
            JOIN ATENCION a ON r.ATENCION_ID = a.ID
            JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON r.PROFESIONAL_ID = pr.ID
            JOIN CITA c ON a.CITA_ID = c.ID
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            WHERE r.PACIENTE_ID = ?
            ORDER BY r.CREATED_AT DESC, r.ID DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long recetaId = rs.getLong("RECETA_ID");
            List<RecetaDetalleResponse> detalles = buscarDetallesPorRecetaId(recetaId);
            Timestamp tsCreated = rs.getTimestamp("CREATED_AT");

            return new RecetaResponse(
                    rs.getString("RECETA_PUBLIC_ID"),
                    rs.getString("ATENCION_PUBLIC_ID"),
                    rs.getString("PACIENTE_PUBLIC_ID"),
                    rs.getString("PACIENTE_NOMBRE"),
                    rs.getString("PROFESIONAL_PUBLIC_ID"),
                    rs.getString("PROFESIONAL_NOMBRE"),
                    rs.getString("ESPECIALIDAD_NOMBRE"),
                    rs.getInt("VIGENCIA_DIAS"),
                    tsCreated != null ? tsCreated.toInstant() : null,
                    detalles
            );
        }, pacienteId, offset, safeSize);
    }

    /**
     * Cuenta el total de recetas emitidas para un paciente (HU-08, HU-09).
     *
     * @param pacienteId Identificador interno del paciente
     * @return Total de recetas
     */
    public int contarPorPacienteId(Long pacienteId) {
        if (pacienteId == null) {
            return 0;
        }

        String sql = "SELECT COUNT(*) FROM RECETA WHERE PACIENTE_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, pacienteId);
        return count != null ? count : 0;
    }
}

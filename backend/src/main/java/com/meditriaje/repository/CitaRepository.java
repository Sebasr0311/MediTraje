package com.meditriaje.repository;

import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.model.Cita;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
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

        String triajeNivel = null;
        try {
            triajeNivel = rs.getString("TRIAJE_NIVEL");
        } catch (SQLException ignored) {
        }

        String motivoConsulta = null;
        try {
            motivoConsulta = rs.getString("MOTIVO_CONSULTA");
        } catch (SQLException ignored) {
        }

        boolean tieneAtencion = false;
        try {
            Object obj = rs.getObject("TIENE_ATENCION");
            if (obj != null) {
                tieneAtencion = rs.getInt("TIENE_ATENCION") > 0;
            }
        } catch (SQLException ignored) {
        }

        String atencionPublicId = null;
        try {
            atencionPublicId = rs.getString("ATENCION_PUBLIC_ID");
        } catch (SQLException ignored) {
        }

        String atencionEstado = null;
        try {
            atencionEstado = rs.getString("ATENCION_ESTADO");
        } catch (SQLException ignored) {
        }

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
                rs.getString("TRIAJE_PUBLIC_ID"),
                rs.getString("CITA_ORIGEN_PUBLIC_ID"),
                triajeNivel,
                motivoConsulta,
                tsCreated != null ? tsCreated.toInstant() : null,
                tieneAtencion,
                atencionPublicId,
                atencionEstado
        );
    };

    private final RowMapper<AdminCitaDetalleResponse> adminCitaDetalleRowMapper = (rs, rowNum) -> {
        Timestamp tsInicio = rs.getTimestamp("FECHA_HORA_INICIO");
        Timestamp tsFin = rs.getTimestamp("FECHA_HORA_FIN");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");

        return new AdminCitaDetalleResponse(
                rs.getString("CITA_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PACIENTE_DOC_TIPO"),
                rs.getString("PACIENTE_DOC_NUM"),
                rs.getString("PACIENTE_TELEFONO"),
                rs.getString("PACIENTE_EMAIL"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("REGISTRO_MEDICO"),
                rs.getString("ESPECIALIDAD_PUBLIC_ID"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                rs.getString("SEDE_PUBLIC_ID"),
                rs.getString("SEDE_NOMBRE"),
                rs.getString("SEDE_CIUDAD"),
                tsInicio != null ? tsInicio.toInstant() : null,
                tsFin != null ? tsFin.toInstant() : null,
                rs.getString("MODALIDAD"),
                rs.getString("ESTADO"),
                rs.getString("MOTIVO_CANCELACION"),
                rs.getString("TRIAJE_PUBLIC_ID"),
                rs.getString("TRIAJE_NIVEL"),
                rs.getString("MOTIVO_CONSULTA"),
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
                   t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                   co.PUBLIC_ID AS CITA_ORIGEN_PUBLIC_ID,
                   t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                   COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                   c.CREATED_AT,
                   (CASE WHEN atn.ID IS NOT NULL THEN 1 ELSE 0 END) AS TIENE_ATENCION,
                   atn.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   atn.ESTADO AS ATENCION_ESTADO
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
            LEFT JOIN CITA co ON c.CITA_ORIGEN_ID = co.ID
            LEFT JOIN ATENCION atn ON atn.CITA_ID = c.ID
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

    /**
     * Consulta la agenda de citas del profesional asistencial con filtros opcionales (HU-06, ADR-007).
     *
     * @param profesionalId Identificador interno del profesional
     * @param fechaDesde    Límite inferior opcional de inicio de slot
     * @param fechaHasta    Límite superior opcional de inicio de slot
     * @param estado        Filtro de estado opcional (PROGRAMADA, CONFIRMADA, ATENDIDA, CANCELADA, etc.)
     * @param page          Número de página (0-indexed)
     * @param size          Tamaño de página
     * @return Lista de citas ordenadas por fecha/hora de inicio ascendente e ID de cita ascendente
     */
    public List<CitaResponse> listarAgendaProfesional(
            Long profesionalId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado,
            int page,
            int size
    ) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");

        StringBuilder sql = new StringBuilder("""
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
                   t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                   co.PUBLIC_ID AS CITA_ORIGEN_PUBLIC_ID,
                   t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                   COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                   c.CREATED_AT,
                   (CASE WHEN atn.ID IS NOT NULL THEN 1 ELSE 0 END) AS TIENE_ATENCION,
                   atn.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   atn.ESTADO AS ATENCION_ESTADO
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
            LEFT JOIN CITA co ON c.CITA_ORIGEN_ID = co.ID
            LEFT JOIN ATENCION atn ON atn.CITA_ID = c.ID
            WHERE s.PROFESIONAL_ID = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(profesionalId);

        if (fechaDesde != null) {
            sql.append(" AND s.FECHA_HORA_INICIO >= ?");
            params.add(Timestamp.from(fechaDesde));
        }

        if (fechaHasta != null) {
            sql.append(" AND s.FECHA_HORA_INICIO <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND c.ESTADO = ?");
            params.add(estado.trim().toUpperCase(Locale.ROOT));
        }

        sql.append(" ORDER BY s.FECHA_HORA_INICIO ASC, c.ID ASC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);
        params.add(offset);
        params.add(limit);

        return jdbcTemplate.query(sql.toString(), citaResponseRowMapper, params.toArray());
    }

    /**
     * Cuenta el total de citas en la agenda del profesional para los filtros dados (HU-06).
     *
     * @param profesionalId Identificador interno del profesional
     * @param fechaDesde    Límite inferior opcional de inicio de slot
     * @param fechaHasta    Límite superior opcional de inicio de slot
     * @param estado        Filtro de estado opcional
     * @return Total de citas
     */
    public int contarAgendaProfesional(
            Long profesionalId,
            Instant fechaDesde,
            Instant fechaHasta,
            String estado
    ) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");

        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            WHERE s.PROFESIONAL_ID = ?
            """);

        List<Object> params = new ArrayList<>();
        params.add(profesionalId);

        if (fechaDesde != null) {
            sql.append(" AND s.FECHA_HORA_INICIO >= ?");
            params.add(Timestamp.from(fechaDesde));
        }

        if (fechaHasta != null) {
            sql.append(" AND s.FECHA_HORA_INICIO <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND c.ESTADO = ?");
            params.add(estado.trim().toUpperCase(Locale.ROOT));
        }

        Integer total = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return total != null ? total : 0;
    }

    /**
     * Verifica si existe una cita activa futura (PROGRAMADA o CONFIRMADA) entre un profesional
     * y un paciente para fundamentar relación asistencial (ADR-007).
     *
     * @param profesionalId Identificador interno del profesional
     * @param pacienteId    Identificador interno del paciente
     * @param ahora         Instante de referencia temporal
     * @return true si existe al menos una cita activa futura
     */
    public boolean existeCitaActivaFutura(Long profesionalId, Long pacienteId, Instant ahora) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(ahora, "ahora no puede ser nulo");

        String sql = """
            SELECT COUNT(*)
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            WHERE c.PACIENTE_ID = ?
              AND s.PROFESIONAL_ID = ?
              AND c.ESTADO IN ('PROGRAMADA', 'CONFIRMADA')
              AND s.FECHA_HORA_INICIO > ?
            """;

        Integer count = jdbcTemplate.queryForObject(
                sql,
                Integer.class,
                pacienteId,
                profesionalId,
                Timestamp.from(ahora)
        );
        return count != null && count > 0;
    }

    /**
     * Consulta paginada de las citas médicas reservadas por un paciente (HU-09).
     *
     * @param pacienteId Identificador interno del paciente
     * @param page       Número de página (0-indexed)
     * @param size       Tamaño de página
     * @return Lista de citas mapeadas a {@link CitaResponse}
     */
    public List<CitaResponse> listarPorPacienteId(Long pacienteId, int page, int size) {
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");

        String sql = """
            SELECT
                c.PUBLIC_ID AS CITA_PUBLIC_ID,
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
                t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                co.PUBLIC_ID AS CITA_ORIGEN_PUBLIC_ID,
                t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                c.CREATED_AT,
                (CASE WHEN atn.ID IS NOT NULL THEN 1 ELSE 0 END) AS TIENE_ATENCION,
                atn.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                atn.ESTADO AS ATENCION_ESTADO
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
            LEFT JOIN CITA co ON c.CITA_ORIGEN_ID = co.ID
            LEFT JOIN ATENCION atn ON atn.CITA_ID = c.ID
            WHERE c.PACIENTE_ID = ?
            ORDER BY s.FECHA_HORA_INICIO DESC, c.ID DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);

        return jdbcTemplate.query(sql, citaResponseRowMapper, pacienteId, offset, limit);
    }

    /**
     * Cuenta el total de citas asociadas a un paciente (HU-09).
     *
     * @param pacienteId Identificador interno del paciente
     * @return Total de citas
     */
    public int contarPorPacienteId(Long pacienteId) {
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        String sql = "SELECT COUNT(*) FROM CITA WHERE PACIENTE_ID = ?";
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, pacienteId);
        return count != null ? count : 0;
    }

    /**
     * Consulta consolidada de citas médicas para supervisión, calendario semanal y reportes de administrador.
     */
    public List<AdminCitaDetalleResponse> listarCitasAdmin(
            Instant fechaDesde,
            Instant fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado,
            int page,
            int size
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT
                c.PUBLIC_ID AS CITA_PUBLIC_ID,
                p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                p.NOMBRES || ' ' || p.APELLIDOS AS PACIENTE_NOMBRE,
                p.TIPO_DOCUMENTO AS PACIENTE_DOC_TIPO,
                p.NUMERO_DOCUMENTO AS PACIENTE_DOC_NUM,
                p.TELEFONO AS PACIENTE_TELEFONO,
                up.EMAIL AS PACIENTE_EMAIL,
                pr.PUBLIC_ID AS PROFESIONAL_PUBLIC_ID,
                pr.NOMBRES || ' ' || pr.APELLIDOS AS PROFESIONAL_NOMBRE,
                pr.REGISTRO_MEDICO AS REGISTRO_MEDICO,
                e.PUBLIC_ID AS ESPECIALIDAD_PUBLIC_ID,
                e.NOMBRE AS ESPECIALIDAD_NOMBRE,
                sd.PUBLIC_ID AS SEDE_PUBLIC_ID,
                sd.NOMBRE AS SEDE_NOMBRE,
                sd.CIUDAD AS SEDE_CIUDAD,
                s.FECHA_HORA_INICIO,
                s.FECHA_HORA_FIN,
                s.MODALIDAD,
                c.ESTADO,
                c.MOTIVO_CANCELACION,
                t.PUBLIC_ID AS TRIAJE_PUBLIC_ID,
                t.NIVEL_PRIORIDAD AS TRIAJE_NIVEL,
                COALESCE(t.OBSERVACIONES, 'Consulta médica general') AS MOTIVO_CONSULTA,
                c.CREATED_AT
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
            JOIN USUARIO up ON p.USUARIO_ID = up.ID
            JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            LEFT JOIN TRIAJE t ON c.TRIAJE_ID = t.ID
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND s.FECHA_HORA_INICIO >= ?");
            params.add(Timestamp.from(fechaDesde));
        }

        if (fechaHasta != null) {
            sql.append(" AND s.FECHA_HORA_INICIO <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        if (profesionalPublicId != null && !profesionalPublicId.isBlank()) {
            sql.append(" AND pr.PUBLIC_ID = ?");
            params.add(profesionalPublicId.trim());
        }

        if (especialidadPublicId != null && !especialidadPublicId.isBlank()) {
            sql.append(" AND e.PUBLIC_ID = ?");
            params.add(especialidadPublicId.trim());
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND c.ESTADO = ?");
            params.add(estado.trim().toUpperCase(Locale.ROOT));
        }

        sql.append(" ORDER BY s.FECHA_HORA_INICIO ASC, c.ID ASC");

        if (size > 0) {
            sql.append(" OFFSET ? ROWS FETCH NEXT ? ROWS ONLY");
            int offset = Math.max(0, page) * Math.max(1, size);
            params.add(offset);
            params.add(size);
        }

        return jdbcTemplate.query(sql.toString(), adminCitaDetalleRowMapper, params.toArray());
    }

    /**
     * Cuenta el total de citas para los filtros administrativos dados.
     */
    public int contarCitasAdmin(
            Instant fechaDesde,
            Instant fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado
    ) {
        StringBuilder sql = new StringBuilder("""
            SELECT COUNT(*)
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN PACIENTE p ON c.PACIENTE_ID = p.ID
            JOIN PROFESIONAL pr ON s.PROFESIONAL_ID = pr.ID
            JOIN ESPECIALIDAD e ON s.ESPECIALIDAD_ID = e.ID
            JOIN SEDE sd ON s.SEDE_ID = sd.ID
            WHERE 1 = 1
            """);

        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND s.FECHA_HORA_INICIO >= ?");
            params.add(Timestamp.from(fechaDesde));
        }

        if (fechaHasta != null) {
            sql.append(" AND s.FECHA_HORA_INICIO <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        if (profesionalPublicId != null && !profesionalPublicId.isBlank()) {
            sql.append(" AND pr.PUBLIC_ID = ?");
            params.add(profesionalPublicId.trim());
        }

        if (especialidadPublicId != null && !especialidadPublicId.isBlank()) {
            sql.append(" AND e.PUBLIC_ID = ?");
            params.add(especialidadPublicId.trim());
        }

        if (estado != null && !estado.isBlank()) {
            sql.append(" AND c.ESTADO = ?");
            params.add(estado.trim().toUpperCase(Locale.ROOT));
        }

        Integer total = jdbcTemplate.queryForObject(sql.toString(), Integer.class, params.toArray());
        return total != null ? total : 0;
    }
}



package com.meditriaje.repository;

import com.meditriaje.dto.pharmacy.DispensacionDetalleResponse;
import com.meditriaje.dto.pharmacy.DispensacionResponse;
import com.meditriaje.dto.pharmacy.RecetaDispensacionResponse;
import com.meditriaje.dto.pharmacy.SaldoMedicamentoDto;
import com.meditriaje.model.Dispensacion;
import com.meditriaje.model.DispensacionDetalle;
import com.meditriaje.model.EstadoRecetaDispensacion;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Repositorio JDBC para dispensaciones farmacéuticas y control de saldos de recetas médicas (F2.4, ADR-016).
 * Persistencia inmutable con SQL 100% parametrizado.
 */
@Repository
public class DispensacionRepository {

    private final JdbcTemplate jdbcTemplate;

    private final RowMapper<Dispensacion> dispensacionRowMapper = (rs, rowNum) -> {
        Timestamp ts = rs.getTimestamp("CREATED_AT");
        return new Dispensacion(
                rs.getLong("ID"),
                rs.getString("PUBLIC_ID"),
                rs.getLong("RECETA_ID"),
                rs.getLong("SEDE_ID"),
                rs.getLong("USUARIO_ID"),
                rs.getString("OBSERVACIONES"),
                ts != null ? ts.toInstant() : null
        );
    };

    public DispensacionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Persiste una nueva cabecera de dispensación farmacéutica. Inmutable tras su inserción (ADR-016).
     *
     * @param dispensacion Entidad de dispensación a persistir
     * @return Identificador numérico generado en base de datos
     */
    public Long crearDispensacion(Dispensacion dispensacion) {
        Objects.requireNonNull(dispensacion, "dispensacion no puede ser nula");

        String sql = """
            INSERT INTO DISPENSACION (PUBLIC_ID, RECETA_ID, SEDE_ID, USUARIO_ID, OBSERVACIONES)
            VALUES (?, ?, ?, ?, ?)
            """;

        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, new String[]{"ID"});
            ps.setString(1, dispensacion.publicId());
            ps.setLong(2, dispensacion.recetaId());
            ps.setLong(3, dispensacion.sedeId());
            ps.setLong(4, dispensacion.usuarioId());
            ps.setString(5, dispensacion.observaciones());
            return ps;
        }, keyHolder);

        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("No se pudo obtener el ID autogenerado para la dispensacion.");
        }
        return key.longValue();
    }

    /**
     * Guarda por lotes los ítems entregados en DISPENSACION_DETALLE con lote y vencimiento.
     *
     * @param dispensacionId Identificador de la cabecera de dispensación
     * @param detalles       Lista de ítems entregados
     */
    public void guardarDetalles(Long dispensacionId, List<DispensacionDetalle> detalles) {
        if (detalles == null || detalles.isEmpty()) {
            return;
        }

        String sql = """
            INSERT INTO DISPENSACION_DETALLE (
                DISPENSACION_ID, RECETA_DETALLE_ID, CANTIDAD_ENTREGADA, LOTE, FECHA_VENCIMIENTO_LOTE
            ) VALUES (?, ?, ?, ?, ?)
            """;

        jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                DispensacionDetalle d = detalles.get(i);
                ps.setLong(1, dispensacionId != null ? dispensacionId : d.dispensacionId());
                ps.setLong(2, d.recetaDetalleId());
                ps.setInt(3, d.cantidadEntregada());
                ps.setString(4, d.lote());
                if (d.fechaVencimientoLote() != null) {
                    ps.setDate(5, Date.valueOf(d.fechaVencimientoLote()));
                } else {
                    ps.setNull(5, java.sql.Types.DATE);
                }
            }

            @Override
            public int getBatchSize() {
                return detalles.size();
            }
        });
    }

    /**
     * Consulta la entidad inmutable {@link Dispensacion} por su UUID público.
     *
     * @param publicId UUID público de la dispensación
     * @return Optional con la entidad si existe
     */
    public Optional<Dispensacion> buscarEntidadPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT ID, PUBLIC_ID, RECETA_ID, SEDE_ID, USUARIO_ID, OBSERVACIONES, CREATED_AT
            FROM DISPENSACION
            WHERE PUBLIC_ID = ?
            """;

        List<Dispensacion> lista = jdbcTemplate.query(sql, dispensacionRowMapper, publicId.trim());
        return lista.stream().findFirst();
    }

    /**
     * Consulta detallada de una dispensación por su UUID público incluyendo sus ítems entregados.
     *
     * @param publicId UUID público de la dispensación
     * @return DispensacionResponse con detalles si existe
     */
    public Optional<DispensacionResponse> buscarPorPublicId(String publicId) {
        if (publicId == null || publicId.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT d.ID AS DISPENSACION_ID,
                   d.PUBLIC_ID AS DISPENSACION_PUBLIC_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   s.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   s.NOMBRE AS SEDE_NOMBRE,
                   u.PUBLIC_ID AS DISPENSADOR_PUBLIC_ID,
                   COALESCE(TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS), u.EMAIL) AS DISPENSADOR_NOMBRE,
                   d.OBSERVACIONES,
                   d.CREATED_AT
            FROM DISPENSACION d
            JOIN RECETA r ON d.RECETA_ID = r.ID
            JOIN SEDE s ON d.SEDE_ID = s.ID
            JOIN USUARIO u ON d.USUARIO_ID = u.ID
            LEFT JOIN PROFESIONAL pr ON u.ID = pr.USUARIO_ID
            WHERE d.PUBLIC_ID = ?
            """;

        List<DispensacionResponse> lista = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long dispensacionId = rs.getLong("DISPENSACION_ID");
            List<DispensacionDetalleResponse> detalles = buscarDetallesPorDispensacionId(dispensacionId);
            Timestamp ts = rs.getTimestamp("CREATED_AT");

            return new DispensacionResponse(
                    rs.getString("DISPENSACION_PUBLIC_ID"),
                    rs.getString("RECETA_PUBLIC_ID"),
                    rs.getString("SEDE_PUBLIC_ID"),
                    rs.getString("SEDE_NOMBRE"),
                    rs.getString("DISPENSADOR_PUBLIC_ID"),
                    rs.getString("DISPENSADOR_NOMBRE"),
                    rs.getString("OBSERVACIONES"),
                    ts != null ? ts.toInstant() : null,
                    detalles
            );
        }, publicId.trim());

        return lista.stream().findFirst();
    }

    /**
     * Consulta los detalles entregados en un evento de dispensación específico.
     *
     * @param dispensacionId Identificador de base de datos de la dispensación
     * @return Lista de detalles entregados
     */
    public List<DispensacionDetalleResponse> buscarDetallesPorDispensacionId(Long dispensacionId) {
        if (dispensacionId == null) {
            return List.of();
        }

        String sql = """
            SELECT m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   m.CODIGO AS MEDICAMENTO_CODIGO,
                   rd.SNAPSHOT_NOMBRE AS NOMBRE_COMERCIAL,
                   dd.CANTIDAD_ENTREGADA,
                   dd.LOTE,
                   dd.FECHA_VENCIMIENTO_LOTE,
                   dd.CREATED_AT
            FROM DISPENSACION_DETALLE dd
            JOIN RECETA_DETALLE rd ON dd.RECETA_DETALLE_ID = rd.ID
            JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
            WHERE dd.DISPENSACION_ID = ?
            ORDER BY dd.ID ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Date fv = rs.getDate("FECHA_VENCIMIENTO_LOTE");
            Timestamp ts = rs.getTimestamp("CREATED_AT");
            return new DispensacionDetalleResponse(
                    rs.getString("MEDICAMENTO_PUBLIC_ID"),
                    rs.getString("MEDICAMENTO_CODIGO"),
                    rs.getString("NOMBRE_COMERCIAL"),
                    rs.getInt("CANTIDAD_ENTREGADA"),
                    rs.getString("LOTE"),
                    fv != null ? fv.toLocalDate() : null,
                    ts != null ? ts.toInstant() : null
            );
        }, dispensacionId);
    }

    /**
     * Lista todas las entregas previas registradas para una receta médica.
     *
     * @param recetaId Identificador interno de la receta
     * @return Lista de eventos de dispensación ordenados cronológicamente
     */
    public List<DispensacionResponse> listarPorRecetaId(Long recetaId) {
        if (recetaId == null) {
            return List.of();
        }

        String sql = """
            SELECT d.ID AS DISPENSACION_ID,
                   d.PUBLIC_ID AS DISPENSACION_PUBLIC_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   s.PUBLIC_ID AS SEDE_PUBLIC_ID,
                   s.NOMBRE AS SEDE_NOMBRE,
                   u.PUBLIC_ID AS DISPENSADOR_PUBLIC_ID,
                   COALESCE(TRIM(pr.NOMBRES || ' ' || pr.APELLIDOS), u.EMAIL) AS DISPENSADOR_NOMBRE,
                   d.OBSERVACIONES,
                   d.CREATED_AT
            FROM DISPENSACION d
            JOIN RECETA r ON d.RECETA_ID = r.ID
            JOIN SEDE s ON d.SEDE_ID = s.ID
            JOIN USUARIO u ON d.USUARIO_ID = u.ID
            LEFT JOIN PROFESIONAL pr ON u.ID = pr.USUARIO_ID
            WHERE d.RECETA_ID = ?
            ORDER BY d.CREATED_AT ASC, d.ID ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long dispensacionId = rs.getLong("DISPENSACION_ID");
            List<DispensacionDetalleResponse> detalles = buscarDetallesPorDispensacionId(dispensacionId);
            Timestamp ts = rs.getTimestamp("CREATED_AT");

            return new DispensacionResponse(
                    rs.getString("DISPENSACION_PUBLIC_ID"),
                    rs.getString("RECETA_PUBLIC_ID"),
                    rs.getString("SEDE_PUBLIC_ID"),
                    rs.getString("SEDE_NOMBRE"),
                    rs.getString("DISPENSADOR_PUBLIC_ID"),
                    rs.getString("DISPENSADOR_NOMBRE"),
                    rs.getString("OBSERVACIONES"),
                    ts != null ? ts.toInstant() : null,
                    detalles
            );
        }, recetaId);
    }

    /**
     * Calcula los totales históricos acumulados entregados para cada ítem de una receta médica.
     *
     * @param recetaId Identificador interno de la receta
     * @return Mapa de recetaDetalleId a sumatoria de cantidad entregada
     */
    public Map<Long, Integer> obtenerTotalesDispensadosPorRecetaId(Long recetaId) {
        if (recetaId == null) {
            return Map.of();
        }

        String sql = """
            SELECT dd.RECETA_DETALLE_ID, SUM(dd.CANTIDAD_ENTREGADA) AS TOTAL_ENTREGADO
            FROM DISPENSACION_DETALLE dd
            JOIN DISPENSACION d ON dd.DISPENSACION_ID = d.ID
            WHERE d.RECETA_ID = ?
            GROUP BY dd.RECETA_DETALLE_ID
            """;

        Map<Long, Integer> totales = new HashMap<>();
        jdbcTemplate.query(sql, rs -> {
            totales.put(rs.getLong("RECETA_DETALLE_ID"), rs.getInt("TOTAL_ENTREGADO"));
        }, recetaId);

        return totales;
    }

    /**
     * Consulta los saldos de cada ítem prescrito en la receta (prescrito vs. entregado vs. saldo pendiente).
     *
     * @param recetaId Identificador interno de la receta
     * @return Lista de SaldoMedicamentoDto calculada dinámicamente
     */
    public List<SaldoMedicamentoDto> obtenerSaldosPorRecetaId(Long recetaId) {
        if (recetaId == null) {
            return List.of();
        }

        String sql = """
            SELECT rd.ID AS RECETA_DETALLE_ID,
                   m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   m.CODIGO AS MEDICAMENTO_CODIGO,
                   rd.SNAPSHOT_NOMBRE,
                   rd.SNAPSHOT_PRINCIPIO_ACTIVO,
                   rd.SNAPSHOT_PRESENTACION,
                   rd.SNAPSHOT_CONCENTRACION,
                   rd.DOSIS,
                   rd.FRECUENCIA,
                   rd.DURACION_DIAS,
                   rd.CANTIDAD AS CANTIDAD_PRESCRITA,
                   COALESCE(disp.TOTAL_ENTREGADO, 0) AS CANTIDAD_DISPENSADA,
                   rd.INDICACIONES
            FROM RECETA_DETALLE rd
            JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
            LEFT JOIN (
                SELECT dd.RECETA_DETALLE_ID, SUM(dd.CANTIDAD_ENTREGADA) AS TOTAL_ENTREGADO
                FROM DISPENSACION_DETALLE dd
                JOIN DISPENSACION d ON dd.DISPENSACION_ID = d.ID
                WHERE d.RECETA_ID = ?
                GROUP BY dd.RECETA_DETALLE_ID
            ) disp ON rd.ID = disp.RECETA_DETALLE_ID
            WHERE rd.RECETA_ID = ?
            ORDER BY rd.ID ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            int prescrita = rs.getInt("CANTIDAD_PRESCRITA");
            int dispensada = rs.getInt("CANTIDAD_DISPENSADA");
            int saldo = Math.max(0, prescrita - dispensada);

            String estado;
            if (dispensada == 0) {
                estado = "PENDIENTE";
            } else if (saldo <= 0) {
                estado = "DISPENSADA_TOTAL";
            } else {
                estado = "DISPENSADA_PARCIAL";
            }

            return new SaldoMedicamentoDto(
                    rs.getString("MEDICAMENTO_PUBLIC_ID"),
                    rs.getString("MEDICAMENTO_CODIGO"),
                    rs.getString("SNAPSHOT_NOMBRE"),
                    rs.getString("SNAPSHOT_PRINCIPIO_ACTIVO"),
                    rs.getString("SNAPSHOT_PRESENTACION"),
                    rs.getString("SNAPSHOT_CONCENTRACION"),
                    rs.getString("DOSIS"),
                    rs.getString("FRECUENCIA"),
                    rs.getInt("DURACION_DIAS"),
                    prescrita,
                    dispensada,
                    saldo,
                    estado,
                    rs.getString("INDICACIONES")
            );
        }, recetaId, recetaId);
    }

    /**
     * Información del ítem prescrito para validaciones de correspondencia y saldo en servicio.
     */
    public record ItemPrescritoInfo(
            Long recetaDetalleId,
            String medicamentoPublicId,
            String medicamentoNombre,
            int cantidadPrescrita
    ) {}

    /**
     * Consulta la lista de ítems prescritos asociados a una receta médica.
     *
     * @param recetaId Identificador interno de la receta
     * @return Lista de ítems con ID de detalle y cantidad prescrita
     */
    public List<ItemPrescritoInfo> buscarItemsPrescripcion(Long recetaId) {
        if (recetaId == null) {
            return List.of();
        }

        String sql = """
            SELECT rd.ID AS RECETA_DETALLE_ID,
                   m.PUBLIC_ID AS MEDICAMENTO_PUBLIC_ID,
                   rd.SNAPSHOT_NOMBRE,
                   rd.CANTIDAD AS CANTIDAD_PRESCRITA
            FROM RECETA_DETALLE rd
            JOIN MEDICAMENTO m ON rd.MEDICAMENTO_ID = m.ID
            WHERE rd.RECETA_ID = ?
            ORDER BY rd.ID ASC
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> new ItemPrescritoInfo(
                rs.getLong("RECETA_DETALLE_ID"),
                rs.getString("MEDICAMENTO_PUBLIC_ID"),
                rs.getString("SNAPSHOT_NOMBRE"),
                rs.getInt("CANTIDAD_PRESCRITA")
        ), recetaId);
    }

    /**
     * Consulta una receta médica para dispensación por su UUID público, calculando saldos y entregas previas.
     *
     * @param recetaPublicId UUID público de la receta
     * @return Optional con RecetaDispensacionResponse si existe
     */
    public Optional<RecetaDispensacionResponse> buscarRecetaDispensacionPorPublicId(String recetaPublicId) {
        if (recetaPublicId == null || recetaPublicId.isBlank()) {
            return Optional.empty();
        }

        String sql = """
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.TIPO_DOCUMENTO || ' ' || p.NUMERO_DOCUMENTO AS PACIENTE_DOCUMENTO,
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
            JOIN DISPONIBILIDAD_SLOT sl ON c.SLOT_ID = sl.ID
            JOIN ESPECIALIDAD e ON sl.ESPECIALIDAD_ID = e.ID
            WHERE r.PUBLIC_ID = ?
            """;

        List<RecetaDispensacionResponse> lista = jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long recetaId = rs.getLong("RECETA_ID");
            return construirRecetaDispensacionResponse(rs, recetaId);
        }, recetaPublicId.trim());

        return lista.stream().findFirst();
    }

    /**
     * Busca recetas médicas para la ventanilla de farmacia filtrando por documento del paciente o código.
     *
     * @param query Término de búsqueda (código de reclamación, documento o nombre del paciente)
     * @param page  Página (0-indexada)
     * @param size  Tamaño de página
     * @return Lista paginada de recetas para dispensación
     */
    public List<RecetaDispensacionResponse> buscarRecetasDispensacion(String query, int page, int size) {
        int offset = Math.max(0, page) * Math.max(1, size);
        int limit = Math.max(1, size);

        String term = query != null ? query.trim() : "";
        String normalizedId = term.toUpperCase().startsWith("REC-") ? term.substring(4) : term;
        String wildcard = "%" + term + "%";
        String wildcardId = "%" + normalizedId + "%";

        String sql = """
            SELECT r.ID AS RECETA_ID,
                   r.PUBLIC_ID AS RECETA_PUBLIC_ID,
                   a.PUBLIC_ID AS ATENCION_PUBLIC_ID,
                   p.PUBLIC_ID AS PACIENTE_PUBLIC_ID,
                   p.TIPO_DOCUMENTO || ' ' || p.NUMERO_DOCUMENTO AS PACIENTE_DOCUMENTO,
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
            JOIN DISPONIBILIDAD_SLOT sl ON c.SLOT_ID = sl.ID
            JOIN ESPECIALIDAD e ON sl.ESPECIALIDAD_ID = e.ID
            WHERE (? = '' OR
                   LOWER(p.NUMERO_DOCUMENTO) LIKE LOWER(?) OR
                   LOWER(r.PUBLIC_ID) LIKE LOWER(?) OR
                   LOWER(TRIM(p.NOMBRES || ' ' || p.APELLIDOS)) LIKE LOWER(?))
            ORDER BY r.CREATED_AT DESC, r.ID DESC
            OFFSET ? ROWS FETCH NEXT ? ROWS ONLY
            """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> {
            Long recetaId = rs.getLong("RECETA_ID");
            return construirRecetaDispensacionResponse(rs, recetaId);
        }, term, wildcard, wildcardId, wildcard, offset, limit);
    }

    /**
     * Cuenta el total de recetas coincidentes con el criterio de búsqueda.
     *
     * @param query Término de búsqueda
     * @return Total de registros
     */
    public int contarRecetasDispensacion(String query) {
        String term = query != null ? query.trim() : "";
        String normalizedId = term.toUpperCase().startsWith("REC-") ? term.substring(4) : term;
        String wildcard = "%" + term + "%";
        String wildcardId = "%" + normalizedId + "%";

        String sql = """
            SELECT COUNT(*)
            FROM RECETA r
            JOIN PACIENTE p ON r.PACIENTE_ID = p.ID
            WHERE (? = '' OR
                   LOWER(p.NUMERO_DOCUMENTO) LIKE LOWER(?) OR
                   LOWER(r.PUBLIC_ID) LIKE LOWER(?) OR
                   LOWER(TRIM(p.NOMBRES || ' ' || p.APELLIDOS)) LIKE LOWER(?))
            """;

        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, term, wildcard, wildcardId, wildcard);
        return count != null ? count : 0;
    }

    private RecetaDispensacionResponse construirRecetaDispensacionResponse(java.sql.ResultSet rs, Long recetaId) throws SQLException {
        String recetaPublicId = rs.getString("RECETA_PUBLIC_ID");
        int vigenciaDias = rs.getInt("VIGENCIA_DIAS");
        Timestamp tsCreated = rs.getTimestamp("CREATED_AT");
        Instant fechaEmision = tsCreated != null ? tsCreated.toInstant() : Instant.now();
        Instant fechaVencimiento = fechaEmision.plus(vigenciaDias, ChronoUnit.DAYS);
        boolean vencida = Instant.now().isAfter(fechaVencimiento);

        String cleanUuid = recetaPublicId.replace("-", "");
        String codigoReclamacion = "REC-" + (cleanUuid.length() >= 8 ? cleanUuid.substring(0, 8).toUpperCase() : cleanUuid.toUpperCase());

        List<SaldoMedicamentoDto> items = obtenerSaldosPorRecetaId(recetaId);
        List<DispensacionResponse> entregasPrevias = listarPorRecetaId(recetaId);

        EstadoRecetaDispensacion estado;
        if (items.isEmpty()) {
            estado = EstadoRecetaDispensacion.PENDIENTE;
        } else {
            boolean todasEntregadas = items.stream().allMatch(item -> item.saldoPendiente() == 0);
            boolean algunaEntregada = items.stream().anyMatch(item -> item.cantidadDispensada() > 0);

            if (todasEntregadas) {
                estado = EstadoRecetaDispensacion.DISPENSADA_TOTAL;
            } else if (algunaEntregada) {
                estado = EstadoRecetaDispensacion.DISPENSADA_PARCIAL;
            } else {
                estado = EstadoRecetaDispensacion.PENDIENTE;
            }
        }

        return new RecetaDispensacionResponse(
                recetaPublicId,
                codigoReclamacion,
                rs.getString("ATENCION_PUBLIC_ID"),
                rs.getString("PACIENTE_PUBLIC_ID"),
                rs.getString("PACIENTE_DOCUMENTO"),
                rs.getString("PACIENTE_NOMBRE"),
                rs.getString("PROFESIONAL_PUBLIC_ID"),
                rs.getString("PROFESIONAL_NOMBRE"),
                rs.getString("ESPECIALIDAD_NOMBRE"),
                vigenciaDias,
                fechaEmision,
                fechaVencimiento,
                vencida,
                estado,
                items,
                entregasPrevias
        );
    }
}

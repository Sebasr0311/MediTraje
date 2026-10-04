package com.meditriaje.repository;

import com.meditriaje.dto.report.DistribucionItemDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Repositorio JDBC para métricas e indicadores operativos agregados (F2.6, RF-30, ADR-018).
 * Agregación matemática 100% en base de datos sin exponer datos clínicos identificables (ADR-007).
 */
@Repository
public class ReporteRepository {

    private final JdbcTemplate jdbcTemplate;

    public ReporteRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate no puede ser nulo");
    }

    /**
     * Cuenta citas agrupadas por su estado en la ventana temporal especificada.
     */
    public Map<String, Long> contarCitasPorEstado(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("SELECT ESTADO, COUNT(*) AS TOTAL FROM CITA WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }
        sql.append(" GROUP BY ESTADO");

        Map<String, Long> resultados = new HashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            resultados.put(rs.getString("ESTADO"), rs.getLong("TOTAL"));
        }, params.toArray());

        return resultados;
    }

    /**
     * Distribución de citas por especialidad médica.
     */
    public List<DistribucionItemDto> obtenerCitasPorEspecialidad(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("""
            SELECT esp.PUBLIC_ID, esp.NOMBRE, COUNT(c.ID) AS TOTAL
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN ESPECIALIDAD esp ON s.ESPECIALIDAD_ID = esp.ID
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND c.CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND c.CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }
        sql.append(" GROUP BY esp.PUBLIC_ID, esp.NOMBRE ORDER BY TOTAL DESC");

        List<DistribucionItemDto> items = new ArrayList<>();
        long granTotal = 0;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        for (Map<String, Object> row : rows) {
            granTotal += ((Number) row.get("TOTAL")).longValue();
        }

        for (Map<String, Object> row : rows) {
            long cant = ((Number) row.get("TOTAL")).longValue();
            double pct = granTotal > 0 ? (cant * 100.0 / granTotal) : 0.0;
            items.add(new DistribucionItemDto(
                    (String) row.get("PUBLIC_ID"),
                    (String) row.get("NOMBRE"),
                    cant,
                    Math.round(pct * 100.0) / 100.0
            ));
        }

        return items;
    }

    /**
     * Distribución de citas por sede hospitalaria.
     */
    public List<DistribucionItemDto> obtenerCitasPorSede(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("""
            SELECT sed.PUBLIC_ID, sed.NOMBRE, COUNT(c.ID) AS TOTAL
            FROM CITA c
            JOIN DISPONIBILIDAD_SLOT s ON c.SLOT_ID = s.ID
            JOIN SEDE sed ON s.SEDE_ID = sed.ID
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND c.CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND c.CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }
        sql.append(" GROUP BY sed.PUBLIC_ID, sed.NOMBRE ORDER BY TOTAL DESC");

        List<DistribucionItemDto> items = new ArrayList<>();
        long granTotal = 0;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        for (Map<String, Object> row : rows) {
            granTotal += ((Number) row.get("TOTAL")).longValue();
        }

        for (Map<String, Object> row : rows) {
            long cant = ((Number) row.get("TOTAL")).longValue();
            double pct = granTotal > 0 ? (cant * 100.0 / granTotal) : 0.0;
            items.add(new DistribucionItemDto(
                    (String) row.get("PUBLIC_ID"),
                    (String) row.get("NOMBRE"),
                    cant,
                    Math.round(pct * 100.0) / 100.0
            ));
        }

        return items;
    }

    /**
     * Cuenta triajes agrupados por nivel de prioridad (I..V).
     */
    public Map<String, Long> contarTriajesPorNivel(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("SELECT NIVEL_PRIORIDAD, COUNT(*) AS TOTAL FROM TRIAJE WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }
        sql.append(" GROUP BY NIVEL_PRIORIDAD");

        Map<String, Long> resultados = new HashMap<>();
        jdbcTemplate.query(sql.toString(), rs -> {
            resultados.put(rs.getString("NIVEL_PRIORIDAD"), rs.getLong("TOTAL"));
        }, params.toArray());

        return resultados;
    }

    /**
     * Cuenta total de triajes clasificados como corte de emergencia.
     */
    public long contarTriajesEmergencia(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM TRIAJE WHERE ES_EMERGENCIA = 1");
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }

    /**
     * Distribución de triajes por ruta sugerida.
     */
    public List<DistribucionItemDto> obtenerTriajesPorRuta(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("SELECT RUTA_SUGERIDA, COUNT(*) AS TOTAL FROM TRIAJE WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }
        sql.append(" GROUP BY RUTA_SUGERIDA ORDER BY TOTAL DESC");

        List<DistribucionItemDto> items = new ArrayList<>();
        long granTotal = 0;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        for (Map<String, Object> row : rows) {
            granTotal += ((Number) row.get("TOTAL")).longValue();
        }

        for (Map<String, Object> row : rows) {
            long cant = ((Number) row.get("TOTAL")).longValue();
            double pct = granTotal > 0 ? (cant * 100.0 / granTotal) : 0.0;
            String ruta = (String) row.get("RUTA_SUGERIDA");
            items.add(new DistribucionItemDto(
                    ruta,
                    ruta,
                    cant,
                    Math.round(pct * 100.0) / 100.0
            ));
        }

        return items;
    }

    /**
     * Total de recetas emitidas en la ventana temporal.
     */
    public long contarRecetasEmitidas(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM RECETA WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }

    /**
     * Total de unidades farmacológicas entregadas en farmacia.
     */
    public long contarUnidadesDispensadas(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("""
            SELECT COALESCE(SUM(dd.CANTIDAD_ENTREGADA), 0)
            FROM DISPENSACION_DETALLE dd
            JOIN DISPENSACION d ON dd.DISPENSACION_ID = d.ID
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND d.CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND d.CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }

    /**
     * Cuenta recetas clasificadas por su estado de dispensación en la ventana temporal.
     */
    public Map<String, Long> contarRecetasPorEstadoDispensacion(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("""
            SELECT
              COALESCE(SUM(CASE WHEN entregado = 0 THEN 1 ELSE 0 END), 0) AS PENDIENTES,
              COALESCE(SUM(CASE WHEN entregado > 0 AND entregado < prescrito THEN 1 ELSE 0 END), 0) AS PARCIALES,
              COALESCE(SUM(CASE WHEN entregado >= prescrito AND prescrito > 0 THEN 1 ELSE 0 END), 0) AS TOTALES
            FROM (
              SELECT r.ID,
                     SUM(rd.CANTIDAD) AS prescrito,
                     COALESCE(SUM(dd_tot.TOTAL_ENTREGADO), 0) AS entregado
              FROM RECETA r
              JOIN RECETA_DETALLE rd ON rd.RECETA_ID = r.ID
              LEFT JOIN (
                  SELECT dd.RECETA_DETALLE_ID, SUM(dd.CANTIDAD_ENTREGADA) AS TOTAL_ENTREGADO
                  FROM DISPENSACION_DETALLE dd
                  GROUP BY dd.RECETA_DETALLE_ID
              ) dd_tot ON dd_tot.RECETA_DETALLE_ID = rd.ID
              WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND r.CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND r.CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        sql.append(" GROUP BY r.ID ) subq");

        Map<String, Long> resultados = new HashMap<>();
        resultados.put("PENDIENTE", 0L);
        resultados.put("DISPENSADA_PARCIAL", 0L);
        resultados.put("DISPENSADA_TOTAL", 0L);

        jdbcTemplate.query(sql.toString(), rs -> {
            resultados.put("PENDIENTE", rs.getLong("PENDIENTES"));
            resultados.put("DISPENSADA_PARCIAL", rs.getLong("PARCIALES"));
            resultados.put("DISPENSADA_TOTAL", rs.getLong("TOTALES"));
        }, params.toArray());

        return resultados;
    }

    /**
     * Total de activaciones de Break-Glass en la ventana temporal.
     */
    public long contarActivacionesBreakGlass(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ACCESO_BREAK_GLASS WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }

        Long total = jdbcTemplate.queryForObject(sql.toString(), Long.class, params.toArray());
        return total != null ? total : 0L;
    }

    /**
     * Total de accesos Break-Glass actualmente activos (no expirados).
     */
    public long contarBreakGlassActivos(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora no puede ser nulo");
        String sql = "SELECT COUNT(*) FROM ACCESO_BREAK_GLASS WHERE FECHA_EXPIRACION > ?";
        Long total = jdbcTemplate.queryForObject(sql, Long.class, Timestamp.from(ahora));
        return total != null ? total : 0L;
    }

    /**
     * Distribución de activaciones Break-Glass por especialidad del médico solicitante.
     */
    public List<DistribucionItemDto> obtenerBreakGlassPorEspecialidad(Instant fechaDesde, Instant fechaHasta) {
        StringBuilder sql = new StringBuilder("""
            SELECT esp.PUBLIC_ID, esp.NOMBRE, COUNT(bg.ID) AS TOTAL
            FROM ACCESO_BREAK_GLASS bg
            JOIN PROFESIONAL p ON bg.PROFESIONAL_ID = p.ID
            JOIN ESPECIALIDAD esp ON p.ESPECIALIDAD_ID = esp.ID
            WHERE 1=1
            """);
        List<Object> params = new ArrayList<>();

        if (fechaDesde != null) {
            sql.append(" AND bg.CREATED_AT >= ?");
            params.add(Timestamp.from(fechaDesde));
        }
        if (fechaHasta != null) {
            sql.append(" AND bg.CREATED_AT <= ?");
            params.add(Timestamp.from(fechaHasta));
        }
        sql.append(" GROUP BY esp.PUBLIC_ID, esp.NOMBRE ORDER BY TOTAL DESC");

        List<DistribucionItemDto> items = new ArrayList<>();
        long granTotal = 0;

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params.toArray());
        for (Map<String, Object> row : rows) {
            granTotal += ((Number) row.get("TOTAL")).longValue();
        }

        for (Map<String, Object> row : rows) {
            long cant = ((Number) row.get("TOTAL")).longValue();
            double pct = granTotal > 0 ? (cant * 100.0 / granTotal) : 0.0;
            items.add(new DistribucionItemDto(
                    (String) row.get("PUBLIC_ID"),
                    (String) row.get("NOMBRE"),
                    cant,
                    Math.round(pct * 100.0) / 100.0
            ));
        }

        return items;
    }
}

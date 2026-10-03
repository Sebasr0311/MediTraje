package com.meditriaje.repository;

import com.meditriaje.triage.NivelPrioridad;
import com.meditriaje.triage.ReglaTriaje;
import com.meditriaje.triage.SintomaTriaje;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Objects;

/** Carga (solo lectura) del catálogo de síntomas y reglas activas de triaje (ADR-009). */
@Repository
public class TriajeReglasRepository {

    private final JdbcTemplate jdbcTemplate;

    public TriajeReglasRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "JdbcTemplate no puede ser nulo");
    }

    public List<SintomaTriaje> cargarSintomasActivos() {
        final String sql = "SELECT CODIGO, ES_ALARMA FROM SINTOMA WHERE ESTADO = 'ACTIVO' ORDER BY CODIGO";
        return jdbcTemplate.query(sql,
                (rs, n) -> new SintomaTriaje(rs.getString("CODIGO"), rs.getInt("ES_ALARMA") == 1));
    }

    public List<ReglaTriaje> cargarReglasActivas(String version) {
        final String sql = """
            SELECT S.CODIGO, R.DURACION_MIN_HORAS, R.DURACION_MAX_HORAS,
                   R.INTENSIDAD_MIN, R.INTENSIDAD_MAX, R.NIVEL_PRIORIDAD
              FROM REGLA_TRIAJE R
              JOIN SINTOMA S ON S.ID = R.SINTOMA_ID
             WHERE R.VERSION = ? AND R.ESTADO = 'ACTIVO' AND S.ESTADO = 'ACTIVO'
             ORDER BY S.CODIGO, R.INTENSIDAD_MIN
            """;
        return jdbcTemplate.query(sql,
                (rs, n) -> new ReglaTriaje(
                        rs.getString("CODIGO"),
                        rs.getBigDecimal("DURACION_MIN_HORAS"),
                        rs.getBigDecimal("DURACION_MAX_HORAS"),
                        rs.getInt("INTENSIDAD_MIN"),
                        rs.getInt("INTENSIDAD_MAX"),
                        NivelPrioridad.valueOf(rs.getString("NIVEL_PRIORIDAD"))),
                version);
    }
}

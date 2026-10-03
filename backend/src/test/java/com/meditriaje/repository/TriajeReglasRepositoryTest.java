package com.meditriaje.repository;

import com.meditriaje.triage.NivelPrioridad;
import com.meditriaje.triage.ReglaTriaje;
import com.meditriaje.triage.SintomaTriaje;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriajeReglasRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TriajeReglasRepository repository;

    @BeforeEach
    void setUp() {
        repository = new TriajeReglasRepository(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void cargarSintomasActivos_filtraActivosYMapeaAlarma() throws Exception {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<SintomaTriaje>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class))).thenReturn(List.of());

        repository.cargarSintomasActivos();

        verify(jdbcTemplate).query(sql.capture(), mapper.capture());
        assertThat(sql.getValue()).contains("ESTADO = 'ACTIVO'");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CODIGO")).thenReturn("CONVULSIONES");
        when(rs.getInt("ES_ALARMA")).thenReturn(1);
        assertThat(mapper.getValue().mapRow(rs, 0)).isEqualTo(new SintomaTriaje("CONVULSIONES", true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void cargarReglasActivas_parametrizaVersionYMapeaRegla() throws Exception {
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<RowMapper<ReglaTriaje>> mapper = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("v1-prototipo"))).thenReturn(List.of());

        repository.cargarReglasActivas("v1-prototipo");

        verify(jdbcTemplate).query(sql.capture(), mapper.capture(), eq("v1-prototipo"));
        assertThat(sql.getValue()).contains("R.VERSION = ?").contains("R.ESTADO = 'ACTIVO'");
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CODIGO")).thenReturn("FIEBRE");
        when(rs.getBigDecimal("DURACION_MIN_HORAS")).thenReturn(BigDecimal.ZERO);
        when(rs.getBigDecimal("DURACION_MAX_HORAS")).thenReturn(null);
        when(rs.getInt("INTENSIDAD_MIN")).thenReturn(7);
        when(rs.getInt("INTENSIDAD_MAX")).thenReturn(10);
        when(rs.getString("NIVEL_PRIORIDAD")).thenReturn("II");
        assertThat(mapper.getValue().mapRow(rs, 0))
                .isEqualTo(new ReglaTriaje("FIEBRE", BigDecimal.ZERO, null, 7, 10, NivelPrioridad.II));
    }
}

package com.meditriaje.repository;

import com.meditriaje.dto.triage.CatalogoSintomaResponse;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.model.Triaje;
import com.meditriaje.model.TriajeSintoma;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriajeRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private TriajeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new TriajeRepository(jdbcTemplate);
    }

    @Test
    void guardarTriaje_insertaFilaYRetornaIdGenerado() throws SQLException {
        Triaje triaje = new Triaje(
                "triaje-uuid-1", 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, "Observacion de prueba"
        );

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);

        doAnswer(invocation -> {
            PreparedStatementCreator psc = invocation.getArgument(0);
            KeyHolder kh = invocation.getArgument(1);
            psc.createPreparedStatement(connection);
            kh.getKeyList().add(Map.of("ID", 777L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long id = repository.guardarTriaje(triaje);

        assertThat(id).isEqualTo(777L);
        verify(ps).setString(1, "triaje-uuid-1");
        verify(ps).setLong(2, 10L);
        verify(ps).setString(3, "v1-prototipo");
        verify(ps).setString(4, "III");
        verify(ps).setString(5, "CITA_PRESENCIAL");
        verify(ps).setInt(6, 0);
        verify(ps).setString(7, "Observacion de prueba");
    }

    @Test
    void guardarSintomas_ejecutaBatchUpdateConParametros() throws SQLException {
        List<TriajeSintoma> sintomas = List.of(
                new TriajeSintoma(1L, 101L, BigDecimal.valueOf(12.5), 6),
                new TriajeSintoma(1L, 102L, BigDecimal.valueOf(2.0), 3)
        );

        PreparedStatement ps = mock(PreparedStatement.class);
        ArgumentCaptor<BatchPreparedStatementSetter> bpssCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);

        when(jdbcTemplate.batchUpdate(anyString(), bpssCaptor.capture())).thenReturn(new int[]{1, 1});

        repository.guardarSintomas(1L, sintomas);

        BatchPreparedStatementSetter bpss = bpssCaptor.getValue();
        assertThat(bpss.getBatchSize()).isEqualTo(2);

        bpss.setValues(ps, 0);
        verify(ps).setLong(1, 1L);
        verify(ps).setLong(2, 101L);
        verify(ps).setBigDecimal(3, BigDecimal.valueOf(12.5));
        verify(ps).setInt(4, 6);

        bpss.setValues(ps, 1);
        verify(ps).setLong(2, 102L);
        verify(ps).setBigDecimal(3, BigDecimal.valueOf(2.0));
        verify(ps).setInt(4, 3);
    }

    @Test
    void guardarSintomas_conListaVacia_noEjecutaUpdate() {
        repository.guardarSintomas(1L, List.of());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarPorPublicId_mapeaTriajeYSintomasCorrectamente() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.next()).thenReturn(true, true, false);

        when(rs.getString("TRIAJE_PUBLIC_ID")).thenReturn("triaje-uuid-1");
        when(rs.getString("PACIENTE_PUBLIC_ID")).thenReturn("pac-uuid-1");
        when(rs.getString("VERSION_REGLAS")).thenReturn("v1-prototipo");
        when(rs.getString("NIVEL_PRIORIDAD")).thenReturn("II");
        when(rs.getString("RUTA_SUGERIDA")).thenReturn("ATENCION_PRIORITARIA");
        when(rs.getInt("ES_EMERGENCIA")).thenReturn(0);
        when(rs.getString("OBSERVACIONES")).thenReturn("Fiebre moderada");
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.from(Instant.parse("2026-10-10T12:00:00Z")));

        // Síntoma 1
        when(rs.getString("SINTOMA_CODIGO")).thenReturn("FIEBRE", "DOLOR_GARGANTA");
        when(rs.getString("SINTOMA_NOMBRE")).thenReturn("Fiebre", "Dolor de garganta");
        when(rs.getBigDecimal("DURACION_HORAS")).thenReturn(BigDecimal.valueOf(24.0), BigDecimal.valueOf(12.0));
        when(rs.getInt("INTENSIDAD")).thenReturn(8, 4);
        when(rs.getInt("ES_ALARMA")).thenReturn(0, 0);

        doAnswer(invocation -> {
            ResultSetExtractor<Optional<TriajeResponse>> rse = invocation.getArgument(1);
            return rse.extractData(rs);
        }).when(jdbcTemplate).query(anyString(), any(ResultSetExtractor.class), eq("triaje-uuid-1"));

        Optional<TriajeResponse> resultado = repository.buscarPorPublicId("triaje-uuid-1");

        assertThat(resultado).isPresent();
        TriajeResponse resp = resultado.get();
        assertThat(resp.publicId()).isEqualTo("triaje-uuid-1");
        assertThat(resp.pacientePublicId()).isEqualTo("pac-uuid-1");
        assertThat(resp.nivelPrioridad()).isEqualTo("II");
        assertThat(resp.rutaSugerida()).isEqualTo("ATENCION_PRIORITARIA");
        assertThat(resp.esEmergencia()).isFalse();
        assertThat(resp.sintomas()).hasSize(2);
        assertThat(resp.sintomas().get(0).codigo()).isEqualTo("FIEBRE");
        assertThat(resp.sintomas().get(1).codigo()).isEqualTo("DOLOR_GARGANTA");
        assertThat(resp.sintomasAlarma()).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarPorPublicId_inexistente_retornaEmpty() {
        when(jdbcTemplate.query(anyString(), any(ResultSetExtractor.class), eq("no-existe")))
                .thenReturn(Optional.empty());

        Optional<TriajeResponse> resultado = repository.buscarPorPublicId("no-existe");
        assertThat(resultado).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarEntidadPorPublicId_retornaEntidad() {
        Triaje triaje = new Triaje(
                1L, "triaje-uuid-1", 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, null, Instant.now()
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("triaje-uuid-1")))
                .thenReturn(List.of(triaje));

        Optional<Triaje> resultado = repository.buscarEntidadPorPublicId("triaje-uuid-1");
        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(1L);
    }

    @Test
    @SuppressWarnings("unchecked")
    void buscarEntidadPorId_retornaEntidad() {
        Triaje triaje = new Triaje(
                1L, "triaje-uuid-1", 10L, "v1-prototipo", "III", "CITA_PRESENCIAL", false, null, Instant.now()
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(triaje));

        Optional<Triaje> resultado = repository.buscarEntidadPorId(1L);
        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("triaje-uuid-1");
    }

    @Test
    @SuppressWarnings("unchecked")
    void listarCatalogoSintomasActivos_retornaLista() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("PUBLIC_ID")).thenReturn("sintoma-uuid-1");
        when(rs.getString("CODIGO")).thenReturn("FIEBRE");
        when(rs.getString("NOMBRE")).thenReturn("Fiebre");
        when(rs.getString("CATEGORIA")).thenReturn("GENERAL");
        when(rs.getInt("ES_ALARMA")).thenReturn(0);

        ArgumentCaptor<RowMapper<CatalogoSintomaResponse>> mapperCaptor = ArgumentCaptor.forClass(RowMapper.class);
        when(jdbcTemplate.query(anyString(), mapperCaptor.capture())).thenReturn(List.of(
                new CatalogoSintomaResponse("sintoma-uuid-1", "FIEBRE", "Fiebre", "GENERAL", false)
        ));

        List<CatalogoSintomaResponse> catalogo = repository.listarCatalogoSintomasActivos();
        assertThat(catalogo).hasSize(1);
        assertThat(catalogo.get(0).codigo()).isEqualTo("FIEBRE");

        CatalogoSintomaResponse mapped = mapperCaptor.getValue().mapRow(rs, 0);
        assertThat(mapped.codigo()).isEqualTo("FIEBRE");
        assertThat(mapped.esAlarma()).isFalse();
    }

    @Test
    void obtenerMapaCodigoAIdSintomas_retornaMapeo() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("CODIGO")).thenReturn("FIEBRE", "TOS");
        when(rs.getLong("ID")).thenReturn(10L, 20L);

        doAnswer(invocation -> {
            RowCallbackHandler rch = invocation.getArgument(1);
            rch.processRow(rs);
            rch.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class));

        Map<String, Long> mapa = repository.obtenerMapaCodigoAIdSintomas();
        assertThat(mapa).containsEntry("FIEBRE", 10L);
        assertThat(mapa).containsEntry("TOS", 20L);
    }
}

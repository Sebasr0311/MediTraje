package com.meditriaje.repository;

import com.meditriaje.dto.prescription.RecetaDetalleResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.model.Receta;
import com.meditriaje.model.RecetaDetalle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

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
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecetaRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private RecetaRepository repository;

    @BeforeEach
    void setUp() {
        repository = new RecetaRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("crearReceta - Inserta fila y retorna el ID autogenerado")
    void crearReceta_retornaIdGenerado() throws SQLException {
        Receta receta = new Receta(
                null,
                "receta-uuid-1",
                10L,
                20L,
                30L,
                30,
                Instant.now()
        );

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);

        doAnswer(invocation -> {
            PreparedStatementCreator psc = invocation.getArgument(0);
            KeyHolder kh = invocation.getArgument(1);
            psc.createPreparedStatement(connection);
            kh.getKeyList().add(Map.of("ID", 555L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long id = repository.crearReceta(receta);

        assertThat(id).isEqualTo(555L);
        verify(ps).setString(1, "receta-uuid-1");
        verify(ps).setLong(2, 10L);
        verify(ps).setLong(3, 20L);
        verify(ps).setLong(4, 30L);
        verify(ps).setInt(5, 30);
    }

    @Test
    @DisplayName("guardarDetalles - Ejecuta batchUpdate con los parámetros y snapshots inmutables")
    void guardarDetalles_ejecutaBatchUpdate() throws SQLException {
        RecetaDetalle detalle = new RecetaDetalle(
                null,
                555L,
                100L,
                "Acetaminofen",
                "Acetaminofen",
                "Tableta",
                "500 mg",
                "500 mg",
                "Cada 8 horas",
                5,
                15,
                "Tomar con abundante agua"
        );

        PreparedStatement ps = mock(PreparedStatement.class);
        ArgumentCaptor<BatchPreparedStatementSetter> bpssCaptor = ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);

        when(jdbcTemplate.batchUpdate(anyString(), bpssCaptor.capture())).thenReturn(new int[]{1});

        repository.guardarDetalles(555L, List.of(detalle));

        BatchPreparedStatementSetter bpss = bpssCaptor.getValue();
        assertThat(bpss.getBatchSize()).isEqualTo(1);

        bpss.setValues(ps, 0);
        verify(ps).setLong(1, 555L);
        verify(ps).setLong(2, 100L);
        verify(ps).setString(3, "Acetaminofen");
        verify(ps).setString(4, "Acetaminofen");
        verify(ps).setString(5, "Tableta");
        verify(ps).setString(6, "500 mg");
        verify(ps).setString(7, "500 mg");
        verify(ps).setString(8, "Cada 8 horas");
        verify(ps).setInt(9, 5);
        verify(ps).setInt(10, 15);
        verify(ps).setString(11, "Tomar con abundante agua");
    }

    @Test
    @DisplayName("guardarDetalles - Lista nula o vacía no ejecuta batchUpdate")
    void guardarDetalles_listaVacia_noEjecuta() {
        repository.guardarDetalles(555L, null);
        repository.guardarDetalles(555L, List.of());
        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna receta con detalles y joins correctos")
    void buscarPorPublicId_retornaRecetaConDetalles() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("RECETA_ID")).thenReturn(555L);
        when(rs.getString("RECETA_PUBLIC_ID")).thenReturn("receta-uuid-1");
        when(rs.getString("ATENCION_PUBLIC_ID")).thenReturn("atencion-uuid-1");
        when(rs.getString("PACIENTE_PUBLIC_ID")).thenReturn("pac-uuid-1");
        when(rs.getString("PACIENTE_NOMBRE")).thenReturn("Juan Perez");
        when(rs.getString("PROFESIONAL_PUBLIC_ID")).thenReturn("prof-uuid-1");
        when(rs.getString("PROFESIONAL_NOMBRE")).thenReturn("Carlos Gomez");
        when(rs.getString("ESPECIALIDAD_NOMBRE")).thenReturn("Medicina General");
        when(rs.getInt("VIGENCIA_DIAS")).thenReturn(30);
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.from(Instant.now()));

        doAnswer(invocation -> {
            RowMapper<RecetaResponse> mapper = invocation.getArgument(1);
            RecetaResponse res = mapper.mapRow(rs, 1);
            return List.of(res);
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), eq("receta-uuid-1"));

        // Mock para buscarDetallesPorRecetaId
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(555L)))
                .thenReturn(List.of(new RecetaDetalleResponse(
                        "med-uuid-1",
                        "MED-ACE-500",
                        "Acetaminofen",
                        "Acetaminofen",
                        "Tableta",
                        "500 mg",
                        "500 mg",
                        "Cada 8 horas",
                        5,
                        15,
                        "Tomar con agua"
                )));

        Optional<RecetaResponse> resultado = repository.buscarPorPublicId("receta-uuid-1");

        assertThat(resultado).isPresent();
        RecetaResponse receta = resultado.get();
        assertThat(receta.publicId()).isEqualTo("receta-uuid-1");
        assertThat(receta.pacienteNombre()).isEqualTo("Juan Perez");
        assertThat(receta.profesionalNombre()).isEqualTo("Carlos Gomez");
        assertThat(receta.especialidadNombre()).isEqualTo("Medicina General");
        assertThat(receta.detalles()).hasSize(1);
        assertThat(receta.detalles().get(0).nombreComercial()).isEqualTo("Acetaminofen");
    }

    @Test
    @DisplayName("buscarPorPublicId - Retorna vacío con publicId nulo o en blanco")
    void buscarPorPublicId_nuloOBlanco_retornaVacio() {
        assertThat(repository.buscarPorPublicId(null)).isEmpty();
        assertThat(repository.buscarPorPublicId("   ")).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId - Retorna Receta si existe")
    void buscarEntidadPorPublicId_existe_retornaEntidad() {
        Receta receta = new Receta(
                555L,
                "receta-uuid-1",
                10L,
                20L,
                30L,
                30,
                Instant.now()
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("receta-uuid-1")))
                .thenReturn(List.of(receta));

        Optional<Receta> resultado = repository.buscarEntidadPorPublicId("receta-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(555L);
        assertThat(resultado.get().pacienteId()).isEqualTo(20L);
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId - Retorna vacío si no existe")
    void buscarEntidadPorPublicId_noExiste_retornaVacio() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("receta-inexistente")))
                .thenReturn(List.of());

        Optional<Receta> resultado = repository.buscarEntidadPorPublicId("receta-inexistente");

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("buscarDetallesPorRecetaId - RecetaId nulo retorna lista vacía")
    void buscarDetallesPorRecetaId_nulo_retornaVacio() {
        assertThat(repository.buscarDetallesPorRecetaId(null)).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("existePorAtencionId - Retorna true si hay receta para la atencion")
    void existePorAtencionId_existe_retornaTrue() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(10L)))
                .thenReturn(1);

        assertThat(repository.existePorAtencionId(10L)).isTrue();
    }

    @Test
    @DisplayName("existePorAtencionId - Retorna false si atencionId es nulo o conteo es 0")
    void existePorAtencionId_noExiste_retornaFalse() {
        assertThat(repository.existePorAtencionId(null)).isFalse();

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(10L)))
                .thenReturn(0);
        assertThat(repository.existePorAtencionId(10L)).isFalse();
    }

    @Test
    @DisplayName("listarPorPacienteId - Retorna lista vacía si pacienteId es nulo")
    void listarPorPacienteId_nulo_retornaVacio() {
        List<RecetaResponse> resp = repository.listarPorPacienteId(null, 0, 10);
        assertThat(resp).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @DisplayName("listarPorPacienteId - Consulta paginada con JOINs y detalles")
    void listarPorPacienteId_exito() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("RECETA_ID")).thenReturn(555L);
        when(rs.getString("RECETA_PUBLIC_ID")).thenReturn("receta-uuid-1");
        when(rs.getString("ATENCION_PUBLIC_ID")).thenReturn("atencion-uuid-1");
        when(rs.getString("PACIENTE_PUBLIC_ID")).thenReturn("pac-uuid-1");
        when(rs.getString("PACIENTE_NOMBRE")).thenReturn("Juan Perez");
        when(rs.getString("PROFESIONAL_PUBLIC_ID")).thenReturn("prof-uuid-1");
        when(rs.getString("PROFESIONAL_NOMBRE")).thenReturn("Carlos Gomez");
        when(rs.getString("ESPECIALIDAD_NOMBRE")).thenReturn("Medicina General");
        when(rs.getInt("VIGENCIA_DIAS")).thenReturn(30);
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.from(Instant.now()));

        doAnswer(invocation -> {
            RowMapper<RecetaResponse> mapper = invocation.getArgument(1);
            RecetaResponse res = mapper.mapRow(rs, 1);
            return List.of(res);
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(20L), eq(0), eq(10));

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(555L)))
                .thenReturn(List.of(new RecetaDetalleResponse(
                        "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg",
                        "500 mg", "Cada 8 horas", 5, 15, "Tomar con agua"
                )));

        List<RecetaResponse> resultado = repository.listarPorPacienteId(20L, 0, 10);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).publicId()).isEqualTo("receta-uuid-1");
        assertThat(resultado.get(0).detalles()).hasSize(1);
        assertThat(resultado.get(0).detalles().get(0).nombreComercial()).isEqualTo("Acetaminofen");
    }

    @Test
    @DisplayName("contarPorPacienteId - Retorna 0 si pacienteId es nulo o conteo desde BD")
    void contarPorPacienteId_pruebas() {
        assertThat(repository.contarPorPacienteId(null)).isEqualTo(0);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(20L)))
                .thenReturn(5);

        assertThat(repository.contarPorPacienteId(20L)).isEqualTo(5);
    }
}

package com.meditriaje.repository;

import com.meditriaje.dto.pharmacy.DispensacionDetalleResponse;
import com.meditriaje.dto.pharmacy.DispensacionResponse;
import com.meditriaje.dto.pharmacy.RecetaDispensacionResponse;
import com.meditriaje.dto.pharmacy.SaldoMedicamentoDto;
import com.meditriaje.model.Dispensacion;
import com.meditriaje.model.DispensacionDetalle;
import com.meditriaje.model.EstadoRecetaDispensacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispensacionRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DispensacionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DispensacionRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("crearDispensacion - Inserta cabecera y retorna ID generado")
    void crearDispensacion_retornaIdGenerado() throws SQLException {
        Dispensacion disp = new Dispensacion(
                null,
                "disp-uuid-1",
                10L,
                20L,
                30L,
                "Entrega en ventanilla",
                Instant.now()
        );

        Connection conn = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(conn.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);

        doAnswer(inv -> {
            PreparedStatementCreator psc = inv.getArgument(0);
            KeyHolder kh = inv.getArgument(1);
            psc.createPreparedStatement(conn);
            kh.getKeyList().add(Map.of("ID", 777L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long id = repository.crearDispensacion(disp);

        assertThat(id).isEqualTo(777L);
        verify(ps).setString(1, "disp-uuid-1");
        verify(ps).setLong(2, 10L);
        verify(ps).setLong(3, 20L);
        verify(ps).setLong(4, 30L);
        verify(ps).setString(5, "Entrega en ventanilla");
    }

    @Test
    @DisplayName("guardarDetalles - Inserta detalles por lotes con fecha y lote")
    void guardarDetalles_batchUpdateExecuted() throws SQLException {
        List<DispensacionDetalle> detalles = List.of(
                new DispensacionDetalle(null, 100L, 501L, 10, "LOTE-A1", LocalDate.of(2027, 12, 31), Instant.now()),
                new DispensacionDetalle(null, 100L, 502L, 5, null, null, Instant.now())
        );

        PreparedStatement ps = mock(PreparedStatement.class);

        doAnswer(inv -> {
            BatchPreparedStatementSetter bpss = inv.getArgument(1);
            assertThat(bpss.getBatchSize()).isEqualTo(2);

            bpss.setValues(ps, 0);
            bpss.setValues(ps, 1);
            return new int[]{1, 1};
        }).when(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));

        repository.guardarDetalles(100L, detalles);

        org.mockito.Mockito.verify(ps, org.mockito.Mockito.times(2)).setLong(1, 100L);
        verify(ps).setLong(2, 501L);
        verify(ps).setInt(3, 10);
        verify(ps).setString(4, "LOTE-A1");
        verify(ps).setDate(5, Date.valueOf(LocalDate.of(2027, 12, 31)));

        verify(ps).setLong(2, 502L);
        verify(ps).setInt(3, 5);
        verify(ps).setNull(5, java.sql.Types.DATE);
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId - Retorna entidad Dispensacion si existe")
    void buscarEntidadPorPublicId_retornaDispensacion() {
        Dispensacion mockDisp = new Dispensacion(1L, "disp-uuid-1", 10L, 20L, 30L, "Obs", Instant.now());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("disp-uuid-1")))
                .thenReturn(List.of(mockDisp));

        Optional<Dispensacion> res = repository.buscarEntidadPorPublicId("disp-uuid-1");

        assertThat(res).isPresent();
        assertThat(res.get().publicId()).isEqualTo("disp-uuid-1");
    }

    @Test
    @DisplayName("buscarDetallesPorDispensacionId - Retorna lista de detalles entregados")
    void buscarDetallesPorDispensacionId_retornaLista() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("MEDICAMENTO_PUBLIC_ID")).thenReturn("med-uuid-1");
        when(rs.getString("MEDICAMENTO_CODIGO")).thenReturn("MED-ACE-500");
        when(rs.getString("NOMBRE_COMERCIAL")).thenReturn("Acetaminofen");
        when(rs.getInt("CANTIDAD_ENTREGADA")).thenReturn(10);
        when(rs.getString("LOTE")).thenReturn("LOTE-99");
        when(rs.getDate("FECHA_VENCIMIENTO_LOTE")).thenReturn(Date.valueOf(LocalDate.of(2028, 5, 20)));
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.from(Instant.now()));

        doAnswer(inv -> {
            RowMapper<DispensacionDetalleResponse> rm = inv.getArgument(1);
            return List.of(rm.mapRow(rs, 1));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(55L));

        List<DispensacionDetalleResponse> list = repository.buscarDetallesPorDispensacionId(55L);

        assertThat(list).hasSize(1);
        assertThat(list.get(0).medicamentoPublicId()).isEqualTo("med-uuid-1");
        assertThat(list.get(0).cantidadEntregada()).isEqualTo(10);
        assertThat(list.get(0).lote()).isEqualTo("LOTE-99");
    }

    @Test
    @DisplayName("obtenerTotalesDispensadosPorRecetaId - Retorna mapa con acumulados")
    void obtenerTotalesDispensadosPorRecetaId_retornaMapa() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("RECETA_DETALLE_ID")).thenReturn(501L);
        when(rs.getInt("TOTAL_ENTREGADO")).thenReturn(15);

        doAnswer(inv -> {
            RowCallbackHandler rch = inv.getArgument(1);
            rch.processRow(rs);
            return null;
        }).when(jdbcTemplate).query(anyString(), any(RowCallbackHandler.class), eq(10L));

        Map<Long, Integer> mapa = repository.obtenerTotalesDispensadosPorRecetaId(10L);

        assertThat(mapa).containsEntry(501L, 15);
    }

    @Test
    @DisplayName("obtenerSaldosPorRecetaId - Calcula saldos y estados PENDIENTE, PARCIAL y TOTAL")
    void obtenerSaldosPorRecetaId_calculaSaldosYEstados() throws SQLException {
        ResultSet rs1 = mock(ResultSet.class);
        when(rs1.getString("MEDICAMENTO_PUBLIC_ID")).thenReturn("med-1");
        when(rs1.getString("MEDICAMENTO_CODIGO")).thenReturn("MED-1");
        when(rs1.getString("SNAPSHOT_NOMBRE")).thenReturn("Farmaco 1");
        when(rs1.getString("SNAPSHOT_PRINCIPIO_ACTIVO")).thenReturn("Principio 1");
        when(rs1.getString("SNAPSHOT_PRESENTACION")).thenReturn("Tableta");
        when(rs1.getString("SNAPSHOT_CONCENTRACION")).thenReturn("500 mg");
        when(rs1.getString("DOSIS")).thenReturn("1 c/8h");
        when(rs1.getString("FRECUENCIA")).thenReturn("8h");
        when(rs1.getInt("DURACION_DIAS")).thenReturn(5);
        when(rs1.getInt("CANTIDAD_PRESCRITA")).thenReturn(20);
        when(rs1.getInt("CANTIDAD_DISPENSADA")).thenReturn(0);
        when(rs1.getString("INDICACIONES")).thenReturn("Oral");

        ResultSet rs2 = mock(ResultSet.class);
        when(rs2.getString("MEDICAMENTO_PUBLIC_ID")).thenReturn("med-2");
        when(rs2.getString("MEDICAMENTO_CODIGO")).thenReturn("MED-2");
        when(rs2.getString("SNAPSHOT_NOMBRE")).thenReturn("Farmaco 2");
        when(rs2.getString("SNAPSHOT_PRINCIPIO_ACTIVO")).thenReturn("Principio 2");
        when(rs2.getString("SNAPSHOT_PRESENTACION")).thenReturn("Jarabe");
        when(rs2.getString("SNAPSHOT_CONCENTRACION")).thenReturn("15 mg");
        when(rs2.getString("DOSIS")).thenReturn("5 ml");
        when(rs2.getString("FRECUENCIA")).thenReturn("12h");
        when(rs2.getInt("DURACION_DIAS")).thenReturn(3);
        when(rs2.getInt("CANTIDAD_PRESCRITA")).thenReturn(10);
        when(rs2.getInt("CANTIDAD_DISPENSADA")).thenReturn(4);
        when(rs2.getString("INDICACIONES")).thenReturn("Oral");

        ResultSet rs3 = mock(ResultSet.class);
        when(rs3.getString("MEDICAMENTO_PUBLIC_ID")).thenReturn("med-3");
        when(rs3.getString("MEDICAMENTO_CODIGO")).thenReturn("MED-3");
        when(rs3.getString("SNAPSHOT_NOMBRE")).thenReturn("Farmaco 3");
        when(rs3.getString("SNAPSHOT_PRINCIPIO_ACTIVO")).thenReturn("Principio 3");
        when(rs3.getString("SNAPSHOT_PRESENTACION")).thenReturn("Capsula");
        when(rs3.getString("SNAPSHOT_CONCENTRACION")).thenReturn("20 mg");
        when(rs3.getString("DOSIS")).thenReturn("1 dia");
        when(rs3.getString("FRECUENCIA")).thenReturn("24h");
        when(rs3.getInt("DURACION_DIAS")).thenReturn(30);
        when(rs3.getInt("CANTIDAD_PRESCRITA")).thenReturn(30);
        when(rs3.getInt("CANTIDAD_DISPENSADA")).thenReturn(30);
        when(rs3.getString("INDICACIONES")).thenReturn("Oral");

        doAnswer(inv -> {
            RowMapper<SaldoMedicamentoDto> rm = inv.getArgument(1);
            return List.of(
                    rm.mapRow(rs1, 1),
                    rm.mapRow(rs2, 2),
                    rm.mapRow(rs3, 3)
            );
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(10L), eq(10L));

        List<SaldoMedicamentoDto> saldos = repository.obtenerSaldosPorRecetaId(10L);

        assertThat(saldos).hasSize(3);

        // Ítem 1: Sin entregas -> PENDIENTE, saldo 20
        assertThat(saldos.get(0).estado()).isEqualTo("PENDIENTE");
        assertThat(saldos.get(0).saldoPendiente()).isEqualTo(20);

        // Ítem 2: Entrega parcial (4 de 10) -> DISPENSADA_PARCIAL, saldo 6
        assertThat(saldos.get(1).estado()).isEqualTo("DISPENSADA_PARCIAL");
        assertThat(saldos.get(1).saldoPendiente()).isEqualTo(6);

        // Ítem 3: Entrega total (30 de 30) -> DISPENSADA_TOTAL, saldo 0
        assertThat(saldos.get(2).estado()).isEqualTo("DISPENSADA_TOTAL");
        assertThat(saldos.get(2).saldoPendiente()).isEqualTo(0);
    }

    @Test
    @DisplayName("buscarItemsPrescripcion - Retorna mapeo de ítems con ID de detalle")
    void buscarItemsPrescripcion_retornaItems() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("RECETA_DETALLE_ID")).thenReturn(888L);
        when(rs.getString("MEDICAMENTO_PUBLIC_ID")).thenReturn("med-uuid-abc");
        when(rs.getString("SNAPSHOT_NOMBRE")).thenReturn("Ibuprofeno");
        when(rs.getInt("CANTIDAD_PRESCRITA")).thenReturn(12);

        doAnswer(inv -> {
            RowMapper<DispensacionRepository.ItemPrescritoInfo> rm = inv.getArgument(1);
            return List.of(rm.mapRow(rs, 1));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(99L));

        List<DispensacionRepository.ItemPrescritoInfo> items = repository.buscarItemsPrescripcion(99L);

        assertThat(items).hasSize(1);
        assertThat(items.get(0).recetaDetalleId()).isEqualTo(888L);
        assertThat(items.get(0).medicamentoPublicId()).isEqualTo("med-uuid-abc");
        assertThat(items.get(0).cantidadPrescrita()).isEqualTo(12);
    }

    @Test
    @DisplayName("buscarRecetaDispensacionPorPublicId - Construye respuesta completa con código y estado")
    void buscarRecetaDispensacionPorPublicId_retornaRespuestaCompleta() throws SQLException {
        ResultSet rs = mock(ResultSet.class);
        when(rs.getString("RECETA_PUBLIC_ID")).thenReturn("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        when(rs.getString("ATENCION_PUBLIC_ID")).thenReturn("atn-1");
        when(rs.getString("PACIENTE_PUBLIC_ID")).thenReturn("pac-1");
        when(rs.getString("PACIENTE_DOCUMENTO")).thenReturn("CC 10203040");
        when(rs.getString("PACIENTE_NOMBRE")).thenReturn("Carlos Gomez");
        when(rs.getString("PROFESIONAL_PUBLIC_ID")).thenReturn("prof-1");
        when(rs.getString("PROFESIONAL_NOMBRE")).thenReturn("Dra. Perez");
        when(rs.getString("ESPECIALIDAD_NOMBRE")).thenReturn("Medicina General");
        when(rs.getInt("VIGENCIA_DIAS")).thenReturn(30);
        when(rs.getTimestamp("CREATED_AT")).thenReturn(Timestamp.from(Instant.now()));

        doAnswer(inv -> {
            RowMapper<RecetaDispensacionResponse> rm = inv.getArgument(1);
            return List.of(rm.mapRow(rs, 1));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), eq("a1b2c3d4-e5f6-7890-abcd-ef1234567890"));

        Optional<RecetaDispensacionResponse> res = repository.buscarRecetaDispensacionPorPublicId("a1b2c3d4-e5f6-7890-abcd-ef1234567890");

        assertThat(res).isPresent();
        RecetaDispensacionResponse r = res.get();
        assertThat(r.recetaPublicId()).isEqualTo("a1b2c3d4-e5f6-7890-abcd-ef1234567890");
        assertThat(r.codigoReclamacion()).isEqualTo("REC-A1B2C3D4");
        assertThat(r.vencida()).isFalse();
        assertThat(r.pacienteDocumento()).isEqualTo("CC 10203040");
    }

    @Test
    @DisplayName("contarRecetasDispensacion - Retorna total de coincidencias")
    void contarRecetasDispensacion_retornaTotal() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(), any(), any(), any()))
                .thenReturn(5);

        int total = repository.contarRecetasDispensacion("10203040");

        assertThat(total).isEqualTo(5);
    }
}

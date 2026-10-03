package com.meditriaje.repository;

import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.model.DisponibilidadSlot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DisponibilidadSlotRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DisponibilidadSlotRepository repository;

    @BeforeEach
    void setUp() {
        repository = new DisponibilidadSlotRepository(jdbcTemplate);
    }

    @Test
    void guardar_insertaYRetornaIdGenerado() throws SQLException {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        DisponibilidadSlot slot = new DisponibilidadSlot(
                "slot-uuid", 1L, 2L, 3L, inicio, fin, "PRESENCIAL", "LIBRE"
        );

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);

        doAnswer(invocation -> {
            PreparedStatementCreator psc = invocation.getArgument(0);
            KeyHolder kh = invocation.getArgument(1);
            psc.createPreparedStatement(connection);
            kh.getKeyList().add(java.util.Map.of("ID", 99L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long id = repository.guardar(slot);

        assertThat(id).isEqualTo(99L);
        verify(ps).setString(1, "slot-uuid");
        verify(ps).setLong(2, 1L);
        verify(ps).setLong(3, 2L);
        verify(ps).setLong(4, 3L);
        verify(ps).setTimestamp(5, Timestamp.from(inicio));
        verify(ps).setTimestamp(6, Timestamp.from(fin));
        verify(ps).setString(7, "PRESENCIAL");
        verify(ps).setString(8, "LIBRE");
    }

    @Test
    void guardarLote_ejecutaBatchUpdate() throws SQLException {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");
        DisponibilidadSlot slot1 = new DisponibilidadSlot(
                "slot-1", 1L, 2L, 3L, inicio, fin, "PRESENCIAL", "LIBRE"
        );
        DisponibilidadSlot slot2 = new DisponibilidadSlot(
                "slot-2", 1L, 2L, 3L, fin, fin.plusSeconds(1200), "PRESENCIAL", "LIBRE"
        );

        doAnswer(invocation -> {
            BatchPreparedStatementSetter bpss = invocation.getArgument(1);
            assertThat(bpss.getBatchSize()).isEqualTo(2);
            PreparedStatement ps = mock(PreparedStatement.class);
            bpss.setValues(ps, 0);
            verify(ps).setString(1, "slot-1");
            return new int[]{1, 1};
        }).when(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));

        repository.guardarLote(List.of(slot1, slot2));

        verify(jdbcTemplate).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    void buscarPorPublicId_retornaOptionalConSlotResponse() {
        SlotResponse esperado = new SlotResponse(
                "slot-uuid", "prof-uuid", "Dr. Gregory House", "sede-uuid", "Sede Central",
                "esp-uuid", "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("slot-uuid")))
                .thenReturn(List.of(esperado));

        Optional<SlotResponse> resultado = repository.buscarPorPublicId("slot-uuid");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("slot-uuid");
        assertThat(resultado.get().profesionalNombre()).isEqualTo("Dr. Gregory House");
    }

    @Test
    void buscarPorId_retornaOptionalConDisponibilidadSlot() {
        DisponibilidadSlot esperado = new DisponibilidadSlot(
                10L, "slot-uuid", 1L, 2L, 3L, Instant.now(), Instant.now().plusSeconds(1200), "PRESENCIAL", "LIBRE"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(esperado));

        Optional<DisponibilidadSlot> resultado = repository.buscarPorId(10L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(10L);
    }

    @Test
    void existeSolape_retornaTrueSiHayColision() {
        Instant inicio = Instant.parse("2026-10-10T08:00:00Z");
        Instant fin = Instant.parse("2026-10-10T08:20:00Z");

        when(jdbcTemplate.queryForObject(
                anyString(),
                eq(Integer.class),
                eq(1L),
                eq(Timestamp.from(fin)),
                eq(Timestamp.from(inicio))
        )).thenReturn(1);

        boolean existe = repository.existeSolape(1L, inicio, fin);

        assertThat(existe).isTrue();
    }

    @Test
    void cambiarEstado_ejecutaUpdate() {
        repository.cambiarEstado("slot-uuid", "BLOQUEADO");

        verify(jdbcTemplate).update(
                contains("UPDATE DISPONIBILIDAD_SLOT SET ESTADO = ? WHERE PUBLIC_ID = ?"),
                eq("BLOQUEADO"),
                eq("slot-uuid")
        );
    }

    @Test
    void eliminar_ejecutaDeleteEnEstadoLibre() {
        when(jdbcTemplate.update(anyString(), eq("slot-uuid"))).thenReturn(1);

        int eliminados = repository.eliminar("slot-uuid");

        assertThat(eliminados).isEqualTo(1);
        verify(jdbcTemplate).update(
                contains("DELETE FROM DISPONIBILIDAD_SLOT WHERE PUBLIC_ID = ? AND ESTADO = 'LIBRE'"),
                eq("slot-uuid")
        );
    }

    @Test
    void listarYContar_aplicaFiltrosYPaginacion() {
        Instant desde = Instant.now();
        Instant hasta = desde.plusSeconds(3600);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        List<SlotResponse> lista = repository.listar(0, 10, "prof-1", "sede-1", "esp-1", desde, hasta, "LIBRE");
        int count = repository.contar("prof-1", "sede-1", "esp-1", desde, hasta, "LIBRE");

        assertThat(lista).isEmpty();
        assertThat(count).isZero();
    }

    @Test
    void consultarDisponiblesYContarDisponibles_aplicaFiltrosAsistenciales() {
        Instant desde = Instant.now();
        Instant hasta = desde.plusSeconds(3600);

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of());
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), any(Object[].class)))
                .thenReturn(0);

        List<com.meditriaje.dto.availability.DisponibilidadSlotResponse> lista = repository.consultarDisponibles(
                "esp-1", "sede-1", "PRESENCIAL", desde, hasta, 0, 10
        );
        int count = repository.contarDisponibles("esp-1", "sede-1", "PRESENCIAL", desde, hasta);

        assertThat(lista).isEmpty();
        assertThat(count).isZero();
    }
}

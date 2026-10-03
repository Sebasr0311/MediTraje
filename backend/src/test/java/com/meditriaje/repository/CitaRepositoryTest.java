package com.meditriaje.repository;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.model.Cita;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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
class CitaRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CitaRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CitaRepository(jdbcTemplate);
    }

    @Test
    void crear_insertaCitaYRetornaIdGenerado() throws SQLException {
        Cita cita = new Cita(
                "cita-uuid-1", 10L, 20L, null, null, "PROGRAMADA", null
        );

        Connection connection = mock(Connection.class);
        PreparedStatement ps = mock(PreparedStatement.class);
        when(connection.prepareStatement(anyString(), any(String[].class))).thenReturn(ps);

        doAnswer(invocation -> {
            PreparedStatementCreator psc = invocation.getArgument(0);
            KeyHolder kh = invocation.getArgument(1);
            psc.createPreparedStatement(connection);
            kh.getKeyList().add(Map.of("ID", 101L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long id = repository.crear(cita);

        assertThat(id).isEqualTo(101L);
        verify(ps).setString(1, "cita-uuid-1");
        verify(ps).setLong(2, 10L);
        verify(ps).setLong(3, 20L);
        verify(ps).setNull(4, Types.NUMERIC);
        verify(ps).setNull(5, Types.NUMERIC);
        verify(ps).setString(6, "PROGRAMADA");
        verify(ps).setString(7, null);
    }

    @Test
    void buscarPorPublicId_retornaOptionalConCitaResponse() {
        CitaResponse mockResponse = new CitaResponse(
                "cita-uuid-1", "slot-uuid-1", "pac-uuid-1", "Juan Perez",
                "prof-uuid-1", "Dr. House", "esp-uuid-1", "Medicina General",
                "sede-uuid-1", "Sede Norte", "Calle 100",
                Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "PROGRAMADA", null, null, Instant.now()
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("cita-uuid-1")))
                .thenReturn(List.of(mockResponse));

        Optional<CitaResponse> resultado = repository.buscarPorPublicId("cita-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("cita-uuid-1");
        assertThat(resultado.get().pacienteNombre()).isEqualTo("Juan Perez");
        assertThat(resultado.get().profesionalNombre()).isEqualTo("Dr. House");
        assertThat(resultado.get().estado()).isEqualTo("PROGRAMADA");
    }

    @Test
    void buscarEntidadPorPublicId_retornaOptionalConCita() {
        Cita mockCita = new Cita(
                1L, "cita-uuid-1", 10L, 20L, null, null, "PROGRAMADA", null, Instant.now(), null
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("cita-uuid-1")))
                .thenReturn(List.of(mockCita));

        Optional<Cita> resultado = repository.buscarEntidadPorPublicId("cita-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("cita-uuid-1");
        assertThat(resultado.get().slotId()).isEqualTo(10L);
    }

    @Test
    void buscarEntidadPorId_retornaOptionalConCita() {
        Cita mockCita = new Cita(
                1L, "cita-uuid-1", 10L, 20L, null, null, "PROGRAMADA", null, Instant.now(), null
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(mockCita));

        Optional<Cita> resultado = repository.buscarEntidadPorId(1L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(1L);
    }

    @Test
    void actualizarEstado_ejecutaUpdateConParametros() {
        repository.actualizarEstado(1L, "CANCELADA", "Paciente solicito cancelacion");

        verify(jdbcTemplate).update(
                contains("UPDATE CITA"),
                eq("CANCELADA"),
                eq("Paciente solicito cancelacion"),
                eq(1L)
        );
    }

    @Test
    void existeCitaActivaEnSlot_retornaTrueSiHayCitasActivas() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(10L)))
                .thenReturn(1);

        boolean existe = repository.existeCitaActivaEnSlot(10L);

        assertThat(existe).isTrue();
    }

    @Test
    void existeCitaActivaEnSlot_retornaFalseSiNoHayCitasActivas() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(10L)))
                .thenReturn(0);

        boolean existe = repository.existeCitaActivaEnSlot(10L);

        assertThat(existe).isFalse();
    }
}

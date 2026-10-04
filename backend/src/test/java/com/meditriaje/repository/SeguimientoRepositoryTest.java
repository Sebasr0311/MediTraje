package com.meditriaje.repository;

import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.model.SeguimientoPostAtencion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeguimientoRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private SeguimientoRepository repository;

    @Test
    @DisplayName("guardar ejecuta inserción con PreparedStatementCreator y KeyHolder")
    void guardar_ejecutaInsert() {
        SeguimientoPostAtencion seguimiento = new SeguimientoPostAtencion(
                null,
                "seg-uuid-1",
                10L,
                20L,
                30L,
                "CONTROL_MEDICO",
                "Control de presión arterial en 7 días",
                LocalDate.now().plusDays(7),
                "PENDIENTE",
                null,
                null,
                Instant.now(),
                Instant.now()
        );

        org.mockito.Mockito.doAnswer(invocation -> {
            KeyHolder kh = invocation.getArgument(1);
            kh.getKeyList().add(java.util.Map.of("ID", 1L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long id = repository.guardar(seguimiento);

        assertThat(id).isEqualTo(1L);
        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    @DisplayName("buscarPorPublicId retorna Optional con SeguimientoResponse")
    void buscarPorPublicId_retornaResponse() {
        SeguimientoResponse resp = new SeguimientoResponse(
                "seg-uuid-1", "at-1", "pac-1", "Carlos Gomez",
                "prof-1", "Dra. Laura Perez", "Medicina General",
                "CONTROL_MEDICO", "Control de presión",
                LocalDate.now().plusDays(7), "PENDIENTE",
                null, null, Instant.now(), Instant.now()
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("seg-uuid-1")))
                .thenReturn(List.of(resp));

        Optional<SeguimientoResponse> opt = repository.buscarPorPublicId("seg-uuid-1");

        assertThat(opt).isPresent();
        assertThat(opt.get().publicId()).isEqualTo("seg-uuid-1");
        assertThat(opt.get().tipo()).isEqualTo("CONTROL_MEDICO");
    }

    @Test
    @DisplayName("registrarReportePaciente actualiza reporte, fecha y estado COMPLETADO")
    void registrarReportePaciente_ejecutaUpdate() {
        repository.registrarReportePaciente(10L, "Evolución favorable sin dolor", Instant.now());

        verify(jdbcTemplate).update(anyString(), eq("Evolución favorable sin dolor"), any(), eq(10L));
    }

    @Test
    @DisplayName("actualizarEstado actualiza estado del seguimiento")
    void actualizarEstado_ejecutaUpdate() {
        repository.actualizarEstado(10L, "CANCELADO");

        verify(jdbcTemplate).update(anyString(), eq("CANCELADO"), eq(10L));
    }

    @Test
    @DisplayName("listarPorAtencionId consulta seguimientos por atención")
    void listarPorAtencionId_retornaLista() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(5L)))
                .thenReturn(List.of());

        List<SeguimientoResponse> lista = repository.listarPorAtencionId(5L);

        assertThat(lista).isNotNull();
        verify(jdbcTemplate).query(anyString(), any(RowMapper.class), eq(5L));
    }

    @Test
    @DisplayName("contarPorPacienteId ejecuta conteo parametrizado")
    void contarPorPacienteId_retornaTotal() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(20L)))
                .thenReturn(3);

        int total = repository.contarPorPacienteId(20L, null);

        assertThat(total).isEqualTo(3);
    }
}

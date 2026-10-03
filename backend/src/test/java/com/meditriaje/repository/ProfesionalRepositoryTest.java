package com.meditriaje.repository;

import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.model.Profesional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProfesionalRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private ProfesionalRepository repository;

    @BeforeEach
    void setUp() {
        repository = new ProfesionalRepository(jdbcTemplate);
    }

    @Test
    void actualizar_ejecutaUpdateParametrizado() {
        Profesional prof = new Profesional(
                10L, "prof-uuid", 5L, "RM-12345", "Carlos", "Perez"
        );

        repository.actualizar(prof);

        verify(jdbcTemplate).update(
                anyString(),
                eq(5L),
                eq("Carlos"),
                eq("Perez"),
                eq("prof-uuid")
        );
    }

    @Test
    void buscarPorPublicId_ejecutaQueryParametrizada() {
        Profesional prof = new Profesional(
                10L, "prof-uuid", 5L, "RM-12345", "Carlos", "Perez"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("prof-uuid")))
                .thenReturn(List.of(prof));

        Optional<Profesional> res = repository.buscarPorPublicId("prof-uuid");

        assertThat(res).isPresent();
        assertThat(res.get().registroMedico()).isEqualTo("RM-12345");
    }

    @Test
    void buscarPorId_ejecutaQueryParametrizada() {
        Profesional prof = new Profesional(
                1L, 10L, "prof-uuid", 5L, "RM-12345", "Carlos", "Perez", null, null
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(prof));

        Optional<Profesional> res = repository.buscarPorId(1L);

        assertThat(res).isPresent();
        assertThat(res.get().id()).isEqualTo(1L);
    }

    @Test
    void buscarPorUsuarioId_ejecutaQueryParametrizada() {
        Profesional prof = new Profesional(
                10L, "prof-uuid", 5L, "RM-12345", "Carlos", "Perez"
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(prof));

        Optional<Profesional> res = repository.buscarPorUsuarioId(10L);

        assertThat(res).isPresent();
        assertThat(res.get().usuarioId()).isEqualTo(10L);
    }

    @Test
    void existePorRegistroMedico_retornaTrueCuandoExiste() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("RM-12345")))
                .thenReturn(1);

        boolean existe = repository.existePorRegistroMedico("RM-12345");

        assertThat(existe).isTrue();
    }

    @Test
    void existePorRegistroMedicoYNoPublicId_retornaTrueCuandoExisteOtro() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq("RM-12345"), eq("prof-uuid")))
                .thenReturn(1);

        boolean existe = repository.existePorRegistroMedicoYNoPublicId("RM-12345", "prof-uuid");

        assertThat(existe).isTrue();
    }

    @Test
    void listar_conFiltros_ejecutaQueryCorrecta() {
        when(jdbcTemplate.query(contains("AND e.PUBLIC_ID = ?"), any(RowMapper.class), any(), any(), any(), any()))
                .thenReturn(List.of());

        List<ProfesionalResponse> list = repository.listar(0, 10, "esp-uuid", "ACTIVO");

        assertThat(list).isNotNull();
    }

    @Test
    void contar_conFiltros_retornaCantidad() {
        when(jdbcTemplate.queryForObject(contains("AND u.ESTADO = ?"), eq(Integer.class), any(), any()))
                .thenReturn(5);

        int count = repository.contar("esp-uuid", "ACTIVO");

        assertThat(count).isEqualTo(5);
    }
}

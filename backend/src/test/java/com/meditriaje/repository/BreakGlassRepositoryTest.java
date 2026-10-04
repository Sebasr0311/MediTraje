package com.meditriaje.repository;

import com.meditriaje.dto.clinical.AccesoBreakGlassResponse;
import com.meditriaje.model.AccesoBreakGlass;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BreakGlassRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BreakGlassRepository breakGlassRepository;

    @BeforeEach
    void setUp() {
        breakGlassRepository = new BreakGlassRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("registrarAcceso: persiste e inyecta ID autogenerado")
    void registrarAcceso_exitoso() {
        Instant ahora = Instant.now();
        Instant exp = ahora.plus(24, ChronoUnit.HOURS);
        AccesoBreakGlass acceso = new AccesoBreakGlass(
                null,
                "bg-uuid-1234",
                10L,
                20L,
                "Paciente inconsciente por traumatismo craneoencefalico severo",
                exp,
                ahora
        );

        doAnswer(invocation -> {
            KeyHolder kh = invocation.getArgument(1);
            kh.getKeyList().add(Map.of("ID", 100L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long idGenerado = breakGlassRepository.registrarAcceso(acceso);

        assertThat(idGenerado).isEqualTo(100L);
    }

    @Test
    @DisplayName("existeAccesoActivo: retorna true si existe registro no expirado")
    void existeAccesoActivo_retornaTrue() {
        Instant ahora = Instant.now();
        when(jdbcTemplate.queryForObject(
                contains("FROM ACCESO_BREAK_GLASS"),
                eq(Integer.class),
                eq(10L),
                eq(20L),
                eq(Timestamp.from(ahora))
        )).thenReturn(1);

        boolean existe = breakGlassRepository.existeAccesoActivo(10L, 20L, ahora);

        assertThat(existe).isTrue();
    }

    @Test
    @DisplayName("existeAccesoActivo: con parámetros nulos retorna false sin ir a BD")
    void existeAccesoActivo_parametrosNulos_retornaFalse() {
        assertThat(breakGlassRepository.existeAccesoActivo(null, 20L, Instant.now())).isFalse();
        assertThat(breakGlassRepository.existeAccesoActivo(10L, null, Instant.now())).isFalse();
        assertThat(breakGlassRepository.existeAccesoActivo(10L, 20L, null)).isFalse();
    }

    @Test
    @DisplayName("buscarPorPublicId: retorna DTO con nombres si existe")
    @SuppressWarnings("unchecked")
    void buscarPorPublicId_encontrado() {
        Instant ahora = Instant.now();
        Instant exp = ahora.plus(24, ChronoUnit.HOURS);
        AccesoBreakGlassResponse esperado = new AccesoBreakGlassResponse(
                "bg-uuid-1",
                "prof-uuid-1",
                "Dr. Roberto Gomez",
                "pac-uuid-1",
                "Carlos Sanchez",
                "Urgencia vital por paro cardiorrespiratorio",
                exp,
                ahora,
                true
        );

        when(jdbcTemplate.queryForObject(contains("FROM ACCESO_BREAK_GLASS"), any(RowMapper.class), eq("bg-uuid-1")))
                .thenReturn(esperado);

        Optional<AccesoBreakGlassResponse> resultado = breakGlassRepository.buscarPorPublicId("bg-uuid-1", ahora);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("bg-uuid-1");
        assertThat(resultado.get().profesionalNombre()).isEqualTo("Dr. Roberto Gomez");
        assertThat(resultado.get().pacienteNombre()).isEqualTo("Carlos Sanchez");
        assertThat(resultado.get().activo()).isTrue();
    }

    @Test
    @DisplayName("buscarPorPublicId: si no existe retorna Optional.empty()")
    @SuppressWarnings("unchecked")
    void buscarPorPublicId_noExiste() {
        when(jdbcTemplate.queryForObject(contains("FROM ACCESO_BREAK_GLASS"), any(RowMapper.class), eq("bg-inexistente")))
                .thenThrow(new EmptyResultDataAccessException(1));

        Optional<AccesoBreakGlassResponse> resultado = breakGlassRepository.buscarPorPublicId("bg-inexistente", Instant.now());

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("listarActivosPorProfesional: retorna lista de accesos vigentes")
    @SuppressWarnings("unchecked")
    void listarActivosPorProfesional_retornaLista() {
        Instant ahora = Instant.now();
        Instant exp = ahora.plus(12, ChronoUnit.HOURS);
        AccesoBreakGlassResponse item = new AccesoBreakGlassResponse(
                "bg-uuid-2",
                "prof-uuid-1",
                "Dr. Roberto Gomez",
                "pac-uuid-2",
                "Ana Maria Lopez",
                "Politrauma en accidente de transito con perdida de conciencia",
                exp,
                ahora,
                true
        );

        when(jdbcTemplate.query(contains("FROM ACCESO_BREAK_GLASS"), any(RowMapper.class), eq(10L), eq(Timestamp.from(ahora))))
                .thenReturn(List.of(item));

        List<AccesoBreakGlassResponse> lista = breakGlassRepository.listarActivosPorProfesional(10L, ahora);

        assertThat(lista).hasSize(1);
        assertThat(lista.get(0).publicId()).isEqualTo("bg-uuid-2");
        assertThat(lista.get(0).activo()).isTrue();
    }
}

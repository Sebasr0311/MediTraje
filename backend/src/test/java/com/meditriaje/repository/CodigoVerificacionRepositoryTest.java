package com.meditriaje.repository;

import com.meditriaje.model.CodigoVerificacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodigoVerificacionRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private CodigoVerificacionRepository repository;

    @BeforeEach
    void setUp() {
        repository = new CodigoVerificacionRepository(jdbcTemplate);
    }

    @Test
    void buscarPorId_retornaCodigoSiExiste() {
        CodigoVerificacion codigo = new CodigoVerificacion(
                1L, "cod-uuid", 10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                "hash123", Instant.now().plus(15, ChronoUnit.MINUTES), 0, 3, false, Instant.now()
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(codigo));

        Optional<CodigoVerificacion> resultado = repository.buscarPorId(1L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(1L);
        assertThat(resultado.get().usuarioId()).isEqualTo(10L);
        assertThat(resultado.get().codigoHash()).isEqualTo("hash123");
    }

    @Test
    void buscarPorId_retornaVacioSiNoExiste() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(99L)))
                .thenReturn(List.of());

        Optional<CodigoVerificacion> resultado = repository.buscarPorId(99L);

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscarUltimoPendientePorUsuarioYTipo_retornaCodigoSiExiste() {
        CodigoVerificacion codigo = new CodigoVerificacion(
                2L, "cod-uuid-2", 10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD,
                "hash456", Instant.now().plus(15, ChronoUnit.MINUTES), 1, 3, false, Instant.now()
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L), eq(CodigoVerificacion.TIPO_RECUPERACION_PASSWORD)))
                .thenReturn(List.of(codigo));

        Optional<CodigoVerificacion> resultado = repository
                .buscarUltimoPendientePorUsuarioYTipo(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(2L);
        assertThat(resultado.get().intentosFallidos()).isEqualTo(1);
    }

    @Test
    void incrementarIntentos_ejecutaUpdateParametrizado() {
        repository.incrementarIntentos(5L);

        verify(jdbcTemplate).update(
                anyString(),
                eq(5L)
        );
    }

    @Test
    void marcarComoUsado_ejecutaUpdateParametrizado() {
        repository.marcarComoUsado(5L);

        verify(jdbcTemplate).update(
                anyString(),
                eq(5L)
        );
    }

    @Test
    void invalidarCodigosPrevios_ejecutaUpdateParametrizado() {
        repository.invalidarCodigosPrevios(10L, CodigoVerificacion.TIPO_RECUPERACION_PASSWORD);

        verify(jdbcTemplate).update(
                anyString(),
                eq(10L),
                eq(CodigoVerificacion.TIPO_RECUPERACION_PASSWORD)
        );
    }
}

package com.meditriaje.repository;

import com.meditriaje.model.Paciente;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private PacienteRepository repository;

    @BeforeEach
    void setUp() {
        repository = new PacienteRepository(jdbcTemplate);
    }

    @Test
    void buscarPorUsuarioId_retornaPacienteSiExiste() {
        Paciente paciente = new Paciente(
                10L, 1L, "pac-uuid", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(1L)))
                .thenReturn(List.of(paciente));

        Optional<Paciente> resultado = repository.buscarPorUsuarioId(1L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(10L);
        assertThat(resultado.get().usuarioId()).isEqualTo(1L);
        assertThat(resultado.get().nombres()).isEqualTo("Pepito");
    }

    @Test
    void buscarPorUsuarioId_retornaVacioSiNoExiste() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(99L)))
                .thenReturn(List.of());

        Optional<Paciente> resultado = repository.buscarPorUsuarioId(99L);

        assertThat(resultado).isEmpty();
    }

    @Test
    void buscarPorId_retornaPacienteSiExiste() {
        Paciente paciente = new Paciente(
                10L, 1L, "pac-uuid", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(paciente));

        Optional<Paciente> resultado = repository.buscarPorId(10L);

        assertThat(resultado).isPresent();
        assertThat(resultado.get().id()).isEqualTo(10L);
    }

    @Test
    void buscarPorPublicId_retornaPacienteSiExiste() {
        Paciente paciente = new Paciente(
                10L, 1L, "pac-uuid", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), null
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("pac-uuid")))
                .thenReturn(List.of(paciente));

        Optional<Paciente> resultado = repository.buscarPorPublicId("pac-uuid");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().publicId()).isEqualTo("pac-uuid");
    }
}


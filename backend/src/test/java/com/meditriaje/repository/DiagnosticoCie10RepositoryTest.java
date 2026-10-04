package com.meditriaje.repository;

import com.meditriaje.dto.clinical.DiagnosticoCie10Response;
import com.meditriaje.model.DiagnosticoCie10;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DiagnosticoCie10RepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private DiagnosticoCie10Repository repository;

    @BeforeEach
    void setUp() {
        repository = new DiagnosticoCie10Repository(jdbcTemplate);
    }

    @Test
    void buscarPorCodigo_retornaEntidadSiExiste() {
        DiagnosticoCie10 diag = new DiagnosticoCie10(1L, "J00", "Rinofaringitis aguda", "ACTIVO");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("J00")))
                .thenReturn(List.of(diag));

        Optional<DiagnosticoCie10> resultado = repository.buscarPorCodigo("J00");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().codigo()).isEqualTo("J00");
        assertThat(resultado.get().descripcion()).isEqualTo("Rinofaringitis aguda");
    }

    @Test
    void buscarPorCodigo_retornaVacioSiNoExiste() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("Z99.9")))
                .thenReturn(List.of());

        Optional<DiagnosticoCie10> resultado = repository.buscarPorCodigo("Z99.9");

        assertThat(resultado).isEmpty();
    }

    @Test
    void listarActivos_conQueryFiltraCorrectamente() {
        DiagnosticoCie10Response item = new DiagnosticoCie10Response("J00", "Rinofaringitis aguda");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any()))
                .thenReturn(List.of(item));

        List<DiagnosticoCie10Response> resultado = repository.listarActivos("rino");

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).codigo()).isEqualTo("J00");
    }
}

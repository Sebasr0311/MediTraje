package com.meditriaje.repository;

import com.meditriaje.model.Alergia;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AlergiaRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AlergiaRepository alergiaRepository;

    @BeforeEach
    void setUp() {
        alergiaRepository = new AlergiaRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("listarPorPacienteId: retorna lista de alergias")
    void listarPorPacienteId_retornaLista() {
        Alergia alergia = new Alergia(1L, 10L, "Penicilina", "Anafilaxia", "GRAVE", Instant.now());
        when(jdbcTemplate.query(ArgumentMatchers.contains("FROM ALERGIA"), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(alergia));

        List<Alergia> resultado = alergiaRepository.listarPorPacienteId(10L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).sustancia()).isEqualTo("Penicilina");
        assertThat(resultado.get(0).severidad()).isEqualTo("GRAVE");
    }

    @Test
    @DisplayName("listarPorPacienteId: con id nulo retorna lista vacía sin consultar BD")
    void listarPorPacienteId_idNulo() {
        List<Alergia> resultado = alergiaRepository.listarPorPacienteId(null);
        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("crear: inserta y recupera clave generada")
    void crear_exito() {
        doAnswer(invocation -> {
            KeyHolder kh = invocation.getArgument(1);
            kh.getKeyList().add(Map.of("ID", 55L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Alergia nueva = new Alergia(null, 10L, "Ibuprofeno", "Erupcion", "MODERADA", null);
        Long id = alergiaRepository.crear(nueva);

        assertThat(id).isEqualTo(55L);
    }
}

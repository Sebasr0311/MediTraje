package com.meditriaje.repository;

import com.meditriaje.dto.allergy.AlergiaResponse;
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

import java.sql.Timestamp;
import java.time.Instant;
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
class AlergiaRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private AlergiaRepository alergiaRepository;

    @BeforeEach
    void setUp() {
        alergiaRepository = new AlergiaRepository(jdbcTemplate);
    }

    @Test
    @DisplayName("listarPorPacienteId: retorna lista de alergias activas")
    void listarPorPacienteId_retornaLista() {
        Alergia alergia = new Alergia(1L, "ale-uuid-1", 10L, "Penicilina", "Anafilaxia", "GRAVE", "ACTIVA", "PROFESIONAL", 1L, null, null, null, null, Instant.now(), Instant.now());
        when(jdbcTemplate.query(contains("FROM ALERGIA"), any(RowMapper.class), eq(10L), eq(1)))
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
    @DisplayName("listarPorPacientePublicId: retorna lista de DTOs AlergiaResponse")
    void listarPorPacientePublicId_exito() {
        AlergiaResponse response = new AlergiaResponse(
                "ale-uuid-1", "pac-uuid-10", "Ibuprofeno", "Erupcion", "MODERADA", "ACTIVA", "PACIENTE", true, null, Instant.now(), null, null
        );
        when(jdbcTemplate.query(contains("FROM ALERGIA a"), any(RowMapper.class), eq("pac-uuid-10"), eq(1)))
                .thenReturn(List.of(response));

        List<AlergiaResponse> resultado = alergiaRepository.listarPorPacientePublicId("pac-uuid-10", true);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).sustancia()).isEqualTo("Ibuprofeno");
        assertThat(resultado.get(0).autorreportada()).isTrue();
    }

    @Test
    @DisplayName("buscarEntidadPorPublicId: retorna Optional con la entidad si existe")
    void buscarEntidadPorPublicId_exito() {
        Alergia alergia = new Alergia(1L, "ale-uuid-1", 10L, "Dipirona", "Hipotension", "GRAVE", "ACTIVA", "PROFESIONAL", 2L, null, null, null, null, Instant.now(), Instant.now());
        when(jdbcTemplate.query(contains("WHERE PUBLIC_ID = ?"), any(RowMapper.class), eq("ale-uuid-1")))
                .thenReturn(List.of(alergia));

        Optional<Alergia> resultado = alergiaRepository.buscarEntidadPorPublicId("ale-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().sustancia()).isEqualTo("Dipirona");
    }

    @Test
    @DisplayName("buscarPorPublicId: retorna Optional con AlergiaResponse DTO")
    void buscarPorPublicId_exito() {
        AlergiaResponse response = new AlergiaResponse(
                "ale-uuid-1", "pac-uuid-10", "Dipirona", "Hipotension", "GRAVE", "ACTIVA", "PROFESIONAL", false, null, Instant.now(), null, null
        );
        when(jdbcTemplate.query(contains("WHERE a.PUBLIC_ID = ?"), any(RowMapper.class), eq("ale-uuid-1")))
                .thenReturn(List.of(response));

        Optional<AlergiaResponse> resultado = alergiaRepository.buscarPorPublicId("ale-uuid-1");

        assertThat(resultado).isPresent();
        assertThat(resultado.get().sustancia()).isEqualTo("Dipirona");
        assertThat(resultado.get().autorreportada()).isFalse();
    }

    @Test
    @DisplayName("existeActivaPorSustancia: true si conteo > 0")
    void existeActivaPorSustancia_true() {
        when(jdbcTemplate.queryForObject(contains("COUNT(*)"), eq(Integer.class), eq(10L), eq("Penicilina")))
                .thenReturn(1);

        boolean existe = alergiaRepository.existeActivaPorSustancia(10L, "Penicilina");
        assertThat(existe).isTrue();
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

    @Test
    @DisplayName("inactivar: ejecuta update y retorna filas afectadas")
    void inactivar_exito() {
        Instant inactInstant = Instant.now();
        when(jdbcTemplate.update(
                contains("UPDATE ALERGIA"),
                eq(99L),
                eq("Prueba negativa"),
                eq(Timestamp.from(inactInstant)),
                eq(55L)
        )).thenReturn(1);

        int actualizados = alergiaRepository.inactivar(55L, 99L, "Prueba negativa", inactInstant);
        assertThat(actualizados).isEqualTo(1);
    }
}

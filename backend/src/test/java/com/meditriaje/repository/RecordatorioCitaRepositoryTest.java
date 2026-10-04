package com.meditriaje.repository;

import com.meditriaje.model.RecordatorioCita;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecordatorioCitaRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private RecordatorioCitaRepository repository;

    @Test
    @DisplayName("guardar ejecuta inserción con PreparedStatementCreator y KeyHolder")
    void guardar_ejecutaInsert() {
        RecordatorioCita recordatorio = new RecordatorioCita(
                null,
                "rec-uuid-1",
                10L,
                20L,
                "CONFIRMACION_RESERVA",
                "EMAIL",
                "paciente@example.com",
                "ENVIADO",
                null,
                Instant.now()
        );

        repository.guardar(recordatorio);

        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    @DisplayName("listarPorCitaId ejecuta consulta parametrizada con citaId")
    void listarPorCitaId_ejecutaQuery() {
        Long citaId = 15L;
        RecordatorioCita r1 = new RecordatorioCita(1L, "rec-1", citaId, 2L, "CONFIRMACION_RESERVA", "EMAIL", "a@b.com", "ENVIADO", null, Instant.now());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(citaId)))
                .thenReturn(List.of(r1));

        List<RecordatorioCita> resultado = repository.listarPorCitaId(citaId);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).publicId()).isEqualTo("rec-1");
    }

    @Test
    @DisplayName("contarPorCitaIdYTipo ejecuta conteo parametrizado")
    void contarPorCitaIdYTipo_ejecutaConteo() {
        Long citaId = 15L;
        String tipo = "CONFIRMACION_RESERVA";
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(citaId), eq(tipo)))
                .thenReturn(1);

        int count = repository.contarPorCitaIdYTipo(citaId, tipo);

        assertThat(count).isEqualTo(1);
    }
}

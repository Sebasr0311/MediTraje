package com.meditriaje.repository;

import com.meditriaje.model.MfaBackupCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MfaBackupCodeRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private MfaBackupCodeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new MfaBackupCodeRepository(jdbcTemplate);
    }

    @Test
    void guardarLote_conListaVaciaONula_noEjecutaBatchUpdate() {
        repository.guardarLote(1L, null);
        repository.guardarLote(1L, List.of());

        verify(jdbcTemplate, never()).batchUpdate(anyString(), any(List.class));
    }

    @Test
    void guardarLote_conHashesValidos_ejecutaBatchUpdate() {
        List<String> hashes = List.of("hash1", "hash2", "hash3");

        repository.guardarLote(10L, hashes);

        ArgumentCaptor<List<Object[]>> captor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(anyString(), captor.capture());

        List<Object[]> batchArgs = captor.getValue();
        assertThat(batchArgs).hasSize(3);
        assertThat(batchArgs.get(0)).containsExactly(10L, "hash1");
        assertThat(batchArgs.get(1)).containsExactly(10L, "hash2");
        assertThat(batchArgs.get(2)).containsExactly(10L, "hash3");
    }

    @Test
    void eliminarPorUsuario_ejecutaUpdateParametrizado() {
        repository.eliminarPorUsuario(15L);

        verify(jdbcTemplate).update(anyString(), eq(15L));
    }

    @Test
    void consumirCodigo_cuandoExisteCodigoValido_retornaTrue() {
        when(jdbcTemplate.update(anyString(), eq(15L), eq("hash-valido"))).thenReturn(1);

        boolean consumido = repository.consumirCodigo(15L, "hash-valido");

        assertThat(consumido).isTrue();
        verify(jdbcTemplate).update(anyString(), eq(15L), eq("hash-valido"));
    }

    @Test
    void consumirCodigo_cuandoCodigoYaUsadoONoExiste_retornaFalse() {
        when(jdbcTemplate.update(anyString(), eq(15L), eq("hash-invalido"))).thenReturn(0);

        boolean consumido = repository.consumirCodigo(15L, "hash-invalido");

        assertThat(consumido).isFalse();
    }

    @Test
    void contarDisponibles_retornaConteo() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(20L))).thenReturn(8);

        int disponibles = repository.contarDisponibles(20L);

        assertThat(disponibles).isEqualTo(8);
    }

    @Test
    void contarDisponibles_cuandoRetornaNull_retornaCero() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(20L))).thenReturn(null);

        int disponibles = repository.contarDisponibles(20L);

        assertThat(disponibles).isEqualTo(0);
    }

    @Test
    void listarPorUsuario_retornaListaDeCodigos() {
        MfaBackupCode code = new MfaBackupCode(1L, 20L, "hash1", false, null, Instant.now());
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(20L))).thenReturn(List.of(code));

        List<MfaBackupCode> resultado = repository.listarPorUsuario(20L);

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).codeHash()).isEqualTo("hash1");
        assertThat(resultado.get(0).usado()).isFalse();
    }
}

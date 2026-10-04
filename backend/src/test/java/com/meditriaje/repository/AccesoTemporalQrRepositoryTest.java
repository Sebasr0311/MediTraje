package com.meditriaje.repository;

import com.meditriaje.model.AccesoTemporalQr;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccesoTemporalQrRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AccesoTemporalQrRepository repository;

    @Test
    @DisplayName("crear ejecuta inserción con PreparedStatementCreator y KeyHolder y retorna ID")
    void crear_ejecutaInsertYRetornaId() {
        AccesoTemporalQr acceso = new AccesoTemporalQr(
                null,
                "qr-uuid-1",
                10L,
                "tokenhash123456",
                null,
                true,
                true,
                true,
                true,
                3,
                0,
                false,
                Instant.now().plusSeconds(900),
                Instant.now(),
                Instant.now()
        );

        doAnswer(invocation -> {
            KeyHolder kh = invocation.getArgument(1);
            kh.getKeyList().add(Map.of("ID", 42L));
            return 1;
        }).when(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));

        Long id = repository.crear(acceso);

        assertThat(id).isEqualTo(42L);
        verify(jdbcTemplate).update(any(PreparedStatementCreator.class), any(KeyHolder.class));
    }

    @Test
    @DisplayName("buscarPorTokenHash retorna Optional con entidad si existe")
    void buscarPorTokenHash_encuentraRegistro() {
        String tokenHash = "hash-token-abc";
        AccesoTemporalQr esperado = new AccesoTemporalQr(
                1L, "qr-1", 10L, tokenHash, null,
                true, true, true, true,
                3, 0, false, Instant.now().plusSeconds(900), Instant.now(), null
        );

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(tokenHash)))
                .thenReturn(esperado);

        Optional<AccesoTemporalQr> resultado = repository.buscarPorTokenHash(tokenHash);

        assertThat(resultado).isPresent().contains(esperado);
    }

    @Test
    @DisplayName("buscarPorTokenHash retorna Optional vacío ante EmptyResultDataAccessException")
    void buscarPorTokenHash_noEncuentra() {
        String tokenHash = "inexistente";
        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(tokenHash)))
                .thenThrow(new EmptyResultDataAccessException(1));

        Optional<AccesoTemporalQr> resultado = repository.buscarPorTokenHash(tokenHash);

        assertThat(resultado).isEmpty();
    }

    @Test
    @DisplayName("buscarPorPublicId retorna Optional con entidad")
    void buscarPorPublicId_encuentraRegistro() {
        String publicId = "qr-uuid-abc";
        AccesoTemporalQr esperado = new AccesoTemporalQr(
                1L, publicId, 10L, "hash", null,
                true, true, true, true,
                3, 0, false, Instant.now().plusSeconds(900), Instant.now(), null
        );

        when(jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), eq(publicId)))
                .thenReturn(esperado);

        Optional<AccesoTemporalQr> resultado = repository.buscarPorPublicId(publicId);

        assertThat(resultado).isPresent().contains(esperado);
    }

    @Test
    @DisplayName("listarPorPacienteId retorna lista ordenada")
    void listarPorPacienteId_retornaLista() {
        Long pacienteId = 10L;
        AccesoTemporalQr a1 = new AccesoTemporalQr(1L, "qr-1", pacienteId, "h1", null, true, true, true, true, 3, 0, false, Instant.now(), Instant.now(), null);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq(pacienteId)))
                .thenReturn(List.of(a1));

        List<AccesoTemporalQr> lista = repository.listarPorPacienteId(pacienteId);

        assertThat(lista).hasSize(1).contains(a1);
    }

    @Test
    @DisplayName("registrarAcceso ejecuta UPDATE condicional y retorna filas afectadas")
    void registrarAcceso_ejecutaUpdate() {
        Long id = 5L;
        Instant now = Instant.now();
        when(jdbcTemplate.update(anyString(), eq(id), any(Timestamp.class))).thenReturn(1);

        int actualizadas = repository.registrarAcceso(id, now);

        assertThat(actualizadas).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), eq(id), any(Timestamp.class));
    }

    @Test
    @DisplayName("revocar ejecuta UPDATE con publicId y pacienteId")
    void revocar_ejecutaUpdate() {
        String publicId = "qr-123";
        Long pacienteId = 10L;
        when(jdbcTemplate.update(anyString(), eq(publicId), eq(pacienteId))).thenReturn(1);

        int actualizadas = repository.revocar(publicId, pacienteId);

        assertThat(actualizadas).isEqualTo(1);
        verify(jdbcTemplate).update(anyString(), eq(publicId), eq(pacienteId));
    }
}

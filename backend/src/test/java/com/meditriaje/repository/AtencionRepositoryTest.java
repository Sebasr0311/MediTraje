package com.meditriaje.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AtencionRepository}.
 */
@ExtendWith(MockitoExtension.class)
class AtencionRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AtencionRepository atencionRepository;

    @Test
    void existeAtencionPreviaEnVentana_retornaTrueCuandoExisteRegistro() {
        Long profesionalId = 1L;
        Long pacienteId = 2L;
        Instant fechaLimite = Instant.now().minusSeconds(86400 * 365);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(pacienteId), eq(profesionalId), any(Timestamp.class)))
                .thenReturn(1);

        boolean resultado = atencionRepository.existeAtencionPreviaEnVentana(profesionalId, pacienteId, fechaLimite);

        assertThat(resultado).isTrue();
        verify(jdbcTemplate).queryForObject(anyString(), eq(Integer.class), eq(pacienteId), eq(profesionalId), eq(Timestamp.from(fechaLimite)));
    }

    @Test
    void existeAtencionPreviaEnVentana_retornaFalseCuandoNoHayRegistro() {
        Long profesionalId = 1L;
        Long pacienteId = 2L;
        Instant fechaLimite = Instant.now().minusSeconds(86400 * 365);

        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(pacienteId), eq(profesionalId), any(Timestamp.class)))
                .thenReturn(0);

        boolean resultado = atencionRepository.existeAtencionPreviaEnVentana(profesionalId, pacienteId, fechaLimite);

        assertThat(resultado).isFalse();
    }

    @Test
    void buscarEnmiendasPorAtencionId_atencionIdNulo_retornaVacio() {
        var lista = atencionRepository.buscarEnmiendasPorAtencionId(null);
        assertThat(lista).isEmpty();
    }

    @Test
    void listarHistoriaPaciente_pacienteIdNulo_retornaVacio() {
        var lista = atencionRepository.listarHistoriaPaciente(null, 0, 10);
        assertThat(lista).isEmpty();
    }

    @Test
    void contarHistoriaPaciente_pacienteIdNulo_retornaCero() {
        int total = atencionRepository.contarHistoriaPaciente(null);
        assertThat(total).isZero();
    }

    @Test
    void contarHistoriaPaciente_retornaTotal() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Integer.class), eq(5L))).thenReturn(3);
        int total = atencionRepository.contarHistoriaPaciente(5L);
        assertThat(total).isEqualTo(3);
    }
}

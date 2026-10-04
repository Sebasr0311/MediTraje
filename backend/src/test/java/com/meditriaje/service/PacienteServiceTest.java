package com.meditriaje.service;

import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.repository.PacienteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PacienteServiceTest {

    @Mock
    private PacienteRepository pacienteRepository;

    private PacienteService pacienteService;

    @BeforeEach
    void setUp() {
        pacienteService = new PacienteService(pacienteRepository);
    }

    @Test
    void obtenerMiPerfil_pacienteExiste_retornaPerfil() {
        String usuarioPublicId = "usr-pub-123";
        PacientePerfilResponse perfilEsperado = new PacientePerfilResponse(
                "pac-pub-456",
                "CC",
                "1020304050",
                "Carlos",
                "Perez",
                LocalDate.of(1995, 5, 20),
                "3001234567",
                "carlos.perez@example.com"
        );

        when(pacienteRepository.buscarPerfilPorUsuarioPublicId(usuarioPublicId))
                .thenReturn(Optional.of(perfilEsperado));

        PacientePerfilResponse resultado = pacienteService.obtenerMiPerfil(usuarioPublicId);

        assertThat(resultado).isNotNull();
        assertThat(resultado.publicId()).isEqualTo("pac-pub-456");
        assertThat(resultado.nombres()).isEqualTo("Carlos");
        assertThat(resultado.email()).isEqualTo("carlos.perez@example.com");
    }

    @Test
    void obtenerMiPerfil_pacienteNoExiste_lanzaRecursoNoEncontrado() {
        String usuarioPublicId = "usr-inexistente";
        when(pacienteRepository.buscarPerfilPorUsuarioPublicId(usuarioPublicId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> pacienteService.obtenerMiPerfil(usuarioPublicId))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("recurso solicitado no fue encontrado");
    }

    @Test
    void obtenerMiPerfil_idInvalido_lanzaIllegalArgumentException() {
        assertThatThrownBy(() -> pacienteService.obtenerMiPerfil(null))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> pacienteService.obtenerMiPerfil("   "))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

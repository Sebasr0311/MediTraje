package com.meditriaje.service;

import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.repository.ConsentimientoRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ConsentimientoRepository consentimientoRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(
                usuarioRepository,
                pacienteRepository,
                consentimientoRepository,
                auditoriaService,
                passwordEncoder
        );
    }

    private RegistroPacienteRequest requestValido() {
        return new RegistroPacienteRequest(
                "CC",
                "1020304050",
                "Carlos",
                "Perez",
                LocalDate.of(1995, 5, 20),
                "3001234567",
                "carlos.perez@example.com",
                "Segura12345*",
                "v1.0",
                true
        );
    }

    @Test
    void registrarPaciente_exito_creaUsuarioPacienteConsentimientoYAudita() {
        RegistroPacienteRequest req = requestValido();

        when(usuarioRepository.existePorEmail("carlos.perez@example.com")).thenReturn(false);
        when(pacienteRepository.existePorDocumento("CC", "1020304050")).thenReturn(false);
        when(passwordEncoder.encode("Segura12345*")).thenReturn("$argon2id$encoded");
        when(usuarioRepository.crear(anyString(), eq("carlos.perez@example.com"), eq("$argon2id$encoded"))).thenReturn(100L);
        when(usuarioRepository.buscarRolIdPorNombre("ROLE_PACIENTE")).thenReturn(Optional.of(1L));
        when(pacienteRepository.crear(eq(100L), anyString(), eq("CC"), eq("1020304050"), eq("Carlos"), eq("Perez"), eq(LocalDate.of(1995, 5, 20)), eq("3001234567"))).thenReturn(200L);
        when(consentimientoRepository.registrar(eq(100L), eq("v1.0"), eq(true), eq("192.168.1.5"))).thenReturn(300L);

        RegistroPacienteResponse res = authService.registrarPaciente(req, "192.168.1.5");

        assertThat(res).isNotNull();
        assertThat(res.email()).isEqualTo("carlos.perez@example.com");
        assertThat(res.pacientePublicId()).isNotBlank();
        assertThat(res.nombres()).isEqualTo("Carlos");
        assertThat(res.apellidos()).isEqualTo("Perez");

        verify(usuarioRepository).asignarRol(100L, 1L);
        verify(auditoriaService).registrarEvento(
                eq(100L),
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("PACIENTE"),
                anyString(),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.5")
        );
    }

    @Test
    void registrarPaciente_correoDuplicado_lanzaExcepcionYAuditaFallo() {
        RegistroPacienteRequest req = requestValido();
        when(usuarioRepository.existePorEmail("carlos.perez@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrarPaciente(req, "192.168.1.5"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("correo electronico ya se encuentra registrado");

        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("USUARIO"),
                any(),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.5")
        );
        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString());
    }

    @Test
    void registrarPaciente_documentoDuplicado_lanzaExcepcionYAuditaFallo() {
        RegistroPacienteRequest req = requestValido();
        when(usuarioRepository.existePorEmail("carlos.perez@example.com")).thenReturn(false);
        when(pacienteRepository.existePorDocumento("CC", "1020304050")).thenReturn(true);

        assertThatThrownBy(() -> authService.registrarPaciente(req, "192.168.1.5"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("documento de identidad ya se encuentra registrado");

        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("PACIENTE"),
                any(),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.5")
        );
        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString());
    }

    @Test
    void registrarPaciente_sinConsentimiento_lanzaExcepcionYAuditaFallo() {
        RegistroPacienteRequest req = new RegistroPacienteRequest(
                "CC", "1020304050", "Carlos", "Perez",
                LocalDate.of(1995, 5, 20), null, "carlos.perez@example.com",
                "Segura12345*", "v1.0", false
        );

        assertThatThrownBy(() -> authService.registrarPaciente(req, "192.168.1.5"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("consentimiento informado");

        verify(auditoriaService).registrarEvento(
                eq(AccionAuditable.REGISTRO_PACIENTE),
                eq("USUARIO"),
                any(),
                eq(ResultadoAuditoria.FALLO),
                eq("192.168.1.5")
        );
    }
}

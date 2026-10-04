package com.meditriaje.service;

import com.meditriaje.dto.emergency.AccesoQrResponse;
import com.meditriaje.dto.emergency.GenerarQrRequest;
import com.meditriaje.dto.emergency.GenerarQrResponse;
import com.meditriaje.dto.emergency.VerificarQrResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccesoTemporalQr;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AccesoTemporalQrRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.security.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyQrServiceTest {

    @Mock
    private AccesoTemporalQrRepository accesoTemporalQrRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    private Clock fixedClock;
    private Instant fixedInstant;

    private EmergencyQrService emergencyQrService;

    private Usuario usuarioPaciente;
    private Paciente paciente;

    @BeforeEach
    void setUp() {
        fixedInstant = Instant.parse("2026-10-04T12:00:00Z");
        fixedClock = Clock.fixed(fixedInstant, ZoneOffset.UTC);

        emergencyQrService = new EmergencyQrService(
                accesoTemporalQrRepository,
                pacienteRepository,
                usuarioRepository,
                auditoriaService,
                passwordEncoder,
                fixedClock
        );

        usuarioPaciente = new Usuario(
                10L,
                "user-pac-uuid",
                "paciente@test.com",
                "hash",
                "ACTIVO",
                0,
                null,
                fixedInstant,
                false,
                false,
                null,
                null
        );

        paciente = new Paciente(
                20L,
                10L,
                "pac-uuid-1",
                "CC",
                "123456789",
                "Carlos",
                "Perez",
                LocalDate.of(1990, 5, 15),
                "3001234567",
                fixedInstant,
                fixedInstant
        );
    }

    @Test
    @DisplayName("generarAccesoQr: genera token de 256 bits, hash SHA-256, expira en 15 min y audita")
    void generarAccesoQr_exitoSinPin() {
        when(usuarioRepository.buscarPorPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));
        when(accesoTemporalQrRepository.crear(any(AccesoTemporalQr.class))).thenReturn(100L);

        GenerarQrRequest request = new GenerarQrRequest(null, true, true, true, true);
        GenerarQrResponse response = emergencyQrService.generarAccesoQr(request, "user-pac-uuid", "192.168.1.1");

        assertThat(response).isNotNull();
        assertThat(response.token()).isNotBlank();
        assertThat(response.token().length()).isGreaterThan(30);
        assertThat(response.qrUrl()).isEqualTo("#/emergency-summary/" + response.token());
        assertThat(response.expiraAt()).isEqualTo(fixedInstant.plus(15, ChronoUnit.MINUTES));
        assertThat(response.maxAccesos()).isEqualTo(3);
        assertThat(response.requierePin()).isFalse();
        assertThat(response.incluirAlergias()).isTrue();
        assertThat(response.incluirMedicamentos()).isTrue();
        assertThat(response.incluirAtenciones()).isTrue();
        assertThat(response.incluirContacto()).isTrue();

        ArgumentCaptor<AccesoTemporalQr> captor = ArgumentCaptor.forClass(AccesoTemporalQr.class);
        verify(accesoTemporalQrRepository).crear(captor.capture());
        AccesoTemporalQr guardado = captor.getValue();
        assertThat(guardado.pacienteId()).isEqualTo(20L);
        assertThat(guardado.tokenHash()).isEqualTo(TokenHashUtil.hash(response.token()));
        assertThat(guardado.pinHash()).isNull();
        assertThat(guardado.maxAccesos()).isEqualTo(3);
        assertThat(guardado.accesosRealizados()).isZero();
        assertThat(guardado.revocado()).isFalse();

        ArgumentCaptor<EventoAuditoria> auditCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(auditCaptor.capture());
        EventoAuditoria evento = auditCaptor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.GENERACION_QR_EMERGENCIA);
        assertThat(evento.tipoRecurso()).isEqualTo("ACCESO_TEMPORAL_QR");
        assertThat(evento.recursoPublicId()).isEqualTo(response.publicId());
        assertThat(evento.ipOrigen()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("generarAccesoQr: con PIN numérico de 4 dígitos, lo hashea con PasswordEncoder")
    void generarAccesoQr_exitoConPin() {
        when(usuarioRepository.buscarPorPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));
        when(passwordEncoder.encode("4321")).thenReturn("argon2id$hashedpin");
        when(accesoTemporalQrRepository.crear(any(AccesoTemporalQr.class))).thenReturn(101L);

        GenerarQrRequest request = new GenerarQrRequest("4321", true, false, true, false);
        GenerarQrResponse response = emergencyQrService.generarAccesoQr(request, "user-pac-uuid", "127.0.0.1");

        assertThat(response.requierePin()).isTrue();
        assertThat(response.incluirMedicamentos()).isFalse();
        assertThat(response.incluirContacto()).isFalse();

        ArgumentCaptor<AccesoTemporalQr> captor = ArgumentCaptor.forClass(AccesoTemporalQr.class);
        verify(accesoTemporalQrRepository).crear(captor.capture());
        assertThat(captor.getValue().pinHash()).isEqualTo("argon2id$hashedpin");
        assertThat(captor.getValue().incluirMedicamentos()).isFalse();
    }

    @Test
    @DisplayName("generarAccesoQr: lanza AccesoNoAutorizadoException si usuario no es paciente")
    void generarAccesoQr_usuarioNoPaciente_lanzaExcepcion() {
        when(usuarioRepository.buscarPorPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> emergencyQrService.generarAccesoQr(new GenerarQrRequest(null, true, true, true, true), "user-pac-uuid", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Solo pacientes registrados");
    }

    @Test
    @DisplayName("listarMisAccesosQr: resuelve estado dinámicamente según instante actual")
    void listarMisAccesosQr_resuelveEstados() {
        when(usuarioRepository.buscarPorPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));

        AccesoTemporalQr activo = new AccesoTemporalQr(
                1L, "qr-1", 20L, "hash1", null, true, true, true, true, 3, 1, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        AccesoTemporalQr expirado = new AccesoTemporalQr(
                2L, "qr-2", 20L, "hash2", null, true, true, true, true, 3, 0, false,
                fixedInstant.minus(1, ChronoUnit.MINUTES), fixedInstant.minus(16, ChronoUnit.MINUTES), fixedInstant
        );

        AccesoTemporalQr agotado = new AccesoTemporalQr(
                3L, "qr-3", 20L, "hash3", null, true, true, true, true, 3, 3, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        AccesoTemporalQr revocado = new AccesoTemporalQr(
                4L, "qr-4", 20L, "hash4", null, true, true, true, true, 3, 0, true,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );

        when(accesoTemporalQrRepository.listarPorPacienteId(20L))
                .thenReturn(List.of(activo, expirado, agotado, revocado));

        List<AccesoQrResponse> lista = emergencyQrService.listarMisAccesosQr("user-pac-uuid");

        assertThat(lista).hasSize(4);
        assertThat(lista.get(0).estado()).isEqualTo("ACTIVO");
        assertThat(lista.get(1).estado()).isEqualTo("EXPIRADO");
        assertThat(lista.get(2).estado()).isEqualTo("AGOTADO");
        assertThat(lista.get(3).estado()).isEqualTo("REVOCADO");
    }

    @Test
    @DisplayName("revocarAccesoQr: revoca exitosamente y audita si es el paciente titular")
    void revocarAccesoQr_exito() {
        when(usuarioRepository.buscarPorPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));

        AccesoTemporalQr acceso = new AccesoTemporalQr(
                1L, "qr-uuid", 20L, "hash1", null, true, true, true, true, 3, 0, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );
        when(accesoTemporalQrRepository.buscarPorPublicId("qr-uuid")).thenReturn(Optional.of(acceso));

        emergencyQrService.revocarAccesoQr("qr-uuid", "user-pac-uuid", "192.168.1.1");

        verify(accesoTemporalQrRepository).revocar("qr-uuid", 20L);

        ArgumentCaptor<EventoAuditoria> auditCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(auditCaptor.capture());
        EventoAuditoria evento = auditCaptor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.REVOCACION_QR_EMERGENCIA);
        assertThat(evento.recursoPublicId()).isEqualTo("qr-uuid");
    }

    @Test
    @DisplayName("revocarAccesoQr: rechaza si el QR pertenece a otro paciente")
    void revocarAccesoQr_pacienteAjeno_lanzaExcepcion() {
        when(usuarioRepository.buscarPorPublicId("user-pac-uuid")).thenReturn(Optional.of(usuarioPaciente));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.of(paciente));

        AccesoTemporalQr accesoAjeno = new AccesoTemporalQr(
                1L, "qr-uuid", 999L, "hash1", null, true, true, true, true, 3, 0, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );
        when(accesoTemporalQrRepository.buscarPorPublicId("qr-uuid")).thenReturn(Optional.of(accesoAjeno));

        assertThatThrownBy(() -> emergencyQrService.revocarAccesoQr("qr-uuid", "user-pac-uuid", "192.168.1.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para revocar un acceso QR ajeno");
    }

    @Test
    @DisplayName("verificarTokenPublico: verifica vigencia, estado y requerimiento de PIN")
    void verificarTokenPublico_variosEscenarios() {
        String tokenPlano = "secure-random-token-xyz-1234567890";
        String tokenHash = TokenHashUtil.hash(tokenPlano);

        // Caso 1: Token activo con PIN
        AccesoTemporalQr activoConPin = new AccesoTemporalQr(
                1L, "qr-1", 20L, tokenHash, "argon2id$pin", true, true, true, true, 3, 0, false,
                fixedInstant.plus(10, ChronoUnit.MINUTES), fixedInstant, fixedInstant
        );
        when(accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)).thenReturn(Optional.of(activoConPin));

        VerificarQrResponse resp1 = emergencyQrService.verificarTokenPublico(tokenPlano);
        assertThat(resp1.valido()).isTrue();
        assertThat(resp1.requierePin()).isTrue();
        assertThat(resp1.estado()).isEqualTo("ACTIVO");
        assertThat(resp1.expiraAt()).isEqualTo(fixedInstant.plus(10, ChronoUnit.MINUTES));

        // Caso 2: Token no existe
        when(accesoTemporalQrRepository.buscarPorTokenHash(TokenHashUtil.hash("inexistente")))
                .thenReturn(Optional.empty());
        VerificarQrResponse resp2 = emergencyQrService.verificarTokenPublico("inexistente");
        assertThat(resp2.valido()).isFalse();
        assertThat(resp2.estado()).isEqualTo("EXPIRADO");

        // Caso 3: Token nulo o vacío
        VerificarQrResponse resp3 = emergencyQrService.verificarTokenPublico("");
        assertThat(resp3.valido()).isFalse();
    }
}

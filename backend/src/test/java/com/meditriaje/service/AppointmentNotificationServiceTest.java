package com.meditriaje.service;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.RecordatorioCita;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.RecordatorioCitaRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.service.email.EmailTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentNotificationServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private EmailTemplateService templateService;

    @Mock
    private RecordatorioCitaRepository recordatorioCitaRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    private AppointmentNotificationService notificationService;

    private CitaResponse dummyCita;

    @BeforeEach
    void setUp() {
        notificationService = new AppointmentNotificationService(
                emailService,
                templateService,
                recordatorioCitaRepository,
                pacienteRepository,
                usuarioRepository,
                "http://localhost:8080"
        );

        dummyCita = new CitaResponse(
                "cita-uuid-123",
                "slot-uuid-1",
                "paciente-uuid-1",
                "Carlos Perez",
                "prof-uuid-1",
                "Dr. Roberto Gomez",
                "esp-uuid-1",
                "Medicina General",
                "sede-uuid-1",
                "Sede Principal",
                "Calle 100 #15-20",
                Instant.parse("2026-10-15T13:30:00Z"),
                Instant.parse("2026-10-15T14:00:00Z"),
                "PRESENCIAL",
                "PROGRAMADA",
                null,
                null,
                Instant.now()
        );
    }

    @Test
    @DisplayName("enviarConfirmacionReserva exitoso despacha correo y registra ENVIADO en BD")
    void enviarConfirmacionReserva_exitoso() {
        when(templateService.renderizar(anyString(), anyMap())).thenReturn("<html>Confirmacion</html>");

        notificationService.enviarConfirmacionReserva(10L, 20L, "carlos@example.com", dummyCita);

        verify(emailService).enviarCorreoHtml(
                eq("carlos@example.com"),
                eq("MediTriaje 2.0 — Confirmación de Cita Médica (Medicina General)"),
                eq("<html>Confirmacion</html>")
        );

        ArgumentCaptor<RecordatorioCita> captor = ArgumentCaptor.forClass(RecordatorioCita.class);
        verify(recordatorioCitaRepository).guardar(captor.capture());

        RecordatorioCita guardado = captor.getValue();
        assertThat(guardado.citaId()).isEqualTo(10L);
        assertThat(guardado.pacienteId()).isEqualTo(20L);
        assertThat(guardado.tipo()).isEqualTo("CONFIRMACION_RESERVA");
        assertThat(guardado.destinatario()).isEqualTo("carlos@example.com");
        assertThat(guardado.estadoEnvio()).isEqualTo("ENVIADO");
        assertThat(guardado.errorMensaje()).isNull();
    }

    @Test
    @DisplayName("enviarConfirmacionReserva ante fallo SMTP no lanza excepción y registra FALLIDO")
    void enviarConfirmacionReserva_falloSmtp_tolerante() {
        when(templateService.renderizar(anyString(), anyMap())).thenReturn("<html>Confirmacion</html>");
        doThrow(new RuntimeException("Connection refused to mail.server.com:25"))
                .when(emailService).enviarCorreoHtml(anyString(), anyString(), anyString());

        assertThatCode(() -> notificationService.enviarConfirmacionReserva(10L, 20L, "carlos@example.com", dummyCita))
                .doesNotThrowAnyException();

        ArgumentCaptor<RecordatorioCita> captor = ArgumentCaptor.forClass(RecordatorioCita.class);
        verify(recordatorioCitaRepository).guardar(captor.capture());

        RecordatorioCita guardado = captor.getValue();
        assertThat(guardado.estadoEnvio()).isEqualTo("FALLIDO");
        assertThat(guardado.errorMensaje()).contains("Connection refused");
    }

    @Test
    @DisplayName("enviarNotificacionCancelacion exitoso despacha correo y registra CANCELACION")
    void enviarNotificacionCancelacion_exitoso() {
        when(templateService.renderizar(anyString(), anyMap())).thenReturn("<html>Cancelacion</html>");

        notificationService.enviarNotificacionCancelacion(10L, 20L, "carlos@example.com", dummyCita, "Calamidad doméstica");

        verify(emailService).enviarCorreoHtml(
                eq("carlos@example.com"),
                eq("MediTriaje 2.0 — Cancelación de Cita Médica (Medicina General)"),
                eq("<html>Cancelacion</html>")
        );

        ArgumentCaptor<RecordatorioCita> captor = ArgumentCaptor.forClass(RecordatorioCita.class);
        verify(recordatorioCitaRepository).guardar(captor.capture());

        RecordatorioCita guardado = captor.getValue();
        assertThat(guardado.tipo()).isEqualTo("CANCELACION");
        assertThat(guardado.estadoEnvio()).isEqualTo("ENVIADO");
    }

    @Test
    @DisplayName("enviarNotificacionCancelacion ante fallo SMTP no bloquea flujo")
    void enviarNotificacionCancelacion_falloSmtp_tolerante() {
        when(templateService.renderizar(anyString(), anyMap())).thenReturn("<html>Cancelacion</html>");
        doThrow(new RuntimeException("SMTP timeout"))
                .when(emailService).enviarCorreoHtml(anyString(), anyString(), anyString());

        assertThatCode(() -> notificationService.enviarNotificacionCancelacion(10L, 20L, "carlos@example.com", dummyCita, null))
                .doesNotThrowAnyException();

        ArgumentCaptor<RecordatorioCita> captor = ArgumentCaptor.forClass(RecordatorioCita.class);
        verify(recordatorioCitaRepository).guardar(captor.capture());
        assertThat(captor.getValue().estadoEnvio()).isEqualTo("FALLIDO");
    }

    @Test
    @DisplayName("enviarResumenAtencionSeguimiento despacha correo al paciente")
    void enviarResumenAtencionSeguimiento_exitoso() {
        when(templateService.renderizar(anyString(), anyMap())).thenReturn("<html>Resumen</html>");

        notificationService.enviarResumenAtencionSeguimiento(
                "carlos@example.com",
                "Carlos Perez",
                "Dr. Roberto Gomez",
                "Medicina General",
                "15/10/2026",
                "CONTROL_MEDICO",
                "Tomar abundantes liquidos",
                "22/10/2026"
        );

        verify(emailService).enviarCorreoHtml(
                eq("carlos@example.com"),
                eq("MediTriaje 2.0 — Plan de Cuidado y Seguimiento Post-Atención"),
                eq("<html>Resumen</html>")
        );
    }

    @Test
    @DisplayName("resolverEmailPaciente obtiene el correo del usuario vinculado al paciente")
    void resolverEmailPaciente_exitoso() {
        Paciente paciente = new Paciente(20L, 50L, "pac-uuid", "CC", "12345678", "Carlos", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", Instant.now(), Instant.now());
        Usuario usuario = new Usuario(50L, "user-uuid", "paciente@example.com", "hash", "ACTIVO", 0, null, Instant.now());

        when(pacienteRepository.buscarPorId(20L)).thenReturn(Optional.of(paciente));
        when(usuarioRepository.buscarPorId(50L)).thenReturn(Optional.of(usuario));

        String email = notificationService.resolverEmailPaciente(20L);

        assertThat(email).isEqualTo("paciente@example.com");
    }

    @Test
    @DisplayName("resolverEmailPaciente retorna null si paciente no existe")
    void resolverEmailPaciente_noExiste() {
        when(pacienteRepository.buscarPorId(999L)).thenReturn(Optional.empty());

        String email = notificationService.resolverEmailPaciente(999L);

        assertThat(email).isNull();
    }
}

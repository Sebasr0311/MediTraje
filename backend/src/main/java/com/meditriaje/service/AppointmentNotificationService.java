package com.meditriaje.service;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.model.RecordatorioCita;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.RecordatorioCitaRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.service.email.EmailTemplateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio encargado del despacho de notificaciones y recordatorios de citas
 * con plantillas HTML institucionales y tolerancia a fallos (ADR-015).
 */
@Service
public class AppointmentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(AppointmentNotificationService.class);
    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter FORMATTER_FECHA_HORA = DateTimeFormatter.ofPattern(
            "EEEE d 'de' MMMM 'de' yyyy, hh:mm a",
            Locale.forLanguageTag("es-CO")
    );

    private static final String PLANTILLA_CONFIRMACION = "templates/email/confirmacion-cita.html";
    private static final String PLANTILLA_CANCELACION = "templates/email/cancelacion-cita.html";
    private static final String PLANTILLA_RESUMEN_SEGUIMIENTO = "templates/email/resumen-atencion-seguimiento.html";

    private final EmailService emailService;
    private final EmailTemplateService templateService;
    private final RecordatorioCitaRepository recordatorioCitaRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final String appUrl;

    public AppointmentNotificationService(
            EmailService emailService,
            EmailTemplateService templateService,
            RecordatorioCitaRepository recordatorioCitaRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            @Value("${meditriaje.app-url:http://localhost:8080}") String appUrl
    ) {
        this.emailService = Objects.requireNonNull(emailService, "emailService no puede ser nulo");
        this.templateService = Objects.requireNonNull(templateService, "templateService no puede ser nulo");
        this.recordatorioCitaRepository = Objects.requireNonNull(recordatorioCitaRepository, "recordatorioCitaRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.appUrl = appUrl != null && !appUrl.isBlank() ? appUrl : "http://localhost:8080";
    }

    /**
     * Envía un correo con la confirmación de la cita reservada y registra la auditoría en RECORDATORIO_CITA.
     */
    public void enviarConfirmacionReserva(Long citaId, Long pacienteId, String emailDestinatario, CitaResponse cita) {
        if (cita == null || emailDestinatario == null || emailDestinatario.isBlank()) {
            return;
        }

        String fechaHoraTexto = formatearFechaHora(cita.fechaHoraInicio());
        String modalidadTexto = "PRESENCIAL".equalsIgnoreCase(cita.modalidad()) ? "Presencial" : "Telemedicina";
        String sedeNombre = cita.sedeNombre() != null ? cita.sedeNombre() : "Sede Asistencial Principal";
        String sedeDireccion = cita.sedeDireccion() != null ? cita.sedeDireccion() : "Consulta Externa";

        Map<String, String> variables = Map.of(
                "pacienteNombre", cita.pacienteNombre() != null ? cita.pacienteNombre() : "Paciente",
                "especialidadNombre", cita.especialidadNombre() != null ? cita.especialidadNombre() : "Medicina General",
                "profesionalNombre", cita.profesionalNombre() != null ? cita.profesionalNombre() : "Profesional Asistencial",
                "fechaHoraBogota", fechaHoraTexto,
                "modalidad", modalidadTexto,
                "sedeNombre", sedeNombre,
                "sedeDireccion", sedeDireccion,
                "codigoCita", cita.publicId(),
                "appUrl", appUrl
        );

        String cuerpoHtml = templateService.renderizar(PLANTILLA_CONFIRMACION, variables);
        String asunto = "MediTriaje 2.0 — Confirmación de Cita Médica (" + cita.especialidadNombre() + ")";

        despacharYRegistrar(citaId, pacienteId, emailDestinatario, "CONFIRMACION_RESERVA", asunto, cuerpoHtml);
    }

    /**
     * Envía un correo notificando la cancelación de la cita médica y registra el evento en RECORDATORIO_CITA.
     */
    public void enviarNotificacionCancelacion(Long citaId, Long pacienteId, String emailDestinatario, CitaResponse cita, String motivo) {
        if (cita == null || emailDestinatario == null || emailDestinatario.isBlank()) {
            return;
        }

        String fechaHoraTexto = formatearFechaHora(cita.fechaHoraInicio());
        String motivoTexto = (motivo != null && !motivo.isBlank())
                ? motivo
                : "Cancelación solicitada por el usuario o personal asistencial";

        Map<String, String> variables = Map.of(
                "pacienteNombre", cita.pacienteNombre() != null ? cita.pacienteNombre() : "Paciente",
                "especialidadNombre", cita.especialidadNombre() != null ? cita.especialidadNombre() : "Medicina General",
                "profesionalNombre", cita.profesionalNombre() != null ? cita.profesionalNombre() : "Profesional Asistencial",
                "fechaHoraBogota", fechaHoraTexto,
                "motivoCancelacion", motivoTexto,
                "codigoCita", cita.publicId(),
                "appUrl", appUrl
        );

        String cuerpoHtml = templateService.renderizar(PLANTILLA_CANCELACION, variables);
        String asunto = "MediTriaje 2.0 — Cancelación de Cita Médica (" + cita.especialidadNombre() + ")";

        despacharYRegistrar(citaId, pacienteId, emailDestinatario, "CANCELACION", asunto, cuerpoHtml);
    }

    /**
     * Envía un correo con el resumen de atención médica e indicaciones de seguimiento post-atención.
     */
    public void enviarResumenAtencionSeguimiento(
            String emailDestinatario,
            String pacienteNombre,
            String profesionalNombre,
            String especialidadNombre,
            String fechaAtencion,
            String tipoSeguimiento,
            String indicaciones,
            String fechaSugeridaControl
    ) {
        if (emailDestinatario == null || emailDestinatario.isBlank()) {
            return;
        }

        Map<String, String> variables = Map.of(
                "pacienteNombre", pacienteNombre != null ? pacienteNombre : "Paciente",
                "profesionalNombre", profesionalNombre != null ? profesionalNombre : "Profesional Asistencial",
                "especialidadNombre", especialidadNombre != null ? especialidadNombre : "Consulta Médica",
                "fechaAtencion", fechaAtencion != null ? fechaAtencion : "",
                "tipoSeguimiento", tipoSeguimiento != null ? tipoSeguimiento : "Control Ambulatorio",
                "indicaciones", indicaciones != null ? indicaciones : "Continuar con las recomendaciones dadas en consulta.",
                "fechaSugeridaControl", fechaSugeridaControl != null ? fechaSugeridaControl : "Según evolución clínica",
                "appUrl", appUrl
        );

        String cuerpoHtml = templateService.renderizar(PLANTILLA_RESUMEN_SEGUIMIENTO, variables);
        String asunto = "MediTriaje 2.0 — Plan de Cuidado y Seguimiento Post-Atención";

        try {
            emailService.enviarCorreoHtml(emailDestinatario, asunto, cuerpoHtml);
        } catch (Exception ex) {
            log.warn("Fallo no bloqueante al enviar correo de resumen de atencion a [{}]: {}", emailDestinatario, ex.getMessage());
        }
    }

    /**
     * Resuelve la dirección de correo electrónico del paciente a partir de su ID de dominio.
     */
    public String resolverEmailPaciente(Long pacienteId) {
        if (pacienteId == null) {
            return null;
        }
        return pacienteRepository.buscarPorId(pacienteId)
                .flatMap(paciente -> usuarioRepository.buscarPorId(paciente.usuarioId()))
                .map(com.meditriaje.model.Usuario::email)
                .orElse(null);
    }

    private void despacharYRegistrar(Long citaId, Long pacienteId, String destinatario, String tipo, String asunto, String cuerpoHtml) {
        String estadoEnvio = "ENVIADO";
        String errorMensaje = null;

        try {
            emailService.enviarCorreoHtml(destinatario, asunto, cuerpoHtml);
        } catch (Exception ex) {
            log.warn("Fallo no bloqueante al despachar correo [{}] hacia [{}]: {}", tipo, destinatario, ex.getMessage());
            estadoEnvio = "FALLIDO";
            errorMensaje = ex.getMessage();
            if (errorMensaje != null && errorMensaje.length() > 500) {
                errorMensaje = errorMensaje.substring(0, 500);
            }
        }

        if (citaId != null && pacienteId != null) {
            try {
                RecordatorioCita recordatorio = new RecordatorioCita(
                        null,
                        UUID.randomUUID().toString(),
                        citaId,
                        pacienteId,
                        tipo,
                        "EMAIL",
                        destinatario,
                        estadoEnvio,
                        errorMensaje,
                        Instant.now()
                );
                recordatorioCitaRepository.guardar(recordatorio);
            } catch (Exception ex) {
                log.error("No se pudo registrar la trazabilidad del recordatorio en BD para citaId={}: {}", citaId, ex.getMessage());
            }
        }
    }

    private String formatearFechaHora(Instant instant) {
        if (instant == null) {
            return "";
        }
        ZonedDateTime bogotaTime = instant.atZone(ZONE_BOGOTA);
        String formateado = FORMATTER_FECHA_HORA.format(bogotaTime);
        if (!formateado.isEmpty()) {
            formateado = Character.toUpperCase(formateado.charAt(0)) + formateado.substring(1);
        }
        return formateado + " (UTC-5)";
    }
}

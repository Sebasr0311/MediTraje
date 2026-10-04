package com.meditriaje.service;

import com.meditriaje.dto.assistant.PreguntaAsistenteRequest;
import com.meditriaje.dto.assistant.RespuestaAsistenteResponse;
import com.meditriaje.dto.assistant.SugerenciaAccionDto;
import com.meditriaje.exception.DatosInvalidosException;
import org.springframework.stereotype.Service;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;

/**
 * Servicio y motor del Asistente Virtual del Sistema (F2.6, RF-27, ADR-018, §5.19).
 * Guía interactiva de orientación operativa y navegación con detección infalible de emergencias.
 * Regla de oro: CERO diagnósticos, CERO prescripción de medicamentos.
 */
@Service
public class AssistantService {

    public static final String AVISO_LEGAL =
            "Esta orientación es proporcionada por el asistente de navegación de MediTriaje 2.0. "
            + "No sustituye la valoración, diagnóstico ni tratamiento de un profesional de la salud.";

    private static final List<String> PALABRAS_CLAVE_EMERGENCIA = List.of(
            "dolor de pecho", "dolor toracico", "presion en el pecho", "opresion en el pecho",
            "no puedo respirar", "falta de aire", "ahogo", "dificultad respiratoria",
            "inconsciente", "perdida de conciencia", "desmayo", "sin conocimiento",
            "convulsion", "convulsiones",
            "sangrado abundante", "sangrado incontrolable", "hemorragia",
            "paralisis", "boca torcida", "perdida de fuerza en brazo", "acv", "derrame cerebral", "infarto",
            "intoxicacion severa", "envenenamiento"
    );

    public RespuestaAsistenteResponse procesarConsulta(PreguntaAsistenteRequest request) {
        if (request == null || request.mensaje() == null || request.mensaje().trim().isBlank()) {
            throw new DatosInvalidosException("El mensaje de consulta no puede estar vacío.");
        }

        String rawMensaje = request.mensaje().trim();
        String normalizado = normalizarTexto(rawMensaje);

        // 1. Detección infalible de emergencia médica vital
        if (esAlertaEmergencia(normalizado)) {
            return new RespuestaAsistenteResponse(
                    "⚠️ ALERTA DE EMERGENCIA MÉDICA: Los síntomas o situaciones que describes indican un posible riesgo vital o emergencia inminente. "
                    + "Por favor, comunícate inmediatamente a la línea nacional de emergencias 123 o acude sin demora al servicio de urgencias hospitalario más cercano. "
                    + "No aguardes por una cita programada ni esperes respuesta en línea.",
                    true,
                    "EMERGENCIA",
                    List.of(
                            new SugerenciaAccionDto("Llamar al 123", "tel:123", "phone"),
                            new SugerenciaAccionDto("Iniciar Triaje de Emergencia", "#/patient/triage", "activity")
                    ),
                    AVISO_LEGAL
            );
        }

        // 2. Consulta sobre Triaje Clínico
        if (contieneAlguno(normalizado, "triaje", "triage", "sintoma", "prioridad", "nivel i", "nivel ii", "nivel iii", "nivel iv", "nivel v", "clasificacion")) {
            return new RespuestaAsistenteResponse(
                    "El Triaje Clínico de MediTriaje 2.0 te orienta según la severidad y duración de tus síntomas:\n"
                    + "• Nivel I (Rojo): Urgencia vital inmediata (remisión directa a urgencias/123).\n"
                    + "• Nivel II (Naranja): Atención prioritaria en urgencias.\n"
                    + "• Nivel III (Amarillo): Cita médica presencial general.\n"
                    + "• Nivel IV (Verde): Consulta médica por telemedicina.\n"
                    + "• Nivel V (Azul): Consulta médica general programada.\n\n"
                    + "¿Deseas iniciar una evaluación guiada de síntomas ahora?",
                    false,
                    "TRIAJE",
                    List.of(
                            new SugerenciaAccionDto("Realizar Triaje Clínico", "#/patient/triage", "activity"),
                            new SugerenciaAccionDto("Consultar Citas", "#/patient/appointments", "calendar")
                    ),
                    AVISO_LEGAL
            );
        }

        // 3. Consulta sobre Citas y Agendamiento
        if (contieneAlguno(normalizado, "cita", "agendar", "reservar", "cancelar", "disponibilidad", "slot", "turno", "horario")) {
            return new RespuestaAsistenteResponse(
                    "En MediTriaje 2.0 puedes buscar disponibilidad real por especialidad y sede hospitalaria en tiempo real.\n"
                    + "• Agendamiento: Selecciona tu especialidad y el horario más conveniente.\n"
                    + "• Cancelación oportuna: Si no puedes asistir, puedes cancelar tu cita hasta con 2 horas de anticipación para liberar el turno a otro paciente que lo necesite.\n"
                    + "• Puntualidad: Se recomienda conectarse o llegar 15 minutos antes de la hora acordada.",
                    false,
                    "CITAS",
                    List.of(
                            new SugerenciaAccionDto("Buscar Disponibilidad", "#/patient/book", "calendar"),
                            new SugerenciaAccionDto("Mis Citas Programadas", "#/patient/appointments", "clock")
                    ),
                    AVISO_LEGAL
            );
        }

        // 4. Consulta sobre Farmacia y Recetas Médicas
        if (contieneAlguno(normalizado, "receta", "medicamento", "farmacia", "dispensacion", "reclamar", "lote", "invima", "saldo", "droga", "remedio")) {
            return new RespuestaAsistenteResponse(
                    "Tus recetas médicas son emitidas digitalmente con trazabilidad inmutable:\n"
                    + "• Código de reclamación: Cada receta cuenta con un código alfanumérico (ej. REC-XXXXXXXX).\n"
                    + "• En ventanilla: Presenta tu documento de identidad y tu código de reclamación en la farmacia de la sede.\n"
                    + "• Entregas y saldos: Puedes reclamar entregas parciales y hacer seguimiento del saldo pendiente de tus medicamentos desde tu portal.",
                    false,
                    "FARMACIA",
                    List.of(
                            new SugerenciaAccionDto("Ver Mis Recetas", "#/patient/prescriptions", "pill"),
                            new SugerenciaAccionDto("Mi Panel Principal", "#/patient/dashboard", "user")
                    ),
                    AVISO_LEGAL
            );
        }

        // 5. Consulta sobre Resumen de Salud y QR de Emergencia
        if (contieneAlguno(normalizado, "qr", "codigo qr", "resumen de salud", "emergencia qr", "prehospitalario", "paramedico")) {
            return new RespuestaAsistenteResponse(
                    "El código QR de emergencia permite que personal de rescate o urgencias consulte un resumen seguro de tu salud ante una eventualidad:\n"
                    + "• Temporal y cifrado: Cada QR expira a los 15 minutos o tras 3 lecturas autorizadas.\n"
                    + "• Protección con PIN: Puedes exigir un PIN numérico de 4 dígitos para autorizar la lectura prehospitalaria.\n"
                    + "• Contenido: Incluye grupo sanguíneo, alergias activas, medicamentos vigentes y contactos de emergencia.",
                    false,
                    "RESUMEN_QR",
                    List.of(
                            new SugerenciaAccionDto("Generar QR de Emergencia", "#/patient/emergency-qr", "shield"),
                            new SugerenciaAccionDto("Ver Mi Historia", "#/patient/history", "file-text")
                    ),
                    AVISO_LEGAL
            );
        }

        // 6. Consulta sobre Historia Clínica e Inmutabilidad
        if (contieneAlguno(normalizado, "historia", "historial", "antecedente", "inmutable", "enmienda", "correccion", "cie-10", "diagnostico")) {
            return new RespuestaAsistenteResponse(
                    "Tu historia clínica en MediTriaje 2.0 cumple estrictos estándares legales de seguridad (Resolución 1995 de 1999):\n"
                    + "• Inmutabilidad: Una vez cerrada la atención por el médico tratante, el registro no puede ser alterado ni borrado.\n"
                    + "• Aclaraciones: Cualquier corrección debe realizarse mediante enmiendas clínicas cronológicas fechadas y firmadas por el profesional.\n"
                    + "• Consulta segura: Solo tú y los profesionales con relación asistencial activa o autorización de emergencia pueden consultar tus registros.",
                    false,
                    "HISTORIA_CLINICA",
                    List.of(
                            new SugerenciaAccionDto("Consultar Historia Clínica", "#/patient/history", "file-text"),
                            new SugerenciaAccionDto("Mis Citas", "#/patient/appointments", "calendar")
                    ),
                    AVISO_LEGAL
            );
        }

        // 7. Consulta sobre Seguridad, Contraseña o MFA
        if (contieneAlguno(normalizado, "contrasena", "password", "clave", "mfa", "totp", "recuperar", "bloqueo", "datos", "seguridad")) {
            return new RespuestaAsistenteResponse(
                    "Seguridad y gestión de tu cuenta en MediTriaje 2.0:\n"
                    + "• Recuperación: Si olvidaste tu contraseña, solicita un código numérico temporal de 6 dígitos enviado a tu correo registrado (válido por 15 minutos).\n"
                    + "• Doble factor (MFA): Profesionales y administradores cuentan con autenticación reforzada mediante aplicaciones TOTP (Google Authenticator / Authy) y códigos de respaldo.\n"
                    + "• Habeas Data: Tratamos tus datos sensibles con cifrado estricto y conforme a la Ley 1581 de 2012.",
                    false,
                    "SEGURIDAD",
                    List.of(
                            new SugerenciaAccionDto("Recuperar Contraseña", "#/forgot-password", "shield"),
                            new SugerenciaAccionDto("Iniciar Sesión", "#/login", "user")
                    ),
                    AVISO_LEGAL
            );
        }

        // 8. Orientación General por defecto
        return new RespuestaAsistenteResponse(
                "¡Hola! Soy tu asistente de orientación en MediTriaje 2.0. Estoy aquí para ayudarte a navegar por la plataforma:\n"
                + "¿Qué deseas hacer hoy? Puedes preguntarme sobre cómo orientar tus síntomas con el triaje, agendar o cancelar citas, reclamar medicamentos con tu código de receta, o activar tu código QR de emergencia.",
                false,
                "GENERAL",
                List.of(
                        new SugerenciaAccionDto("Triaje Clínico", "#/patient/triage", "activity"),
                        new SugerenciaAccionDto("Agendar Cita", "#/patient/book", "calendar"),
                        new SugerenciaAccionDto("Recetas Médicas", "#/patient/prescriptions", "pill")
                ),
                AVISO_LEGAL
        );
    }

    private boolean esAlertaEmergencia(String texto) {
        for (String palabra : PALABRAS_CLAVE_EMERGENCIA) {
            if (texto.contains(palabra)) {
                return true;
            }
        }
        return false;
    }

    private boolean contieneAlguno(String texto, String... terminos) {
        for (String termino : terminos) {
            if (texto.contains(normalizarTexto(termino))) {
                return true;
            }
        }
        return false;
    }

    private String normalizarTexto(String input) {
        if (input == null) return "";
        String descompuesto = Normalizer.normalize(input.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        return descompuesto.replaceAll("\\p{M}", "");
    }
}

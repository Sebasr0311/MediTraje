package com.meditriaje.model;

import java.time.Instant;

/**
 * Registro inmutable de una notificación o recordatorio de cita médica.
 */
public record RecordatorioCita(
        Long id,
        String publicId,
        Long citaId,
        Long pacienteId,
        String tipo,
        String canal,
        String destinatario,
        String estadoEnvio,
        String errorMensaje,
        Instant enviadoAt
) {
    public RecordatorioCita {
        if (publicId == null || publicId.isBlank()) {
            throw new IllegalArgumentException("publicId no puede ser nulo ni vacío");
        }
        if (citaId == null) {
            throw new IllegalArgumentException("citaId no puede ser nulo");
        }
        if (pacienteId == null) {
            throw new IllegalArgumentException("pacienteId no puede ser nulo");
        }
        if (tipo == null || tipo.isBlank()) {
            throw new IllegalArgumentException("tipo no puede ser nulo ni vacío");
        }
        if (destinatario == null || destinatario.isBlank()) {
            throw new IllegalArgumentException("destinatario no puede ser nulo ni vacío");
        }
        if (canal == null) {
            canal = "EMAIL";
        }
        if (estadoEnvio == null) {
            estadoEnvio = "ENVIADO";
        }
    }
}

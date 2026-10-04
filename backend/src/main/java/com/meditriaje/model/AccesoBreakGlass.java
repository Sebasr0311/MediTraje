package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable para un registro de acceso de emergencia Break-Glass (ADR-007, ADR-017).
 * Representa una autorización temporal excepcional otorgada a un profesional asistencial
 * para acceder a la historia clínica de un paciente ante una situación de urgencia vital.
 */
public record AccesoBreakGlass(
        Long id,
        String publicId,
        Long profesionalId,
        Long pacienteId,
        String motivo,
        Instant fechaExpiracion,
        Instant createdAt
) {
    public AccesoBreakGlass {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(motivo, "motivo no puede ser nulo");
        Objects.requireNonNull(fechaExpiracion, "fechaExpiracion no puede ser nula");
        if (motivo.trim().length() < 20) {
            throw new IllegalArgumentException("El motivo de emergencia debe tener al menos 20 caracteres.");
        }
    }

    /**
     * Determina si el acceso break-glass se encuentra activo (no expirado) en el instante dado.
     */
    public boolean estaActivo(Instant ahora) {
        Objects.requireNonNull(ahora, "ahora no puede ser nulo");
        return ahora.isBefore(fechaExpiracion);
    }
}

package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Modelo de dominio inmutable que representa un acceso temporal mediante código QR (ADR-010, §5.17, §5.18).
 */
public record AccesoTemporalQr(
        Long id,
        String publicId,
        Long pacienteId,
        String tokenHash,
        String pinHash,
        boolean incluirAlergias,
        boolean incluirMedicamentos,
        boolean incluirAtenciones,
        boolean incluirContacto,
        int maxAccesos,
        int accesosRealizados,
        boolean revocado,
        int intentosPinFallidos,
        Instant expiraAt,
        Instant createdAt,
        Instant updatedAt
) {
    public AccesoTemporalQr {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");
        Objects.requireNonNull(tokenHash, "tokenHash no puede ser nulo");
        Objects.requireNonNull(expiraAt, "expiraAt no puede ser nulo");
    }

    /**
     * Constructor de compatibilidad para instancias previas sin contador de fallos de PIN.
     */
    public AccesoTemporalQr(
            Long id,
            String publicId,
            Long pacienteId,
            String tokenHash,
            String pinHash,
            boolean incluirAlergias,
            boolean incluirMedicamentos,
            boolean incluirAtenciones,
            boolean incluirContacto,
            int maxAccesos,
            int accesosRealizados,
            boolean revocado,
            Instant expiraAt,
            Instant createdAt,
            Instant updatedAt
    ) {
        this(
                id,
                publicId,
                pacienteId,
                tokenHash,
                pinHash,
                incluirAlergias,
                incluirMedicamentos,
                incluirAtenciones,
                incluirContacto,
                maxAccesos,
                accesosRealizados,
                revocado,
                0,
                expiraAt,
                createdAt,
                updatedAt
        );
    }

    /**
     * Determina si el acceso temporal está activo y puede ser utilizado para consultar el resumen.
     */
    public boolean estaActivo(Instant now) {
        return resolverEstado(now) == EstadoAccesoQr.ACTIVO;
    }

    /**
     * Indica si el acceso requiere verificación de PIN numérico.
     */
    public boolean requierePin() {
        return pinHash != null && !pinHash.isBlank();
    }

    /**
     * Resuelve el estado actual del token temporal respecto a un instante de tiempo.
     */
    public EstadoAccesoQr resolverEstado(Instant now) {
        if (revocado || intentosPinFallidos >= 3) {
            return EstadoAccesoQr.REVOCADO;
        }
        if (now.isAfter(expiraAt)) {
            return EstadoAccesoQr.EXPIRADO;
        }
        if (accesosRealizados >= maxAccesos) {
            return EstadoAccesoQr.AGOTADO;
        }
        return EstadoAccesoQr.ACTIVO;
    }
}

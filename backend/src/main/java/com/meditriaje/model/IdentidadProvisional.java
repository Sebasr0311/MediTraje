package com.meditriaje.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Representa la identificación temporal de un paciente indocumentado o no identificado (NN) (Fase U, ADR-023).
 */
public record IdentidadProvisional(
        Long id,
        String publicId,
        Long episodioId,
        String codigoProvisional,
        String descripcionFisica,
        Integer edadAparente,
        String generoAparente,
        String condicionLlegada,
        String estado,
        Long vinculadoPacienteId,
        Long confirmadoPorUsuarioId,
        Instant fechaConfirmacion,
        Instant creadoAt
) {
    public IdentidadProvisional {
        Objects.requireNonNull(publicId, "publicId no puede ser nulo");
        Objects.requireNonNull(episodioId, "episodioId no puede ser nulo");
        Objects.requireNonNull(codigoProvisional, "codigoProvisional no puede ser nulo");
        Objects.requireNonNull(estado, "estado no puede ser nulo");
    }
}

package com.meditriaje.model;

import java.time.Instant;

/**
 * Registro médico de egreso y alta hospitalaria con epicrisis y destino (Fase H, ADR-024).
 * Destinos: ALTA_DOMICILIO, CONTRAREFERENCIA, TRASLADO_OTRA_IPS, HOSPITALIZACION_DOMICILIARIA, FALLECIMIENTO.
 */
public record EgresoHospitalario(
        Long id,
        String publicId,
        Long episodioId,
        Long medicoEgresoId,
        Instant fechaEgreso,
        String tipoDestino,
        String diagnosticoEgreso,
        String epicrisisResumen,
        String planManejo,
        Instant creadoAt
) {
}

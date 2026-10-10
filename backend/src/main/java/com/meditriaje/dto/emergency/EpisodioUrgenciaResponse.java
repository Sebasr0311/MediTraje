package com.meditriaje.dto.emergency;

import java.time.Instant;

/**
 * Respuesta con el detalle de un episodio de urgencias (Fase U, ADR-022).
 */
public record EpisodioUrgenciaResponse(
        String episodioPublicId,
        String pacientePublicId,
        String pacienteNombreCompleto,
        String sedePublicId,
        String sedeNombre,
        String tipo,
        String estado,
        boolean esIdentidadProvisional,
        String codigoProvisional,
        String identidadEstado,
        String medioLlegada,
        String motivoLlegada,
        String nivelTriajeActual,
        String profesionalTratanteNombre,
        Instant ingresoAt,
        Instant egresoAt,
        Instant creadoAt
) {
}

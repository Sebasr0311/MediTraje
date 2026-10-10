package com.meditriaje.dto.emergency;

import java.time.Instant;

/**
 * Elemento de la cola priorizada de urgencias por sede y nivel de triaje (Fase U, ADR-027, U05).
 */
public record ItemColaUrgenciaResponse(
        String episodioPublicId,
        String pacientePublicId,
        String identificadorVisible,
        String codigoProvisional,
        String sedePublicId,
        String sedeNombre,
        String tipoEpisodio,
        String estadoEpisodio,
        String nivelTriaje,
        String motivoLlegada,
        Long minutosEspera,
        Instant ingresoAt,
        String profesionalTratanteNombre
) {
}

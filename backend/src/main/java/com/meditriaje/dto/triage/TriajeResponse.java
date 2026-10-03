package com.meditriaje.dto.triage;

import java.time.Instant;
import java.util.List;

/**
 * Respuesta consolidada tras la evaluación o consulta de un triaje clínico (ADR-009, HU-02).
 */
public record TriajeResponse(
        String publicId,
        String pacientePublicId,
        String nivelPrioridad,
        String rutaSugerida,
        boolean esEmergencia,
        String versionReglas,
        String mensaje,
        String aviso,
        List<String> sintomasAlarma,
        List<SintomaItemResponse> sintomas,
        String observaciones,
        Instant createdAt
) {}

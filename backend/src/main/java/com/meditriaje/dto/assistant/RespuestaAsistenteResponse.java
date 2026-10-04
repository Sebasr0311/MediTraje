package com.meditriaje.dto.assistant;

import java.util.List;

/**
 * Respuesta estructurada del asistente virtual con acciones y disclaimer médico (F2.6, RF-27, ADR-018).
 */
public record RespuestaAsistenteResponse(
        String respuesta,
        boolean esEmergencia,
        String categoria,
        List<SugerenciaAccionDto> sugerencias,
        String avisoLegal
) {}

package com.meditriaje.dto.assistant;

/**
 * Sugerencia de acción interactiva o navegación en la SPA (F2.6, RF-27).
 */
public record SugerenciaAccionDto(
        String etiqueta,
        String rutaSpa,
        String icono
) {}

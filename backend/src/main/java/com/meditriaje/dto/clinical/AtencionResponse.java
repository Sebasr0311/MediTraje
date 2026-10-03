package com.meditriaje.dto.clinical;

import java.time.Instant;
import java.util.List;

/**
 * DTO de respuesta para la atención médica (HU-07, HU-09).
 */
public record AtencionResponse(
        String publicId,
        String citaPublicId,
        String pacientePublicId,
        String pacienteNombre,
        String profesionalPublicId,
        String profesionalNombre,
        String especialidadNombre,
        String estado,
        Instant createdAt,
        Instant fechaCierre,
        String diagnosticoCodigo,
        String diagnosticoDescripcion,
        String motivoConsulta,
        String evolucion,
        String indicaciones,
        SignosVitalesDto signosVitales,
        List<EnmiendaResponse> enmiendas
) {
    public AtencionResponse(
            String publicId,
            String citaPublicId,
            String pacientePublicId,
            String pacienteNombre,
            String profesionalPublicId,
            String profesionalNombre,
            String especialidadNombre,
            String estado,
            Instant createdAt,
            Instant fechaCierre,
            String diagnosticoCodigo,
            String diagnosticoDescripcion,
            String motivoConsulta,
            String evolucion,
            String indicaciones,
            SignosVitalesDto signosVitales
    ) {
        this(
                publicId,
                citaPublicId,
                pacientePublicId,
                pacienteNombre,
                profesionalPublicId,
                profesionalNombre,
                especialidadNombre,
                estado,
                createdAt,
                fechaCierre,
                diagnosticoCodigo,
                diagnosticoDescripcion,
                motivoConsulta,
                evolucion,
                indicaciones,
                signosVitales,
                List.of()
        );
    }
}

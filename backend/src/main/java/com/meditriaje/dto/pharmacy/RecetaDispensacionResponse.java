package com.meditriaje.dto.pharmacy;

import com.meditriaje.model.EstadoRecetaDispensacion;

import java.time.Instant;
import java.util.List;

/**
 * Consulta de receta médica con saldos y entregas para dispensación farmacéutica y portal del paciente (F2.4, ADR-016).
 */
public record RecetaDispensacionResponse(
        String recetaPublicId,
        String codigoReclamacion,
        String atencionPublicId,
        String pacientePublicId,
        String pacienteDocumento,
        String pacienteNombre,
        String profesionalPublicId,
        String profesionalNombre,
        String especialidadNombre,
        int vigenciaDias,
        Instant fechaEmision,
        Instant fechaVencimiento,
        boolean vencida,
        EstadoRecetaDispensacion estadoDispensacion,
        List<SaldoMedicamentoDto> items,
        List<DispensacionResponse> entregasPrevias
) {}

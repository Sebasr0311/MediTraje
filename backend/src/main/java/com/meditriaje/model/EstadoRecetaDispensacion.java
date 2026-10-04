package com.meditriaje.model;

/**
 * Estado calculado de la dispensación farmacéutica de una receta médica (F2.4, ADR-016).
 */
public enum EstadoRecetaDispensacion {
    /** Ningún medicamento de la receta ha sido entregado aún. */
    PENDIENTE,
    /** Se ha dispensado parte de los medicamentos prescritos, quedando saldo disponible por entregar. */
    DISPENSADA_PARCIAL,
    /** Se han entregado todos los medicamentos prescritos en su totalidad (saldo = 0 en todos los ítems). */
    DISPENSADA_TOTAL
}

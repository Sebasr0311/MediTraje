package com.meditriaje.model;

/**
 * Estados válidos para el ciclo de vida de una cita médica asistencial (ADR-006, HU-04, HU-05).
 */
public enum EstadoCita {
    PROGRAMADA,
    CONFIRMADA,
    ATENDIDA,
    CANCELADA,
    NO_ASISTIO,
    REPROGRAMADA
}

package com.meditriaje.model;

import com.meditriaje.exception.DatosInvalidosException;

import java.util.Map;
import java.util.Set;

/**
 * Máquina de estados formal para el ciclo de vida de una cita médica (ADR-006, HU-05).
 * Controla rigurosamente las transiciones permitidas y la inmutabilidad de estados finales.
 */
public final class CitaStateMachine {

    private static final Set<EstadoCita> ESTADOS_FINALES = Set.of(
            EstadoCita.ATENDIDA,
            EstadoCita.CANCELADA,
            EstadoCita.NO_ASISTIO,
            EstadoCita.REPROGRAMADA
    );

    private static final Map<EstadoCita, Set<EstadoCita>> TRANSICIONES_PERMITIDAS = Map.of(
            EstadoCita.PROGRAMADA, Set.of(
                    EstadoCita.CONFIRMADA,
                    EstadoCita.CANCELADA,
                    EstadoCita.NO_ASISTIO,
                    EstadoCita.REPROGRAMADA
            ),
            EstadoCita.CONFIRMADA, Set.of(
                    EstadoCita.ATENDIDA,
                    EstadoCita.CANCELADA,
                    EstadoCita.NO_ASISTIO,
                    EstadoCita.REPROGRAMADA
            )
    );

    private CitaStateMachine() {
    }

    /**
     * Determina si un estado es terminal/final (inmutable sin transiciones salientes permitidas).
     *
     * @param estado Estado a evaluar.
     * @return {@code true} si el estado es final (ATENDIDA, CANCELADA, NO_ASISTIO, REPROGRAMADA).
     */
    public static boolean esEstadoFinal(EstadoCita estado) {
        if (estado == null) {
            return false;
        }
        return ESTADOS_FINALES.contains(estado);
    }

    /**
     * Evalúa si una transición entre dos estados de cita es permitida por las reglas de negocio.
     *
     * @param origen  Estado actual de la cita.
     * @param destino Estado objetivo al que se desea transicionar.
     * @return {@code true} si la transición es válida, {@code false} en caso contrario.
     */
    public static boolean esTransicionValida(EstadoCita origen, EstadoCita destino) {
        if (origen == null || destino == null) {
            return false;
        }
        Set<EstadoCita> destinosValidos = TRANSICIONES_PERMITIDAS.get(origen);
        return destinosValidos != null && destinosValidos.contains(destino);
    }

    /**
     * Valida la transición de estado arrojando {@link DatosInvalidosException} si la transición no es permitida.
     *
     * @param origen  Estado actual de la cita.
     * @param destino Estado objetivo al que se desea transicionar.
     * @throws DatosInvalidosException si la transición de estado no es válida.
     */
    public static void validarTransicion(EstadoCita origen, EstadoCita destino) {
        if (!esTransicionValida(origen, destino)) {
            throw new DatosInvalidosException("Transicion de estado no permitida de " + origen + " a " + destino + ".");
        }
    }
}

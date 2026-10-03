package com.meditriaje.model;

import com.meditriaje.exception.DatosInvalidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CitaStateMachineTest {

    // =========================================================================
    // ESTADOS FINALES (ADR-006)
    // =========================================================================

    @Test
    @DisplayName("esEstadoFinal retorna true para ATENDIDA, CANCELADA, NO_ASISTIO y REPROGRAMADA")
    void esEstadoFinal_estadosFinales_retornaTrue() {
        assertThat(CitaStateMachine.esEstadoFinal(EstadoCita.ATENDIDA)).isTrue();
        assertThat(CitaStateMachine.esEstadoFinal(EstadoCita.CANCELADA)).isTrue();
        assertThat(CitaStateMachine.esEstadoFinal(EstadoCita.NO_ASISTIO)).isTrue();
        assertThat(CitaStateMachine.esEstadoFinal(EstadoCita.REPROGRAMADA)).isTrue();
    }

    @Test
    @DisplayName("esEstadoFinal retorna false para PROGRAMADA, CONFIRMADA y null")
    void esEstadoFinal_estadosNoFinales_retornaFalse() {
        assertThat(CitaStateMachine.esEstadoFinal(EstadoCita.PROGRAMADA)).isFalse();
        assertThat(CitaStateMachine.esEstadoFinal(EstadoCita.CONFIRMADA)).isFalse();
        assertThat(CitaStateMachine.esEstadoFinal(null)).isFalse();
    }

    // =========================================================================
    // TRANSICIONES VÁLIDAS DESDE PROGRAMADA (ADR-006)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"CONFIRMADA", "CANCELADA", "NO_ASISTIO", "REPROGRAMADA"})
    @DisplayName("Transiciones válidas desde PROGRAMADA son permitidas")
    void esTransicionValida_desdeProgramada_destinosValidos_retornaTrue(EstadoCita destino) {
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.PROGRAMADA, destino)).isTrue();
        assertThatCode(() -> CitaStateMachine.validarTransicion(EstadoCita.PROGRAMADA, destino))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("PROGRAMADA no puede transicionar directamente a ATENDIDA ni a sí misma")
    void esTransicionValida_desdeProgramada_destinosInvalidos_retornaFalse() {
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.PROGRAMADA, EstadoCita.ATENDIDA)).isFalse();
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.PROGRAMADA, EstadoCita.PROGRAMADA)).isFalse();
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.PROGRAMADA, null)).isFalse();

        assertThatThrownBy(() -> CitaStateMachine.validarTransicion(EstadoCita.PROGRAMADA, EstadoCita.ATENDIDA))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("Transicion de estado no permitida de PROGRAMADA a ATENDIDA.");
    }

    // =========================================================================
    // TRANSICIONES VÁLIDAS DESDE CONFIRMADA (ADR-006)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"ATENDIDA", "CANCELADA", "NO_ASISTIO", "REPROGRAMADA"})
    @DisplayName("Transiciones válidas desde CONFIRMADA son permitidas")
    void esTransicionValida_desdeConfirmada_destinosValidos_retornaTrue(EstadoCita destino) {
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.CONFIRMADA, destino)).isTrue();
        assertThatCode(() -> CitaStateMachine.validarTransicion(EstadoCita.CONFIRMADA, destino))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("CONFIRMADA no puede retroceder a PROGRAMADA ni a sí misma")
    void esTransicionValida_desdeConfirmada_destinosInvalidos_retornaFalse() {
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.CONFIRMADA, EstadoCita.PROGRAMADA)).isFalse();
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.CONFIRMADA, EstadoCita.CONFIRMADA)).isFalse();
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.CONFIRMADA, null)).isFalse();

        assertThatThrownBy(() -> CitaStateMachine.validarTransicion(EstadoCita.CONFIRMADA, EstadoCita.PROGRAMADA))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("Transicion de estado no permitida de CONFIRMADA a PROGRAMADA.");
    }

    // =========================================================================
    // INMUTABILIDAD DE ESTADOS TERMINALES (ADR-006)
    // =========================================================================

    @ParameterizedTest
    @EnumSource(value = EstadoCita.class, names = {"ATENDIDA", "CANCELADA", "NO_ASISTIO", "REPROGRAMADA"})
    @DisplayName("Estados finales no permiten ninguna transición saliente")
    void esTransicionValida_desdeEstadosFinales_ningunaTransicionPermitida(EstadoCita estadoFinal) {
        for (EstadoCita destino : EstadoCita.values()) {
            assertThat(CitaStateMachine.esTransicionValida(estadoFinal, destino)).isFalse();
            assertThatThrownBy(() -> CitaStateMachine.validarTransicion(estadoFinal, destino))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessage("Transicion de estado no permitida de " + estadoFinal + " a " + destino + ".");
        }
    }

    // =========================================================================
    // CASOS NULOS O INVÁLIDOS
    // =========================================================================

    @Test
    @DisplayName("Transición con origen nulo es rechazada")
    void validarTransicion_origenNulo_lanzaDatosInvalidosException() {
        assertThat(CitaStateMachine.esTransicionValida(null, EstadoCita.CANCELADA)).isFalse();
        assertThatThrownBy(() -> CitaStateMachine.validarTransicion(null, EstadoCita.CANCELADA))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("Transicion de estado no permitida de null a CANCELADA.");
    }

    @Test
    @DisplayName("Transición con destino nulo es rechazada")
    void validarTransicion_destinoNulo_lanzaDatosInvalidosException() {
        assertThat(CitaStateMachine.esTransicionValida(EstadoCita.PROGRAMADA, null)).isFalse();
        assertThatThrownBy(() -> CitaStateMachine.validarTransicion(EstadoCita.PROGRAMADA, null))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessage("Transicion de estado no permitida de PROGRAMADA a null.");
    }
}

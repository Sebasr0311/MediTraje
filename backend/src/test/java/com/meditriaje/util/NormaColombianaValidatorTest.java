package com.meditriaje.util;

import com.meditriaje.exception.DatosInvalidosException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("NormaColombianaValidator — Pruebas de validación normativa colombiana")
class NormaColombianaValidatorTest {

    // Reloj fijo al 1 de Julio de 2026 en Bogotá
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-01T12:00:00Z"), ZoneId.of("America/Bogota"));
    private final LocalDate hoy = LocalDate.now(clock); // 2026-07-01

    @Nested
    @DisplayName("Validación de Formato de Documentos")
    class ValidacionDocumentoTest {

        @ParameterizedTest(name = "Cédula válida: {0}")
        @ValueSource(strings = {"123456", "1098765432", "52987654", "80123456"})
        void cedulaCiudadaniaValida(String numDoc) {
            assertThatCode(() -> NormaColombianaValidator.validarDocumento("CC", numDoc))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Cédula inválida: {0}")
        @ValueSource(strings = {"12345", "12345678901", "10987A5432", "ABCDEF", "10-234-567"})
        void cedulaCiudadaniaInvalida(String numDoc) {
            assertThatThrownBy(() -> NormaColombianaValidator.validarDocumento("CC", numDoc))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("Cedula de Ciudadania (CC) debe contener entre 6 y 10 digitos");
        }

        @ParameterizedTest(name = "Tarjeta Identidad válida: {0}")
        @ValueSource(strings = {"1098765432", "10987654321"})
        void tarjetaIdentidadValida(String numDoc) {
            assertThatCode(() -> NormaColombianaValidator.validarDocumento("TI", numDoc))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Tarjeta Identidad inválida: {0}")
        @ValueSource(strings = {"123456789", "109876543210", "109876543A"})
        void tarjetaIdentidadInvalida(String numDoc) {
            assertThatThrownBy(() -> NormaColombianaValidator.validarDocumento("TI", numDoc))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("Tarjeta de Identidad (TI) debe contener 10 u 11 digitos");
        }

        @ParameterizedTest(name = "Registro Civil válido: {0}")
        @ValueSource(strings = {"1098765432", "10987654321"})
        void registroCivilValido(String numDoc) {
            assertThatCode(() -> NormaColombianaValidator.validarDocumento("RC", numDoc))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Cédula Extranjería válida: {0}")
        @ValueSource(strings = {"123456", "E123456", "CE98765432"})
        void cedulaExtranjeriaValida(String numDoc) {
            assertThatCode(() -> NormaColombianaValidator.validarDocumento("CE", numDoc))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Pasaporte válido: {0}")
        @ValueSource(strings = {"PA12345", "CO9876543210", "PASS12345678"})
        void pasaporteValido(String numDoc) {
            assertThatCode(() -> NormaColombianaValidator.validarDocumento("PA", numDoc))
                    .doesNotThrowAnyException();
        }

        @Test
        void tipoDocumentoDesconocidoRechazado() {
            assertThatThrownBy(() -> NormaColombianaValidator.validarDocumento("DNI", "12345678"))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("Tipo de documento no valido segun la norma colombiana");
        }
    }

    @Nested
    @DisplayName("Validación de Coherencia Documento vs Edad")
    class CoherenciaDocumentoEdadTest {

        @Test
        void cedulaConMayorDeEdadEsValida() {
            LocalDate nac18 = hoy.minusYears(18);
            LocalDate nac30 = hoy.minusYears(30);

            assertThatCode(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("CC", nac18, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("CC", nac30, clock))
                    .doesNotThrowAnyException();
        }

        @Test
        void cedulaConMenorDeEdadEsRechazada() {
            LocalDate nac17 = hoy.minusYears(17).minusDays(30);

            assertThatThrownBy(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("CC", nac17, clock))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("La Cedula de Ciudadania (CC) solo es valida para personas mayores de 18 anos");
        }

        @Test
        void tarjetaIdentidadRangoValido() {
            LocalDate nac7 = hoy.minusYears(7);
            LocalDate nac15 = hoy.minusYears(15);
            LocalDate nac17 = hoy.minusYears(17);

            assertThatCode(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("TI", nac7, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("TI", nac15, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("TI", nac17, clock))
                    .doesNotThrowAnyException();
        }

        @Test
        void tarjetaIdentidadMenorA7AnosRechazada() {
            LocalDate nac6 = hoy.minusYears(6);

            assertThatThrownBy(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("TI", nac6, clock))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("La Tarjeta de Identidad (TI) solo es valida para menores entre 7 y 17 anos");
        }

        @Test
        void tarjetaIdentidadMayorA17AnosRechazada() {
            LocalDate nac18 = hoy.minusYears(18);

            assertThatThrownBy(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("TI", nac18, clock))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("La Tarjeta de Identidad (TI) solo es valida para menores entre 7 y 17 anos");
        }

        @Test
        void registroCivilMenorA7AnosValido() {
            LocalDate nac2 = hoy.minusYears(2);
            LocalDate nac6 = hoy.minusYears(6).minusMonths(6);

            assertThatCode(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("RC", nac2, clock))
                    .doesNotThrowAnyException();
            assertThatCode(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("RC", nac6, clock))
                    .doesNotThrowAnyException();
        }

        @Test
        void registroCivilMayorA6AnosRechazado() {
            LocalDate nac7 = hoy.minusYears(7);

            assertThatThrownBy(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("RC", nac7, clock))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("El Registro Civil (RC) solo es valido para ninos menores de 7 anos");
        }

        @Test
        void fechaFuturaRechazada() {
            LocalDate manana = hoy.plusDays(1);

            assertThatThrownBy(() -> NormaColombianaValidator.validarCoherenciaDocumentoEdad("CC", manana, clock))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("La fecha de nacimiento no puede corresponder al futuro");
        }
    }

    @Nested
    @DisplayName("Validación de Nombres, Apellidos y Celular Colombia")
    class DatosContactoTest {

        @ParameterizedTest(name = "Nombre válido: {0}")
        @ValueSource(strings = {"Juan Carlos", "María José", "O'Connor", "Jean-Luc", "Ángela Inés", "Ñusta Müller"})
        void nombresValidos(String valor) {
            assertThatCode(() -> NormaColombianaValidator.validarNombresOApellidos("nombres", valor))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Nombre inválido: {0}")
        @ValueSource(strings = {"Juan123", "Carlos@Admin", "A", "<script>"})
        void nombresInvalidos(String valor) {
            assertThatThrownBy(() -> NormaColombianaValidator.validarNombresOApellidos("nombres", valor))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("solo puede contener letras, espacios, tildes y guiones");
        }

        @ParameterizedTest(name = "Celular Colombia válido: {0}")
        @ValueSource(strings = {"3001234567", "3159876543", "3201122334", "+573001234567", "+573105556677", " 300 1234567 "})
        void celularColombiaValido(String tel) {
            assertThatCode(() -> NormaColombianaValidator.validarCelularColombia(tel))
                    .doesNotThrowAnyException();
        }

        @ParameterizedTest(name = "Celular Colombia inválido: {0}")
        @ValueSource(strings = {"2345678", "4001234567", "6011234567", "30012345", "300123456789", "+572345678"})
        void celularColombiaInvalido(String tel) {
            assertThatThrownBy(() -> NormaColombianaValidator.validarCelularColombia(tel))
                    .isInstanceOf(DatosInvalidosException.class)
                    .hasMessageContaining("celular colombiano valido de 10 digitos (iniciando por 3)");
        }

        @Test
        void celularVacioEsPermitidoPorqueEsOpcional() {
            assertThatCode(() -> NormaColombianaValidator.validarCelularColombia(null))
                    .doesNotThrowAnyException();
            assertThatCode(() -> NormaColombianaValidator.validarCelularColombia(""))
                    .doesNotThrowAnyException();
            assertThatCode(() -> NormaColombianaValidator.validarCelularColombia("   "))
                    .doesNotThrowAnyException();
        }
    }
}

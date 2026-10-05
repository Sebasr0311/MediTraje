package com.meditriaje.util;

import com.meditriaje.exception.DatosInvalidosException;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Validador de identificación y datos de personas conforme a la normativa colombiana de salud
 * (MinSalud RIPS / Registraduría Nacional / Resolución 3374 y 2275 de 2023 / Ley 1581 de 2012).
 */
public final class NormaColombianaValidator {

    public static final ZoneId ZONA_COLOMBIA = ZoneId.of("America/Bogota");

    private static final Set<String> TIPOS_DOC_VALIDOS = Set.of("CC", "TI", "RC", "CE", "PA");

    private static final Pattern PATTERN_NUMERICO = Pattern.compile("^[0-9]+$");
    private static final Pattern PATTERN_ALFANUMERICO = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern PATTERN_NOMBRES = Pattern.compile("^[a-zA-ZáéíóúÁÉÍÓÚñÑüÜ\\s'-]{2,60}$");
    private static final Pattern PATTERN_CELULAR_COLOMBIA = Pattern.compile("^(\\+57)?3[0-9]{9}$");

    private NormaColombianaValidator() {
        // Utility class
    }

    /**
     * Valida el formato del número de documento según el tipo documental reconocido en Colombia.
     */
    public static void validarDocumento(String tipoDocumento, String numeroDocumento) {
        if (tipoDocumento == null || tipoDocumento.isBlank()) {
            throw new DatosInvalidosException("El tipo de documento es obligatorio.");
        }
        String tipo = tipoDocumento.trim().toUpperCase();
        if (!TIPOS_DOC_VALIDOS.contains(tipo)) {
            throw new DatosInvalidosException("Tipo de documento no valido segun la norma colombiana: " + tipoDocumento + ". Permitidos: CC, TI, RC, CE, PA.");
        }

        if (numeroDocumento == null || numeroDocumento.isBlank()) {
            throw new DatosInvalidosException("El numero de documento es obligatorio.");
        }
        String num = numeroDocumento.trim();

        switch (tipo) {
            case "CC" -> {
                if (!PATTERN_NUMERICO.matcher(num).matches() || num.length() < 6 || num.length() > 10) {
                    throw new DatosInvalidosException("La Cedula de Ciudadania (CC) debe contener entre 6 y 10 digitos numericos.");
                }
            }
            case "TI" -> {
                if (!PATTERN_NUMERICO.matcher(num).matches() || (num.length() != 10 && num.length() != 11)) {
                    throw new DatosInvalidosException("La Tarjeta de Identidad (TI) debe contener 10 u 11 digitos numericos.");
                }
            }
            case "RC" -> {
                if (!PATTERN_NUMERICO.matcher(num).matches() || (num.length() != 10 && num.length() != 11)) {
                    throw new DatosInvalidosException("El Registro Civil (RC) debe contener 10 u 11 digitos numericos.");
                }
            }
            case "CE" -> {
                if (!PATTERN_ALFANUMERICO.matcher(num).matches() || num.length() < 3 || num.length() > 10) {
                    throw new DatosInvalidosException("La Cedula de Extranjeria (CE) debe contener entre 3 y 10 caracteres alfanumericos.");
                }
            }
            case "PA" -> {
                if (!PATTERN_ALFANUMERICO.matcher(num).matches() || num.length() < 5 || num.length() > 20) {
                    throw new DatosInvalidosException("El Pasaporte (PA) debe contener entre 5 y 20 caracteres alfanumericos.");
                }
            }
            default -> throw new DatosInvalidosException("Tipo de documento no soportado: " + tipo);
        }
    }

    /**
     * Valida la coherencia etaria entre el tipo de documento y la fecha de nacimiento.
     */
    public static void validarCoherenciaDocumentoEdad(String tipoDocumento, LocalDate fechaNacimiento, Clock clock) {
        Objects.requireNonNull(fechaNacimiento, "La fecha de nacimiento no puede ser nula.");
        LocalDate hoy = (clock != null) ? LocalDate.now(clock) : LocalDate.now(ZONA_COLOMBIA);

        if (fechaNacimiento.isAfter(hoy)) {
            throw new DatosInvalidosException("La fecha de nacimiento no puede corresponder al futuro.");
        }

        int edad = Period.between(fechaNacimiento, hoy).getYears();
        if (edad > 125) {
            throw new DatosInvalidosException("La fecha de nacimiento no es valida (edad superior a 125 anos).");
        }

        String tipo = (tipoDocumento != null) ? tipoDocumento.trim().toUpperCase() : "";

        switch (tipo) {
            case "CC" -> {
                if (edad < 18) {
                    throw new DatosInvalidosException(
                            "La Cedula de Ciudadania (CC) solo es valida para personas mayores de 18 anos. Edad actual: " + edad + " anos."
                    );
                }
            }
            case "TI" -> {
                if (edad < 7 || edad > 17) {
                    throw new DatosInvalidosException(
                            "La Tarjeta de Identidad (TI) solo es valida para menores entre 7 y 17 anos cumplidos. Edad actual: " + edad + " anos."
                    );
                }
            }
            case "RC" -> {
                if (edad >= 7) {
                    throw new DatosInvalidosException(
                            "El Registro Civil (RC) solo es valido para ninos menores de 7 anos. Edad actual: " + edad + " anos."
                    );
                }
            }
            case "CE", "PA" -> {
                // CE y PA admiten cualquier edad legal en Colombia
            }
            default -> throw new DatosInvalidosException("Tipo de documento no soportado para validacion de edad: " + tipoDocumento);
        }
    }

    /**
     * Valida que el nombre o apellido contenga solo caracteres alfabéticos válidos en español.
     */
    public static void validarNombresOApellidos(String campo, String valor) {
        if (valor == null || valor.isBlank()) {
            throw new DatosInvalidosException("El campo " + campo + " es obligatorio.");
        }
        String normalizado = valor.trim();
        if (!PATTERN_NOMBRES.matcher(normalizado).matches()) {
            throw new DatosInvalidosException("El campo " + campo + " solo puede contener letras, espacios, tildes y guiones (longitud entre 2 y 60 caracteres).");
        }
    }

    /**
     * Valida el formato de teléfono celular para Colombia (10 dígitos iniciando por 3 o prefijo +57).
     */
    public static void validarCelularColombia(String telefono) {
        if (telefono == null || telefono.isBlank()) {
            return; // Teléfono opcional
        }
        String normalizado = telefono.trim().replaceAll("\\s+", "");
        if (!PATTERN_CELULAR_COLOMBIA.matcher(normalizado).matches()) {
            throw new DatosInvalidosException("El telefono debe ser un celular colombiano valido de 10 digitos (iniciando por 3) o con prefijo +573XXXXXXXXX.");
        }
    }

    /**
     * Calcula la edad en años a partir de la fecha de nacimiento.
     */
    public static int calcularEdad(LocalDate fechaNacimiento, Clock clock) {
        Objects.requireNonNull(fechaNacimiento, "La fecha de nacimiento no puede ser nula.");
        LocalDate hoy = (clock != null) ? LocalDate.now(clock) : LocalDate.now(ZONA_COLOMBIA);
        return Period.between(fechaNacimiento, hoy).getYears();
    }
}

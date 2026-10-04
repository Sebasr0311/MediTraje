package com.meditriaje.dto.clinical;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/**
 * DTO para la captura y respuesta de parámetros fisiológicos (signos vitales).
 */
public record SignosVitalesDto(
        @Min(value = 40, message = "La presion sistolica debe ser al menos 40 mmHg.")
        @Max(value = 300, message = "La presion sistolica no puede superar 300 mmHg.")
        Integer presionSistolica,

        @Min(value = 20, message = "La presion diastolica debe ser al menos 20 mmHg.")
        @Max(value = 200, message = "La presion diastolica no puede superar 200 mmHg.")
        Integer presionDiastolica,

        @Min(value = 30, message = "La frecuencia cardiaca debe ser al menos 30 lpm.")
        @Max(value = 250, message = "La frecuencia cardiaca no puede superar 250 lpm.")
        Integer frecuenciaCardiaca,

        @Min(value = 5, message = "La frecuencia respiratoria debe ser al menos 5 rpm.")
        @Max(value = 60, message = "La frecuencia respiratoria no puede superar 60 rpm.")
        Integer frecuenciaRespiratoria,

        @DecimalMin(value = "30.0", message = "La temperatura debe ser al menos 30.0 °C.")
        @DecimalMax(value = "45.0", message = "La temperatura no puede superar 45.0 °C.")
        BigDecimal temperatura,

        @Min(value = 0, message = "La saturacion de oxigeno debe ser al menos 0%.")
        @Max(value = 100, message = "La saturacion de oxigeno no puede superar 100%.")
        Integer saturacionOxigeno,

        @DecimalMin(value = "0.5", message = "El peso debe ser al menos 0.5 kg.")
        @DecimalMax(value = "500.0", message = "El peso no puede superar 500.0 kg.")
        BigDecimal pesoKg,

        @DecimalMin(value = "20.0", message = "La talla debe ser al menos 20.0 cm.")
        @DecimalMax(value = "260.0", message = "La talla no puede superar 260.0 cm.")
        BigDecimal tallaCm
) {
    public boolean tieneValores() {
        return presionSistolica != null || presionDiastolica != null || frecuenciaCardiaca != null ||
                frecuenciaRespiratoria != null || temperatura != null || saturacionOxigeno != null ||
                pesoKg != null || tallaCm != null;
    }
}

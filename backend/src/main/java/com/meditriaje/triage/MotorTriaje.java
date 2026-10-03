package com.meditriaje.triage;

/** Motor de triaje: función pura de la entrada reportada hacia una orientación (ADR-009). */
public interface MotorTriaje {

    ResultadoTriaje evaluar(EntradaTriaje entrada);
}

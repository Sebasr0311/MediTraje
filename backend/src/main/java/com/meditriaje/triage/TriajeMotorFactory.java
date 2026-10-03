package com.meditriaje.triage;

import com.meditriaje.repository.TriajeReglasRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Construye el motor de triaje desde BD para la versión de reglas vigente
 * ({@code meditriaje.triage.rules-version}, por defecto {@code v1-prototipo}) y lo cachea en memoria:
 * el catálogo de una versión es inmutable.
 */
@Service
public class TriajeMotorFactory {

    private final TriajeReglasRepository repository;
    private final String versionReglas;
    private volatile MotorTriaje motor;

    public TriajeMotorFactory(TriajeReglasRepository repository,
                              @Value("${meditriaje.triage.rules-version:v1-prototipo}") String versionReglas) {
        this.repository = repository;
        this.versionReglas = versionReglas;
    }

    public String versionVigente() {
        return versionReglas;
    }

    public MotorTriaje obtenerMotor() {
        MotorTriaje actual = motor;
        if (actual == null) {
            synchronized (this) {
                actual = motor;
                if (actual == null) {
                    var catalogo = repository.cargarSintomasActivos();
                    if (catalogo.isEmpty()) {
                        throw new IllegalStateException("Catálogo de síntomas vacío; no se puede construir el motor");
                    }
                    actual = new MotorTriajeBasadoEnReglas(versionReglas, catalogo,
                            repository.cargarReglasActivas(versionReglas));
                    motor = actual;
                }
            }
        }
        return actual;
    }
}

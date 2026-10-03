package com.meditriaje.triage;

import com.meditriaje.repository.TriajeReglasRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TriajeMotorFactoryTest {

    @Test
    void construyeMotorUnaVezYLoCachea() {
        TriajeReglasRepository repo = mock(TriajeReglasRepository.class);
        when(repo.cargarSintomasActivos()).thenReturn(List.of(new SintomaTriaje("FIEBRE", false)));
        when(repo.cargarReglasActivas("vX")).thenReturn(
                List.of(new ReglaTriaje("FIEBRE", BigDecimal.ZERO, null, 0, 10, NivelPrioridad.IV)));
        TriajeMotorFactory factory = new TriajeMotorFactory(repo, "vX");

        MotorTriaje a = factory.obtenerMotor();
        MotorTriaje b = factory.obtenerMotor();

        assertThat(a).isSameAs(b);
        verify(repo, times(1)).cargarSintomasActivos();
        verify(repo, times(1)).cargarReglasActivas("vX");
        assertThat(a.evaluar(new EntradaTriaje(List.of(new SintomaReportado("FIEBRE", BigDecimal.ONE, 5)))).versionReglas())
                .isEqualTo("vX");
    }
}

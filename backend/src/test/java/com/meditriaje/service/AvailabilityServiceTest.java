package com.meditriaje.service;

import com.meditriaje.dto.availability.DisponibilidadSlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AvailabilityServiceTest {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    // 2026-10-10 10:00:00 en Bogotá (UTC-5) -> 2026-10-10 15:00:00Z
    private static final Instant FIXED_NOW = Instant.parse("2026-10-10T15:00:00Z");

    @Mock
    private DisponibilidadSlotRepository slotRepository;

    private Clock clock;
    private AvailabilityService availabilityService;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(FIXED_NOW, ZONE_BOGOTA);
        availabilityService = new AvailabilityService(slotRepository, clock);
    }

    @Test
    void consultarDisponibilidad_sinFiltros_retornaSlotsLibresFuturos() {
        DisponibilidadSlotResponse slot = new DisponibilidadSlotResponse(
                "slot-1", "prof-1", "Dra. Gregory House", "esp-1", "Medicina General",
                "sede-1", "Sede Central", "Calle 100", "Bogota",
                FIXED_NOW.plusSeconds(3600), FIXED_NOW.plusSeconds(4800), "PRESENCIAL"
        );

        when(slotRepository.consultarDisponibles(isNull(), isNull(), isNull(), eq(FIXED_NOW), isNull(), eq(0), eq(10)))
                .thenReturn(List.of(slot));
        when(slotRepository.contarDisponibles(isNull(), isNull(), isNull(), eq(FIXED_NOW), isNull()))
                .thenReturn(1);

        PaginatedResponse<DisponibilidadSlotResponse> resultado = availabilityService.consultarDisponibilidad(
                null, null, null, null, 0, 10
        );

        assertThat(resultado.content()).hasSize(1);
        assertThat(resultado.totalElements()).isEqualTo(1);
        assertThat(resultado.content().getFirst().slotPublicId()).isEqualTo("slot-1");
        assertThat(resultado.content().getFirst().duracionMinutos()).isEqualTo(20);

        verify(slotRepository).consultarDisponibles(null, null, null, FIXED_NOW, null, 0, 10);
        verify(slotRepository).contarDisponibles(null, null, null, FIXED_NOW, null);
    }

    @Test
    void consultarDisponibilidad_conFiltrosIndividualesYCombinados_aplicaFiltros() {
        LocalDate fechaFutura = LocalDate.of(2026, 10, 15);
        ZonedDateTime startOfDay = fechaFutura.atStartOfDay(ZONE_BOGOTA);
        ZonedDateTime endOfDay = fechaFutura.atTime(23, 59, 59, 999_999_999).atZone(ZONE_BOGOTA);

        when(slotRepository.consultarDisponibles(
                eq("esp-1"), eq("sede-1"), eq("PRESENCIAL"),
                eq(startOfDay.toInstant()), eq(endOfDay.toInstant()), eq(0), eq(10)
        )).thenReturn(List.of());
        when(slotRepository.contarDisponibles(
                eq("esp-1"), eq("sede-1"), eq("PRESENCIAL"),
                eq(startOfDay.toInstant()), eq(endOfDay.toInstant())
        )).thenReturn(0);

        PaginatedResponse<DisponibilidadSlotResponse> resultado = availabilityService.consultarDisponibilidad(
                " esp-1 ", " sede-1 ", "presencial", fechaFutura, 0, 10
        );

        assertThat(resultado.content()).isEmpty();
        assertThat(resultado.totalElements()).isZero();

        verify(slotRepository).consultarDisponibles(
                "esp-1", "sede-1", "PRESENCIAL",
                startOfDay.toInstant(), endOfDay.toInstant(), 0, 10
        );
    }

    @Test
    void consultarDisponibilidad_conFechaHoy_acotaDesdeNowHastaFinDelDia() {
        LocalDate hoyBogota = LocalDate.of(2026, 10, 10);
        ZonedDateTime endOfDay = hoyBogota.atTime(23, 59, 59, 999_999_999).atZone(ZONE_BOGOTA);

        when(slotRepository.consultarDisponibles(
                isNull(), isNull(), isNull(),
                eq(FIXED_NOW), eq(endOfDay.toInstant()), eq(0), eq(10)
        )).thenReturn(List.of());
        when(slotRepository.contarDisponibles(
                isNull(), isNull(), isNull(),
                eq(FIXED_NOW), eq(endOfDay.toInstant())
        )).thenReturn(0);

        PaginatedResponse<DisponibilidadSlotResponse> resultado = availabilityService.consultarDisponibilidad(
                null, null, null, hoyBogota, 0, 10
        );

        assertThat(resultado.content()).isEmpty();
        verify(slotRepository).consultarDisponibles(
                isNull(), isNull(), isNull(), eq(FIXED_NOW), eq(endOfDay.toInstant()), eq(0), eq(10)
        );
    }

    @Test
    void consultarDisponibilidad_conFechaPasada_retornaListaVaciaSinConsultarRepositorio() {
        LocalDate fechaPasada = LocalDate.of(2026, 10, 9);

        PaginatedResponse<DisponibilidadSlotResponse> resultado = availabilityService.consultarDisponibilidad(
                "esp-1", "sede-1", "PRESENCIAL", fechaPasada, 0, 10
        );

        assertThat(resultado.content()).isEmpty();
        assertThat(resultado.totalElements()).isZero();
        verifyNoInteractions(slotRepository);
    }

    @Test
    void consultarDisponibilidad_modalidadInvalida_lanzaDatosInvalidosException() {
        assertThatThrownBy(() -> availabilityService.consultarDisponibilidad(
                null, null, "DOMICILIARIA", null, 0, 10
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Modalidad inválida: 'DOMICILIARIA'");

        verifyNoInteractions(slotRepository);
    }

    @Test
    void consultarDisponibilidad_modalidadCaseInsensitive_funcionaCorrectamente() {
        when(slotRepository.consultarDisponibles(isNull(), isNull(), eq("TELEMEDICINA"), eq(FIXED_NOW), isNull(), eq(0), eq(10)))
                .thenReturn(List.of());
        when(slotRepository.contarDisponibles(isNull(), isNull(), eq("TELEMEDICINA"), eq(FIXED_NOW), isNull()))
                .thenReturn(0);

        PaginatedResponse<DisponibilidadSlotResponse> resultado = availabilityService.consultarDisponibilidad(
                null, null, "telemedicina", null, 0, 10
        );

        assertThat(resultado.content()).isEmpty();
        verify(slotRepository).consultarDisponibles(isNull(), isNull(), eq("TELEMEDICINA"), eq(FIXED_NOW), isNull(), eq(0), eq(10));
    }

    @Test
    void consultarDisponibilidad_pageNegativo_lanzaDatosInvalidosException() {
        assertThatThrownBy(() -> availabilityService.consultarDisponibilidad(
                null, null, null, null, -1, 10
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El número de página no puede ser menor a 0.");

        verifyNoInteractions(slotRepository);
    }

    @Test
    void consultarDisponibilidad_sizeMenorAUno_lanzaDatosInvalidosException() {
        assertThatThrownBy(() -> availabilityService.consultarDisponibilidad(
                null, null, null, null, 0, 0
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El tamaño de página debe estar entre 1 y 100.");

        verifyNoInteractions(slotRepository);
    }

    @Test
    void consultarDisponibilidad_sizeMayorACien_lanzaDatosInvalidosException() {
        assertThatThrownBy(() -> availabilityService.consultarDisponibilidad(
                null, null, null, null, 0, 101
        ))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El tamaño de página debe estar entre 1 y 100.");

        verifyNoInteractions(slotRepository);
    }
}

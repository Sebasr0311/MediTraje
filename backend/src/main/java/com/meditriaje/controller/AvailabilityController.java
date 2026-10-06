package com.meditriaje.controller;

import com.meditriaje.dto.availability.DisponibilidadSlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AvailabilityService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Controlador para la consulta de disponibilidad de citas (HU-03, ADR-002, ADR-003, ADR-005, ADR-006).
 * Permite a cualquier usuario autenticado (paciente, profesional o administrador) consultar slots disponibles.
 */
@RestController
@RequestMapping("/api/v1/availability")
@PreAuthorize("isAuthenticated()")
public class AvailabilityController {

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = Objects.requireNonNull(availabilityService, "AvailabilityService no puede ser nulo");
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<DisponibilidadSlotResponse>> consultarDisponibilidad(
            @RequestParam(required = false) String especialidad,
            @RequestParam(required = false) String especialidadPublicId,
            @RequestParam(required = false) String profesional,
            @RequestParam(required = false) String profesionalPublicId,
            @RequestParam(required = false) String sede,
            @RequestParam(required = false) String sedePublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String modalidad,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        String espFiltro = (especialidadPublicId != null && !especialidadPublicId.isBlank())
                ? especialidadPublicId
                : especialidad;

        String profFiltro = (profesionalPublicId != null && !profesionalPublicId.isBlank())
                ? profesionalPublicId
                : profesional;

        String sedeFiltro = (sedePublicId != null && !sedePublicId.isBlank())
                ? sedePublicId
                : sede;

        PaginatedResponse<DisponibilidadSlotResponse> response;
        if (profFiltro != null && !profFiltro.isBlank()) {
            response = availabilityService.consultarDisponibilidad(
                    espFiltro,
                    profFiltro,
                    sedeFiltro,
                    modalidad,
                    fecha,
                    page,
                    size
            );
        } else {
            response = availabilityService.consultarDisponibilidad(
                    espFiltro,
                    sedeFiltro,
                    modalidad,
                    fecha,
                    page,
                    size
            );
        }

        return ResponseEntity.ok(response);
    }
}

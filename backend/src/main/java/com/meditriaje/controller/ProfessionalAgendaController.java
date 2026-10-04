package com.meditriaje.controller;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Controlador REST para la consulta de agenda asistencial por parte del profesional médico (HU-06, ADR-003, ADR-007).
 */
@RestController
@RequestMapping("/api/v1/professionals/me/agenda")
@PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
public class ProfessionalAgendaController {

    private final AppointmentService appointmentService;

    public ProfessionalAgendaController(AppointmentService appointmentService) {
        this.appointmentService = Objects.requireNonNull(appointmentService, "AppointmentService no puede ser nulo");
    }

    /**
     * Consulta la agenda paginada de citas pertenecientes al profesional autenticado.
     *
     * @param fecha          Fecha específica a filtrar en formato ISO (YYYY-MM-DD), opcional.
     * @param estado         Estado de la cita a filtrar, opcional.
     * @param page           Índice de página (default 0).
     * @param size           Cantidad de resultados por página (default 10, min 1, max 100).
     * @param authentication Contexto de seguridad del usuario autenticado.
     * @return Paginación de citas asistenciales.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<CitaResponse>> obtenerMiAgenda(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fecha,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        PaginatedResponse<CitaResponse> agenda = appointmentService.obtenerMiAgenda(
                usuarioPublicId,
                fecha,
                estado,
                page,
                size
        );
        return ResponseEntity.ok(agenda);
    }
}

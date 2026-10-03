package com.meditriaje.controller;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Objects;

/**
 * Controlador REST para el ciclo de vida y agendamiento de citas médicas (ADR-002, ADR-003, ADR-006, HU-04).
 */
@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("hasAnyAuthority('ROLE_PACIENTE', 'ROLE_ADMINISTRADOR')")
public class AppointmentController {

    private final AppointmentService appointmentService;

    public AppointmentController(AppointmentService appointmentService) {
        this.appointmentService = Objects.requireNonNull(appointmentService, "AppointmentService no puede ser nulo");
    }

    /**
     * Endpoint para reservar una cita médica en un slot disponible.
     * Retorna HTTP 201 Created con la cabecera Location y el cuerpo consolidado de la cita.
     */
    @PostMapping
    public ResponseEntity<CitaResponse> reservarCita(
            @Valid @RequestBody ReservarCitaRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        CitaResponse response = appointmentService.reservarCita(request, usuarioPublicId, ipOrigen);
        URI location = URI.create("/api/v1/appointments/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }
}

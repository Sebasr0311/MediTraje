package com.meditriaje.controller;

import com.meditriaje.dto.appointment.CancelarCitaRequest;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;

/**
 * Controlador REST para el ciclo de vida y agendamiento de citas médicas (ADR-002, ADR-003, ADR-006, HU-04, HU-05).
 */
@RestController
@RequestMapping("/api/v1/appointments")
@PreAuthorize("isAuthenticated()")
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
    @PreAuthorize("hasAnyAuthority('ROLE_PACIENTE', 'ROLE_ADMINISTRADOR')")
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

    /**
     * Endpoint para cancelar una cita médica agendada (HU-05, ADR-006).
     * Retorna HTTP 200 OK con el cuerpo consolidado de la cita cancelada.
     */
    @PatchMapping("/{publicId}/cancel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<CitaResponse> cancelarCita(
            @PathVariable String publicId,
            @Valid @RequestBody(required = false) CancelarCitaRequest request,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String ipOrigen = IpUtil.extraerIp(servletRequest);

        CitaResponse response = appointmentService.cancelarCita(
                publicId,
                request,
                usuarioPublicId,
                authorities,
                ipOrigen
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para registrar la inasistencia (no-show) a una cita médica programada (D4, T5).
     * Autorizado únicamente para el profesional asignado a la cita o administradores.
     * Retorna HTTP 200 OK con el cuerpo consolidado de la cita actualizada a NO_ASISTIO.
     */
    @PatchMapping("/{publicId}/no-show")
    @PreAuthorize("hasAnyAuthority('ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<CitaResponse> marcarNoAsistio(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest servletRequest
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String ipOrigen = IpUtil.extraerIp(servletRequest);

        CitaResponse response = appointmentService.marcarNoAsistio(
                publicId,
                usuarioPublicId,
                authorities,
                ipOrigen
        );

        return ResponseEntity.ok(response);
    }
}

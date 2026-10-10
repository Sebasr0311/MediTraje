package com.meditriaje.controller;

import com.meditriaje.dto.emergency.AsignarEquipoRequest;
import com.meditriaje.dto.emergency.EpisodioUrgenciaResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.dto.emergency.ReconciliarIdentidadRequest;
import com.meditriaje.dto.emergency.RegistrarAdmisionUrgenciaRequest;
import com.meditriaje.dto.emergency.RegistrarValoracionTriajeRequest;
import com.meditriaje.dto.emergency.ValoracionTriajeResponse;
import com.meditriaje.service.EmergencyService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controlador REST para el circuito de admisiones de urgencias, identidades provisionales,
 * triaje clínico presencial y asignación asistencial (Fase U, ADR-022, ADR-023, ADR-026, ADR-027).
 */
@RestController
@RequestMapping("/api/v1/emergency")
@PreAuthorize("isAuthenticated()")
public class EmergencyController {

    private final EmergencyService emergencyService;

    public EmergencyController(EmergencyService emergencyService) {
        this.emergencyService = Objects.requireNonNull(emergencyService, "EmergencyService no puede ser nulo");
    }

    /**
     * Endpoint para registrar el ingreso presencial de urgencias (U02).
     * Retorna 201 Created con cabecera Location.
     */
    @PostMapping("/admissions")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<EpisodioUrgenciaResponse> registrarAdmision(
            @Valid @RequestBody RegistrarAdmisionUrgenciaRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        EpisodioUrgenciaResponse response = emergencyService.registrarAdmisionUrgencia(request, usuarioPublicId, ipOrigen);
        URI location = URI.create("/api/v1/emergency/admissions/" + response.episodioPublicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Endpoint para consultar el detalle de un episodio de urgencias (U02).
     */
    @GetMapping("/admissions/{episodePublicId}")
    public ResponseEntity<EpisodioUrgenciaResponse> obtenerDetalleEpisodio(
            @PathVariable String episodePublicId,
            Authentication authentication
    ) {
        EpisodioUrgenciaResponse response = emergencyService.obtenerDetalleEpisodio(
                episodePublicId,
                authentication.getName(),
                authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para reconciliar una identidad provisional con un paciente civil verificado (U03).
     */
    @PostMapping("/episodes/{episodePublicId}/reconcile")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<EpisodioUrgenciaResponse> reconciliarIdentidad(
            @PathVariable String episodePublicId,
            @Valid @RequestBody ReconciliarIdentidadRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        EpisodioUrgenciaResponse response = emergencyService.reconciliarIdentidad(episodePublicId, request, usuarioPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para registrar una valoración clínica presencial de triaje o reevaluación (U04).
     */
    @PostMapping("/episodes/{episodePublicId}/triage-assessments")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<ValoracionTriajeResponse> registrarValoracionTriaje(
            @PathVariable String episodePublicId,
            @Valid @RequestBody RegistrarValoracionTriajeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        ValoracionTriajeResponse response = emergencyService.registrarValoracionTriaje(
                episodePublicId,
                request,
                usuarioPublicId,
                authentication.getAuthorities(),
                ipOrigen
        );
        URI location = URI.create("/api/v1/emergency/episodes/" + episodePublicId + "/triage-assessments/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Endpoint para consultar el historial de valoraciones de triaje de un episodio (U04).
     */
    @GetMapping("/episodes/{episodePublicId}/triage-assessments")
    public ResponseEntity<List<ValoracionTriajeResponse>> listarHistorialTriaje(
            @PathVariable String episodePublicId,
            Authentication authentication
    ) {
        List<ValoracionTriajeResponse> response = emergencyService.listarHistorialTriaje(
                episodePublicId,
                authentication.getName(),
                authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Endpoint para asignar un profesional de la salud al equipo asistencial del episodio (U06).
     */
    @PostMapping("/episodes/{episodePublicId}/care-team")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<Map<String, String>> asignarEquipoAsistencial(
            @PathVariable String episodePublicId,
            @Valid @RequestBody AsignarEquipoRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        emergencyService.asignarEquipoAsistencial(episodePublicId, request, usuarioPublicId, ipOrigen);
        return ResponseEntity.ok(Map.of("mensaje", "Profesional asignado exitosamente al equipo asistencial."));
    }

    /**
     * Endpoint para consultar la cola priorizada de urgencias por sede (U05).
     */
    @GetMapping("/queue")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<List<ItemColaUrgenciaResponse>> listarColaUrgencias(
            @RequestParam(required = false) String siteId,
            @RequestParam(required = false) String status
    ) {
        List<ItemColaUrgenciaResponse> cola = emergencyService.listarColaUrgencias(siteId, status);
        return ResponseEntity.ok(cola);
    }

    /**
     * Endpoint para cerrar o egresar un episodio de urgencias (U06).
     */
    @PostMapping("/episodes/{episodePublicId}/close")
    @PreAuthorize("hasAnyAuthority('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<Map<String, String>> cerrarEpisodio(
            @PathVariable String episodePublicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        emergencyService.cerrarEpisodio(episodePublicId, usuarioPublicId, ipOrigen);
        return ResponseEntity.ok(Map.of("mensaje", "Episodio de urgencias cerrado y egresado exitosamente."));
    }
}

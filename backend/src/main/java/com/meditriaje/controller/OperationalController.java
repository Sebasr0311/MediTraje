package com.meditriaje.controller;

import com.meditriaje.dto.operational.*;
import com.meditriaje.service.OperationalAnalyticsService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * Endpoints REST para Analítica Hospitalaria, Alertas de Saturación y QR de Seguimiento (Fase O, ADR-027, ADR-028).
 */
@RestController
@RequestMapping("/api/v1/operational")
public class OperationalController {

    private final OperationalAnalyticsService analyticsService;

    public OperationalController(OperationalAnalyticsService analyticsService) {
        this.analyticsService = Objects.requireNonNull(analyticsService, "analyticsService no puede ser nulo");
    }

    private String obtenerIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : request.getRemoteAddr();
    }

    /**
     * O01: Panel y KPIs hospitalarios en tiempo real (camas, urgencias, tiempos de triaje y alertas).
     */
    @GetMapping("/dashboard")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<DashboardHospitalarioResponse> obtenerDashboard(
            @RequestParam(required = false) String sedePublicId,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth != null ? auth.getName() : null;
        DashboardHospitalarioResponse resp = analyticsService.obtenerDashboardHospitalario(sedePublicId, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.ok(resp);
    }

    /**
     * O02: Lista alertas operativas de saturación y demoras en triage.
     */
    @GetMapping("/alerts")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<AlertaOperativaResponse>> listarAlertas(
            @RequestParam(required = false) String sedePublicId,
            @RequestParam(required = false) String estado
    ) {
        return ResponseEntity.ok(analyticsService.listarAlertas(sedePublicId, estado));
    }

    /**
     * O02: Reconocimiento auditado de una alerta operativa por personal autorizado.
     */
    @PostMapping("/alerts/{alertaPublicId}/acknowledge")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<Void> reconocerAlerta(
            @PathVariable String alertaPublicId,
            @Valid @RequestBody ReconocerAlertaRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        analyticsService.reconocerAlerta(alertaPublicId, request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * O03: Generación de token QR de seguimiento para manilla hospitalaria o cabecera.
     */
    @PostMapping("/episodes/{episodioPublicId}/tracking-qr")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<QrSeguimientoResponse> generarTrackingQr(
            @PathVariable String episodioPublicId,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        QrSeguimientoResponse resp = analyticsService.generarQrSeguimiento(episodioPublicId, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * O03: Consulta segura de ubicación y estado de paciente por escaneo de QR (sin PHI diagnóstica).
     */
    @GetMapping("/tracking-qr/{token}")
    public ResponseEntity<QrSeguimientoResponse> consultarTrackingQr(
            @PathVariable String token,
            HttpServletRequest httpRequest
    ) {
        QrSeguimientoResponse resp = analyticsService.consultarPorTokenQr(token, obtenerIp(httpRequest));
        return ResponseEntity.ok(resp);
    }
}

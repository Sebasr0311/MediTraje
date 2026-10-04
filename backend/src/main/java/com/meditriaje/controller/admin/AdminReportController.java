package com.meditriaje.controller.admin;

import com.meditriaje.dto.report.MetricasCitasDto;
import com.meditriaje.dto.report.MetricasTriajeDto;
import com.meditriaje.dto.report.ResumenOperativoResponse;
import com.meditriaje.service.ReporteService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
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
 * Controlador de reportes e indicadores operativos administrativos (F2.6, RF-30, ADR-018).
 * Requiere rol ROLE_ADMINISTRADOR y garantiza segregación total de datos clínicos (ADR-007).
 */
@RestController
@RequestMapping("/api/v1/admin/reports")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminReportController {

    private final ReporteService reporteService;

    public AdminReportController(ReporteService reporteService) {
        this.reporteService = Objects.requireNonNull(reporteService, "reporteService no puede ser nulo");
    }

    /**
     * Resumen operativo consolidado de la plataforma (citas, triaje, farmacia y break-glass).
     */
    @GetMapping("/operational")
    public ResponseEntity<ResumenOperativoResponse> obtenerResumenOperativo(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        ResumenOperativoResponse response = reporteService.obtenerResumenOperativo(desde, hasta, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    /**
     * Reporte analítico detallado de agendamiento y estado de citas.
     */
    @GetMapping("/appointments")
    public ResponseEntity<MetricasCitasDto> obtenerReporteCitas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        MetricasCitasDto response = reporteService.obtenerReporteCitas(desde, hasta, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    /**
     * Reporte analítico de distribución de triaje y cortes de emergencia.
     */
    @GetMapping("/triage")
    public ResponseEntity<MetricasTriajeDto> obtenerReporteTriaje(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        MetricasTriajeDto response = reporteService.obtenerReporteTriaje(desde, hasta, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }
}

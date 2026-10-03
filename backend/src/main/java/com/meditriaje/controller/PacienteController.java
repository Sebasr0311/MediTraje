package com.meditriaje.controller;

import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.PacienteService;
import com.meditriaje.service.PrescriptionService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Endpoints asistenciales y de consulta para el paciente (HU-08, HU-09).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PacienteController {

    private final PacienteService pacienteService;
    private final ClinicalAttentionService clinicalAttentionService;
    private final PrescriptionService prescriptionService;

    public PacienteController(
            PacienteService pacienteService,
            ClinicalAttentionService clinicalAttentionService,
            PrescriptionService prescriptionService
    ) {
        this.pacienteService = Objects.requireNonNull(pacienteService, "PacienteService no puede ser nulo");
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "ClinicalAttentionService no puede ser nulo");
        this.prescriptionService = Objects.requireNonNull(prescriptionService, "PrescriptionService no puede ser nulo");
    }

    /**
     * Consulta el perfil demográfico y de contacto del paciente actualmente autenticado.
     * Requiere obligatoriamente el rol ROLE_PACIENTE (ADR-002, HU-09).
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PacientePerfilResponse> obtenerMiPerfil(Authentication authentication) {
        String usuarioPublicId = authentication.getName();
        PacientePerfilResponse perfil = pacienteService.obtenerMiPerfil(usuarioPublicId);
        return ResponseEntity.ok(perfil);
    }

    /**
     * Consulta paginada del historial de atenciones médicas y enmiendas del paciente autenticado (HU-09).
     * Solo lectura, sin exponer IDs numéricos de base de datos.
     */
    @GetMapping("/me/history")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<AtencionResponse>> obtenerMiHistoria(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        PaginatedResponse<AtencionResponse> historia = clinicalAttentionService.obtenerMiHistoriaClinica(
                usuarioPublicId, page, size, ipOrigen
        );
        return ResponseEntity.ok(historia);
    }

    /**
     * Consulta paginada de las recetas médicas emitidas para el paciente autenticado (HU-08, HU-09).
     * Solo lectura, sin exponer IDs numéricos autonuméricos de base de datos.
     */
    @GetMapping("/me/prescriptions")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<RecetaResponse>> obtenerMisRecetas(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        PaginatedResponse<RecetaResponse> recetas = prescriptionService.obtenerMisRecetas(
                usuarioPublicId, page, size, ipOrigen
        );
        return ResponseEntity.ok(recetas);
    }
}

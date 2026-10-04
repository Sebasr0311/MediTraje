package com.meditriaje.controller;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collection;
import java.util.Objects;

/**
 * Controlador REST para consulta de la historia clínica de pacientes por profesionales asistenciales
 * (HU-07, HU-09, ADR-007, ADR-017).
 *
 * <p>Requiere relación asistencial activa (cita activa futura o atención previa en 12 meses)
 * O autorización excepcional de emergencia Break-Glass activa y no expirada.</p>
 */
@RestController
@RequestMapping("/api/v1/clinical/patients")
@PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
public class ClinicalPatientHistoryController {

    private final ClinicalAttentionService clinicalAttentionService;

    public ClinicalPatientHistoryController(ClinicalAttentionService clinicalAttentionService) {
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "ClinicalAttentionService no puede ser nulo");
    }

    /**
     * Consulta paginada del historial de atenciones médicas y enmiendas de un paciente.
     * Restringido a ROLE_PROFESIONAL con relación asistencial o Break-Glass vigente.
     */
    @GetMapping("/{publicId}/history")
    public ResponseEntity<PaginatedResponse<AtencionResponse>> obtenerHistoriaPaciente(
            @PathVariable String publicId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        PaginatedResponse<AtencionResponse> historia = clinicalAttentionService.obtenerHistoriaClinicaPaciente(
                publicId, usuarioPublicId, authorities, page, size, ipOrigen
        );
        return ResponseEntity.ok(historia);
    }
}

package com.meditriaje.controller;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.clinical.CerrarAtencionRequest;
import com.meditriaje.dto.clinical.IniciarAtencionRequest;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Collection;
import java.util.Objects;

/**
 * Controlador REST para el ciclo de vida de la atención médica (HU-07, HU-09, ADR-007, ADR-008).
 */
@RestController
@RequestMapping("/api/v1/attentions")
public class ClinicalAttentionController {

    private final ClinicalAttentionService clinicalAttentionService;

    public ClinicalAttentionController(ClinicalAttentionService clinicalAttentionService) {
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "ClinicalAttentionService no puede ser nulo");
    }

    /**
     * Inicia una atención médica vinculada a una cita.
     * Restringido exclusivamente al profesional asignado a la cita (ADR-007).
     */
    @PostMapping
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<AtencionResponse> iniciarAtencion(
            @Valid @RequestBody IniciarAtencionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        AtencionResponse response = clinicalAttentionService.iniciarAtencion(request, usuarioPublicId, ipOrigen);
        URI location = URI.create("/api/v1/attentions/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Cierra y consolida una atención médica de forma inmutable (ADR-008, HU-07).
     * Pasa la cita a ATENDIDA y audita el evento.
     */
    @PostMapping("/{publicId}/close")
    @PreAuthorize("hasAuthority('ROLE_PROFESIONAL')")
    public ResponseEntity<AtencionResponse> cerrarAtencion(
            @PathVariable String publicId,
            @Valid @RequestBody CerrarAtencionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        AtencionResponse response = clinicalAttentionService.cerrarAtencion(publicId, request, usuarioPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta el detalle de una atención médica.
     * Requiere relación asistencial o ser el paciente dueño (ADR-007).
     */
    @GetMapping("/{publicId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<AtencionResponse> obtenerAtencion(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        AtencionResponse response = clinicalAttentionService.obtenerPorPublicId(publicId, usuarioPublicId, authorities, ipOrigen);
        return ResponseEntity.ok(response);
    }
}

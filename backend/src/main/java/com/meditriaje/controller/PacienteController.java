package com.meditriaje.controller;

import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.service.PacienteService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Endpoints asistenciales y de consulta para el paciente (HU-09).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PacienteController {

    private final PacienteService pacienteService;

    public PacienteController(PacienteService pacienteService) {
        this.pacienteService = Objects.requireNonNull(pacienteService, "PacienteService no puede ser nulo");
    }

    /**
     * Consulta el perfil demográfico y de contacto del paciente actualmente autenticado.
     * Requiere obligatoriamente el rol ROLE_PACIENTE (ADR-002, HU-09).
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PacientePerfilResponse> obtenerMiPerfil(Authentication authentication) {
        String usuarioPublicId = (String) authentication.getPrincipal();
        PacientePerfilResponse perfil = pacienteService.obtenerMiPerfil(usuarioPublicId);
        return ResponseEntity.ok(perfil);
    }
}

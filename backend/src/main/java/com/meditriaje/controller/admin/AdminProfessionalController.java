package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.ActualizarProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalResponse;
import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AdminProfessionalService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Controlador administrativo para la gestión de profesionales asistenciales (HU-10, ADR-002, ADR-003).
 * Requiere el rol ROLE_ADMINISTRADOR para todas las operaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/professionals")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminProfessionalController {

    private final AdminProfessionalService professionalService;

    public AdminProfessionalController(AdminProfessionalService professionalService) {
        this.professionalService = Objects.requireNonNull(professionalService, "AdminProfessionalService no puede ser nulo");
    }

    @PostMapping
    public ResponseEntity<CrearProfesionalResponse> crear(
            @Valid @RequestBody CrearProfesionalRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        CrearProfesionalResponse response = professionalService.altaProfesional(request, adminPublicId, ipOrigen);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<ProfesionalResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String especialidadPublicId,
            @RequestParam(required = false) String estado
    ) {
        PaginatedResponse<ProfesionalResponse> response = professionalService.listar(page, size, especialidadPublicId, estado);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<ProfesionalResponse> obtener(@PathVariable String publicId) {
        ProfesionalResponse response = professionalService.obtenerPorPublicId(publicId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<ProfesionalResponse> actualizar(
            @PathVariable String publicId,
            @Valid @RequestBody ActualizarProfesionalRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        ProfesionalResponse response = professionalService.actualizarProfesional(publicId, request, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<ProfesionalResponse> desactivar(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        ProfesionalResponse response = professionalService.cambiarEstado(publicId, "INACTIVO", adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/activate")
    public ResponseEntity<ProfesionalResponse> activar(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        ProfesionalResponse response = professionalService.cambiarEstado(publicId, "ACTIVO", adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }
}

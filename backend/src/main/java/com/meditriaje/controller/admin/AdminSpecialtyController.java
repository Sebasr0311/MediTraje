package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.ActualizarEspecialidadRequest;
import com.meditriaje.dto.admin.CrearEspecialidadRequest;
import com.meditriaje.dto.admin.EspecialidadResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AdminCatalogService;
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
 * Controlador administrativo para la gestión de especialidades médicas (HU-10, ADR-002).
 * Requiere rol ROLE_ADMINISTRADOR para todas las operaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/specialties")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminSpecialtyController {

    private final AdminCatalogService catalogService;

    public AdminSpecialtyController(AdminCatalogService catalogService) {
        this.catalogService = Objects.requireNonNull(catalogService, "AdminCatalogService no puede ser nulo");
    }

    @PostMapping
    public ResponseEntity<EspecialidadResponse> crear(
            @Valid @RequestBody CrearEspecialidadRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        EspecialidadResponse response = catalogService.crearEspecialidad(request, adminPublicId, ipOrigen);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<EspecialidadResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String estado
    ) {
        PaginatedResponse<EspecialidadResponse> response = catalogService.listarEspecialidades(page, size, estado);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<EspecialidadResponse> obtener(@PathVariable String publicId) {
        EspecialidadResponse response = catalogService.obtenerEspecialidad(publicId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<EspecialidadResponse> actualizar(
            @PathVariable String publicId,
            @Valid @RequestBody ActualizarEspecialidadRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        EspecialidadResponse response = catalogService.actualizarEspecialidad(publicId, request, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<EspecialidadResponse> desactivar(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        EspecialidadResponse response = catalogService.desactivarEspecialidad(publicId, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/activate")
    public ResponseEntity<EspecialidadResponse> activar(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        EspecialidadResponse response = catalogService.activarEspecialidad(publicId, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }
}

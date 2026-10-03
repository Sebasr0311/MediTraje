package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.ActualizarSedeRequest;
import com.meditriaje.dto.admin.CrearSedeRequest;
import com.meditriaje.dto.admin.SedeResponse;
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
 * Controlador administrativo para la gestión de sedes médicas (HU-10, ADR-002).
 * Requiere rol ROLE_ADMINISTRADOR para todas las operaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/sites")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminSiteController {

    private final AdminCatalogService catalogService;

    public AdminSiteController(AdminCatalogService catalogService) {
        this.catalogService = Objects.requireNonNull(catalogService, "AdminCatalogService no puede ser nulo");
    }

    @PostMapping
    public ResponseEntity<SedeResponse> crear(
            @Valid @RequestBody CrearSedeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        SedeResponse response = catalogService.crearSede(request, adminPublicId, ipOrigen);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<SedeResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String institucionPublicId,
            @RequestParam(required = false) String estado
    ) {
        PaginatedResponse<SedeResponse> response = catalogService.listarSedes(page, size, institucionPublicId, estado);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<SedeResponse> obtener(@PathVariable String publicId) {
        SedeResponse response = catalogService.obtenerSede(publicId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{publicId}")
    public ResponseEntity<SedeResponse> actualizar(
            @PathVariable String publicId,
            @Valid @RequestBody ActualizarSedeRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        SedeResponse response = catalogService.actualizarSede(publicId, request, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/deactivate")
    public ResponseEntity<SedeResponse> desactivar(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        SedeResponse response = catalogService.desactivarSede(publicId, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/activate")
    public ResponseEntity<SedeResponse> activar(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        SedeResponse response = catalogService.activarSede(publicId, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }
}

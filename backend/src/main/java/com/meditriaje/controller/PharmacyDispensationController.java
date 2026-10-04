package com.meditriaje.controller;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.pharmacy.DispensacionResponse;
import com.meditriaje.dto.pharmacy.RecetaDispensacionResponse;
import com.meditriaje.dto.pharmacy.RegistrarDispensacionRequest;
import com.meditriaje.service.DispensationService;
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
import java.util.Objects;

/**
 * Controlador REST para el servicio de farmacia y dispensación de recetas médicas (F2.4, ADR-016).
 * Acceso estrictamente segregado: exclusivo para usuarios con {@code ROLE_FARMACEUTICO}.
 */
@RestController
@RequestMapping("/api/v1/pharmacy")
@PreAuthorize("hasAuthority('ROLE_FARMACEUTICO')")
public class PharmacyDispensationController {

    private final DispensationService dispensationService;

    public PharmacyDispensationController(DispensationService dispensationService) {
        this.dispensationService = Objects.requireNonNull(dispensationService, "DispensationService no puede ser nulo");
    }

    /**
     * Registra un evento de dispensación farmacéutica de medicamentos (F2.4, ADR-016).
     */
    @PostMapping("/dispensations")
    public ResponseEntity<DispensacionResponse> registrarDispensacion(
            @Valid @RequestBody RegistrarDispensacionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);

        DispensacionResponse response = dispensationService.registrarDispensacion(
                request, usuarioPublicId, authentication.getAuthorities(), ipOrigen
        );
        URI location = URI.create("/api/v1/pharmacy/dispensations/" + response.publicId());

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Consulta una dispensación previamente registrada por su UUID público.
     */
    @GetMapping("/dispensations/{publicId}")
    public ResponseEntity<DispensacionResponse> consultarDispensacion(@PathVariable String publicId) {
        DispensacionResponse response = dispensationService.consultarDispensacionPorPublicId(publicId);
        return ResponseEntity.ok(response);
    }

    /**
     * Búsqueda de recetas médicas para ventanilla de farmacia por documento del paciente o código de reclamación.
     */
    @GetMapping("/prescriptions")
    public ResponseEntity<PaginatedResponse<RecetaDispensacionResponse>> buscarRecetas(
            @RequestParam(name = "query", required = false, defaultValue = "") String query,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        PaginatedResponse<RecetaDispensacionResponse> response = dispensationService.buscarRecetasParaFarmacia(
                query, page, size, authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta detallada de una receta médica con saldos disponibles y entregas previas para dispensación.
     */
    @GetMapping("/prescriptions/{publicId}")
    public ResponseEntity<RecetaDispensacionResponse> consultarReceta(
            @PathVariable String publicId,
            Authentication authentication
    ) {
        RecetaDispensacionResponse response = dispensationService.consultarRecetaParaFarmacia(
                publicId, authentication.getAuthorities()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Lista de sedes asistenciales activas para selección en ventanilla de farmacia.
     */
    @GetMapping("/sites")
    public ResponseEntity<List<com.meditriaje.dto.admin.SedeResponse>> listarSedes() {
        List<com.meditriaje.dto.admin.SedeResponse> response = dispensationService.listarSedesActivas();
        return ResponseEntity.ok(response);
    }
}

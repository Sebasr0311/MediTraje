package com.meditriaje.controller.admin;

import com.meditriaje.dto.audit.RegistroAuditoriaResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AuditoriaService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Controlador para la supervisión y visor de la bitácora de auditoría de seguridad (F2.7, RF-26, RNF-11, ADR-019).
 * Restringido exclusivamente a ROLE_ADMINISTRADOR.
 * Proyecta metadatos técnicos y de seguridad con cero datos clínicos (ADR-007).
 */
@RestController
@RequestMapping("/api/v1/admin/audit")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminAuditController {

    private final AuditoriaService auditoriaService;

    public AdminAuditController(AuditoriaService auditoriaService) {
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
    }

    /**
     * Consulta paginada y filtrada de eventos de la bitácora de auditoría.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<RegistroAuditoriaResponse>> consultarBitacora(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String accion,
            @RequestParam(required = false) String resultado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        PaginatedResponse<RegistroAuditoriaResponse> response =
                auditoriaService.consultarBitacora(desde, hasta, accion, resultado, page, size);
        return ResponseEntity.ok(response);
    }
}

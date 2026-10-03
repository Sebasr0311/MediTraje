package com.meditriaje.controller;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.service.PrescriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Controlador REST para el catálogo maestro de medicamentos (HU-08).
 */
@RestController
@RequestMapping("/api/v1/catalogs/medications")
public class MedicationCatalogController {

    private final PrescriptionService prescriptionService;

    public MedicationCatalogController(PrescriptionService prescriptionService) {
        this.prescriptionService = Objects.requireNonNull(prescriptionService, "PrescriptionService no puede ser nulo");
    }

    /**
     * Consulta paginada y filtrable del catálogo maestro de medicamentos.
     * Accesible a cualquier usuario autenticado en el sistema.
     */
    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PaginatedResponse<MedicamentoResponse>> listarMedicamentos(
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size
    ) {
        PaginatedResponse<MedicamentoResponse> response = prescriptionService.listarCatalogo(q, page, size);
        return ResponseEntity.ok(response);
    }
}

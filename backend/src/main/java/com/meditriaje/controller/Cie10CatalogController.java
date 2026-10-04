package com.meditriaje.controller;

import com.meditriaje.dto.clinical.DiagnosticoCie10Response;
import com.meditriaje.repository.DiagnosticoCie10Repository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Controlador REST para el catálogo maestro de diagnósticos CIE-10 (ADR-013, HU-07).
 */
@RestController
@RequestMapping("/api/v1/catalogs/icd10")
public class Cie10CatalogController {

    private final DiagnosticoCie10Repository diagnosticoCie10Repository;

    public Cie10CatalogController(DiagnosticoCie10Repository diagnosticoCie10Repository) {
        this.diagnosticoCie10Repository = Objects.requireNonNull(diagnosticoCie10Repository, "DiagnosticoCie10Repository no puede ser nulo");
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<DiagnosticoCie10Response>> listarDiagnosticos(
            @RequestParam(name = "q", required = false) String query
    ) {
        List<DiagnosticoCie10Response> resultados = diagnosticoCie10Repository.listarActivos(query);
        return ResponseEntity.ok(resultados);
    }
}

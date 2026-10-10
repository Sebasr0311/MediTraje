package com.meditriaje.controller;

import com.meditriaje.dto.affiliation.*;
import com.meditriaje.model.EntidadEps;
import com.meditriaje.service.AffiliationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * Endpoints REST para Gestión de Afiliaciones EPS, Importación Segura y Citas Avanzadas (Fases A y C, ADR-025, ADR-026).
 */
@RestController
@RequestMapping("/api/v1/affiliations")
public class AffiliationController {

    private final AffiliationService affiliationService;

    public AffiliationController(AffiliationService affiliationService) {
        this.affiliationService = Objects.requireNonNull(affiliationService, "affiliationService no puede ser nulo");
    }

    private String obtenerIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : request.getRemoteAddr();
    }

    /**
     * Lista las entidades promotoras de salud (EPS) activas en el sistema.
     */
    @GetMapping("/eps")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_PACIENTE')")
    public ResponseEntity<List<EntidadEps>> listarEps() {
        return ResponseEntity.ok(affiliationService.listarEpsActivas());
    }

    /**
     * Descarga la plantilla oficial en formato Excel (.xlsx) para importación masiva de afiliados.
     */
    @GetMapping(value = "/template", produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> descargarPlantilla() {
        byte[] archivo = affiliationService.generarPlantillaExcel();
        return ResponseEntity.ok()
                .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"plantilla_afiliados_eps.xlsx\"")
                .body(archivo);
    }

    /**
     * A02: Carga un archivo Excel (XLSX) con protección Zip Bomb y sanitización en staging preview.
     */
    @PostMapping(value = "/upload-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('ROLE_ADMINISTRADOR')")
    public ResponseEntity<LotePreviewResponse> cargarPreview(
            @RequestParam("file") MultipartFile file,
            @RequestParam("epsPublicId") String epsPublicId,
            Authentication auth,
            HttpServletRequest httpRequest
    ) throws IOException {
        String usuarioPubId = auth.getName();
        byte[] bytes = file.getBytes();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "importacion.xlsx";

        LotePreviewResponse response = affiliationService.procesarArchivoExcelPreview(
                bytes,
                originalFilename,
                epsPublicId,
                usuarioPubId,
                obtenerIp(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * A03/A04: Confirma y aplica el commit atómico o por filas válidas de un lote en staging.
     */
    @PostMapping("/batches/{lotePublicId}/commit")
    @PreAuthorize("hasRole('ROLE_ADMINISTRADOR')")
    public ResponseEntity<Void> confirmarLote(
            @PathVariable String lotePublicId,
            @Valid @RequestBody(required = false) CommitLoteRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        affiliationService.commitLote(lotePublicId, request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * A05: Consulta el estado de afiliación de un paciente por tipo y número de documento (no bloqueante en urgencias).
     */
    @GetMapping("/patients/{tipoDocumento}/{numeroDocumento}")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<AfiliacionResponse> consultarAfiliacion(
            @PathVariable String tipoDocumento,
            @PathVariable String numeroDocumento,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        AfiliacionResponse response = affiliationService.consultarAfiliacion(
                tipoDocumento,
                numeroDocumento,
                usuarioPubId,
                obtenerIp(httpRequest)
        );
        return ResponseEntity.ok(response);
    }

    /**
     * C02: Registra una ausencia médica o bloqueo temporal de agenda.
     */
    @PostMapping("/absences")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PROFESIONAL')")
    public ResponseEntity<AusenciaResponse> registrarAusencia(
            @Valid @RequestBody RegistrarAusenciaRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        AusenciaResponse response = affiliationService.registrarAusenciaMedica(request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * C02: Lista las ausencias médicas de un profesional.
     */
    @GetMapping("/professionals/{profesionalPublicId}/absences")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<AusenciaResponse>> listarAusencias(
            @PathVariable String profesionalPublicId
    ) {
        return ResponseEntity.ok(affiliationService.listarAusenciasProfesional(profesionalPublicId));
    }

    /**
     * C03: Registra una representación legal para agendamiento de menores de edad.
     */
    @PostMapping("/guardians")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PACIENTE')")
    public ResponseEntity<RepresentacionLegalResponse> registrarRepresentacion(
            @Valid @RequestBody RepresentacionLegalRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        RepresentacionLegalResponse response = affiliationService.registrarRepresentacionLegal(request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * C03: Lista los menores a cargo de un tutor o representante.
     */
    @GetMapping("/guardians/{tutorPublicId}/minors")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_PACIENTE')")
    public ResponseEntity<List<RepresentacionLegalResponse>> listarMenoresACargo(
            @PathVariable String tutorPublicId
    ) {
        return ResponseEntity.ok(affiliationService.listarRepresentacionesPorTutor(tutorPublicId));
    }
}

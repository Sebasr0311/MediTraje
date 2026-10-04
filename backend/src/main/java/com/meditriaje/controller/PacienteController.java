package com.meditriaje.controller;

import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.followup.ReportarEvolucionRequest;
import com.meditriaje.dto.followup.SeguimientoResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.service.AppointmentService;
import com.meditriaje.service.ClinicalAttentionService;
import com.meditriaje.service.FollowUpService;
import com.meditriaje.service.PacienteService;
import com.meditriaje.service.PrescriptionService;
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

import java.util.Objects;

/**
 * Endpoints asistenciales y de consulta para el paciente (HU-08, HU-09).
 */
@RestController
@RequestMapping("/api/v1/patients")
public class PacienteController {

    private final PacienteService pacienteService;
    private final ClinicalAttentionService clinicalAttentionService;
    private final PrescriptionService prescriptionService;
    private final AppointmentService appointmentService;
    private final FollowUpService followUpService;

    public PacienteController(
            PacienteService pacienteService,
            ClinicalAttentionService clinicalAttentionService,
            PrescriptionService prescriptionService,
            AppointmentService appointmentService,
            FollowUpService followUpService
    ) {
        this.pacienteService = Objects.requireNonNull(pacienteService, "PacienteService no puede ser nulo");
        this.clinicalAttentionService = Objects.requireNonNull(clinicalAttentionService, "ClinicalAttentionService no puede ser nulo");
        this.prescriptionService = Objects.requireNonNull(prescriptionService, "PrescriptionService no puede ser nulo");
        this.appointmentService = Objects.requireNonNull(appointmentService, "AppointmentService no puede ser nulo");
        this.followUpService = Objects.requireNonNull(followUpService, "FollowUpService no puede ser nulo");
    }

    /**
     * Consulta el perfil demográfico y de contacto del paciente actualmente autenticado.
     * Requiere obligatoriamente el rol ROLE_PACIENTE (ADR-002, HU-09).
     */
    @GetMapping("/me")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PacientePerfilResponse> obtenerMiPerfil(Authentication authentication) {
        String usuarioPublicId = authentication.getName();
        PacientePerfilResponse perfil = pacienteService.obtenerMiPerfil(usuarioPublicId);
        return ResponseEntity.ok(perfil);
    }

    /**
     * Consulta paginada del historial de atenciones médicas y enmiendas del paciente autenticado (HU-09).
     * Solo lectura, sin exponer IDs numéricos de base de datos.
     */
    @GetMapping("/me/history")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<AtencionResponse>> obtenerMiHistoria(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        PaginatedResponse<AtencionResponse> historia = clinicalAttentionService.obtenerMiHistoriaClinica(
                usuarioPublicId, page, size, ipOrigen
        );
        return ResponseEntity.ok(historia);
    }

    /**
     * Consulta paginada de las recetas médicas emitidas para el paciente autenticado (HU-08, HU-09).
     * Solo lectura, sin exponer IDs numéricos autonuméricos de base de datos.
     */
    @GetMapping("/me/prescriptions")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<RecetaResponse>> obtenerMisRecetas(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        PaginatedResponse<RecetaResponse> recetas = prescriptionService.obtenerMisRecetas(
                usuarioPublicId, page, size, ipOrigen
        );
        return ResponseEntity.ok(recetas);
    }

    /**
     * Consulta paginada de las citas médicas agendadas para el paciente autenticado (HU-04, HU-09).
     * Solo lectura, sin exponer IDs numéricos autonuméricos de base de datos.
     */
    @GetMapping("/me/appointments")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<CitaResponse>> obtenerMisCitas(
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        PaginatedResponse<CitaResponse> citas = appointmentService.obtenerMisCitas(
                usuarioPublicId, page, size
        );
        return ResponseEntity.ok(citas);
    }

    /**
     * Consulta paginada de las tareas de seguimiento post-atención asignadas al paciente (ADR-015, HU-09).
     */
    @GetMapping("/me/follow-ups")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<PaginatedResponse<SeguimientoResponse>> obtenerMisSeguimientos(
            @RequestParam(name = "estado", required = false) String estado,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size,
            Authentication authentication
    ) {
        String usuarioPublicId = authentication.getName();
        PaginatedResponse<SeguimientoResponse> response = followUpService.listarMisSeguimientos(
                usuarioPublicId, estado, page, size
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Registra el reporte de evolución del paciente sobre una tarea de seguimiento (ADR-015, §5.16).
     * El reporte del paciente nunca se convierte automáticamente en un diagnóstico médico.
     */
    @PostMapping("/me/follow-ups/{publicId}/report")
    @PreAuthorize("hasAuthority('ROLE_PACIENTE')")
    public ResponseEntity<SeguimientoResponse> reportarEvolucion(
            @PathVariable String publicId,
            @Valid @RequestBody ReportarEvolucionRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = authentication.getName();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        SeguimientoResponse response = followUpService.reportarEvolucion(
                publicId, request, usuarioPublicId, ipOrigen
        );
        return ResponseEntity.ok(response);
    }
}

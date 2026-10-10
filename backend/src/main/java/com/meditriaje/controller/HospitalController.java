package com.meditriaje.controller;

import com.meditriaje.dto.hospital.*;
import com.meditriaje.service.HospitalService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;

/**
 * Endpoints REST para Gestión Hospitalaria, Camas, Quirófanos y Egresos (Fase H, ADR-024).
 */
@RestController
@RequestMapping("/api/v1/hospital")
public class HospitalController {

    private final HospitalService hospitalService;

    public HospitalController(HospitalService hospitalService) {
        this.hospitalService = Objects.requireNonNull(hospitalService, "hospitalService no puede ser nulo");
    }

    private String obtenerIp(HttpServletRequest request) {
        String xf = request.getHeader("X-Forwarded-For");
        return (xf != null && !xf.isBlank()) ? xf.split(",")[0].trim() : request.getRemoteAddr();
    }

    /**
     * H01/H02: Asignación de cama a un episodio hospitalario.
     */
    @PostMapping("/episodes/{episodeId}/beds/assign")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<CamaDetalleResponse> asignarCama(
            @PathVariable String episodeId,
            @Valid @RequestBody AsignarCamaRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        CamaDetalleResponse resp = hospitalService.asignarCama(episodeId, request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * H03: Traslado intrahospitalario longitudinal del paciente.
     */
    @PostMapping("/episodes/{episodeId}/beds/transfer")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<MovimientoResponse> trasladarPaciente(
            @PathVariable String episodeId,
            @Valid @RequestBody TrasladarPacienteRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        MovimientoResponse resp = hospitalService.trasladarPaciente(episodeId, request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.ok(resp);
    }

    /**
     * H01: Actualización de estado de cama (ej. LIMPIEZA -> DISPONIBLE).
     */
    @PatchMapping("/beds/{camaId}/status")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL', 'ROLE_ADMINISTRADOR')")
    public ResponseEntity<Void> cambiarEstadoCama(
            @PathVariable String camaId,
            @RequestParam String nuevoEstado,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        hospitalService.cambiarEstadoCama(camaId, nuevoEstado, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * H04: Registro de procedimiento quirúrgico o intervención en quirófano.
     */
    @PostMapping("/episodes/{episodeId}/procedures")
    @PreAuthorize("hasRole('ROLE_PROFESIONAL')")
    public ResponseEntity<ProcedimientoResponse> registrarProcedimiento(
            @PathVariable String episodeId,
            @Valid @RequestBody RegistrarProcedimientoRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        ProcedimientoResponse resp = hospitalService.registrarProcedimiento(episodeId, request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(resp);
    }

    /**
     * H04: Transición de estado en procedimiento quirúrgico/recuperación.
     */
    @PatchMapping("/procedures/{procedimientoId}/status")
    @PreAuthorize("hasRole('ROLE_PROFESIONAL')")
    public ResponseEntity<Void> actualizarEstadoProcedimiento(
            @PathVariable String procedimientoId,
            @Valid @RequestBody ActualizarEstadoProcedimientoRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        hospitalService.actualizarEstadoProcedimiento(procedimientoId, request, usuarioPubId, obtenerIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * H05: Egreso hospitalario definitivo y liberación de cama.
     */
    @PostMapping("/episodes/{episodeId}/discharge")
    @PreAuthorize("hasRole('ROLE_PROFESIONAL')")
    public ResponseEntity<Void> registrarEgresoHospitalario(
            @PathVariable String episodeId,
            @Valid @RequestBody RegistrarEgresoRequest request,
            Authentication auth,
            HttpServletRequest httpRequest
    ) {
        String usuarioPubId = auth.getName();
        hospitalService.registrarEgresoHospitalario(episodeId, request, usuarioPubId, auth.getAuthorities(), obtenerIp(httpRequest));
        return ResponseEntity.noContent().build();
    }

    /**
     * H06: Centro de Control Hospitalario: Censo de camas e indicadores de ocupación en tiempo real.
     */
    @GetMapping("/census/{sedeId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<CensoCamasResponse> obtenerCensoCamas(@PathVariable String sedeId) {
        CensoCamasResponse resp = hospitalService.obtenerCensoCamas(sedeId);
        return ResponseEntity.ok(resp);
    }

    /**
     * H01: Listado de camas con estado y paciente actual.
     */
    @GetMapping("/beds/{sedeId}")
    @PreAuthorize("hasAnyRole('ROLE_ADMINISTRADOR', 'ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<CamaDetalleResponse>> listarCamas(@PathVariable String sedeId) {
        List<CamaDetalleResponse> resp = hospitalService.listarCamasDetalle(sedeId);
        return ResponseEntity.ok(resp);
    }

    /**
     * H03: Historial longitudinal de movimientos y traslados de un episodio.
     */
    @GetMapping("/episodes/{episodeId}/movements")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<MovimientoResponse>> listarMovimientos(@PathVariable String episodeId) {
        List<MovimientoResponse> resp = hospitalService.listarMovimientosEpisodio(episodeId);
        return ResponseEntity.ok(resp);
    }

    /**
     * H04: Historial de procedimientos de un episodio.
     */
    @GetMapping("/episodes/{episodeId}/procedures")
    @PreAuthorize("hasAnyRole('ROLE_ENFERMERIA', 'ROLE_PROFESIONAL')")
    public ResponseEntity<List<ProcedimientoResponse>> listarProcedimientos(@PathVariable String episodeId) {
        List<ProcedimientoResponse> resp = hospitalService.listarProcedimientosEpisodio(episodeId);
        return ResponseEntity.ok(resp);
    }
}

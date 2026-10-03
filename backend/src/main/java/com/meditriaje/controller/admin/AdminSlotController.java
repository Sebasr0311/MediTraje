package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.GenerarSlotsRequest;
import com.meditriaje.dto.admin.GenerarSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.SlotGeneratorService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Objects;

/**
 * Controlador administrativo para la gestión y generación en lote de slots de disponibilidad (HU-10, ADR-002, ADR-005, ADR-006).
 * Requiere estrictamente el rol ROLE_ADMINISTRADOR para todas las operaciones.
 */
@RestController
@RequestMapping("/api/v1/admin/slots")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminSlotController {

    private final SlotGeneratorService slotGeneratorService;

    public AdminSlotController(SlotGeneratorService slotGeneratorService) {
        this.slotGeneratorService = Objects.requireNonNull(slotGeneratorService, "SlotGeneratorService no puede ser nulo");
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerarSlotsResponse> generarSlots(
            @Valid @RequestBody GenerarSlotsRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        GenerarSlotsResponse response = slotGeneratorService.generarSlots(request, adminPublicId, ipOrigen);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<SlotResponse>> listar(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String profesionalPublicId,
            @RequestParam(required = false) String sedePublicId,
            @RequestParam(required = false) String especialidadPublicId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fechaHasta,
            @RequestParam(required = false) String estado
    ) {
        PaginatedResponse<SlotResponse> response = slotGeneratorService.listar(
                page, size, profesionalPublicId, sedePublicId, especialidadPublicId, fechaDesde, fechaHasta, estado
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{publicId}")
    public ResponseEntity<SlotResponse> obtener(@PathVariable String publicId) {
        SlotResponse response = slotGeneratorService.obtenerPorPublicId(publicId);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/block")
    public ResponseEntity<SlotResponse> bloquear(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        SlotResponse response = slotGeneratorService.bloquearSlot(publicId, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @PatchMapping("/{publicId}/unblock")
    public ResponseEntity<SlotResponse> desbloquear(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        SlotResponse response = slotGeneratorService.desbloquearSlot(publicId, adminPublicId, ipOrigen);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{publicId}")
    public ResponseEntity<Void> eliminar(
            @PathVariable String publicId,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String adminPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        slotGeneratorService.eliminarSlot(publicId, adminPublicId, ipOrigen);
        return ResponseEntity.noContent().build();
    }
}

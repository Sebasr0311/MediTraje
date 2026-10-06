package com.meditriaje.controller.admin;

import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.service.AdminAppointmentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Controlador administrativo para la supervisión, visor de calendario y exportación
 * de citas médicas (ADR-003, ADR-006, ADR-007, RF-26).
 * Acceso restringido exclusivamente a ROLE_ADMINISTRADOR.
 */
@RestController
@RequestMapping("/api/v1/admin/appointments")
@PreAuthorize("hasAuthority('ROLE_ADMINISTRADOR')")
public class AdminAppointmentController {

    private final AdminAppointmentService adminAppointmentService;

    public AdminAppointmentController(AdminAppointmentService adminAppointmentService) {
        this.adminAppointmentService = Objects.requireNonNull(adminAppointmentService, "adminAppointmentService no puede ser nulo");
    }

    /**
     * Consulta paginada y filtrada de citas para el visor y calendario administrativo.
     */
    @GetMapping
    public ResponseEntity<PaginatedResponse<AdminCitaDetalleResponse>> listarCitas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String profesionalPublicId,
            @RequestParam(required = false) String especialidadPublicId,
            @RequestParam(required = false) String estado,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        LocalDate fDesde = fechaDesde != null ? fechaDesde : desde;
        LocalDate fHasta = fechaHasta != null ? fechaHasta : hasta;

        PaginatedResponse<AdminCitaDetalleResponse> response = adminAppointmentService.listarCitas(
                fDesde,
                fHasta,
                profesionalPublicId,
                especialidadPublicId,
                estado,
                page,
                size
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Exportación de citas a archivo CSV compatible con Microsoft Excel en formato nativo con BOM UTF-8.
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportarCitas(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaDesde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaHasta,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) String profesionalPublicId,
            @RequestParam(required = false) String especialidadPublicId,
            @RequestParam(required = false) String estado
    ) {
        LocalDate fDesde = fechaDesde != null ? fechaDesde : desde;
        LocalDate fHasta = fechaHasta != null ? fechaHasta : hasta;

        byte[] contenido = adminAppointmentService.exportarCitasExcelCsv(
                fDesde,
                fHasta,
                profesionalPublicId,
                especialidadPublicId,
                estado
        );

        String fechaSufijo = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String filename = "citas_meditriaje_" + fechaSufijo + ".csv";

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CONTENT_TYPE, "text/csv; charset=UTF-8")
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(contenido);
    }
}

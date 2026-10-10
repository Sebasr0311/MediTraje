package com.meditriaje.controller;

import com.meditriaje.dto.emergency.ConsultarResumenRequest;
import com.meditriaje.dto.emergency.VerificarQrResponse;
import com.meditriaje.dto.emergency.summary.ResumenSaludResponse;
import com.meditriaje.security.QrRateLimiter;
import com.meditriaje.service.EmergencyQrService;
import com.meditriaje.service.EmergencySummaryService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Controlador público para verificación y lectura del resumen clínico de salud por QR de emergencia (ADR-010, §5.17, §5.18).
 * Diseñado para acceso por personal de atención prehospitalaria o de emergencias sin necesidad de inicio de sesión previo.
 * Protegido con limitador de tasa (rate limiting) por dirección IP (SEC-002).
 */
@RestController
@RequestMapping("/api/v1/emergency-summary")
public class EmergencySummaryController {

    private final EmergencyQrService emergencyQrService;
    private final EmergencySummaryService emergencySummaryService;
    private final QrRateLimiter qrRateLimiter;

    public EmergencySummaryController(
            EmergencyQrService emergencyQrService,
            EmergencySummaryService emergencySummaryService
    ) {
        this(emergencyQrService, emergencySummaryService, new QrRateLimiter(15, 60));
    }

    @Autowired
    public EmergencySummaryController(
            EmergencyQrService emergencyQrService,
            EmergencySummaryService emergencySummaryService,
            QrRateLimiter qrRateLimiter
    ) {
        this.emergencyQrService = Objects.requireNonNull(emergencyQrService, "EmergencyQrService no puede ser nulo");
        this.emergencySummaryService = Objects.requireNonNull(emergencySummaryService, "EmergencySummaryService no puede ser nulo");
        this.qrRateLimiter = (qrRateLimiter != null) ? qrRateLimiter : new QrRateLimiter(15, 60);
    }

    /**
     * Verificación pública del token QR escaneado (indica si está vigente, expirado o agotado, y si requiere PIN).
     */
    @GetMapping("/{token}/check")
    public ResponseEntity<VerificarQrResponse> verificarToken(
            @PathVariable String token,
            HttpServletRequest httpRequest
    ) {
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        qrRateLimiter.validarLimite(ipOrigen);
        VerificarQrResponse response = emergencyQrService.verificarTokenPublico(token);
        return ResponseEntity.ok(response);
    }

    /**
     * Consulta pública del resumen clínico de salud mediante el token QR y PIN opcional.
     * Incrementa atómicamente el contador de accesos en BD y audita el evento sin datos clínicos.
     */
    @PostMapping("/{token}")
    public ResponseEntity<ResumenSaludResponse> consultarResumen(
            @PathVariable String token,
            @RequestBody(required = false) ConsultarResumenRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        qrRateLimiter.validarLimite(ipOrigen);
        ResumenSaludResponse response = emergencySummaryService.consultarResumenPorToken(token, request, ipOrigen);
        return ResponseEntity.ok(response);
    }
}

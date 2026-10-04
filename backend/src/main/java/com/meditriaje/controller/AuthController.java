package com.meditriaje.controller;

import com.meditriaje.dto.AuthSessionResponse;
import com.meditriaje.dto.AuthTokens;
import com.meditriaje.dto.CambiarPasswordRequest;
import com.meditriaje.dto.LoginRequest;
import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.service.AuthService;
import com.meditriaje.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;

/**
 * Endpoints de autenticación, ciclo de vida de sesiones y registro (HU-01, ADR-002).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;
    private final boolean cookieSecure;

    public AuthController(
            AuthService authService,
            @Value("${security.cookie.secure:false}") boolean cookieSecure
    ) {
        this.authService = Objects.requireNonNull(authService, "AuthService no puede ser nulo");
        this.cookieSecure = cookieSecure;
    }

    @PostMapping("/register")
    public ResponseEntity<RegistroPacienteResponse> registrarPaciente(
            @Valid @RequestBody RegistroPacienteRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        RegistroPacienteResponse response = authService.registrarPaciente(request, ipOrigen);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthSessionResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        AuthTokens tokens = authService.login(request, ipOrigen);

        ResponseCookie accessCookie = crearAccessCookie(tokens.accessToken(), Duration.ofMinutes(15));
        ResponseCookie refreshCookie = crearRefreshCookie(tokens.rawRefreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(tokens.sessionResponse());
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthSessionResponse> refresh(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        AuthTokens tokens = authService.refresh(refreshToken, ipOrigen);

        ResponseCookie accessCookie = crearAccessCookie(tokens.accessToken(), Duration.ofMinutes(15));
        ResponseCookie refreshCookie = crearRefreshCookie(tokens.rawRefreshToken(), Duration.ofDays(7));

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, accessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(tokens.sessionResponse());
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @CookieValue(name = "refresh_token", required = false) String refreshToken,
            HttpServletRequest httpRequest
    ) {
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        authService.logout(refreshToken, ipOrigen);

        ResponseCookie clearAccessCookie = crearAccessCookie("", Duration.ZERO);
        ResponseCookie clearRefreshCookie = crearRefreshCookie("", Duration.ZERO);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearAccessCookie.toString())
                .header(HttpHeaders.SET_COOKIE, clearRefreshCookie.toString())
                .body(Map.of("mensaje", "Sesion cerrada exitosamente."));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, String>> cambiarPassword(
            @Valid @RequestBody CambiarPasswordRequest request,
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {
        String usuarioPublicId = (String) authentication.getPrincipal();
        String ipOrigen = IpUtil.extraerIp(httpRequest);
        authService.cambiarPassword(usuarioPublicId, request, ipOrigen);
        return ResponseEntity.ok(Map.of("mensaje", "Contrasena actualizada exitosamente."));
    }

    private ResponseCookie crearAccessCookie(String token, Duration maxAge) {
        return ResponseCookie.from("access_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private ResponseCookie crearRefreshCookie(String token, Duration maxAge) {
        return ResponseCookie.from("refresh_token", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }
}

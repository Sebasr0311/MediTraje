package com.meditriaje.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

/**
 * Controlador de verificación de salud y latido (Heartbeat / Ping / Keep-Alive).
 * Expone endpoints ultraligeros y públicos para evitar la suspensión por inactividad (ej. UptimeRobot, Render).
 */
@RestController
public class PingController {

    @GetMapping({"/api/v1/ping", "/ping", "/health"})
    public ResponseEntity<Map<String, String>> ping() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "MediTriaje 2.0 API",
                "timestamp", Instant.now().toString()
        ));
    }

    @GetMapping("/")
    public ResponseEntity<Map<String, String>> root() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "MediTriaje 2.0 API",
                "frontend", "https://meditraje.vercel.app"
        ));
    }
}


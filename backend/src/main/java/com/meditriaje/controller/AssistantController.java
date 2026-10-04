package com.meditriaje.controller;

import com.meditriaje.dto.assistant.PreguntaAsistenteRequest;
import com.meditriaje.dto.assistant.RespuestaAsistenteResponse;
import com.meditriaje.service.AssistantService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * Controlador del Asistente Virtual del Sistema (F2.6, RF-27, ADR-018, §5.19).
 * Proporciona orientación operativa contextual, navegación asistida y detección infalible de emergencias.
 */
@RestController
@RequestMapping("/api/v1/assistant")
public class AssistantController {

    private final AssistantService assistantService;

    public AssistantController(AssistantService assistantService) {
        this.assistantService = Objects.requireNonNull(assistantService, "assistantService no puede ser nulo");
    }

    /**
     * Procesa una consulta de orientación o soporte del usuario.
     */
    @PostMapping("/chat")
    public ResponseEntity<RespuestaAsistenteResponse> chat(
            @Valid @RequestBody PreguntaAsistenteRequest request
    ) {
        RespuestaAsistenteResponse response = assistantService.procesarConsulta(request);
        return ResponseEntity.ok(response);
    }
}

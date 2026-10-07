package com.meditriaje.service.email;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Transporte HTTP oficial para la API transaccional de Brevo (HTTPS 443 a https://api.brevo.com/v3/smtp/email).
 * Diseñado para entornos como Render donde el tráfico SMTP saliente (puertos 25/465/587) está bloqueado (D1, T3).
 */
public class BrevoApiEmailTransport implements EmailTransport {

    private static final Logger log = LoggerFactory.getLogger(BrevoApiEmailTransport.class);

    private final String apiUrl;
    private final String apiKey;
    private final String mailFrom;
    private final String mailFromName;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public BrevoApiEmailTransport(
            String apiUrl,
            String apiKey,
            String mailFrom,
            String mailFromName,
            HttpClient httpClient,
            ObjectMapper objectMapper
    ) {
        this.apiUrl = (apiUrl != null && !apiUrl.isBlank()) ? apiUrl.trim() : "https://api.brevo.com/v3/smtp/email";
        this.apiKey = (apiKey != null) ? apiKey.trim() : "";
        this.mailFrom = (mailFrom != null && !mailFrom.isBlank()) ? mailFrom.trim() : "no-reply@meditriaje.com";
        this.mailFromName = (mailFromName != null && !mailFromName.isBlank()) ? mailFromName.trim() : "MediTriaje 2.0";
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

    @Override
    public String getNombreTransporte() {
        return "brevo-api";
    }

    @Override
    public ResultadoEnvio enviar(String destinatarioEmail, String asunto, String cuerpoHtml) {
        Objects.requireNonNull(destinatarioEmail, "destinatarioEmail no puede ser nulo");
        Objects.requireNonNull(asunto, "asunto no puede ser nulo");
        Objects.requireNonNull(cuerpoHtml, "cuerpoHtml no puede ser nulo");

        String emailEnmascarado = EmailUtil.enmascararEmail(destinatarioEmail);

        if (apiKey.isBlank()) {
            log.error("Fallo al enviar correo vía Brevo API: BREVO_API_KEY no configurada. Destinatario=[{}]", emailEnmascarado);
            return ResultadoEnvio.fallido("BREVO_CONFIG_ERROR", "Clave de API de Brevo no configurada en el servidor.");
        }

        try {
            Map<String, Object> payloadMap = Map.of(
                    "sender", Map.of("name", mailFromName, "email", mailFrom),
                    "to", List.of(Map.of("email", destinatarioEmail)),
                    "subject", asunto,
                    "htmlContent", cuerpoHtml
            );
            String payloadJson = objectMapper.writeValueAsString(payloadMap);

            return ejecutarEnvioConReintento(payloadJson, emailEnmascarado, asunto);
        } catch (Exception ex) {
            String sanitizedMsg = EmailUtil.sanitizarMensajeError(ex.getMessage(), apiKey);
            log.warn("Excepción al preparar envío de correo vía Brevo API hacia [{}]: {}", emailEnmascarado, sanitizedMsg);
            return ResultadoEnvio.fallido("BREVO_ERROR", sanitizedMsg);
        }
    }

    private ResultadoEnvio ejecutarEnvioConReintento(
            String payloadJson,
            String emailEnmascarado,
            String asunto
    ) {
        // Intento 1
        IntentoResultado r1 = realizarPeticionHttp(payloadJson, emailEnmascarado, asunto);
        if (r1.resultado() != null) {
            // Éxito o error 4xx no reintentable
            return r1.resultado();
        }

        // Si falló por 5xx o timeout/IO de red, reintentar máximo 1 vez (nunca reintentar 4xx)
        log.warn("Reintentando petición Brevo API hacia [{}] tras error transitorio (status={}): {}",
                emailEnmascarado, r1.statusCode(), r1.errorDetalle());

        IntentoResultado r2 = realizarPeticionHttp(payloadJson, emailEnmascarado, asunto);
        if (r2.resultado() != null) {
            return r2.resultado();
        }

        return mapearFallo(r2.statusCode(), r2.errorDetalle(), emailEnmascarado);
    }

    private record IntentoResultado(ResultadoEnvio resultado, int statusCode, String errorDetalle) {}

    private IntentoResultado realizarPeticionHttp(String payloadJson, String emailEnmascarado, String asunto) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .header("api-key", apiKey)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payloadJson, StandardCharsets.UTF_8))
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();

            if (status >= 200 && status < 300) {
                log.info("Correo despachado exitosamente vía Brevo HTTP API hacia [{}] con asunto [{}]", emailEnmascarado, asunto);
                return new IntentoResultado(ResultadoEnvio.exitoso(), status, null);
            }

            String body = response.body();
            String errorMsg = extraerMensajeDeError(body, status);

            if (status >= 400 && status < 500) {
                // Errores 4xx (400, 401, 429) no se reintentan
                ResultadoEnvio res4xx = mapearFallo(status, errorMsg, emailEnmascarado);
                return new IntentoResultado(res4xx, status, errorMsg);
            }

            // Status 5xx: candidato para reintento
            return new IntentoResultado(null, status, errorMsg);
        } catch (HttpTimeoutException ex) {
            String msg = "Timeout de conexión o respuesta con Brevo API: " + ex.getMessage();
            return new IntentoResultado(null, 504, msg);
        } catch (IOException ex) {
            String msg = "Fallo de comunicación de red con Brevo API: " + ex.getMessage();
            return new IntentoResultado(null, 503, msg);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            String msg = "Envío interrumpido";
            return new IntentoResultado(ResultadoEnvio.fallido("INTERRUMPIDO", msg), 500, msg);
        }
    }

    private String extraerMensajeDeError(String body, int status) {
        if (body == null || body.isBlank()) {
            return "Brevo API respondió código " + status;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.has("message")) {
                return root.get("message").asText();
            }
            if (root.has("error")) {
                return root.get("error").asText();
            }
        } catch (Exception ignored) {
        }
        return EmailUtil.sanitizarMensajeError(body, apiKey);
    }

    private ResultadoEnvio mapearFallo(int status, String detalle, String emailEnmascarado) {
        String detalleSanitizado = EmailUtil.sanitizarMensajeError(detalle, apiKey);
        if (status == 400) {
            log.warn("Brevo API 400 (petición/remitente no verificado) hacia [{}]: {}", emailEnmascarado, detalleSanitizado);
            return ResultadoEnvio.fallido("BREVO_400", "Petición o remitente no verificado en Brevo: " + detalleSanitizado);
        } else if (status == 401) {
            log.warn("Brevo API 401 (clave inválida) hacia [{}]: {}", emailEnmascarado, detalleSanitizado);
            return ResultadoEnvio.fallido("BREVO_401", "Clave de API de Brevo inválida o no autorizada.");
        } else if (status == 429) {
            log.warn("Brevo API 429 (cuota excedida) hacia [{}]: {}", emailEnmascarado, detalleSanitizado);
            return ResultadoEnvio.fallido("BREVO_429", "Cuota de envíos o rate limit excedido en Brevo: " + detalleSanitizado);
        } else if (status >= 500) {
            log.warn("Brevo API {} (error de servidor o timeout) hacia [{}]: {}", status, emailEnmascarado, detalleSanitizado);
            return ResultadoEnvio.fallido("BREVO_5XX", "Error de servidor en Brevo o timeout: " + detalleSanitizado);
        } else {
            log.warn("Brevo API {} hacia [{}]: {}", status, emailEnmascarado, detalleSanitizado);
            return ResultadoEnvio.fallido("BREVO_ERROR_" + status, detalleSanitizado);
        }
    }
}

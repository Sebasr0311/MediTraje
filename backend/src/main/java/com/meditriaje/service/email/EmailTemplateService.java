package com.meditriaje.service.email;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio encargado de cargar y renderizar plantillas HTML de correo electrónico.
 */
@Service
public class EmailTemplateService {

    private static final Logger log = LoggerFactory.getLogger(EmailTemplateService.class);
    private final ResourceLoader resourceLoader;
    private final Map<String, String> cachePlantillas = new ConcurrentHashMap<>();

    public EmailTemplateService(ResourceLoader resourceLoader) {
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader no puede ser nulo");
    }

    /**
     * Renderiza la plantilla HTML especificada reemplazando los placeholders {{clave}}.
     *
     * @param rutaPlantilla Ruta en classpath (ej. "templates/email/recuperacion-password.html")
     * @param variables     Mapa de variables a interpolar
     * @return Contenido HTML renderizado
     */
    public String renderizar(String rutaPlantilla, Map<String, String> variables) {
        String plantilla = cachePlantillas.computeIfAbsent(rutaPlantilla, this::cargarDesdeClasspath);
        String resultado = plantilla;
        if (variables != null) {
            for (Map.Entry<String, String> entry : variables.entrySet()) {
                String placeholder = "{{" + entry.getKey() + "}}";
                String valor = entry.getValue() != null ? entry.getValue() : "";
                resultado = resultado.replace(placeholder, valor);
            }
        }
        return resultado;
    }

    private String cargarDesdeClasspath(String ruta) {
        Resource resource = resourceLoader.getResource("classpath:" + ruta);
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Error al cargar la plantilla de correo desde classpath: {}", ruta, e);
            throw new IllegalStateException("No se pudo cargar la plantilla de correo: " + ruta, e);
        }
    }
}

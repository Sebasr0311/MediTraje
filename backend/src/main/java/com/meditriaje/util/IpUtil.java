package com.meditriaje.util;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Utilidad para la extracción segura de direcciones IP en peticiones HTTP.
 */
public final class IpUtil {

    private IpUtil() {}

    /**
     * Extrae la dirección IP cliente considerando proxies inversos (ej. Render, Vercel).
     */
    public static String extraerIp(HttpServletRequest request) {
        if (request == null) {
            return "127.0.0.1";
        }

        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        String remoteAddr = request.getRemoteAddr();
        return (remoteAddr != null && !remoteAddr.isBlank()) ? remoteAddr : "127.0.0.1";
    }
}

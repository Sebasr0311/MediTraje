package com.meditriaje.security;

import com.meditriaje.exception.LimitePeticionesExcedidoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Limitador de tasa (Rate Limiter) en memoria por dirección IP para endpoints públicos de QR (SEC-002).
 * Aplica ventana deslizante de tiempo para prevenir ataques de fuerza bruta o saturación.
 */
@Component
public class QrRateLimiter {

    private final int maxPeticionesPorVentana;
    private final long ventanaSegundos;
    private final Clock clock;
    private final Map<String, Deque<Long>> historialPorIp = new ConcurrentHashMap<>();

    public QrRateLimiter() {
        this(15, 60, Clock.systemUTC());
    }

    @Autowired
    public QrRateLimiter(
            @Value("${meditriaje.security.qr.rate-limit-per-window:15}") int maxPeticionesPorVentana,
            @Value("${meditriaje.security.qr.rate-limit-window-seconds:60}") long ventanaSegundos
    ) {
        this(maxPeticionesPorVentana, ventanaSegundos, Clock.systemUTC());
    }

    public QrRateLimiter(int maxPeticionesPorVentana, long ventanaSegundos, Clock clock) {
        this.maxPeticionesPorVentana = Math.max(1, maxPeticionesPorVentana);
        this.ventanaSegundos = Math.max(1, ventanaSegundos);
        this.clock = (clock != null) ? clock : Clock.systemUTC();
    }

    /**
     * Valida si la dirección IP excede el límite de peticiones permitido.
     * Si lo excede, lanza {@link LimitePeticionesExcedidoException}.
     * Si no lo excede, registra el acceso en la ventana temporal.
     *
     * @param ip Dirección IP del cliente
     */
    public void validarLimite(String ip) {
        String key = (ip != null && !ip.isBlank()) ? ip.trim() : "unknown";
        long ahora = clock.millis();
        long limiteInferior = ahora - (ventanaSegundos * 1000L);

        historialPorIp.compute(key, (k, cola) -> {
            if (cola == null) {
                cola = new ArrayDeque<>();
            }
            // Eliminar marcas temporales fuera de la ventana
            while (!cola.isEmpty() && cola.peekFirst() < limiteInferior) {
                cola.pollFirst();
            }

            if (cola.size() >= maxPeticionesPorVentana) {
                throw new LimitePeticionesExcedidoException(
                        "Ha superado el límite de solicitudes permitidas para la verificación de emergencia. Intente de nuevo más tarde."
                );
            }

            cola.addLast(ahora);
            return cola;
        });
    }

    /**
     * Limpia el historial de accesos (útil para pruebas unitarias).
     */
    public void reset() {
        historialPorIp.clear();
    }
}

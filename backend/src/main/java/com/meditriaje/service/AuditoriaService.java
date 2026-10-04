package com.meditriaje.service;

import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.repository.AuditoriaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Servicio de auditoría del sistema (ADR-011, HU-11).
 *
 * <p><strong>Principios de diseño:</strong></p>
 * <ul>
 *   <li>Operación exclusivamente insert-only.</li>
 *   <li>Sin datos clínicos ni secretos en el modelo ni en los logs.</li>
 *   <li>Registra eventos de autenticación, acceso a historia, citas, atención médica y administración.</li>
 * </ul>
 */
@Service
public class AuditoriaService {

    private static final Logger log = LoggerFactory.getLogger(AuditoriaService.class);

    private final AuditoriaRepository auditoriaRepository;

    public AuditoriaService(AuditoriaRepository auditoriaRepository) {
        this.auditoriaRepository = Objects.requireNonNull(auditoriaRepository, "AuditoriaRepository no puede ser nulo");
    }

    /**
     * Registra un evento en la bitácora de auditoría.
     *
     * @param usuarioId        Identificador interno del usuario (nullable para eventos anónimos o intentos fallidos).
     * @param accion           Acción auditable ejecutada.
     * @param tipoRecurso      Nombre del módulo o recurso auditado (ej. "USUARIO", "CITA", "ATENCION").
     * @param recursoPublicId  Identificador público UUID del recurso (nullable).
     * @param resultado        Resultado de la operación (EXITO, FALLO, BLOQUEADO).
     * @param ipOrigen         Dirección IP del cliente.
     */
    public void registrarEvento(
            Long usuarioId,
            AccionAuditable accion,
            String tipoRecurso,
            String recursoPublicId,
            ResultadoAuditoria resultado,
            String ipOrigen
    ) {
        EventoAuditoria evento = new EventoAuditoria(
                usuarioId,
                accion,
                tipoRecurso,
                recursoPublicId,
                resultado,
                ipOrigen
        );

        auditoriaRepository.registrar(evento);
        log.info("Evento auditado: accion={}, tipoRecurso={}, resultado={}", accion, tipoRecurso, resultado);
    }

    /**
     * Sobrecarga para eventos en los que no existe o aún no se conoce el usuario (ej. fallo de autenticación).
     */
    public void registrarEvento(
            AccionAuditable accion,
            String tipoRecurso,
            String recursoPublicId,
            ResultadoAuditoria resultado,
            String ipOrigen
    ) {
        registrarEvento(null, accion, tipoRecurso, recursoPublicId, resultado, ipOrigen);
    }

    /**
     * Registra un evento de auditoría encapsulado en el record inmutable {@link EventoAuditoria} (ADR-011).
     */
    public void auditar(EventoAuditoria evento) {
        Objects.requireNonNull(evento, "El evento de auditoria no puede ser nulo");
        auditoriaRepository.registrar(evento);
        log.info("Evento auditado: accion={}, tipoRecurso={}, resultado={}",
                evento.accion(), evento.tipoRecurso(), evento.resultado());
    }
}

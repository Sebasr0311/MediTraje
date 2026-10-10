package com.meditriaje.service;

import com.meditriaje.dto.hospital.CensoCamasResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.dto.operational.*;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Servicio de Analítica Hospitalaria, Alertas Operativas y Seguimiento QR Seguro (Fase O, ADR-027, ADR-028).
 */
@Service
public class OperationalAnalyticsService {

    private final AlertaOperativaRepository alertaRepository;
    private final SeguimientoQrRepository seguimientoQrRepository;
    private final HospitalService hospitalService;
    private final SedeRepository sedeRepository;
    private final EpisodioAtencionRepository episodioRepository;
    private final ValoracionTriajeRepository triajeRepository;
    private final IdentidadProvisionalRepository identidadRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public OperationalAnalyticsService(
            AlertaOperativaRepository alertaRepository,
            SeguimientoQrRepository seguimientoQrRepository,
            HospitalService hospitalService,
            SedeRepository sedeRepository,
            EpisodioAtencionRepository episodioRepository,
            ValoracionTriajeRepository triajeRepository,
            IdentidadProvisionalRepository identidadRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.alertaRepository = Objects.requireNonNull(alertaRepository);
        this.seguimientoQrRepository = Objects.requireNonNull(seguimientoQrRepository);
        this.hospitalService = Objects.requireNonNull(hospitalService);
        this.sedeRepository = Objects.requireNonNull(sedeRepository);
        this.episodioRepository = Objects.requireNonNull(episodioRepository);
        this.triajeRepository = Objects.requireNonNull(triajeRepository);
        this.identidadRepository = Objects.requireNonNull(identidadRepository);
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository);
        this.auditoriaService = Objects.requireNonNull(auditoriaService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * O01: Construye el panel analítico hospitalario en tiempo real para una sede.
     */
    @Transactional
    public DashboardHospitalarioResponse obtenerDashboardHospitalario(
            String sedePublicId,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Sede sede = (sedePublicId != null && !sedePublicId.isBlank())
                ? sedeRepository.buscarPorPublicId(sedePublicId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Sede no encontrada: " + sedePublicId))
                : sedeRepository.listar(0, 1, null, "ACTIVO").stream().findFirst()
                    .orElseThrow(() -> new RecursoNoEncontradoException("No hay sedes activas registradas en el sistema."));

        // Métricas de camas
        CensoCamasResponse censo = hospitalService.obtenerCensoCamas(sede.publicId());
        int totalCamas = censo.totalCamas();
        int ocupadas = censo.ocupadas();
        int disponibles = censo.disponibles();
        int limpieza = censo.enLimpieza();
        int mantenimiento = censo.enMantenimiento();
        double tasaOcupacion = censo.tasaOcupacionPorcentaje();

        // Episodios activos de urgencias obtenidos directamente de la cola con triaje y tiempos
        List<ItemColaUrgenciaResponse> cola = episodioRepository.listarColaUrgenciaPorSede(sede.id(), null);

        Map<String, Long> episodiosPorTriaje = new HashMap<>();
        Map<String, List<Long>> minutosEsperaPorTriaje = new HashMap<>();

        for (String n : List.of("I", "II", "III", "IV", "V", "PENDIENTE_VALORACION")) {
            episodiosPorTriaje.put(n, 0L);
            minutosEsperaPorTriaje.put(n, new ArrayList<>());
        }

        for (ItemColaUrgenciaResponse item : cola) {
            String nivel = item.nivelTriaje() != null ? item.nivelTriaje() : "PENDIENTE_VALORACION";
            episodiosPorTriaje.put(nivel, episodiosPorTriaje.getOrDefault(nivel, 0L) + 1);
            minutosEsperaPorTriaje.computeIfAbsent(nivel, k -> new ArrayList<>()).add(item.minutosEspera());
        }

        Map<String, Double> tiemposPromedioEspera = new HashMap<>();
        minutosEsperaPorTriaje.forEach((k, v) -> {
            double avg = v.isEmpty() ? 0.0 : v.stream().mapToLong(Long::longValue).average().orElse(0.0);
            tiemposPromedioEspera.put(k, Math.round(avg * 10.0) / 10.0);
        });

        // O02: Evaluar y disparar alertas automáticas de saturación
        evaluarAlertasAutomaticas(sede, tasaOcupacion, episodiosPorTriaje, tiemposPromedioEspera);

        List<AlertaOperativaResponse> alertasActivas = alertaRepository.listarPorSede(sede.id(), "ACTIVA");

        return new DashboardHospitalarioResponse(
                sede.publicId(),
                sede.nombre(),
                totalCamas,
                disponibles,
                ocupadas,
                limpieza,
                mantenimiento,
                tasaOcupacion,
                cola.size(),
                episodiosPorTriaje,
                tiemposPromedioEspera,
                alertasActivas
        );
    }

    private void evaluarAlertasAutomaticas(
            Sede sede,
            double tasaOcupacion,
            Map<String, Long> episodiosPorTriaje,
            Map<String, Double> tiemposPromedio
    ) {
        // Alerta de camas si ocupación >= 85%
        if (tasaOcupacion >= 85.0) {
            String msg = "Saturación hospitalaria crítica: Ocupación de camas al " + tasaOcupacion + "% en " + sede.nombre();
            crearAlertaSiNoExiste(sede.id(), "SATURACION_CAMAS", "CRITICA", msg);
        }

        // Alerta de demora Triaje II > 30 min (Res 5596/2015)
        Double esperaT2 = tiemposPromedio.get("II");
        if (esperaT2 != null && esperaT2 > 30.0 && episodiosPorTriaje.getOrDefault("II", 0L) > 0) {
            String msg = "Demora en Triaje II: Tiempo promedio de espera (" + esperaT2 + " min) supera el estándar normativo de 30 min.";
            crearAlertaSiNoExiste(sede.id(), "DEMORA_TRIAJE_II", "ALTA", msg);
        }
    }

    private void crearAlertaSiNoExiste(Long sedeId, String tipo, String severidad, String mensaje) {
        List<AlertaOperativaResponse> existentes = alertaRepository.listarPorSede(sedeId, "ACTIVA");
        boolean yaExiste = existentes.stream().anyMatch(a -> tipo.equals(a.tipoAlerta()));
        if (!yaExiste) {
            alertaRepository.guardar(new AlertaOperativa(
                    null,
                    UUID.randomUUID().toString(),
                    sedeId,
                    tipo,
                    severidad,
                    mensaje,
                    "ACTIVA",
                    null,
                    null,
                    null,
                    Instant.now(clock)
            ));
        }
    }

    /**
     * O02: Lista alertas operativas de una sede.
     */
    public List<AlertaOperativaResponse> listarAlertas(String sedePublicId, String estado) {
        Long sedeId = null;
        if (sedePublicId != null && !sedePublicId.isBlank()) {
            sedeId = sedeRepository.buscarPorPublicId(sedePublicId)
                    .map(Sede::id).orElse(null);
        }
        return alertaRepository.listarPorSede(sedeId, estado);
    }

    /**
     * O02: Reconoce y silencia auditadamente una alerta operativa.
     */
    @Transactional
    public void reconocerAlerta(
            String alertaPublicId,
            ReconocerAlertaRequest req,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        AlertaOperativa alerta = alertaRepository.buscarPorPublicId(alertaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Alerta no encontrada: " + alertaPublicId));

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Instant ahora = Instant.now(clock);
        alertaRepository.reconocerAlerta(alerta.id(), usuario.id(), ahora, req.motivo());

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ALERTA_OPERATIVA_RECONOCIDA,
                "ALERTA_OPERATIVA",
                alertaPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    /**
     * O03: Genera o recupera un token QR seguro de seguimiento intrahospitalario para manilla/cabecera.
     */
    @Transactional
    public QrSeguimientoResponse generarQrSeguimiento(
            String episodioPublicId,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        EpisodioAtencion ep = episodioRepository.buscarPorPublicId(episodioPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio no encontrado: " + episodioPublicId));

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Optional<SeguimientoIntrahospitalarioQr> existenteOpt = seguimientoQrRepository.buscarActivoPorEpisodioId(ep.id());
        SeguimientoIntrahospitalarioQr qr = existenteOpt.orElseGet(() -> {
            String token = "QROP-" + UUID.randomUUID().toString().substring(0, 18).toUpperCase();
            Instant expira = Instant.now(clock).plus(Duration.ofDays(7));
            return seguimientoQrRepository.guardar(new SeguimientoIntrahospitalarioQr(
                    null,
                    UUID.randomUUID().toString(),
                    ep.id(),
                    token,
                    "URGENCIAS / SALA DE OBSERVACION",
                    "ACTIVO",
                    Instant.now(clock),
                    expira
            ));
        });

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.QR_SEGUIMIENTO_GENERADO,
                "SEGUIMIENTO_INTRAHOSPITALARIO_QR",
                qr.codigoQrToken(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        String codIdentidad = identidadRepository.buscarPorEpisodioId(ep.id())
                .map(IdentidadProvisional::codigoProvisional).orElse("REGISTRADO");

        Optional<ValoracionTriaje> ultTriaje = triajeRepository.buscarUltimaPorEpisodioId(ep.id());

        return new QrSeguimientoResponse(
                qr.publicId(),
                ep.publicId(),
                codIdentidad,
                qr.codigoQrToken(),
                "/operational/tracking/" + qr.codigoQrToken(),
                qr.ubicacionActualTexto(),
                ep.estado(),
                ultTriaje.map(ValoracionTriaje::nivel).orElse("PENDIENTE"),
                qr.estado(),
                qr.creadoAt(),
                qr.expiraAt()
        );
    }

    /**
     * O03: Consulta la ubicación y estado básico mediante escaneo de QR (sin PHI médica).
     */
    public QrSeguimientoResponse consultarPorTokenQr(String token, String ipOrigen) {
        SeguimientoIntrahospitalarioQr qr = seguimientoQrRepository.buscarPorToken(token)
                .orElseThrow(() -> new RecursoNoEncontradoException("Token QR de seguimiento no válido o expirado."));

        EpisodioAtencion ep = episodioRepository.buscarPorId(qr.episodioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Episodio asociado no encontrado."));

        String codIdentidad = identidadRepository.buscarPorEpisodioId(ep.id())
                .map(IdentidadProvisional::codigoProvisional).orElse("PACIENTE_IDENTIFICADO");

        Optional<ValoracionTriaje> ultTriaje = triajeRepository.buscarUltimaPorEpisodioId(ep.id());

        auditoriaService.auditar(new EventoAuditoria(
                null,
                AccionAuditable.QR_SEGUIMIENTO_ESCANEADO,
                "SEGUIMIENTO_INTRAHOSPITALARIO_QR",
                token,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return new QrSeguimientoResponse(
                qr.publicId(),
                ep.publicId(),
                codIdentidad,
                qr.codigoQrToken(),
                "/operational/tracking/" + qr.codigoQrToken(),
                qr.ubicacionActualTexto(),
                ep.estado(),
                ultTriaje.map(ValoracionTriaje::nivel).orElse("PENDIENTE"),
                qr.estado(),
                qr.creadoAt(),
                qr.expiraAt()
        );
    }
}

package com.meditriaje.service;

import com.meditriaje.dto.report.DistribucionItemDto;
import com.meditriaje.dto.report.MetricasBreakGlassDto;
import com.meditriaje.dto.report.MetricasCitasDto;
import com.meditriaje.dto.report.MetricasFarmaciaDto;
import com.meditriaje.dto.report.MetricasTriajeDto;
import com.meditriaje.dto.report.ResumenOperativoResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.ReporteRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Servicio de métricas e indicadores operativos hospitalarios (F2.6, RF-30, ADR-018).
 * Asegura estricta segregación de datos clínicos (ADR-007): el personal administrativo
 * accede únicamente a resúmenes matemáticos agregados sin datos de salud individuales.
 */
@Service
public class ReporteService {

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");

    private final ReporteRepository reporteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public ReporteService(
            ReporteRepository reporteRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.reporteRepository = Objects.requireNonNull(reporteRepository, "reporteRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Genera el resumen operativo global consolidado (citas, triaje, farmacia y break-glass).
     */
    @Transactional(readOnly = true)
    public ResumenOperativoResponse obtenerResumenOperativo(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String adminPublicId,
            String ipOrigen
    ) {
        validarRangoFechas(fechaDesde, fechaHasta);

        Instant desde = fechaDesde != null ? fechaDesde.atStartOfDay(ZONE_BOGOTA).toInstant() : null;
        Instant hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX).atZone(ZONE_BOGOTA).toInstant() : null;
        Instant ahora = Instant.now(clock);

        MetricasCitasDto metricasCitas = calcularMetricasCitas(desde, hasta);
        MetricasTriajeDto metricasTriaje = calcularMetricasTriaje(desde, hasta);
        MetricasFarmaciaDto metricasFarmacia = calcularMetricasFarmacia(desde, hasta);
        MetricasBreakGlassDto metricasBreakGlass = calcularMetricasBreakGlass(desde, hasta, ahora);

        auditarConsultaReporte(adminPublicId, "RESUMEN_OPERATIVO", ipOrigen);

        return new ResumenOperativoResponse(
                desde,
                hasta,
                metricasCitas,
                metricasTriaje,
                metricasFarmacia,
                metricasBreakGlass,
                ahora
        );
    }

    /**
     * Genera el reporte específico de agendamiento y estado de citas.
     */
    @Transactional(readOnly = true)
    public MetricasCitasDto obtenerReporteCitas(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String adminPublicId,
            String ipOrigen
    ) {
        validarRangoFechas(fechaDesde, fechaHasta);

        Instant desde = fechaDesde != null ? fechaDesde.atStartOfDay(ZONE_BOGOTA).toInstant() : null;
        Instant hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX).atZone(ZONE_BOGOTA).toInstant() : null;

        MetricasCitasDto metricas = calcularMetricasCitas(desde, hasta);
        auditarConsultaReporte(adminPublicId, "CITAS", ipOrigen);
        return metricas;
    }

    /**
     * Genera el reporte específico de triaje clínico y cortes de emergencia.
     */
    @Transactional(readOnly = true)
    public MetricasTriajeDto obtenerReporteTriaje(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String adminPublicId,
            String ipOrigen
    ) {
        validarRangoFechas(fechaDesde, fechaHasta);

        Instant desde = fechaDesde != null ? fechaDesde.atStartOfDay(ZONE_BOGOTA).toInstant() : null;
        Instant hasta = fechaHasta != null ? fechaHasta.atTime(LocalTime.MAX).atZone(ZONE_BOGOTA).toInstant() : null;

        MetricasTriajeDto metricas = calcularMetricasTriaje(desde, hasta);
        auditarConsultaReporte(adminPublicId, "TRIAJE", ipOrigen);
        return metricas;
    }

    // -------------------------------------------------------------------------
    // Métodos auxiliares de cálculo
    // -------------------------------------------------------------------------

    private MetricasCitasDto calcularMetricasCitas(Instant desde, Instant hasta) {
        Map<String, Long> estados = reporteRepository.contarCitasPorEstado(desde, hasta);

        long programadas = estados.getOrDefault("PROGRAMADA", 0L);
        long confirmadas = estados.getOrDefault("CONFIRMADA", 0L);
        long atendidas = estados.getOrDefault("ATENDIDA", 0L);
        long canceladas = estados.getOrDefault("CANCELADA", 0L);
        long noAsistio = estados.getOrDefault("NO_ASISTIO", 0L);
        long reprogramadas = estados.getOrDefault("REPROGRAMADA", 0L);

        long total = programadas + confirmadas + atendidas + canceladas + noAsistio + reprogramadas;
        double tasaCumplimiento = total > 0 ? redondear((atendidas * 100.0) / total) : 0.0;
        double tasaCancelacion = total > 0 ? redondear(((canceladas + noAsistio) * 100.0) / total) : 0.0;

        List<DistribucionItemDto> porEspecialidad = reporteRepository.obtenerCitasPorEspecialidad(desde, hasta);
        List<DistribucionItemDto> porSede = reporteRepository.obtenerCitasPorSede(desde, hasta);

        return new MetricasCitasDto(
                total,
                programadas,
                confirmadas,
                atendidas,
                canceladas,
                noAsistio,
                reprogramadas,
                tasaCumplimiento,
                tasaCancelacion,
                porEspecialidad,
                porSede
        );
    }

    private MetricasTriajeDto calcularMetricasTriaje(Instant desde, Instant hasta) {
        Map<String, Long> niveles = reporteRepository.contarTriajesPorNivel(desde, hasta);

        long n1 = niveles.getOrDefault("I", 0L);
        long n2 = niveles.getOrDefault("II", 0L);
        long n3 = niveles.getOrDefault("III", 0L);
        long n4 = niveles.getOrDefault("IV", 0L);
        long n5 = niveles.getOrDefault("V", 0L);

        long total = n1 + n2 + n3 + n4 + n5;
        long emergencias = reporteRepository.contarTriajesEmergencia(desde, hasta);
        double tasaEmergencia = total > 0 ? redondear((emergencias * 100.0) / total) : 0.0;

        List<DistribucionItemDto> porRuta = reporteRepository.obtenerTriajesPorRuta(desde, hasta);

        return new MetricasTriajeDto(
                total,
                n1,
                n2,
                n3,
                n4,
                n5,
                emergencias,
                tasaEmergencia,
                porRuta
        );
    }

    private MetricasFarmaciaDto calcularMetricasFarmacia(Instant desde, Instant hasta) {
        long totalRecetas = reporteRepository.contarRecetasEmitidas(desde, hasta);
        Map<String, Long> estadosDisp = reporteRepository.contarRecetasPorEstadoDispensacion(desde, hasta);

        long pendientes = estadosDisp.getOrDefault("PENDIENTE", 0L);
        long parciales = estadosDisp.getOrDefault("DISPENSADA_PARCIAL", 0L);
        long totales = estadosDisp.getOrDefault("DISPENSADA_TOTAL", 0L);
        long unidades = reporteRepository.contarUnidadesDispensadas(desde, hasta);

        return new MetricasFarmaciaDto(
                totalRecetas,
                pendientes,
                parciales,
                totales,
                unidades
        );
    }

    private MetricasBreakGlassDto calcularMetricasBreakGlass(Instant desde, Instant hasta, Instant ahora) {
        long total = reporteRepository.contarActivacionesBreakGlass(desde, hasta);
        long activas = reporteRepository.contarBreakGlassActivos(ahora);
        List<DistribucionItemDto> porEsp = reporteRepository.obtenerBreakGlassPorEspecialidad(desde, hasta);

        return new MetricasBreakGlassDto(total, activas, porEsp);
    }

    private void validarRangoFechas(LocalDate fechaDesde, LocalDate fechaHasta) {
        if (fechaDesde != null && fechaHasta != null && fechaDesde.isAfter(fechaHasta)) {
            throw new DatosInvalidosException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }

    private void auditarConsultaReporte(String adminPublicId, String subtipo, String ipOrigen) {
        Long usuarioId = null;
        if (adminPublicId != null) {
            usuarioId = usuarioRepository.buscarPorPublicId(adminPublicId)
                    .map(Usuario::id)
                    .orElse(null);
        }

        auditoriaService.auditar(new EventoAuditoria(
                usuarioId,
                AccionAuditable.CONSULTA_REPORTE_ADMINISTRATIVO,
                "REPORTE_OPERATIVO",
                subtipo,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    private double redondear(double valor) {
        return Math.round(valor * 100.0) / 100.0;
    }
}

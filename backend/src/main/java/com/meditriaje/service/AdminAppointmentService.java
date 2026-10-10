package com.meditriaje.service;

import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.repository.CitaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Servicio administrativo para la consulta, calendario semanal y exportación de citas médicas (ADR-003, ADR-006, RF-26).
 * Toda operación temporal se calcula bajo la zona horaria institucional 'America/Bogota' (ADR-005).
 */
@Service
@Transactional(readOnly = true)
public class AdminAppointmentService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("hh:mm a", new Locale("es", "CO"));

    private final CitaRepository citaRepository;
    private final Clock clock;

    @Autowired
    public AdminAppointmentService(CitaRepository citaRepository) {
        this(citaRepository, Clock.systemUTC());
    }

    public AdminAppointmentService(CitaRepository citaRepository, Clock clock) {
        this.citaRepository = Objects.requireNonNull(citaRepository, "CitaRepository no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "Clock no puede ser nulo");
    }

    /**
     * Consulta paginada de citas para el visor y calendario administrativo.
     */
    public PaginatedResponse<AdminCitaDetalleResponse> listarCitas(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new DatosInvalidosException("El número de página no puede ser menor a 0.");
        }
        if (size < 1 || size > 500) {
            throw new DatosInvalidosException("El tamaño de página debe estar entre 1 y 500.");
        }

        Instant desdeInstant = null;
        Instant hastaInstant = null;

        if (fechaDesde != null) {
            desdeInstant = fechaDesde.atStartOfDay(ZONE_BOGOTA).toInstant();
        }
        if (fechaHasta != null) {
            hastaInstant = fechaHasta.atTime(23, 59, 59, 999_999_999).atZone(ZONE_BOGOTA).toInstant();
        }

        if (desdeInstant != null && hastaInstant != null && desdeInstant.isAfter(hastaInstant)) {
            throw new DatosInvalidosException("La fecha inicial no puede ser posterior a la fecha final.");
        }

        List<AdminCitaDetalleResponse> content = citaRepository.listarCitasAdmin(
                desdeInstant,
                hastaInstant,
                profesionalPublicId,
                especialidadPublicId,
                estado,
                page,
                size
        );

        int total = citaRepository.contarCitasAdmin(
                desdeInstant,
                hastaInstant,
                profesionalPublicId,
                especialidadPublicId,
                estado
        );

        return PaginatedResponse.of(content, page, size, (long) total);
    }

    /**
     * Genera la plantilla de exportación CSV/Excel compatible nativamente con Microsoft Excel.
     * Incorpora BOM UTF-8 y delimitador punto y coma (;) estándar para entornos hispanohablantes.
     */
    public byte[] exportarCitasExcelCsv(
            LocalDate fechaDesde,
            LocalDate fechaHasta,
            String profesionalPublicId,
            String especialidadPublicId,
            String estado
    ) {
        Instant desdeInstant = null;
        Instant hastaInstant = null;

        if (fechaDesde != null) {
            desdeInstant = fechaDesde.atStartOfDay(ZONE_BOGOTA).toInstant();
        }
        if (fechaHasta != null) {
            hastaInstant = fechaHasta.atTime(23, 59, 59, 999_999_999).atZone(ZONE_BOGOTA).toInstant();
        }

        // Obtener hasta 10,000 registros para el reporte analítico
        List<AdminCitaDetalleResponse> citas = citaRepository.listarCitasAdmin(
                desdeInstant,
                hastaInstant,
                profesionalPublicId,
                especialidadPublicId,
                estado,
                0,
                10000
        );

        StringBuilder sb = new StringBuilder();
        // BOM UTF-8 para apertura directa en Microsoft Excel
        sb.append('\uFEFF');

        // Encabezados
        sb.append("Código Cita;Fecha Cita;Hora Inicio;Hora Fin;Modalidad;Estado;Paciente;Tipo Documento;Número Documento;Teléfono;Email;Médico Asignado;Registro Médico;Especialidad;Sede;Ciudad;Motivo Consulta;Nivel Triaje;Fecha Solicitud\r\n");

        for (AdminCitaDetalleResponse c : citas) {
            String fechaCita = c.fechaHoraInicio() != null ? DATE_FMT.format(c.fechaHoraInicio().atZone(ZONE_BOGOTA)) : "";
            String horaInicio = c.fechaHoraInicio() != null ? TIME_FMT.format(c.fechaHoraInicio().atZone(ZONE_BOGOTA)) : "";
            String horaFin = c.fechaHoraFin() != null ? TIME_FMT.format(c.fechaHoraFin().atZone(ZONE_BOGOTA)) : "";
            String fechaCreacion = c.createdAt() != null ? DATE_FMT.format(c.createdAt().atZone(ZONE_BOGOTA)) : "";

            sb.append(limpiarCsv(c.citaPublicId())).append(';')
              .append(limpiarCsv(fechaCita)).append(';')
              .append(limpiarCsv(horaInicio)).append(';')
              .append(limpiarCsv(horaFin)).append(';')
              .append(limpiarCsv(c.modalidad())).append(';')
              .append(limpiarCsv(c.estado())).append(';')
              .append(limpiarCsv(c.pacienteNombre())).append(';')
              .append(limpiarCsv(c.pacienteDocumentoTipo())).append(';')
              .append(limpiarCsv(c.pacienteDocumentoNumero())).append(';')
              .append(limpiarCsv(c.pacienteTelefono())).append(';')
              .append(limpiarCsv(c.pacienteEmail())).append(';')
              .append(limpiarCsv(c.profesionalNombre())).append(';')
              .append(limpiarCsv(c.registroMedico())).append(';')
              .append(limpiarCsv(c.especialidadNombre())).append(';')
              .append(limpiarCsv(c.sedeNombre())).append(';')
              .append(limpiarCsv(c.sedeCiudad())).append(';')
              .append(limpiarCsv(c.motivoConsulta())).append(';')
              .append(limpiarCsv(c.triajeNivel())).append(';')
              .append(limpiarCsv(fechaCreacion)).append("\r\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String limpiarCsv(String valor) {
        if (valor == null) return "";
        String s = valor.replace("\"", "\"\"");
        // Prevención de inyección CSV si inicia con fórmulas (=, +, -, @, \t, \r) (SEC-001)
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@") || s.startsWith("\t") || s.startsWith("\r")) {
            s = "'" + s;
        }
        return "\"" + s + "\"";
    }
}

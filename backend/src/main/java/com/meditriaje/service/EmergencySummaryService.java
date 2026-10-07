package com.meditriaje.service;

import com.meditriaje.dto.clinical.AtencionResponse;
import com.meditriaje.dto.emergency.ConsultarResumenRequest;
import com.meditriaje.dto.emergency.summary.AlergiaEmergenciaDto;
import com.meditriaje.dto.emergency.summary.AtencionResumenDto;
import com.meditriaje.dto.emergency.summary.MedicamentoActivoDto;
import com.meditriaje.dto.emergency.summary.PacienteEmergenciaDto;
import com.meditriaje.dto.emergency.summary.ResumenSaludResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.exception.CredencialesInvalidasException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccesoTemporalQr;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Alergia;
import com.meditriaje.model.EstadoAccesoQr;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AccesoTemporalQrRepository;
import com.meditriaje.repository.AlergiaRepository;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.RecetaRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.security.TokenHashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Servicio de agregación y consulta controlada del resumen clínico de salud para emergencias (ADR-010, §5.17, §5.18).
 */
@Service
public class EmergencySummaryService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    public static final String ADVERTENCIA_LEGAL = "El presente resumen clinico es un documento temporal de orientacion medica generado por el paciente bajo acceso controlado. No sustituye la historia clinica integral.";

    private final AccesoTemporalQrRepository accesoTemporalQrRepository;
    private final PacienteRepository pacienteRepository;
    private final UsuarioRepository usuarioRepository;
    private final AlergiaRepository alergiaRepository;
    private final RecetaRepository recetaRepository;
    private final AtencionRepository atencionRepository;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    @Autowired
    public EmergencySummaryService(
            AccesoTemporalQrRepository accesoTemporalQrRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            AlergiaRepository alergiaRepository,
            RecetaRepository recetaRepository,
            AtencionRepository atencionRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder
    ) {
        this(
                accesoTemporalQrRepository,
                pacienteRepository,
                usuarioRepository,
                alergiaRepository,
                recetaRepository,
                atencionRepository,
                auditoriaService,
                passwordEncoder,
                Clock.systemUTC()
        );
    }

    public EmergencySummaryService(
            AccesoTemporalQrRepository accesoTemporalQrRepository,
            PacienteRepository pacienteRepository,
            UsuarioRepository usuarioRepository,
            AlergiaRepository alergiaRepository,
            RecetaRepository recetaRepository,
            AtencionRepository atencionRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder,
            Clock clock
    ) {
        this.accesoTemporalQrRepository = Objects.requireNonNull(accesoTemporalQrRepository, "accesoTemporalQrRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.alergiaRepository = Objects.requireNonNull(alergiaRepository, "alergiaRepository no puede ser nulo");
        this.recetaRepository = Objects.requireNonNull(recetaRepository, "recetaRepository no puede ser nulo");
        this.atencionRepository = Objects.requireNonNull(atencionRepository, "atencionRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "passwordEncoder no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Consulta pública protegida del resumen clínico de salud mediante token criptográfico y PIN opcional.
     * Valida vigencia, consume un acceso atómicamente y audita el evento sin exponer datos clínicos en bitácora.
     */
    @Transactional
    public ResumenSaludResponse consultarResumenPorToken(String token, ConsultarResumenRequest request, String ipOrigen) {
        if (token == null || token.isBlank()) {
            throw new RecursoNoEncontradoException("Acceso de emergencia no valido o inexistente.");
        }

        String tokenHash = TokenHashUtil.hash(token.trim());
        AccesoTemporalQr acceso = accesoTemporalQrRepository.buscarPorTokenHash(tokenHash)
                .orElseThrow(() -> new RecursoNoEncontradoException("Acceso de emergencia no valido o inexistente."));

        Instant now = Instant.now(clock);
        EstadoAccesoQr estado = acceso.resolverEstado(now);

        if (estado == EstadoAccesoQr.REVOCADO) {
            throw new DatosInvalidosException("El acceso QR ha sido revocado por el paciente.");
        }
        if (estado == EstadoAccesoQr.EXPIRADO) {
            throw new DatosInvalidosException("El acceso QR ha expirado.");
        }
        if (estado == EstadoAccesoQr.AGOTADO) {
            throw new DatosInvalidosException("El acceso QR ha superado el limite maximo de lecturas permitidas.");
        }

        // Validación de PIN de seguridad si el token lo requiere
        if (acceso.requierePin()) {
            String pinProporcionado = (request != null && request.pin() != null) ? request.pin().trim() : "";
            if (pinProporcionado.isBlank() || !passwordEncoder.matches(pinProporcionado, acceso.pinHash())) {
                throw new CredencialesInvalidasException("El PIN de seguridad proporcionado es incorrecto.");
            }
        }

        // Registro atómico de acceso (decrementa cupo disponible en BD)
        int actualizados = accesoTemporalQrRepository.registrarAcceso(acceso.id(), now);
        if (actualizados == 0) {
            throw new DatosInvalidosException("El acceso QR ya no se encuentra disponible.");
        }

        Paciente paciente = pacienteRepository.buscarPorId(acceso.pacienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

        Usuario usuario = usuarioRepository.buscarPorId(paciente.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        // Auditoría obligatoria inmutable (ADR-010, ADR-011)
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.ACCESO_EMERGENCIA_QR,
                "RESUMEN_SALUD",
                acceso.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        // 1. Datos básicos del paciente
        LocalDate fechaHoy = LocalDate.ofInstant(now, ZONE_BOGOTA);
        int edad = paciente.fechaNacimiento() != null
                ? Period.between(paciente.fechaNacimiento(), fechaHoy).getYears()
                : 0;

        String telefono = acceso.incluirContacto() ? paciente.telefono() : null;
        String email = acceso.incluirContacto() ? usuario.email() : null;

        PacienteEmergenciaDto pacienteDto = new PacienteEmergenciaDto(
                paciente.nombres() + " " + paciente.apellidos(),
                paciente.tipoDocumento(),
                paciente.numeroDocumento(),
                paciente.fechaNacimiento(),
                edad,
                telefono,
                email
        );

        // 2. Alergias registradas (si el alcance lo autoriza)
        List<AlergiaEmergenciaDto> alergiasList = new ArrayList<>();
        if (acceso.incluirAlergias()) {
            List<Alergia> alergias = alergiaRepository.listarPorPacienteId(paciente.id());
            for (Alergia a : alergias) {
                alergiasList.add(new AlergiaEmergenciaDto(a.sustancia(), a.reaccion(), a.severidad(), a.origen()));
            }
        }

        // 3. Medicamentos activos de recetas vigentes (si el alcance lo autoriza)
        List<MedicamentoActivoDto> medicamentosList = new ArrayList<>();
        if (acceso.incluirMedicamentos()) {
            List<RecetaResponse> recetas = recetaRepository.listarPorPacienteId(paciente.id(), 0, 50);
            for (RecetaResponse r : recetas) {
                if (r.createdAt() != null && r.createdAt().plus(r.vigenciaDias(), ChronoUnit.DAYS).isAfter(now)) {
                    if (r.detalles() != null) {
                        for (var d : r.detalles()) {
                            medicamentosList.add(new MedicamentoActivoDto(
                                    d.nombreComercial(),
                                    d.principioActivo(),
                                    d.presentacion(),
                                    d.concentracion(),
                                    d.dosis(),
                                    d.frecuencia(),
                                    d.duracionDias(),
                                    d.cantidad(),
                                    d.indicaciones(),
                                    r.createdAt(),
                                    r.vigenciaDias()
                            ));
                        }
                    }
                }
            }
        }

        // 4. Antecedentes / Atenciones clínicas recientes (si el alcance lo autoriza)
        List<AtencionResumenDto> atencionesList = new ArrayList<>();
        if (acceso.incluirAtenciones()) {
            List<AtencionResponse> atenciones = atencionRepository.listarHistoriaPaciente(paciente.id(), 0, 5);
            for (AtencionResponse at : atenciones) {
                Instant fechaAtencion = at.fechaCierre() != null ? at.fechaCierre() : at.createdAt();
                atencionesList.add(new AtencionResumenDto(
                        fechaAtencion,
                        at.especialidadNombre(),
                        at.diagnosticoCodigo(),
                        at.diagnosticoDescripcion(),
                        at.motivoConsulta(),
                        at.indicaciones()
                ));
            }
        }

        return new ResumenSaludResponse(
                pacienteDto,
                alergiasList,
                medicamentosList,
                atencionesList,
                now,
                ADVERTENCIA_LEGAL
        );
    }
}

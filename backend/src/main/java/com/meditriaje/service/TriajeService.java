package com.meditriaje.service;

import com.meditriaje.dto.triage.CatalogoSintomaResponse;
import com.meditriaje.dto.triage.CrearTriajeRequest;
import com.meditriaje.dto.triage.SintomaItemRequest;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Triaje;
import com.meditriaje.model.TriajeSintoma;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.TriajeRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.triage.EntradaTriaje;
import com.meditriaje.triage.MotorTriaje;
import com.meditriaje.triage.ResultadoTriaje;
import com.meditriaje.triage.SintomaReportado;
import com.meditriaje.triage.TriajeMotorFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Servicio de negocio para la evaluación, persistencia y consulta de triajes clínicos (ADR-002, ADR-003, ADR-009, ADR-011, HU-02).
 */
@Service
@Transactional(readOnly = true)
public class TriajeService {

    private final TriajeRepository triajeRepository;
    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final TriajeMotorFactory triajeMotorFactory;
    private final AuditoriaService auditoriaService;
    private final AccesoClinicoService accesoClinicoService;

    public TriajeService(
            TriajeRepository triajeRepository,
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            TriajeMotorFactory triajeMotorFactory,
            AuditoriaService auditoriaService
    ) {
        this(triajeRepository, usuarioRepository, pacienteRepository, triajeMotorFactory, auditoriaService, null);
    }

    @Autowired
    public TriajeService(
            TriajeRepository triajeRepository,
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            TriajeMotorFactory triajeMotorFactory,
            AuditoriaService auditoriaService,
            AccesoClinicoService accesoClinicoService
    ) {
        this.triajeRepository = Objects.requireNonNull(triajeRepository, "TriajeRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "PacienteRepository no puede ser nulo");
        this.triajeMotorFactory = Objects.requireNonNull(triajeMotorFactory, "TriajeMotorFactory no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
        this.accesoClinicoService = accesoClinicoService;
    }

    /**
     * Evalúa la sintomatología con el motor determinista, persiste el triaje inmutable y genera auditoría (ADR-009, ADR-011, HU-02).
     */
    @Transactional
    public TriajeResponse evaluarYGuardarTriaje(CrearTriajeRequest request, String usuarioAutenticadoPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "El request de triaje no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        // 1. Obtener usuario autenticado
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        // 2. Obtener paciente vinculado (autenticado != autorizado, solo pacientes)
        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Solo pacientes registrados pueden realizar el triaje."));

        // 3. Validar ausencia de códigos duplicados
        if (request.sintomas() == null || request.sintomas().isEmpty()) {
            throw new DatosInvalidosException("Debe reportar al menos un síntoma.");
        }
        Set<String> vistos = new HashSet<>();
        for (SintomaItemRequest item : request.sintomas()) {
            if (item == null || item.codigo() == null || item.codigo().isBlank()) {
                throw new DatosInvalidosException("El código del síntoma es obligatorio.");
            }
            if (!vistos.add(item.codigo().trim())) {
                throw new DatosInvalidosException("Síntoma duplicado en la solicitud: " + item.codigo());
            }
        }

        // 4. Transformar a EntradaTriaje y evaluar con el motor
        List<SintomaReportado> reportados = request.sintomas().stream()
                .map(s -> new SintomaReportado(s.codigo().trim(), s.duracionHoras(), s.intensidad()))
                .toList();

        EntradaTriaje entrada = new EntradaTriaje(reportados);
        MotorTriaje motor = triajeMotorFactory.obtenerMotor();
        ResultadoTriaje resultado = motor.evaluar(entrada);

        // 5. Mapear códigos a IDs en BD y verificar existencia en el catálogo
        Map<String, Long> mapaSintomas = triajeRepository.obtenerMapaCodigoAIdSintomas();
        for (SintomaItemRequest s : request.sintomas()) {
            if (!mapaSintomas.containsKey(s.codigo().trim())) {
                throw new DatosInvalidosException("Síntoma desconocido: " + s.codigo());
            }
        }

        // 6. Persistir triaje inmutable
        String triajePublicId = UUID.randomUUID().toString();
        Triaje triaje = new Triaje(
                triajePublicId,
                paciente.id(),
                resultado.versionReglas(),
                resultado.nivel().name(),
                resultado.ruta().name(),
                resultado.emergencia(),
                request.observaciones()
        );
        Long triajeId = triajeRepository.guardarTriaje(triaje);

        // 7. Persistir síntomas asociados
        List<TriajeSintoma> sintomasEntidad = request.sintomas().stream()
                .map(s -> new TriajeSintoma(
                        triajeId,
                        mapaSintomas.get(s.codigo().trim()),
                        s.duracionHoras(),
                        s.intensidad()
                ))
                .toList();
        triajeRepository.guardarSintomas(triajeId, sintomasEntidad);

        // 8. Registro inmutable de auditoría (sin datos clínicos ni síntomas, ADR-011)
        if (resultado.emergencia()) {
            auditoriaService.auditar(new EventoAuditoria(
                    usuario.id(),
                    AccionAuditable.TRIAJE_EMERGENCIA,
                    "TRIAJE",
                    triajePublicId,
                    ResultadoAuditoria.EXITO,
                    ipOrigen
            ));
        }
        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.TRIAJE_REALIZADO,
                "TRIAJE",
                triajePublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        // 9. Retornar vista consolidada del triaje
        return triajeRepository.buscarPorPublicId(triajePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Triaje no encontrado tras guardado."));
    }

    /**
     * Consulta un triaje por su identificador público garantizando aislamiento estricto (ADR-002, ADR-009, HU-02).
     */
    public TriajeResponse obtenerPorPublicId(
            String publicId,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        Objects.requireNonNull(publicId, "El publicId no puede ser nulo");
        Objects.requireNonNull(usuarioAutenticadoPublicId, "El usuario público no puede ser nulo");

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        TriajeResponse triajeResponse = triajeRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Triaje no encontrado."));

        Set<String> roles = authorities != null
                ? authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet())
                : Set.of();

        if (accesoClinicoService != null && triajeResponse.pacientePublicId() != null) {
            accesoClinicoService.validarAccesoHistorialClinico(usuario.publicId(), triajeResponse.pacientePublicId(), authorities);
        } else {
            // Regla no negociable: El Administrador NO accede a contenido clínico (ADR-007, AGENTS.md)
            if (roles.contains("ROLE_ADMINISTRADOR")) {
                throw new AccesoNoAutorizadoException("El personal administrativo no tiene acceso a informacion clinica.");
            }

            // Aislamiento de paciente: un paciente solo puede ver su propio triaje
            if (roles.contains("ROLE_PACIENTE")) {
                Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                        .orElseThrow(() -> new AccesoNoAutorizadoException("Solo pacientes registrados pueden consultar su triaje."));

                if (!Objects.equals(triajeResponse.pacientePublicId(), paciente.publicId())) {
                    throw new AccesoNoAutorizadoException("No tiene autorizacion para acceder al triaje de otro paciente.");
                }
            } else if (!roles.contains("ROLE_PROFESIONAL")) {
                throw new AccesoNoAutorizadoException("No tiene autorizacion para acceder al triaje.");
            }
        }

        return triajeResponse;
    }

    /**
     * Retorna el catálogo de síntomas activos para la interfaz de triaje (ADR-009, HU-02).
     */
    public List<CatalogoSintomaResponse> obtenerCatalogoSintomas() {
        return triajeRepository.listarCatalogoSintomasActivos();
    }
}

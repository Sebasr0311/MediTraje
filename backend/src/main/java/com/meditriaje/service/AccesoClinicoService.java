package com.meditriaje.service;

import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.BreakGlassRepository;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Servicio centralizado de autorización clínica y verificación de relación asistencial
 * (ADR-007, ADR-008, HU-07, HU-09).
 *
 * <p>Reglas estrictas de acceso:</p>
 * <ul>
 *   <li><b>Paciente:</b> solo puede consultar su propia información clínica. Si intenta ver la de otro paciente &rarr; 403 Forbidden.</li>
 *   <li><b>Administrador:</b> NUNCA tiene acceso a contenido clínico (atenciones, historia, recetas, signos vitales) &rarr; 403 Forbidden.</li>
 *   <li><b>Profesional asistencial:</b> solo puede consultar la información de un paciente si existe una relación asistencial activa:
 *     <ul>
 *       <li>Cita activa futura (PROGRAMADA o CONFIRMADA) con el profesional, O</li>
 *       <li>Atención previa propia realizada dentro de la ventana temporal configurada (por defecto 12 meses).</li>
 *     </ul>
 *     Si no se cumple ninguna condición &rarr; 403 Forbidden.
 *   </li>
 * </ul>
 */
@Service
public class AccesoClinicoService {

    public static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    public static final String ROL_ADMIN = "ROLE_ADMINISTRADOR";
    public static final String ROL_PACIENTE = "ROLE_PACIENTE";
    public static final String ROL_PROFESIONAL = "ROLE_PROFESIONAL";

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final CitaRepository citaRepository;
    private final AtencionRepository atencionRepository;
    private final BreakGlassRepository breakGlassRepository;
    private final Clock clock;
    private final int ventanaMeses;

    @Autowired
    public AccesoClinicoService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            CitaRepository citaRepository,
            AtencionRepository atencionRepository,
            BreakGlassRepository breakGlassRepository,
            @Value("${meditriaje.clinical.access-window-months:12}") int ventanaMeses
    ) {
        this(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                citaRepository,
                atencionRepository,
                breakGlassRepository,
                Clock.system(ZONE_BOGOTA),
                ventanaMeses
        );
    }

    public AccesoClinicoService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            CitaRepository citaRepository,
            AtencionRepository atencionRepository,
            Clock clock,
            int ventanaMeses
    ) {
        this(
                usuarioRepository,
                pacienteRepository,
                profesionalRepository,
                citaRepository,
                atencionRepository,
                null,
                clock,
                ventanaMeses
        );
    }

    public AccesoClinicoService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            CitaRepository citaRepository,
            AtencionRepository atencionRepository,
            BreakGlassRepository breakGlassRepository,
            Clock clock,
            int ventanaMeses
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "profesionalRepository no puede ser nulo");
        this.citaRepository = Objects.requireNonNull(citaRepository, "citaRepository no puede ser nulo");
        this.atencionRepository = Objects.requireNonNull(atencionRepository, "atencionRepository no puede ser nulo");
        this.breakGlassRepository = breakGlassRepository;
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
        this.ventanaMeses = ventanaMeses > 0 ? ventanaMeses : 12;
    }

    /**
     * Valida que el usuario autenticado tenga autorización clínica para acceder al historial o datos
     * del paciente identificado por su {@code pacientePublicId} (ADR-007).
     *
     * @param usuarioAutenticadoPublicId Identificador público del usuario autenticado
     * @param pacientePublicId           Identificador público del paciente objetivo
     * @param authorities                Colección de roles/autoridades del usuario autenticado
     * @throws AccesoNoAutorizadoException Si no está autorizado (403)
     * @throws RecursoNoEncontradoException Si el paciente objetivo o usuario no existe (404)
     */
    public void validarAccesoHistorialClinico(
            String usuarioAutenticadoPublicId,
            String pacientePublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (usuarioAutenticadoPublicId == null || usuarioAutenticadoPublicId.isBlank() || authorities == null || authorities.isEmpty()) {
            throw new AccesoNoAutorizadoException("Usuario no autenticado o sin roles asignados.");
        }
        if (pacientePublicId == null || pacientePublicId.isBlank()) {
            throw new AccesoNoAutorizadoException("Identificador de paciente no proporcionado.");
        }

        Set<String> roles = authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        // 1. Regla ADR-007: El administrador NUNCA accede a contenido clínico
        if (roles.contains(ROL_ADMIN)) {
            throw new AccesoNoAutorizadoException("El personal administrativo no tiene autorizacion para acceder a contenido clinico.");
        }

        // 2. Regla paciente: solo ve sus propios datos clínicos
        if (roles.contains(ROL_PACIENTE)) {
            Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));
            Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente."));

            if (!paciente.publicId().equals(pacientePublicId)) {
                throw new AccesoNoAutorizadoException("No tiene autorizacion para acceder a la informacion clinica de otro paciente.");
            }
            return;
        }

        // 3. Regla profesional: requiere relación asistencial activa (ADR-007)
        if (roles.contains(ROL_PROFESIONAL)) {
            Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Usuario profesional no encontrado."));
            Profesional profesional = profesionalRepository.buscarPorUsuarioId(usuario.id())
                    .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como profesional asistencial."));

            Paciente pacienteObjetivo = pacienteRepository.buscarPorPublicId(pacientePublicId)
                    .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

            if (!tieneRelacionAsistencial(profesional.id(), pacienteObjetivo.id())) {
                throw new AccesoNoAutorizadoException("No existe una relacion asistencial activa con el paciente.");
            }
            return;
        }

        throw new AccesoNoAutorizadoException("Rol no autorizado para acceder a contenido clinico.");
    }

    /**
     * Valida que el usuario autenticado tenga autorización clínica para acceder al historial o datos
     * del paciente identificado por su {@code pacienteId} numérico interno.
     */
    public void validarAccesoHistorialClinico(
            String usuarioAutenticadoPublicId,
            Long pacienteId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        if (pacienteId == null) {
            throw new AccesoNoAutorizadoException("Identificador de paciente no proporcionado.");
        }
        Paciente paciente = pacienteRepository.buscarPorId(pacienteId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

        validarAccesoHistorialClinico(usuarioAutenticadoPublicId, paciente.publicId(), authorities);
    }

    /**
     * Determina si existe una relación asistencial activa entre el profesional y el paciente (ADR-007):
     * 1. Cita activa futura (PROGRAMADA o CONFIRMADA) con fecha de inicio posterior a ahora, O
     * 2. Atención médica previa propia realizada dentro de la ventana temporal configurada.
     *
     * @param profesionalId Identificador interno del profesional
     * @param pacienteId    Identificador interno del paciente
     * @return true si existe relación asistencial activa
     */
    public boolean tieneRelacionAsistencial(Long profesionalId, Long pacienteId) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");

        Instant ahora = clock.instant();

        // 1. Cita activa futura
        boolean tieneCitaFutura = citaRepository.existeCitaActivaFutura(profesionalId, pacienteId, ahora);
        if (tieneCitaFutura) {
            return true;
        }

        // 2. Atención previa propia en los últimos N meses (default 12 meses)
        Instant fechaLimite = ZonedDateTime.ofInstant(ahora, ZONE_BOGOTA)
                .minusMonths(ventanaMeses)
                .toInstant();

        if (atencionRepository.existeAtencionPreviaEnVentana(profesionalId, pacienteId, fechaLimite)) {
            return true;
        }

        // 3. Acceso clínico de emergencia Break-Glass activo y no expirado (ADR-017)
        if (breakGlassRepository != null && breakGlassRepository.existeAccesoActivo(profesionalId, pacienteId, ahora)) {
            return true;
        }

        return false;
    }

    /**
     * Sobrecarga de conveniencia para verificar relación asistencial a partir de identificadores públicos UUID.
     */
    public boolean tieneRelacionAsistencial(String profesionalPublicId, String pacientePublicId) {
        Profesional profesional = profesionalRepository.buscarPorPublicId(profesionalPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional no encontrado."));
        Paciente paciente = pacienteRepository.buscarPorPublicId(pacientePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado."));

        return tieneRelacionAsistencial(profesional.id(), paciente.id());
    }

    /**
     * Valida que exista relación asistencial activa o lanza AccesoNoAutorizadoException (ADR-007).
     */
    public void validarRelacionAsistencial(Long profesionalId, Long pacienteId) {
        if (!tieneRelacionAsistencial(profesionalId, pacienteId)) {
            throw new AccesoNoAutorizadoException("El profesional no cuenta con una relacion asistencial activa con el paciente.");
        }
    }

    /**
     * Determina si existe una relación asistencial ordinaria (cita futura o atención previa en ventana),
     * excluyendo el acceso excepcional Break-Glass (ADR-007, ADR-017, T4).
     */
    public boolean tieneRelacionAsistencialOrdinaria(Long profesionalId, Long pacienteId) {
        Objects.requireNonNull(profesionalId, "profesionalId no puede ser nulo");
        Objects.requireNonNull(pacienteId, "pacienteId no puede ser nulo");

        Instant ahora = clock.instant();

        // 1. Cita activa futura
        boolean tieneCitaFutura = citaRepository.existeCitaActivaFutura(profesionalId, pacienteId, ahora);
        if (tieneCitaFutura) {
            return true;
        }

        // 2. Atención previa propia en los últimos N meses (default 12 meses)
        Instant fechaLimite = ZonedDateTime.ofInstant(ahora, ZONE_BOGOTA)
                .minusMonths(ventanaMeses)
                .toInstant();

        return atencionRepository.existeAtencionPreviaEnVentana(profesionalId, pacienteId, fechaLimite);
    }

    /**
     * Valida que el profesional cuente con autorización de escritura clínica sobre el paciente (ADR-007, ADR-017, T4).
     * El acceso Break-Glass es estrictamente de solo lectura; cualquier registro o modificación exige relación asistencial ordinaria.
     */
    public void validarEscrituraClinica(Long profesionalId, Long pacienteId) {
        if (!tieneRelacionAsistencialOrdinaria(profesionalId, pacienteId)) {
            if (breakGlassRepository != null && breakGlassRepository.existeAccesoActivo(profesionalId, pacienteId, clock.instant())) {
                throw new AccesoNoAutorizadoException("El acceso Break-Glass es de solo lectura; no permite registrar ni modificar informacion clinica.");
            }
            throw new AccesoNoAutorizadoException("El profesional no cuenta con una relacion asistencial activa con el paciente.");
        }
    }

    public int getVentanaMeses() {
        return ventanaMeses;
    }
}

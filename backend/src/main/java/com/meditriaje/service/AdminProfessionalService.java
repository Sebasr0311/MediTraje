package com.meditriaje.service;

import com.meditriaje.dto.admin.ActualizarProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalResponse;
import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.email.EmailService;
import com.meditriaje.util.NormaColombianaValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de administración de profesionales asistenciales (HU-10, ADR-002, ADR-003, ADR-011, Ley 1164/2007).
 * Gestiona el alta con validaciones de talento humano en salud de Colombia (ReTHUS), contraseña temporal
 * de un solo uso, actualización de perfil asistencial, activación/desactivación y auditoría inmutable.
 */
@Service
public class AdminProfessionalService {

    private static final Logger log = LoggerFactory.getLogger(AdminProfessionalService.class);
    private static final String ESTADO_ACTIVO = "ACTIVO";
    private static final String ESTADO_INACTIVO = "INACTIVO";
    private static final String RECURSO_PROFESIONAL = "PROFESIONAL";

    private final ProfesionalRepository profesionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final EspecialidadRepository especialidadRepository;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;

    @Autowired
    public AdminProfessionalService(
            ProfesionalRepository profesionalRepository,
            UsuarioRepository usuarioRepository,
            EspecialidadRepository especialidadRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) EmailService emailService
    ) {
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository, "ProfesionalRepository no puede ser nulo");
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.especialidadRepository = Objects.requireNonNull(especialidadRepository, "EspecialidadRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder no puede ser nulo");
        this.emailService = emailService;
    }

    public AdminProfessionalService(
            ProfesionalRepository profesionalRepository,
            UsuarioRepository usuarioRepository,
            EspecialidadRepository especialidadRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder
    ) {
        this(profesionalRepository, usuarioRepository, especialidadRepository, auditoriaService, passwordEncoder, null);
    }

    /**
     * Da de alta a un profesional asistencial validando exhaustivamente sus datos conforme a la ley colombiana
     * (Ley 1164 de 2007, Decreto 780 de 2016, ReTHUS), creando su cuenta de usuario con contraseña temporal segura,
     * asignando el rol asistencial y registrando el evento en la bitácora inmutable.
     */
    @Transactional
    public CrearProfesionalResponse altaProfesional(CrearProfesionalRequest request, String adminPublicId, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de creacion no puede ser nula");

        // 0. Validar tipo y número de documento conforme a la ley colombiana (Ley 1164/2007)
        String tipoDoc = (request.tipoDocumento() != null && !request.tipoDocumento().isBlank())
                ? request.tipoDocumento().trim().toUpperCase(Locale.ROOT)
                : "CC";
        if (!"CC".equals(tipoDoc) && !"CE".equals(tipoDoc)) {
            throw new DatosInvalidosException("Tipo de documento no valido para profesionales en Colombia. Permitidos: CC, CE.");
        }

        String numDoc = (request.numeroDocumento() != null) ? request.numeroDocumento().trim() : "";
        if (numDoc.isBlank()) {
            throw new DatosInvalidosException("El numero de documento es obligatorio.");
        }
        NormaColombianaValidator.validarDocumento(tipoDoc, numDoc);

        if (profesionalRepository.existePorDocumento(tipoDoc, numDoc)) {
            throw new DatosInvalidosException("Ya existe un profesional registrado con el documento ingresado.");
        }

        // 1. Validar nombres y apellidos según norma colombiana
        NormaColombianaValidator.validarNombresOApellidos("nombres", request.nombres());
        NormaColombianaValidator.validarNombresOApellidos("apellidos", request.apellidos());

        // 2. Validar teléfono celular Colombia si fue provisto
        String telefonoNormalizado = null;
        if (request.telefono() != null && !request.telefono().isBlank()) {
            NormaColombianaValidator.validarCelularColombia(request.telefono());
            telefonoNormalizado = request.telefono().trim().replaceAll("\\s+", "");
        }

        // 3. Validar especialidad existente y activa
        Especialidad esp = especialidadRepository.buscarPorPublicId(request.especialidadPublicId().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad no encontrada: " + request.especialidadPublicId()));

        if (!ESTADO_ACTIVO.equalsIgnoreCase(esp.estado())) {
            throw new DatosInvalidosException("La especialidad seleccionada no se encuentra activa.");
        }

        // 4. Validar correo electronico unico
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existePorEmail(emailNormalizado)) {
            throw new DatosInvalidosException("El correo electronico ya se encuentra registrado.");
        }

        // 5. Validar registro medico / ReTHUS unico y formato
        String registroMedicoNormalizado = request.registroMedico().trim();
        if (registroMedicoNormalizado.length() < 4) {
            throw new DatosInvalidosException("El registro medico / ReTHUS debe tener al menos 4 caracteres.");
        }
        if (profesionalRepository.existePorRegistroMedico(registroMedicoNormalizado)) {
            throw new DatosInvalidosException("El registro medico ya se encuentra registrado.");
        }

        // 6. Generar contraseña temporal segura (mínimo 12 caracteres: mayúsculas, minúsculas, dígitos, símbolos)
        String passwordTemporal = generarPasswordTemporal();
        String passwordHash = passwordEncoder.encode(passwordTemporal);

        // 7. Crear USUARIO con debeCambiarPassword = true y estado ACTIVO
        String usuarioPublicId = UUID.randomUUID().toString();
        Long usuarioId = usuarioRepository.crear(usuarioPublicId, emailNormalizado, passwordHash, true);

        // 8. Asignar rol ROLE_PROFESIONAL
        Long rolId = usuarioRepository.buscarRolIdPorNombre("ROLE_PROFESIONAL")
                .orElseThrow(() -> new IllegalStateException("El rol ROLE_PROFESIONAL no existe en el sistema."));
        usuarioRepository.asignarRol(usuarioId, rolId);

        // 9. Crear PROFESIONAL vinculado a usuarioId y especialidadId
        String profesionalPublicId = UUID.randomUUID().toString();
        Profesional profesional = new Profesional(
                usuarioId,
                profesionalPublicId,
                esp.id(),
                tipoDoc,
                numDoc,
                registroMedicoNormalizado,
                request.nombres().trim(),
                request.apellidos().trim(),
                telefonoNormalizado
        );
        profesionalRepository.crear(profesional);

        // 10. Auditar evento administrativo
        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_PROFESIONAL,
                profesionalPublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        // 11. Despachar credenciales de acceso inicial por correo electrónico (Brevo SMTP)
        if (emailService != null) {
            try {
                String nombreCompleto = request.nombres().trim() + " " + request.apellidos().trim();
                String rolDescripcion = "Profesional Asistencial (" + esp.nombre() + ")";
                emailService.enviarCredencialesIniciales(emailNormalizado, nombreCompleto, rolDescripcion, passwordTemporal);
            } catch (Exception e) {
                log.warn("No fue posible despachar correo con credenciales iniciales a [{}]: {}", emailNormalizado, e.getMessage());
            }
        }

        return new CrearProfesionalResponse(
                profesionalPublicId,
                usuarioPublicId,
                tipoDoc,
                numDoc,
                registroMedicoNormalizado,
                request.nombres().trim(),
                request.apellidos().trim(),
                emailNormalizado,
                telefonoNormalizado,
                esp.publicId(),
                esp.nombre(),
                passwordTemporal,
                true,
                Instant.now()
        );
    }

    /**
     * Actualiza los datos asistenciales del profesional (nombres, apellidos, especialidad, teléfono).
     */
    @Transactional
    public ProfesionalResponse actualizarProfesional(
            String publicId,
            ActualizarProfesionalRequest request,
            String adminPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "La solicitud no puede ser nula");

        NormaColombianaValidator.validarNombresOApellidos("nombres", request.nombres());
        NormaColombianaValidator.validarNombresOApellidos("apellidos", request.apellidos());

        Profesional actual = profesionalRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional no encontrado: " + publicId));

        Especialidad esp = especialidadRepository.buscarPorPublicId(request.especialidadPublicId().trim())
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad no encontrada: " + request.especialidadPublicId()));

        if (!ESTADO_ACTIVO.equalsIgnoreCase(esp.estado())) {
            throw new DatosInvalidosException("La especialidad seleccionada no se encuentra activa.");
        }

        String telefonoActualizado = actual.telefono();
        if (request.telefono() != null) {
            if (!request.telefono().isBlank()) {
                NormaColombianaValidator.validarCelularColombia(request.telefono());
                telefonoActualizado = request.telefono().trim().replaceAll("\\s+", "");
            } else {
                telefonoActualizado = null;
            }
        }

        Profesional actualizado = new Profesional(
                actual.id(),
                actual.usuarioId(),
                actual.publicId(),
                esp.id(),
                actual.tipoDocumento(),
                actual.numeroDocumento(),
                actual.registroMedico(),
                request.nombres().trim(),
                request.apellidos().trim(),
                telefonoActualizado,
                actual.createdAt(),
                Instant.now()
        );
        profesionalRepository.actualizar(actualizado);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_PROFESIONAL,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        Usuario usuario = usuarioRepository.buscarPorId(actual.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        return new ProfesionalResponse(
                actual.publicId(),
                usuario.publicId(),
                actual.tipoDocumento(),
                actual.numeroDocumento(),
                actual.registroMedico(),
                request.nombres().trim(),
                request.apellidos().trim(),
                usuario.email(),
                telefonoActualizado,
                esp.publicId(),
                esp.nombre(),
                usuario.estado(),
                usuario.debeCambiarPassword(),
                actual.createdAt()
        );
    }

    /**
     * Cambia el estado de la cuenta del profesional (ACTIVO / INACTIVO) a nivel de USUARIO.
     */
    @Transactional
    public ProfesionalResponse cambiarEstado(String publicId, String nuevoEstado, String adminPublicId, String ipOrigen) {
        String estadoNormalizado = validarYNormalizarEstado(nuevoEstado);

        Profesional profesional = profesionalRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional no encontrado: " + publicId));

        usuarioRepository.actualizarEstado(profesional.usuarioId(), estadoNormalizado);

        Long adminId = obtenerAdminUsuarioId(adminPublicId);
        auditoriaService.registrarEvento(
                adminId,
                AccionAuditable.CAMBIO_ADMINISTRATIVO,
                RECURSO_PROFESIONAL,
                publicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        Usuario usuario = usuarioRepository.buscarPorId(profesional.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Especialidad esp = especialidadRepository.buscarPorId(profesional.especialidadId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad no encontrada"));

        return new ProfesionalResponse(
                profesional.publicId(),
                usuario.publicId(),
                profesional.tipoDocumento(),
                profesional.numeroDocumento(),
                profesional.registroMedico(),
                profesional.nombres(),
                profesional.apellidos(),
                usuario.email(),
                profesional.telefono(),
                esp.publicId(),
                esp.nombre(),
                estadoNormalizado,
                usuario.debeCambiarPassword(),
                profesional.createdAt()
        );
    }

    /**
     * Obtiene los datos detallados de un profesional asistencial.
     */
    @Transactional(readOnly = true)
    public ProfesionalResponse obtenerPorPublicId(String publicId) {
        Profesional profesional = profesionalRepository.buscarPorPublicId(publicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional no encontrado: " + publicId));

        Usuario usuario = usuarioRepository.buscarPorId(profesional.usuarioId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado"));

        Especialidad esp = especialidadRepository.buscarPorId(profesional.especialidadId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Especialidad no encontrada"));

        return new ProfesionalResponse(
                profesional.publicId(),
                usuario.publicId(),
                profesional.tipoDocumento(),
                profesional.numeroDocumento(),
                profesional.registroMedico(),
                profesional.nombres(),
                profesional.apellidos(),
                usuario.email(),
                profesional.telefono(),
                esp.publicId(),
                esp.nombre(),
                usuario.estado(),
                usuario.debeCambiarPassword(),
                profesional.createdAt()
        );
    }

    /**
     * Lista profesionales asistenciales con paginación y filtros opcionales por especialidad y estado.
     */
    @Transactional(readOnly = true)
    public PaginatedResponse<ProfesionalResponse> listar(int page, int size, String especialidadPublicId, String estado) {
        String estadoFiltro = null;
        if (estado != null && !estado.isBlank()) {
            estadoFiltro = validarYNormalizarEstado(estado);
        }

        String especialidadFiltro = (especialidadPublicId != null && !especialidadPublicId.isBlank())
                ? especialidadPublicId.trim()
                : null;

        List<ProfesionalResponse> items = profesionalRepository.listar(page, size, especialidadFiltro, estadoFiltro);
        int total = profesionalRepository.contar(especialidadFiltro, estadoFiltro);

        return PaginatedResponse.of(items, page, size, (long) total);
    }

    // =========================================================================
    // UTILIDADES PRIVADAS
    // =========================================================================

    private Long obtenerAdminUsuarioId(String adminPublicId) {
        return usuarioRepository.buscarPorPublicId(adminPublicId)
                .map(Usuario::id)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario administrador"));
    }

    private String validarYNormalizarEstado(String estado) {
        if (estado == null || estado.isBlank()) {
            throw new DatosInvalidosException("El estado no puede ser vacio.");
        }
        String normalizado = estado.trim().toUpperCase(Locale.ROOT);
        if (!ESTADO_ACTIVO.equals(normalizado) && !ESTADO_INACTIVO.equals(normalizado)) {
            throw new DatosInvalidosException("Estado invalido. Los valores permitidos son: ACTIVO, INACTIVO.");
        }
        return normalizado;
    }

    /**
     * Genera una contraseña aleatoria de 14 caracteres garantizando al menos:
     * 2 mayúsculas, 2 minúsculas, 2 dígitos y 2 símbolos especiales.
     */
    private String generarPasswordTemporal() {
        final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
        final String LOWER = "abcdefghijkmnopqrstuvwxyz";
        final String DIGITS = "23456789";
        final String SYMBOLS = "!@#$%&*+-_=";
        final String ALL = UPPER + LOWER + DIGITS + SYMBOLS;

        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(14);
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(UPPER.charAt(random.nextInt(UPPER.length())));
        sb.append(LOWER.charAt(random.nextInt(LOWER.length())));
        sb.append(LOWER.charAt(random.nextInt(LOWER.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(DIGITS.charAt(random.nextInt(DIGITS.length())));
        sb.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));
        sb.append(SYMBOLS.charAt(random.nextInt(SYMBOLS.length())));

        for (int i = 0; i < 6; i++) {
            sb.append(ALL.charAt(random.nextInt(ALL.length())));
        }

        char[] chars = sb.toString().toCharArray();
        for (int i = chars.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            char temp = chars[i];
            chars[i] = chars[j];
            chars[j] = temp;
        }
        return new String(chars);
    }
}

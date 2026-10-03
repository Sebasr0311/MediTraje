package com.meditriaje.service;

import com.meditriaje.dto.RegistroPacienteRequest;
import com.meditriaje.dto.RegistroPacienteResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.repository.ConsentimientoRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Servicio de autenticación y registro de usuarios (HU-01, ADR-002, ADR-013).
 */
@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PacienteRepository pacienteRepository;
    private final ConsentimientoRepository consentimientoRepository;
    private final AuditoriaService auditoriaService;
    private final PasswordEncoder passwordEncoder;

    public AuthService(
            UsuarioRepository usuarioRepository,
            PacienteRepository pacienteRepository,
            ConsentimientoRepository consentimientoRepository,
            AuditoriaService auditoriaService,
            PasswordEncoder passwordEncoder
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "UsuarioRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "PacienteRepository no puede ser nulo");
        this.consentimientoRepository = Objects.requireNonNull(consentimientoRepository, "ConsentimientoRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "AuditoriaService no puede ser nulo");
        this.passwordEncoder = Objects.requireNonNull(passwordEncoder, "PasswordEncoder no puede ser nulo");
    }

    /**
     * Registra un nuevo paciente en una única transacción atómica:
     * crea USUARIO, asigna ROLE_PACIENTE, crea PACIENTE y registra CONSENTIMIENTO.
     */
    @Transactional
    public RegistroPacienteResponse registrarPaciente(RegistroPacienteRequest request, String ipOrigen) {
        Objects.requireNonNull(request, "La solicitud de registro no puede ser nula");

        // 1. Validar consentimiento
        if (!Boolean.TRUE.equals(request.aceptaConsentimiento())) {
            auditoriaService.registrarEvento(
                    AccionAuditable.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("Debe aceptar el consentimiento informado para registrarse.");
        }

        // 2. Normalizar correo y verificar duplicados
        String emailNormalizado = request.email().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existePorEmail(emailNormalizado)) {
            auditoriaService.registrarEvento(
                    AccionAuditable.REGISTRO_PACIENTE,
                    "USUARIO",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("El correo electronico ya se encuentra registrado.");
        }

        // 3. Verificar documento duplicado
        if (pacienteRepository.existePorDocumento(request.tipoDocumento(), request.numeroDocumento().trim())) {
            auditoriaService.registrarEvento(
                    AccionAuditable.REGISTRO_PACIENTE,
                    "PACIENTE",
                    null,
                    ResultadoAuditoria.FALLO,
                    ipOrigen
            );
            throw new DatosInvalidosException("El documento de identidad ya se encuentra registrado.");
        }

        // 4. Hashear contraseña con Argon2id
        String passwordHash = passwordEncoder.encode(request.password());

        // 5. Crear USUARIO
        String usuarioPublicId = UUID.randomUUID().toString();
        Long usuarioId = usuarioRepository.crear(usuarioPublicId, emailNormalizado, passwordHash);

        // 6. Asignar rol ROLE_PACIENTE
        Long rolId = usuarioRepository.buscarRolIdPorNombre("ROLE_PACIENTE")
                .orElseThrow(() -> new IllegalStateException("El rol ROLE_PACIENTE no existe en el sistema."));
        usuarioRepository.asignarRol(usuarioId, rolId);

        // 7. Crear PACIENTE
        String pacientePublicId = UUID.randomUUID().toString();
        pacienteRepository.crear(
                usuarioId,
                pacientePublicId,
                request.tipoDocumento(),
                request.numeroDocumento().trim(),
                request.nombres().trim(),
                request.apellidos().trim(),
                request.fechaNacimiento(),
                request.telefono() != null ? request.telefono().trim() : null
        );

        // 8. Registrar CONSENTIMIENTO
        consentimientoRepository.registrar(
                usuarioId,
                request.consentimientoTextoVersion().trim(),
                true,
                ipOrigen
        );

        // 9. Registrar evento exitoso en auditoría
        auditoriaService.registrarEvento(
                usuarioId,
                AccionAuditable.REGISTRO_PACIENTE,
                "PACIENTE",
                pacientePublicId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return new RegistroPacienteResponse(
                pacientePublicId,
                emailNormalizado,
                request.nombres().trim(),
                request.apellidos().trim(),
                "Registro completado exitosamente."
        );
    }
}

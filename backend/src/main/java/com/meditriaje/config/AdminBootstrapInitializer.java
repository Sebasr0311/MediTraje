package com.meditriaje.config;

import com.meditriaje.model.Usuario;
import com.meditriaje.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inicializador de bootstrap para garantizar la existencia de una cuenta de administración operativa.
 * Si no existe 'admin@meditriaje.com', la crea con la contraseña institucional 'Admin12345*' o la inyectada.
 * Si ya existe pero estaba bloqueada por intentos fallidos, restablece su estado a ACTIVO.
 */
@Component
@Order(10)
public class AdminBootstrapInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapInitializer.class);
    private static final String DEFAULT_ADMIN_EMAIL = "admin@meditriaje.com";

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public AdminBootstrapInitializer(
            UsuarioRepository usuarioRepository,
            PasswordEncoder passwordEncoder,
            @Value("${meditriaje.admin.email:admin@meditriaje.com}") String adminEmail,
            @Value("${meditriaje.admin.password:Admin12345*}") String adminPassword
    ) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = (adminEmail != null && !adminEmail.isBlank()) ? adminEmail.trim().toLowerCase() : DEFAULT_ADMIN_EMAIL;
        this.adminPassword = (adminPassword != null && !adminPassword.isBlank()) ? adminPassword : "Admin12345*";
    }

    @Override
    public void run(String... args) {
        try {
            Optional<Usuario> usuarioOpt = usuarioRepository.buscarPorEmail(adminEmail);
            Long rolAdmin = usuarioRepository.buscarRolIdPorNombre("ROLE_ADMINISTRADOR")
                    .orElse(null);

            if (rolAdmin == null) {
                log.warn("AdminBootstrap: El rol ROLE_ADMINISTRADOR aún no está disponible en la base de datos.");
                return;
            }

            if (usuarioOpt.isEmpty()) {
                String publicId = "usr-admin-" + UUID.randomUUID().toString().substring(0, 8);
                String hash = passwordEncoder.encode(adminPassword);
                Long usuarioId = usuarioRepository.crear(publicId, adminEmail, hash, false);
                usuarioRepository.asignarRol(usuarioId, rolAdmin);
                log.info("AdminBootstrap: Administrador inicial creado con éxito: {}", adminEmail);
            } else {
                Usuario u = usuarioOpt.get();
                // Si la cuenta estaba bloqueada por intentos fallidos, reactivarla
                if (u.intentosFallidos() > 0 || u.estaBloqueado() || "BLOQUEADO".equalsIgnoreCase(u.estado())) {
                    usuarioRepository.restablecerIntentos(u.id());
                    log.info("AdminBootstrap: Intentos fallidos y bloqueo restablecidos para {}", adminEmail);
                }
                // Asegurar que tenga el rol ROLE_ADMINISTRADOR
                List<String> roles = usuarioRepository.obtenerRoles(u.id());
                if (!roles.contains("ROLE_ADMINISTRADOR")) {
                    usuarioRepository.asignarRol(u.id(), rolAdmin);
                    log.info("AdminBootstrap: Rol ROLE_ADMINISTRADOR asignado a {}", adminEmail);
                }
            }
        } catch (Exception e) {
            log.warn("AdminBootstrap: Advertencia al verificar administrador inicial: {}", e.getMessage());
        }
    }
}

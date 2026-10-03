package com.meditriaje.service;

import com.meditriaje.dto.PacientePerfilResponse;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.repository.PacienteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Servicio de gestión de información de pacientes y consultas de perfil (HU-09).
 */
@Service
public class PacienteService {

    private final PacienteRepository pacienteRepository;

    public PacienteService(PacienteRepository pacienteRepository) {
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "PacienteRepository no puede ser nulo");
    }

    /**
     * Retorna el perfil del paciente autenticado a partir de su publicId de usuario.
     */
    @Transactional(readOnly = true)
    public PacientePerfilResponse obtenerMiPerfil(String usuarioPublicId) {
        if (usuarioPublicId == null || usuarioPublicId.isBlank()) {
            throw new IllegalArgumentException("El ID publico de usuario no puede ser nulo ni vacio");
        }
        return pacienteRepository.buscarPerfilPorUsuarioPublicId(usuarioPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Paciente no encontrado"));
    }
}

package com.meditriaje.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class UsuarioRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private UsuarioRepository repository;

    @BeforeEach
    void setUp() {
        repository = new UsuarioRepository(jdbcTemplate);
    }

    @Test
    void actualizarPassword_ejecutaUpdateParametrizado() {
        repository.actualizarPassword(10L, "nuevoHash", false);

        verify(jdbcTemplate).update(
                anyString(),
                eq("nuevoHash"),
                eq(0),
                eq(10L)
        );
    }

    @Test
    void actualizarEstado_ejecutaUpdateParametrizado() {
        repository.actualizarEstado(10L, "INACTIVO");

        verify(jdbcTemplate).update(
                anyString(),
                eq("INACTIVO"),
                eq(10L)
        );
    }
}

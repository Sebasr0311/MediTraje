package com.meditriaje.repository;

import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.ResultadoAuditoria;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Pruebas unitarias para {@link AuditoriaRepository}.
 * Verifica que el repositorio es estrictamente insert-only y utiliza consultas SQL parametrizadas.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaRepositoryTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private AuditoriaRepository auditoriaRepository;

    @Test
    void registrar_ejecutaInsertConSqlParametrizado() {
        EventoAuditoria evento = new EventoAuditoria(
                10L,
                AccionAuditable.REGISTRO_PACIENTE,
                "PACIENTE",
                "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
                ResultadoAuditoria.EXITO,
                "192.168.0.1"
        );

        auditoriaRepository.registrar(evento);

        verify(jdbcTemplate).update(
                eq("""
                    INSERT INTO AUDITORIA (USUARIO_ID, ACCION, TIPO_RECURSO, RECURSO_PUBLIC_ID, RESULTADO, IP_ORIGEN)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """),
                eq(10L),
                eq("REGISTRO_PACIENTE"),
                eq("PACIENTE"),
                eq("a1b2c3d4-e5f6-7890-abcd-ef1234567890"),
                eq("EXITO"),
                eq("192.168.0.1")
        );
    }

    @Test
    void repositorio_esEstrictamenteInsertOnly() {
        // ADR-011, ADR-012: AuditoriaRepository no debe exponer métodos de modificación ni borrado
        List<String> operacionesProhibidas = List.of(
                "update", "delete", "remove", "modify", "drop", "truncate"
        );

        Method[] methods = AuditoriaRepository.class.getDeclaredMethods();
        for (Method method : methods) {
            if (Modifier.isPublic(method.getModifiers())) {
                String methodNameLower = method.getName().toLowerCase();
                for (String prohibida : operacionesProhibidas) {
                    assertThat(methodNameLower)
                            .as("AuditoriaRepository no debe exponer el método '%s'", method.getName())
                            .doesNotContain(prohibida);
                }
            }
        }
    }
}

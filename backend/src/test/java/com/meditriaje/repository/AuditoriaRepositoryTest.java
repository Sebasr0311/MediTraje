package com.meditriaje.repository;

import com.meditriaje.dto.audit.RegistroAuditoriaResponse;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.ResultadoAuditoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AuditoriaRepository}.
 * Verifica que el repositorio es estrictamente insert-only para mutaciones,
 * expone consultas seguras y parametrizadas para supervisión (ADR-019),
 * y utiliza consultas SQL parametrizadas.
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
    @DisplayName("listarEventos - ejecuta consulta paginada con filtros opcionales")
    @SuppressWarnings("unchecked")
    void listarEventos_conFiltros_ejecutaQueryParametrizado() {
        Instant desde = Instant.parse("2026-10-01T00:00:00Z");
        Instant hasta = Instant.parse("2026-10-04T23:59:59Z");
        RegistroAuditoriaResponse mockItem = new RegistroAuditoriaResponse(
                1L, "admin@meditriaje.com", "LOGIN_EXITOSO", "USUARIO", "u-1", "EXITO", "127.0.0.1", Instant.now()
        );

        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
                .thenReturn(List.of(mockItem));

        List<RegistroAuditoriaResponse> eventos = auditoriaRepository.listarEventos(
                desde, hasta, "LOGIN_EXITOSO", "EXITO", 0, 10
        );

        assertThat(eventos).hasSize(1);
        assertThat(eventos.get(0).accion()).isEqualTo("LOGIN_EXITOSO");
        verify(jdbcTemplate).query(contains("FROM AUDITORIA a"), any(RowMapper.class), any(Object[].class));
    }

    @Test
    @DisplayName("contarEventos - ejecuta conteo con filtros")
    void contarEventos_conFiltros_retornaTotal() {
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class), any(Object[].class)))
                .thenReturn(42L);

        long total = auditoriaRepository.contarEventos(null, null, null, null);

        assertThat(total).isEqualTo(42L);
        verify(jdbcTemplate).queryForObject(contains("COUNT(*)"), eq(Long.class), any(Object[].class));
    }

    @Test
    void repositorio_esEstrictamenteInsertOnly() {
        // ADR-011, ADR-012, ADR-019: AuditoriaRepository no debe exponer métodos de modificación ni borrado
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

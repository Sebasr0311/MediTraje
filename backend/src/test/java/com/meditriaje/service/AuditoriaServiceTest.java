package com.meditriaje.service;

import com.meditriaje.dto.audit.RegistroAuditoriaResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.repository.AuditoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pruebas unitarias para {@link AuditoriaService} y modelos de auditoría.
 * Verifica el registro de eventos, consultas supervisadas (ADR-019),
 * ausencia de datos clínicos y validación de campos obligatorios.
 */
@ExtendWith(MockitoExtension.class)
class AuditoriaServiceTest {

    @Mock
    private AuditoriaRepository auditoriaRepository;

    @InjectMocks
    private AuditoriaService auditoriaService;

    @Test
    void registrarEvento_conUsuario_guardaEventoCorrectamente() {
        Long usuarioId = 42L;
        String publicId = UUID.randomUUID().toString();
        String ip = "192.168.1.100";

        auditoriaService.registrarEvento(
                usuarioId,
                AccionAuditable.LOGIN_EXITOSO,
                "USUARIO",
                publicId,
                ResultadoAuditoria.EXITO,
                ip
        );

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaRepository).registrar(captor.capture());

        EventoAuditoria captured = captor.getValue();
        assertThat(captured.usuarioId()).isEqualTo(usuarioId);
        assertThat(captured.accion()).isEqualTo(AccionAuditable.LOGIN_EXITOSO);
        assertThat(captured.tipoRecurso()).isEqualTo("USUARIO");
        assertThat(captured.recursoPublicId()).isEqualTo(publicId);
        assertThat(captured.resultado()).isEqualTo(ResultadoAuditoria.EXITO);
        assertThat(captured.ipOrigen()).isEqualTo(ip);
    }

    @Test
    void registrarEvento_sinUsuario_permiteUsuarioIdNulo() {
        String ip = "10.0.0.1";

        auditoriaService.registrarEvento(
                AccionAuditable.LOGIN_FALLIDO,
                "AUTH",
                null,
                ResultadoAuditoria.FALLO,
                ip
        );

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaRepository).registrar(captor.capture());

        EventoAuditoria captured = captor.getValue();
        assertThat(captured.usuarioId()).isNull();
        assertThat(captured.accion()).isEqualTo(AccionAuditable.LOGIN_FALLIDO);
        assertThat(captured.resultado()).isEqualTo(ResultadoAuditoria.FALLO);
    }

    @Test
    void eventoAuditoria_validaCamposObligatorios() {
        assertThatThrownBy(() -> new EventoAuditoria(null, null, "USUARIO", null, ResultadoAuditoria.EXITO, "127.0.0.1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("accion");

        assertThatThrownBy(() -> new EventoAuditoria(null, AccionAuditable.LOGIN_EXITOSO, "   ", null, ResultadoAuditoria.EXITO, "127.0.0.1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("tipo de recurso");

        assertThatThrownBy(() -> new EventoAuditoria(null, AccionAuditable.LOGIN_EXITOSO, "USUARIO", null, null, "127.0.0.1"))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("resultado");

        assertThatThrownBy(() -> new EventoAuditoria(null, AccionAuditable.LOGIN_EXITOSO, "USUARIO", null, ResultadoAuditoria.EXITO, ""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("IP");
    }

    @Test
    void eventoAuditoria_noContieneCamposDeContenidoClinico() {
        // ADR-011: La bitácora nunca debe contener datos clínicos, diagnósticos, evolución, etc.
        List<String> camposProhibidos = List.of(
                "diagnostico", "cie10", "evolucion", "motivo", "signovital",
                "presion", "temperatura", "medicamento", "receta", "dosis", "historia"
        );

        Field[] fields = EventoAuditoria.class.getDeclaredFields();
        for (Field field : fields) {
            String fieldNameLower = field.getName().toLowerCase();
            for (String prohibido : camposProhibidos) {
                assertThat(fieldNameLower)
                        .as("El modelo de auditoría no debe contener el campo clínico o sensible '%s'", prohibido)
                        .doesNotContain(prohibido);
            }
        }
    }

    @Test
    @DisplayName("consultarBitacora - lanza DatosInvalidosException con fechas invertidas")
    void consultarBitacora_conFechasInvertidas_lanzaExcepcion() {
        LocalDate desde = LocalDate.of(2026, 10, 10);
        LocalDate hasta = LocalDate.of(2026, 10, 1);

        assertThatThrownBy(() -> auditoriaService.consultarBitacora(desde, hasta, null, null, 0, 20))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no puede ser posterior");
    }

    @Test
    @DisplayName("consultarBitacora - delega a repositorio y mapea respuesta paginada")
    void consultarBitacora_conParametrosValidos_retornaRespuestaPaginada() {
        LocalDate desde = LocalDate.of(2026, 10, 1);
        LocalDate hasta = LocalDate.of(2026, 10, 4);

        RegistroAuditoriaResponse evento = new RegistroAuditoriaResponse(
                55L,
                "admin@meditriaje.com",
                "CAMBIO_ADMINISTRATIVO",
                "ESPECIALIDAD",
                "esp-1",
                "EXITO",
                "127.0.0.1",
                Instant.now()
        );

        when(auditoriaRepository.listarEventos(any(), any(), eq("CAMBIO_ADMINISTRATIVO"), eq("EXITO"), eq(0), eq(15)))
                .thenReturn(List.of(evento));
        when(auditoriaRepository.contarEventos(any(), any(), eq("CAMBIO_ADMINISTRATIVO"), eq("EXITO")))
                .thenReturn(1L);

        PaginatedResponse<RegistroAuditoriaResponse> resultado =
                auditoriaService.consultarBitacora(desde, hasta, "CAMBIO_ADMINISTRATIVO", "EXITO", 0, 15);

        assertThat(resultado).isNotNull();
        assertThat(resultado.totalElements()).isEqualTo(1L);
        assertThat(resultado.content()).hasSize(1);
        assertThat(resultado.content().get(0).accion()).isEqualTo("CAMBIO_ADMINISTRATIVO");
    }

    @Test
    @DisplayName("RegistroAuditoriaResponse - prohíbe exposición de campos clínicos confidenciales")
    void registroAuditoriaResponse_noContieneCamposDeContenidoClinico() {
        // ADR-007, ADR-011, ADR-019: La respuesta de auditoría técnica administrativa nunca expone contenido clínico
        List<String> camposProhibidos = List.of(
                "diagnostico", "cie10", "evolucion", "motivo", "signovital",
                "presion", "temperatura", "medicamento", "receta", "dosis", "historia"
        );

        RecordComponent[] components = RegistroAuditoriaResponse.class.getRecordComponents();
        for (RecordComponent component : components) {
            String nameLower = component.getName().toLowerCase();
            for (String prohibido : camposProhibidos) {
                assertThat(nameLower)
                        .as("El DTO de auditoría no debe contener el campo clínico '%s'", prohibido)
                        .doesNotContain(prohibido);
            }
        }
    }
}

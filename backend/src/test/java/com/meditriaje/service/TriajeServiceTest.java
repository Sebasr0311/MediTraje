package com.meditriaje.service;

import com.meditriaje.dto.triage.CatalogoSintomaResponse;
import com.meditriaje.dto.triage.CrearTriajeRequest;
import com.meditriaje.dto.triage.SintomaItemRequest;
import com.meditriaje.dto.triage.SintomaItemResponse;
import com.meditriaje.dto.triage.TriajeResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Triaje;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.TriajeRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.triage.MotorTriaje;
import com.meditriaje.triage.NivelPrioridad;
import com.meditriaje.triage.ResultadoTriaje;
import com.meditriaje.triage.RutaSugerida;
import com.meditriaje.triage.TriajeMotorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TriajeServiceTest {

    @Mock
    private TriajeRepository triajeRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private TriajeMotorFactory triajeMotorFactory;
    @Mock
    private AuditoriaService auditoriaService;
    @Mock
    private MotorTriaje motorTriaje;

    private TriajeService triajeService;

    private static final String USUARIO_PUBLIC_ID = "usr-uuid-paciente";
    private static final String IP_ORIGEN = "192.168.1.50";
    private static final Instant AHORA = Instant.parse("2026-10-10T12:00:00Z");

    private Usuario usuarioMock;
    private Paciente pacienteMock;

    @BeforeEach
    void setUp() {
        triajeService = new TriajeService(
                triajeRepository,
                usuarioRepository,
                pacienteRepository,
                triajeMotorFactory,
                auditoriaService
        );

        usuarioMock = new Usuario(
                1L, USUARIO_PUBLIC_ID, "paciente@test.com", "hash", "ACTIVO", 0, null, AHORA, false
        );
        pacienteMock = new Paciente(
                10L, 1L, "pac-uuid-1", "CC", "12345678", "Pepito", "Perez",
                LocalDate.of(1990, 1, 1), "3001234567", AHORA, null
        );
    }

    @Test
    void evaluarYGuardarTriaje_noUrgente_persisteCorrectamenteYAuditaRealizado() {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(12.0), 3)),
                "Observacion leve"
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(triajeMotorFactory.obtenerMotor()).thenReturn(motorTriaje);

        ResultadoTriaje resultado = new ResultadoTriaje(
                NivelPrioridad.IV,
                RutaSugerida.CITA_TELEMEDICINA,
                false,
                "v1-prototipo",
                "Se sugiere una cita de telemedicina.",
                "Aviso de prototipo",
                List.of()
        );
        when(motorTriaje.evaluar(any())).thenReturn(resultado);
        when(triajeRepository.obtenerMapaCodigoAIdSintomas()).thenReturn(Map.of("FIEBRE", 101L));
        when(triajeRepository.guardarTriaje(any(Triaje.class))).thenReturn(50L);

        TriajeResponse responseMock = new TriajeResponse(
                "triaje-uuid-1", "pac-uuid-1", "IV", "CITA_TELEMEDICINA", false, "v1-prototipo",
                "Se sugiere una cita de telemedicina.", "Aviso", List.of(),
                List.of(new SintomaItemResponse("FIEBRE", "Fiebre", BigDecimal.valueOf(12.0), 3, false)),
                "Observacion leve", AHORA
        );
        when(triajeRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(responseMock));

        TriajeResponse res = triajeService.evaluarYGuardarTriaje(request, USUARIO_PUBLIC_ID, IP_ORIGEN);

        assertThat(res).isNotNull();
        assertThat(res.esEmergencia()).isFalse();
        assertThat(res.nivelPrioridad()).isEqualTo("IV");

        // Verifica inserción de síntomas
        verify(triajeRepository).guardarSintomas(eq(50L), anyList());

        // Verifica auditoría: solo TRIAJE_REALIZADO, no TRIAJE_EMERGENCIA
        ArgumentCaptor<EventoAuditoria> audCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService, times(1)).auditar(audCaptor.capture());
        EventoAuditoria evento = audCaptor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.TRIAJE_REALIZADO);
        assertThat(evento.tipoRecurso()).isEqualTo("TRIAJE");
        assertThat(evento.usuarioId()).isEqualTo(1L);
    }

    @Test
    void evaluarYGuardarTriaje_corteEmergencia_auditaEmergenciaYRealizado() {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("DOLOR_TORACICO_OPRESIVO", BigDecimal.valueOf(1.0), 8)),
                "Dolor en el pecho"
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(triajeMotorFactory.obtenerMotor()).thenReturn(motorTriaje);

        ResultadoTriaje resultadoEmergencia = new ResultadoTriaje(
                NivelPrioridad.I,
                RutaSugerida.URGENCIAS,
                true,
                "v1-prototipo",
                "Llama al 123 o acude a urgencias de inmediato.",
                "Aviso de prototipo",
                List.of("DOLOR_TORACICO_OPRESIVO")
        );
        when(motorTriaje.evaluar(any())).thenReturn(resultadoEmergencia);
        when(triajeRepository.obtenerMapaCodigoAIdSintomas()).thenReturn(Map.of("DOLOR_TORACICO_OPRESIVO", 1L));
        when(triajeRepository.guardarTriaje(any(Triaje.class))).thenReturn(51L);

        TriajeResponse responseMock = new TriajeResponse(
                "triaje-uuid-emergencia", "pac-uuid-1", "I", "URGENCIAS", true, "v1-prototipo",
                "Llama al 123 o acude a urgencias de inmediato.", "Aviso",
                List.of("DOLOR_TORACICO_OPRESIVO"),
                List.of(new SintomaItemResponse("DOLOR_TORACICO_OPRESIVO", "Dolor torácico", BigDecimal.valueOf(1.0), 8, true)),
                "Dolor en el pecho", AHORA
        );
        when(triajeRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(responseMock));

        TriajeResponse res = triajeService.evaluarYGuardarTriaje(request, USUARIO_PUBLIC_ID, IP_ORIGEN);

        assertThat(res).isNotNull();
        assertThat(res.esEmergencia()).isTrue();
        assertThat(res.nivelPrioridad()).isEqualTo("I");
        assertThat(res.rutaSugerida()).isEqualTo("URGENCIAS");

        // Verifica ambas auditorías: TRIAJE_EMERGENCIA y TRIAJE_REALIZADO
        ArgumentCaptor<EventoAuditoria> audCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService, times(2)).auditar(audCaptor.capture());
        List<EventoAuditoria> eventos = audCaptor.getAllValues();
        assertThat(eventos.get(0).accion()).isEqualTo(AccionAuditable.TRIAJE_EMERGENCIA);
        assertThat(eventos.get(1).accion()).isEqualTo(AccionAuditable.TRIAJE_REALIZADO);
    }

    @Test
    void evaluarYGuardarTriaje_sintomaDuplicado_lanzaDatosInvalidosException() {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(
                        new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(2.0), 5),
                        new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(4.0), 6)
                ),
                null
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> triajeService.evaluarYGuardarTriaje(request, USUARIO_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Síntoma duplicado");

        verify(triajeRepository, never()).guardarTriaje(any());
    }

    @Test
    void evaluarYGuardarTriaje_usuarioNoEsPaciente_lanzaAccesoNoAutorizadoException() {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(2.0), 5)),
                null
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triajeService.evaluarYGuardarTriaje(request, USUARIO_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Solo pacientes registrados pueden realizar el triaje");

        verify(triajeRepository, never()).guardarTriaje(any());
    }

    @Test
    void evaluarYGuardarTriaje_usuarioInexistente_lanzaRecursoNoEncontradoException() {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("FIEBRE", BigDecimal.valueOf(2.0), 5)),
                null
        );

        when(usuarioRepository.buscarPorPublicId("usr-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> triajeService.evaluarYGuardarTriaje(request, "usr-inexistente", IP_ORIGEN))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario no encontrado");
    }

    @Test
    void evaluarYGuardarTriaje_sintomaDesconocidoEnCatalogo_lanzaDatosInvalidosException() {
        CrearTriajeRequest request = new CrearTriajeRequest(
                List.of(new SintomaItemRequest("SINTOMA_INEXISTENTE", BigDecimal.valueOf(2.0), 5)),
                null
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));
        when(triajeMotorFactory.obtenerMotor()).thenReturn(motorTriaje);
        when(motorTriaje.evaluar(any())).thenReturn(new ResultadoTriaje(
                NivelPrioridad.III, RutaSugerida.CITA_PRESENCIAL, false, "v1", "msg", "aviso", List.of()
        ));
        when(triajeRepository.obtenerMapaCodigoAIdSintomas()).thenReturn(Map.of("FIEBRE", 1L));

        assertThatThrownBy(() -> triajeService.evaluarYGuardarTriaje(request, USUARIO_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Síntoma desconocido");
    }

    @Test
    void obtenerPorPublicId_propioPaciente_retornaTriaje() {
        String triajePublicId = "triaje-uuid-1";
        TriajeResponse responseMock = new TriajeResponse(
                triajePublicId, "pac-uuid-1", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triajeRepository.buscarPorPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));

        TriajeResponse res = triajeService.obtenerPorPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        );

        assertThat(res).isNotNull();
        assertThat(res.publicId()).isEqualTo(triajePublicId);
    }

    @Test
    void obtenerPorPublicId_otroPaciente_lanzaAccesoNoAutorizadoException() {
        String triajePublicId = "triaje-uuid-ajeno";
        TriajeResponse responseMock = new TriajeResponse(
                triajePublicId, "pac-uuid-OTRO", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triajeRepository.buscarPorPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));
        when(pacienteRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(pacienteMock));

        assertThatThrownBy(() -> triajeService.obtenerPorPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("No tiene autorizacion para acceder al triaje de otro paciente");
    }

    @Test
    void obtenerPorPublicId_administrador_lanzaAccesoNoAutorizadoException() {
        String triajePublicId = "triaje-uuid-1";
        TriajeResponse responseMock = new TriajeResponse(
                triajePublicId, "pac-uuid-1", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triajeRepository.buscarPorPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));

        assertThatThrownBy(() -> triajeService.obtenerPorPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("El personal administrativo no tiene acceso a informacion clinica");
    }

    @Test
    void obtenerPorPublicId_profesional_accesoPermitido() {
        String triajePublicId = "triaje-uuid-1";
        TriajeResponse responseMock = new TriajeResponse(
                triajePublicId, "pac-uuid-1", "III", "CITA_PRESENCIAL", false, "v1-prototipo",
                "Mensaje", "Aviso", List.of(), List.of(), null, AHORA
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PUBLIC_ID)).thenReturn(Optional.of(usuarioMock));
        when(triajeRepository.buscarPorPublicId(triajePublicId)).thenReturn(Optional.of(responseMock));

        TriajeResponse res = triajeService.obtenerPorPublicId(
                triajePublicId,
                USUARIO_PUBLIC_ID,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(res).isNotNull();
        assertThat(res.publicId()).isEqualTo(triajePublicId);
    }

    @Test
    void obtenerCatalogoSintomas_retornaCatalogoDeRepositorio() {
        List<CatalogoSintomaResponse> catalogoMock = List.of(
                new CatalogoSintomaResponse("uuid-1", "FIEBRE", "Fiebre", "GENERAL", false)
        );
        when(triajeRepository.listarCatalogoSintomasActivos()).thenReturn(catalogoMock);

        List<CatalogoSintomaResponse> resultado = triajeService.obtenerCatalogoSintomas();
        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).codigo()).isEqualTo("FIEBRE");
    }
}

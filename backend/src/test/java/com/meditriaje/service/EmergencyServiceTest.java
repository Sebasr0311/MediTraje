package com.meditriaje.service;

import com.meditriaje.dto.emergency.AsignarEquipoRequest;
import com.meditriaje.dto.emergency.EpisodioUrgenciaResponse;
import com.meditriaje.dto.emergency.ItemColaUrgenciaResponse;
import com.meditriaje.dto.emergency.ReconciliarIdentidadRequest;
import com.meditriaje.dto.emergency.RegistrarAdmisionUrgenciaRequest;
import com.meditriaje.dto.emergency.RegistrarValoracionTriajeRequest;
import com.meditriaje.dto.emergency.ValoracionTriajeResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.EpisodioAtencion;
import com.meditriaje.model.IdentidadProvisional;
import com.meditriaje.model.IngresoUrgencia;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.model.ValoracionTriaje;
import com.meditriaje.repository.AsignacionAsistencialRepository;
import com.meditriaje.repository.EpisodioAtencionRepository;
import com.meditriaje.repository.IdentidadProvisionalRepository;
import com.meditriaje.repository.IngresoUrgenciaRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.repository.ValoracionTriajeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmergencyServiceTest {

    @Mock
    private EpisodioAtencionRepository episodioRepository;
    @Mock
    private IngresoUrgenciaRepository ingresoRepository;
    @Mock
    private IdentidadProvisionalRepository identidadRepository;
    @Mock
    private ValoracionTriajeRepository valoracionRepository;
    @Mock
    private AsignacionAsistencialRepository asignacionRepository;
    @Mock
    private SedeRepository sedeRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private AuditoriaService auditoriaService;

    private EmergencyService emergencyService;

    private static final Instant AHORA = Instant.parse("2026-10-10T14:30:00Z");
    private static final Clock CLOCK_FIJO = Clock.fixed(AHORA, ZoneId.of("America/Bogota"));

    private Usuario enfermeroMock;
    private Sede sedeMock;
    private Paciente pacienteMock;

    @BeforeEach
    void setUp() {
        emergencyService = new EmergencyService(
                episodioRepository,
                ingresoRepository,
                identidadRepository,
                valoracionRepository,
                asignacionRepository,
                sedeRepository,
                pacienteRepository,
                usuarioRepository,
                profesionalRepository,
                auditoriaService,
                CLOCK_FIJO
        );

        enfermeroMock = new Usuario(
                10L, "enf-pub-id", "enfermera@hospital.com", "hash",
                "ACTIVO", 0, null, AHORA, false, false, null, null
        );

        sedeMock = new Sede(
                1L, 1L, "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "Calle 16 # 15-20", "Valledupar", "ACTIVO"
        );

        pacienteMock = new Paciente(
                50L, 20L, "pac-pub-id", "CC", "12345678",
                "Juan", "Gomez", LocalDate.of(1995, 5, 20), "3001234567",
                AHORA, AHORA
        );
    }

    @Test
    @DisplayName("U02: Admisión de urgencia de paciente identificado exitosa")
    void registrarAdmision_pacienteIdentificado_exito() {
        RegistrarAdmisionUrgenciaRequest req = new RegistrarAdmisionUrgenciaRequest(
                "sede-valledupar-pub", "pac-pub-id", false,
                "ESPONTANEO", "Dolor toracico agudo", "Pedro Perez", "3111111111",
                "Sin observaciones", null, null, null, null
        );

        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(sedeRepository.buscarPorPublicId("sede-valledupar-pub")).thenReturn(Optional.of(sedeMock));
        when(pacienteRepository.buscarPorPublicId("pac-pub-id")).thenReturn(Optional.of(pacienteMock));

        EpisodioAtencion epCreado = new EpisodioAtencion(
                100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Dolor toracico agudo", AHORA, AHORA
        );
        when(episodioRepository.guardar(any())).thenReturn(epCreado);

        EpisodioUrgenciaResponse detalleMock = new EpisodioUrgenciaResponse(
                "ep-pub-id", "pac-pub-id", "Juan Gomez", "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "URGENCIA", "REGISTRADO", false, null, null, "ESPONTANEO", "Dolor toracico agudo",
                "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodioRepository.buscarDetallePorPublicId(anyString())).thenReturn(Optional.of(detalleMock));

        EpisodioUrgenciaResponse resp = emergencyService.registrarAdmisionUrgencia(req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.episodioPublicId()).isEqualTo("ep-pub-id");
        assertThat(resp.estado()).isEqualTo("REGISTRADO");
        assertThat(resp.esIdentidadProvisional()).isFalse();
        verify(ingresoRepository).guardar(any());
        verify(auditoriaService).auditar(any());
    }

    @Test
    @DisplayName("U02/U03: Admisión de paciente no identificado (NN) genera código provisional opaco")
    void registrarAdmision_pacienteProvisionalNN_exito() {
        RegistrarAdmisionUrgenciaRequest req = new RegistrarAdmisionUrgenciaRequest(
                "sede-valledupar-pub", null, true,
                "AMBULANCIA", "Paciente inconsciente encontrado en via publica",
                null, null, "Trauma craneoencefalico aparente",
                "Tatuaje en brazo izquierdo, contextura media", 35, "MASCULINO", "INCONSCIENTE"
        );

        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(sedeRepository.buscarPorPublicId("sede-valledupar-pub")).thenReturn(Optional.of(sedeMock));

        EpisodioAtencion epCreado = new EpisodioAtencion(
                101L, "ep-nn-id", null, "URGENCIA", 1L, "REGISTRADO", AHORA, null,
                "Paciente inconsciente", AHORA, AHORA
        );
        when(episodioRepository.guardar(any())).thenReturn(epCreado);

        EpisodioUrgenciaResponse detalleMock = new EpisodioUrgenciaResponse(
                "ep-nn-id", null, null, "sede-valledupar-pub", "Hospital Rosario Pumarejo",
                "URGENCIA", "REGISTRADO", true, "NN-20261010-ABCD", "PROVISIONAL",
                "AMBULANCIA", "Paciente inconsciente", "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodioRepository.buscarDetallePorPublicId(anyString())).thenReturn(Optional.of(detalleMock));

        EpisodioUrgenciaResponse resp = emergencyService.registrarAdmisionUrgencia(req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.esIdentidadProvisional()).isTrue();
        assertThat(resp.codigoProvisional()).startsWith("NN-");
        verify(identidadRepository).guardar(any());
    }

    @Test
    @DisplayName("U03: Reconciliación de identidad provisional hacia paciente verificado")
    void reconciliarIdentidad_pacienteNN_vinculaCorrectamente() {
        ReconciliarIdentidadRequest req = new ReconciliarIdentidadRequest("pac-pub-id", "Llego familiar con cedula original");

        EpisodioAtencion epExistente = new EpisodioAtencion(
                101L, "ep-nn-id", null, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA
        );
        IdentidadProvisional idProv = new IdentidadProvisional(
                5L, "id-prov-pub", 101L, "NN-20261010-ABCD", "Descripcion", 30, "MASCULINO",
                "INCONSCIENTE", "PROVISIONAL", null, null, null, AHORA
        );

        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodioRepository.buscarPorPublicId("ep-nn-id")).thenReturn(Optional.of(epExistente));
        when(identidadRepository.buscarPorEpisodioId(101L)).thenReturn(Optional.of(idProv));
        when(pacienteRepository.buscarPorPublicId("pac-pub-id")).thenReturn(Optional.of(pacienteMock));

        EpisodioUrgenciaResponse detalleReconciliado = new EpisodioUrgenciaResponse(
                "ep-nn-id", "pac-pub-id", "Juan Gomez", "sede-pub", "Hospital", "URGENCIA",
                "REGISTRADO", true, "NN-20261010-ABCD", "VINCULADA", "AMBULANCIA",
                "Motivo", "PENDIENTE_VALORACION", null, AHORA, null, AHORA
        );
        when(episodioRepository.buscarDetallePorPublicId("ep-nn-id")).thenReturn(Optional.of(detalleReconciliado));

        EpisodioUrgenciaResponse resp = emergencyService.reconciliarIdentidad("ep-nn-id", req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.identidadEstado()).isEqualTo("VINCULADA");
        verify(identidadRepository).reconciliarIdentidad(eq(5L), eq(50L), eq(10L), any());
        verify(episodioRepository).vincularPaciente(101L, 50L);
    }

    @Test
    @DisplayName("U03: Reconciliar identidad ya vinculada lanza ConflictoOperacionException")
    void reconciliarIdentidad_yaVinculada_lanzaConflicto() {
        ReconciliarIdentidadRequest req = new ReconciliarIdentidadRequest("pac-pub-id", "Segunda verificacion");
        EpisodioAtencion epExistente = new EpisodioAtencion(101L, "ep-nn-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        IdentidadProvisional idProv = new IdentidadProvisional(5L, "id-prov-pub", 101L, "NN-20261010-ABCD", null, null, null, null, "VINCULADA", 50L, 10L, AHORA, AHORA);

        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodioRepository.buscarPorPublicId("ep-nn-id")).thenReturn(Optional.of(epExistente));
        when(identidadRepository.buscarPorEpisodioId(101L)).thenReturn(Optional.of(idProv));

        assertThatThrownBy(() -> emergencyService.reconciliarIdentidad("ep-nn-id", req, "enf-pub-id", "127.0.0.1"))
                .isInstanceOf(ConflictoOperacionException.class)
                .hasMessageContaining("ya fue reconciliada");
    }

    @Test
    @DisplayName("U04: Registro de valoración de triaje presencial actualiza estado a EN_ATENCION para Nivel I")
    void registrarValoracionTriaje_nivelI_actualizaEstadoAEnAtencion() {
        RegistrarValoracionTriajeRequest req = new RegistrarValoracionTriajeRequest(
                "I", "Paro cardiorrespiratorio inminente", "Cianosis severa",
                "70/40", 140, 35, 82, new BigDecimal("35.5"), 5, false, null
        );

        EpisodioAtencion ep = new EpisodioAtencion(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "REGISTRADO", AHORA, null, "Motivo", AHORA, AHORA);
        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodioRepository.buscarPorPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(valoracionRepository.obtenerSiguienteVersion(100L)).thenReturn(1);

        ValoracionTriajeResponse resp = emergencyService.registrarValoracionTriaje(
                "ep-pub-id", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        );

        assertThat(resp).isNotNull();
        assertThat(resp.nivel()).isEqualTo("I");
        verify(valoracionRepository).guardar(any());
        verify(episodioRepository).actualizarEstado(100L, "EN_ATENCION");
    }

    @Test
    @DisplayName("U04: Usuario sin rol clínico (ej. ADMIN o PACIENTE) es rechazado con 403 al intentar triaje presencial")
    void registrarValoracionTriaje_rolNoAutorizado_lanzaAccesoNoAutorizado() {
        RegistrarValoracionTriajeRequest req = new RegistrarValoracionTriajeRequest(
                "III", "Cefalea", null, null, null, null, null, null, null, false, null
        );

        assertThatThrownBy(() -> emergencyService.registrarValoracionTriaje(
                "ep-pub-id", req, "admin-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR")), "127.0.0.1"
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Solo personal de enfermería o profesionales asistenciales");
    }

    @Test
    @DisplayName("U06: Asignación de equipo asistencial (médico tratante) inactiva asignación previa")
    void asignarEquipoAsistencial_medicoTratante_inactivaPreviaYAsigna() {
        AsignarEquipoRequest req = new AsignarEquipoRequest("prof-pub-id", "MEDICO_TRATANTE");
        EpisodioAtencion ep = new EpisodioAtencion(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "EN_TRIAJE", AHORA, null, "Motivo", AHORA, AHORA);
        Profesional profMock = new Profesional(30L, 15L, "prof-pub-id", 1L, "CC", "998877", "RM-1234", "Carlos", "Medina", "311", AHORA, AHORA);

        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodioRepository.buscarPorPublicId("ep-pub-id")).thenReturn(Optional.of(ep));
        when(profesionalRepository.buscarPorPublicId("prof-pub-id")).thenReturn(Optional.of(profMock));

        emergencyService.asignarEquipoAsistencial("ep-pub-id", req, "enf-pub-id", "127.0.0.1");

        verify(asignacionRepository).inactivarAsignacionPreviaPorFuncion(100L, "MEDICO_TRATANTE");
        verify(asignacionRepository).guardar(any());
        verify(episodioRepository).actualizarEstado(100L, "EN_ATENCION");
    }

    @Test
    @DisplayName("U05: Listar cola de urgencias ordena por nivel y tiempo de espera")
    void listarColaUrgencias_retornaItemsOrdenados() {
        ItemColaUrgenciaResponse item1 = new ItemColaUrgenciaResponse(
                "ep-1", "pac-1", "Carlos Sanchez", null, "sede-pub", "Hospital",
                "URGENCIA", "EN_ATENCION", "I", "Paro", 5L, AHORA, "Dr. Medina"
        );
        ItemColaUrgenciaResponse item2 = new ItemColaUrgenciaResponse(
                "ep-2", null, "NN (NN-20261010-ABCD)", "NN-20261010-ABCD", "sede-pub", "Hospital",
                "URGENCIA", "EN_TRIAJE", "II", "Dolor pecho", 12L, AHORA, null
        );

        when(sedeRepository.buscarPorPublicId("sede-pub")).thenReturn(Optional.of(sedeMock));
        when(episodioRepository.listarColaUrgenciaPorSede(1L, null)).thenReturn(List.of(item1, item2));

        List<ItemColaUrgenciaResponse> cola = emergencyService.listarColaUrgencias("sede-pub", null);

        assertThat(cola).hasSize(2);
        assertThat(cola.get(0).nivelTriaje()).isEqualTo("I");
        assertThat(cola.get(1).codigoProvisional()).isEqualTo("NN-20261010-ABCD");
    }

    @Test
    @DisplayName("U06: Cierre de episodio marca estado EGRESADO exitosamente")
    void cerrarEpisodio_exito() {
        EpisodioAtencion ep = new EpisodioAtencion(100L, "ep-pub-id", 50L, "URGENCIA", 1L, "EN_ATENCION", AHORA, null, "Motivo", AHORA, AHORA);
        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(episodioRepository.buscarPorPublicId("ep-pub-id")).thenReturn(Optional.of(ep));

        emergencyService.cerrarEpisodio("ep-pub-id", "enf-pub-id", "127.0.0.1");

        verify(episodioRepository).cerrarEpisodio(eq(100L), any());
    }
}

package com.meditriaje.service;

import com.meditriaje.dto.hospital.*;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HospitalServiceTest {

    @Mock private AreaHospitalariaRepository areaRepository;
    @Mock private HabitacionSalaRepository habitacionRepository;
    @Mock private CamaHospitalariaRepository camaRepository;
    @Mock private OcupacionCamaRepository ocupacionRepository;
    @Mock private MovimientoPacienteRepository movimientoRepository;
    @Mock private ProcedimientoHospitalarioRepository procedimientoRepository;
    @Mock private EgresoHospitalarioRepository egresoRepository;
    @Mock private EpisodioAtencionRepository episodioRepository;
    @Mock private SedeRepository sedeRepository;
    @Mock private UsuarioRepository usuarioRepository;
    @Mock private ProfesionalRepository profesionalRepository;
    @Mock private AuditoriaService auditoriaService;

    private HospitalService hospitalService;

    private static final Instant AHORA = Instant.parse("2026-10-10T02:00:00Z");
    private static final Clock CLOCK = Clock.fixed(AHORA, ZoneId.of("UTC"));

    private Usuario enfermeroMock;
    private Usuario medicoUsuarioMock;
    private Profesional medicoMock;
    private EpisodioAtencion episodioMock;
    private CamaHospitalaria camaDispMock;
    private HabitacionSala habitacionMock;
    private AreaHospitalaria areaMock;
    private Sede sedeMock;

    @BeforeEach
    void setUp() {
        hospitalService = new HospitalService(
                areaRepository,
                habitacionRepository,
                camaRepository,
                ocupacionRepository,
                movimientoRepository,
                procedimientoRepository,
                egresoRepository,
                episodioRepository,
                sedeRepository,
                usuarioRepository,
                profesionalRepository,
                auditoriaService,
                CLOCK
        );

        enfermeroMock = new Usuario(10L, "enf-pub-id", "enfermera@hospital.com", "hash", "ACTIVO", 0, null, AHORA, false, false, null, null);
        medicoUsuarioMock = new Usuario(20L, "med-pub-id", "medico@hospital.com", "hash", "ACTIVO", 0, null, AHORA, false, false, null, null);
        medicoMock = new Profesional(5L, 20L, "med-prof-pub-id", 1L, "CC", "1234", "RM-999", "Roberto", "Gomez", "300", AHORA, AHORA);

        sedeMock = new Sede(1L, 1L, "sede-pub", "Hospital Central", "Calle 16", "Valledupar", "ACTIVO");
        areaMock = new AreaHospitalaria(1L, "area-pub", 1L, "URG", "Pabellón de Urgencias", "URGENCIAS", "1", "ACTIVA", AHORA);
        habitacionMock = new HabitacionSala(2L, "hab-pub", 1L, "OBS-01", "COMPARTIDA", "ACTIVA", AHORA);
        camaDispMock = new CamaHospitalaria(3L, "cama-pub", 2L, "CAMA-01", "DISPONIBLE", AHORA, AHORA);

        episodioMock = new EpisodioAtencion(100L, "ep-pub", 50L, "URGENCIA", 1L, "EN_TRIAJE", AHORA, null, "Dolor agudo", AHORA, AHORA);
    }

    @Test
    @DisplayName("H01/H02: Asignación de cama disponible a episodio exitosa")
    void asignarCama_disponible_exito() {
        AsignarCamaRequest req = new AsignarCamaRequest("cama-pub", "Paciente requiere observación inmediata");

        when(episodioRepository.buscarPorPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(camaRepository.buscarPorPublicId("cama-pub")).thenReturn(Optional.of(camaDispMock));
        when(ocupacionRepository.buscarActivaPorCamaId(3L)).thenReturn(Optional.empty());
        when(habitacionRepository.buscarPorId(2L)).thenReturn(Optional.of(habitacionMock));
        when(ocupacionRepository.buscarActivaPorEpisodioId(100L)).thenReturn(Optional.empty());

        OcupacionCama ocCreada = new OcupacionCama(1L, "oc-pub", 100L, 3L, AHORA, null, "ACTIVA", 10L, "Obs", AHORA);
        when(ocupacionRepository.guardar(any())).thenReturn(ocCreada);

        CamaDetalleResponse detalle = new CamaDetalleResponse("cama-pub", "hab-pub", "OBS-01", "area-pub", "Urgencias", "sede-pub", "Hospital", "CAMA-01", "OCUPADA", "ep-pub", "Juan Gomez", "oc-pub");
        when(camaRepository.buscarDetallePorPublicId("cama-pub")).thenReturn(Optional.of(detalle));

        CamaDetalleResponse resp = hospitalService.asignarCama("ep-pub", req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.estado()).isEqualTo("OCUPADA");
        verify(camaRepository).actualizarEstado(3L, "OCUPADA");
        verify(movimientoRepository).guardar(any());
        verify(episodioRepository).actualizarEstado(100L, "OBSERVACION");
        verify(auditoriaService).auditar(any());
    }

    @Test
    @DisplayName("H02: Conflicto si la cama seleccionada no está DISPONIBLE")
    void asignarCama_noDisponible_lanzaConflicto() {
        AsignarCamaRequest req = new AsignarCamaRequest("cama-pub", "Asignación");
        CamaHospitalaria camaOcupada = new CamaHospitalaria(3L, "cama-pub", 2L, "CAMA-01", "OCUPADA", AHORA, AHORA);

        when(episodioRepository.buscarPorPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(camaRepository.buscarPorPublicId("cama-pub")).thenReturn(Optional.of(camaOcupada));

        assertThatThrownBy(() -> hospitalService.asignarCama("ep-pub", req, "enf-pub-id", "127.0.0.1"))
                .isInstanceOf(ConflictoOperacionException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    @DisplayName("H03: Traslado intrahospitalario longitudinal finaliza cama anterior y asigna destino")
    void trasladarPaciente_exito() {
        TrasladarPacienteRequest req = new TrasladarPacienteRequest("cama-dest-pub", "Traslado a UCI");
        OcupacionCama ocupActual = new OcupacionCama(1L, "oc-1", 100L, 3L, AHORA, null, "ACTIVA", 10L, "Obs", AHORA);
        CamaHospitalaria camaDest = new CamaHospitalaria(4L, "cama-dest-pub", 2L, "CAMA-02", "DISPONIBLE", AHORA, AHORA);

        when(episodioRepository.buscarPorPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(usuarioRepository.buscarPorPublicId("enf-pub-id")).thenReturn(Optional.of(enfermeroMock));
        when(ocupacionRepository.buscarActivaPorEpisodioId(100L)).thenReturn(Optional.of(ocupActual));
        when(camaRepository.buscarPorPublicId("cama-dest-pub")).thenReturn(Optional.of(camaDest));
        when(camaRepository.buscarPorId(3L)).thenReturn(Optional.of(camaDispMock));
        when(habitacionRepository.buscarPorId(2L)).thenReturn(Optional.of(habitacionMock));

        MovimientoResponse resp = hospitalService.trasladarPaciente("ep-pub", req, "enf-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        verify(ocupacionRepository).finalizarOcupacion(1L, AHORA);
        verify(camaRepository).actualizarEstado(3L, "LIMPIEZA");
        verify(camaRepository).actualizarEstado(4L, "OCUPADA");
        verify(movimientoRepository).guardar(any());
    }

    @Test
    @DisplayName("H04: Registro de procedimiento clínico actualiza episodio a QUIROFANO")
    void registrarProcedimiento_exito() {
        RegistrarProcedimientoRequest req = new RegistrarProcedimientoRequest(
                "APENDICECTOMIA", "Intervención urgente por apendicitis aguda", "med-prof-pub-id", "hab-pub", "Sin complicaciones iniciales"
        );

        when(episodioRepository.buscarPorPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(usuarioRepository.buscarPorPublicId("med-pub-id")).thenReturn(Optional.of(medicoUsuarioMock));
        when(profesionalRepository.buscarPorPublicId("med-prof-pub-id")).thenReturn(Optional.of(medicoMock));
        when(habitacionRepository.buscarPorPublicId("hab-pub")).thenReturn(Optional.of(habitacionMock));

        ProcedimientoResponse resp = hospitalService.registrarProcedimiento("ep-pub", req, "med-pub-id", "127.0.0.1");

        assertThat(resp).isNotNull();
        assertThat(resp.tipoProcedimiento()).isEqualTo("APENDICECTOMIA");
        verify(procedimientoRepository).guardar(any());
        verify(episodioRepository).actualizarEstado(100L, "QUIROFANO");
    }

    @Test
    @DisplayName("H05: Egreso hospitalario libera cama, cierra episodio y audita evento")
    void registrarEgresoHospitalario_medico_liberaCamaYEgresa() {
        RegistrarEgresoRequest req = new RegistrarEgresoRequest(
                "ALTA_DOMICILIO", "Resolución favorable de cuadro agudo", "Paciente afebril con signos estables", "Reposo 5 días y amoxicilina"
        );

        OcupacionCama ocActiva = new OcupacionCama(1L, "oc-1", 100L, 3L, AHORA, null, "ACTIVA", 10L, "Obs", AHORA);

        when(episodioRepository.buscarPorPublicId("ep-pub")).thenReturn(Optional.of(episodioMock));
        when(usuarioRepository.buscarPorPublicId("med-pub-id")).thenReturn(Optional.of(medicoUsuarioMock));
        when(profesionalRepository.buscarPorUsuarioId(20L)).thenReturn(Optional.of(medicoMock));
        when(ocupacionRepository.buscarActivaPorEpisodioId(100L)).thenReturn(Optional.of(ocActiva));

        hospitalService.registrarEgresoHospitalario(
                "ep-pub", req, "med-pub-id", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")), "127.0.0.1"
        );

        verify(ocupacionRepository).finalizarOcupacion(1L, AHORA);
        verify(camaRepository).actualizarEstado(3L, "LIMPIEZA");
        verify(egresoRepository).guardar(any());
        verify(episodioRepository).cerrarEpisodio(100L, AHORA);
    }

    @Test
    @DisplayName("H05: Usuario sin rol médico (ej. enfermero) es rechazado con 403 al intentar dar egreso definitivo")
    void registrarEgresoHospitalario_noMedico_lanzaAccesoNoAutorizado() {
        RegistrarEgresoRequest req = new RegistrarEgresoRequest("ALTA_DOMICILIO", "Diag", "Epicrisis", "Plan");

        assertThatThrownBy(() -> hospitalService.registrarEgresoHospitalario(
                "ep-pub", req, "enf-pub-id", List.of(new SimpleGrantedAuthority("ROLE_ENFERMERIA")), "127.0.0.1"
        ))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Solo profesionales médicos pueden emitir órdenes de egreso");
    }

    @Test
    @DisplayName("H06: Centro de Control calcula censo de camas e indicadores de ocupación")
    void obtenerCensoCamas_calculaOcupacionCorrectamente() {
        when(sedeRepository.buscarPorPublicId("sede-pub")).thenReturn(Optional.of(sedeMock));

        CamaDetalleResponse c1 = new CamaDetalleResponse("c-1", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C1", "OCUPADA", "ep-1", "Paciente 1", "oc-1");
        CamaDetalleResponse c2 = new CamaDetalleResponse("c-2", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C2", "DISPONIBLE", null, null, null);
        CamaDetalleResponse c3 = new CamaDetalleResponse("c-3", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C3", "LIMPIEZA", null, null, null);
        CamaDetalleResponse c4 = new CamaDetalleResponse("c-4", "h-1", "H1", "a-1", "Urgencias", "sede-pub", "Hospital", "C4", "MANTENIMIENTO", null, null, null);

        when(camaRepository.listarDetallePorSedeId(1L)).thenReturn(List.of(c1, c2, c3, c4));
        when(areaRepository.listarPorSedeId(1L)).thenReturn(List.of(areaMock));

        CensoCamasResponse censo = hospitalService.obtenerCensoCamas("sede-pub");

        assertThat(censo).isNotNull();
        assertThat(censo.totalCamas()).isEqualTo(4);
        assertThat(censo.ocupadas()).isEqualTo(1);
        assertThat(censo.disponibles()).isEqualTo(1);
        assertThat(censo.enLimpieza()).isEqualTo(1);
        assertThat(censo.enMantenimiento()).isEqualTo(1);
        assertThat(censo.tasaOcupacionPorcentaje()).isEqualTo(25.0);
    }
}

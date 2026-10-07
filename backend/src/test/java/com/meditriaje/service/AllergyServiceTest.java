package com.meditriaje.service;

import com.meditriaje.dto.allergy.AlergiaResponse;
import com.meditriaje.dto.allergy.InactivarAlergiaRequest;
import com.meditriaje.dto.allergy.RegistrarAlergiaRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Alergia;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AlergiaRepository;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AllergyServiceTest {

    @Mock
    private AlergiaRepository alergiaRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private AtencionRepository atencionRepository;
    @Mock
    private AccesoClinicoService accesoClinicoService;
    @Mock
    private AuditoriaService auditoriaService;

    private Clock clock;
    private AllergyService allergyService;

    private final Instant now = Instant.parse("2026-10-06T15:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneId.of("America/Bogota"));
        allergyService = new AllergyService(
                alergiaRepository,
                pacienteRepository,
                usuarioRepository,
                profesionalRepository,
                atencionRepository,
                accesoClinicoService,
                auditoriaService,
                clock
        );
    }

    // =========================================================================
    // LISTADO POR PROFESIONAL
    // =========================================================================

    @Test
    @DisplayName("listarAlergiasPacienteParaProfesional: profesional con relación asistencial consulta y audita ALERGIA_CONSULTADA")
    void listarAlergias_profesionalConRelacion_exito() {
        Usuario usuario = new Usuario(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        AlergiaResponse response = new AlergiaResponse(
                "ale-pub-1", "pac-pub-1", "Penicilina", "Urticaria", "MODERADA", "ACTIVA", "PROFESIONAL", false, null, now, null, null
        );

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        when(alergiaRepository.listarPorPacientePublicId("pac-pub-1", false)).thenReturn(List.of(response));

        List<AlergiaResponse> resultado = allergyService.listarAlergiasPacienteParaProfesional(
                "pac-pub-1", false, "usr-med-1", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")), "127.0.0.1"
        );

        assertThat(resultado).hasSize(1);
        assertThat(resultado.get(0).sustancia()).isEqualTo("Penicilina");
        verify(accesoClinicoService).validarAccesoHistorialClinico(eq("usr-med-1"), eq("pac-pub-1"), any());

        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.ALERGIA_CONSULTADA);
        assertThat(evento.recursoPublicId()).isEqualTo("pac-pub-1");
    }

    @Test
    @DisplayName("listarAlergiasPacienteParaProfesional: sin autorización clínica lanza AccesoNoAutorizadoException")
    void listarAlergias_sinAutorizacionClinica_lanzaAccesoNoAutorizado() {
        Usuario usuario = new Usuario(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        doThrow(new AccesoNoAutorizadoException("No existe relación asistencial activa."))
                .when(accesoClinicoService).validarAccesoHistorialClinico(anyString(), anyString(), any());

        assertThatThrownBy(() -> allergyService.listarAlergiasPacienteParaProfesional(
                "pac-pub-1", false, "usr-med-1", List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL")), "127.0.0.1"
        )).isInstanceOf(AccesoNoAutorizadoException.class);

        verify(alergiaRepository, never()).listarPorPacientePublicId(anyString(), anyBoolean());
    }

    // =========================================================================
    // REGISTRO POR PROFESIONAL
    // =========================================================================

    @Test
    @DisplayName("registrarAlergiaPorProfesional: profesional con relación ordinaria registra exitosamente y audita ALERGIA_REGISTRADA")
    void registrarAlergia_profesionalConRelacionOrdinaria_exito() {
        Usuario usuario = new Usuario(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Profesional profesional = new Profesional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        doNothing().when(accesoClinicoService).validarEscrituraClinica(5L, 10L);
        when(alergiaRepository.existeActivaPorSustancia(10L, "Amoxicilina")).thenReturn(false);
        when(alergiaRepository.crear(any(Alergia.class))).thenReturn(50L);

        AlergiaResponse response = new AlergiaResponse(
                "ale-new-uuid", "pac-pub-1", "Amoxicilina", "Edema", "GRAVE", "ACTIVA", "PROFESIONAL", false, null, now, null, null
        );
        when(alergiaRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(response));

        RegistrarAlergiaRequest req = new RegistrarAlergiaRequest("Amoxicilina", "Edema", "GRAVE");
        AlergiaResponse creada = allergyService.registrarAlergiaPorProfesional("pac-pub-1", req, "usr-med-1", "127.0.0.1");

        assertThat(creada.sustancia()).isEqualTo("Amoxicilina");
        assertThat(creada.autorreportada()).isFalse();

        verify(accesoClinicoService).validarEscrituraClinica(5L, 10L);
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("registrarAlergiaPorProfesional: break-glass solo lectura es rechazado para registrar alergia (403)")
    void registrarAlergia_breakGlassSoloLectura_lanzaAccesoNoAutorizado() {
        Usuario usuario = new Usuario(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Profesional profesional = new Profesional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        doThrow(new AccesoNoAutorizadoException("El acceso Break-Glass es de solo lectura; no permite registrar ni modificar informacion clinica."))
                .when(accesoClinicoService).validarEscrituraClinica(5L, 10L);

        RegistrarAlergiaRequest req = new RegistrarAlergiaRequest("Aspirina", null, "MODERADA");

        assertThatThrownBy(() -> allergyService.registrarAlergiaPorProfesional("pac-pub-1", req, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Break-Glass es de solo lectura");

        verify(alergiaRepository, never()).crear(any());
    }

    @Test
    @DisplayName("registrarAlergiaPorProfesional: rechaza si la sustancia ya está activa para el paciente (400 DatosInvalidos)")
    void registrarAlergia_sustanciaActivaDuplicada_lanzaDatosInvalidos() {
        Usuario usuario = new Usuario(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Profesional profesional = new Profesional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(pacienteRepository.buscarPorPublicId("pac-pub-1")).thenReturn(Optional.of(paciente));
        when(alergiaRepository.existeActivaPorSustancia(10L, "Penicilina")).thenReturn(true);

        RegistrarAlergiaRequest req = new RegistrarAlergiaRequest("Penicilina", "Shock", "GRAVE");

        assertThatThrownBy(() -> allergyService.registrarAlergiaPorProfesional("pac-pub-1", req, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("ya cuenta con una alergia activa");

        verify(alergiaRepository, never()).crear(any());
    }

    // =========================================================================
    // INACTIVACIÓN POR PROFESIONAL
    // =========================================================================

    @Test
    @DisplayName("inactivarAlergiaPorProfesional: profesional con relación ordinaria inactiva exitosamente y audita ALERGIA_INACTIVADA")
    void inactivarAlergia_profesional_exito() {
        Usuario usuario = new Usuario(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Profesional profesional = new Profesional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Alergia alergia = new Alergia(20L, "ale-pub-1", 10L, "Penicilina", "Urticaria", "MODERADA", "ACTIVA", "PROFESIONAL", 1L, null, null, null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(alergiaRepository.buscarEntidadPorPublicId("ale-pub-1")).thenReturn(Optional.of(alergia));
        doNothing().when(accesoClinicoService).validarEscrituraClinica(5L, 10L);
        when(alergiaRepository.inactivar(eq(20L), eq(1L), eq("Prueba negativa de hipersensibilidad"), any())).thenReturn(1);

        AlergiaResponse response = new AlergiaResponse(
                "ale-pub-1", "pac-pub-1", "Penicilina", "Urticaria", "MODERADA", "INACTIVA", "PROFESIONAL", false, null, now, now, "Prueba negativa de hipersensibilidad"
        );
        when(alergiaRepository.buscarPorPublicId("ale-pub-1")).thenReturn(Optional.of(response));

        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Prueba negativa de hipersensibilidad");
        AlergiaResponse res = allergyService.inactivarAlergiaPorProfesional("ale-pub-1", req, "usr-med-1", "127.0.0.1");

        assertThat(res.estado()).isEqualTo("INACTIVA");
        assertThat(res.motivoInactivacion()).isEqualTo("Prueba negativa de hipersensibilidad");
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("inactivarAlergiaPorProfesional: alergia ya inactiva lanza DatosInvalidosException")
    void inactivarAlergia_yaInactiva_lanzaDatosInvalidos() {
        Usuario usuario = new Usuario(1L, "usr-med-1", "med@hospital.co", "hash", "ACTIVO", 0, null, now);
        Profesional profesional = new Profesional(5L, 1L, "prof-pub-1", 100L, "RM-123", "Carlos", "Med", now, now);
        Alergia alergia = new Alergia(20L, "ale-pub-1", 10L, "Penicilina", "Urticaria", "MODERADA", "INACTIVA", "PROFESIONAL", 1L, null, now, 1L, "Inactivada antes", now, now);

        when(usuarioRepository.buscarPorPublicId("usr-med-1")).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(alergiaRepository.buscarEntidadPorPublicId("ale-pub-1")).thenReturn(Optional.of(alergia));

        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Intento repetido de inactivacion");

        assertThatThrownBy(() -> allergyService.inactivarAlergiaPorProfesional("ale-pub-1", req, "usr-med-1", "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("ya se encuentra inactiva");

        verify(alergiaRepository, never()).inactivar(anyLong(), anyLong(), anyString(), any());
    }

    // =========================================================================
    // GESTIÓN POR PACIENTE (AUTORREPORTADAS)
    // =========================================================================

    @Test
    @DisplayName("listarMisAlergias: paciente consulta sus alergias y audita ALERGIA_CONSULTADA")
    void listarMisAlergias_paciente_exito() {
        Usuario usuario = new Usuario(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(alergiaRepository.listarPorPacientePublicId("pac-pub-1", true)).thenReturn(List.of());

        List<AlergiaResponse> lista = allergyService.listarMisAlergias("usr-pac-1", "127.0.0.1");

        assertThat(lista).isEmpty();
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("registrarMiAlergia: paciente declara alergia autorreportada (origen PACIENTE)")
    void registrarMiAlergia_paciente_exito() {
        Usuario usuario = new Usuario(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(alergiaRepository.existeActivaPorSustancia(10L, "Mariscos")).thenReturn(false);
        when(alergiaRepository.crear(any(Alergia.class))).thenReturn(60L);

        AlergiaResponse response = new AlergiaResponse(
                "ale-mar-1", "pac-pub-1", "Mariscos", "Prurito", "LEVE", "ACTIVA", "PACIENTE", true, null, now, null, null
        );
        when(alergiaRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(response));

        RegistrarAlergiaRequest req = new RegistrarAlergiaRequest("Mariscos", "Prurito", "LEVE");
        AlergiaResponse res = allergyService.registrarMiAlergia(req, "usr-pac-1", "127.0.0.1");

        assertThat(res.sustancia()).isEqualTo("Mariscos");
        assertThat(res.origen()).isEqualTo("PACIENTE");
        assertThat(res.autorreportada()).isTrue();

        ArgumentCaptor<Alergia> alergiaCaptor = ArgumentCaptor.forClass(Alergia.class);
        verify(alergiaRepository).crear(alergiaCaptor.capture());
        assertThat(alergiaCaptor.getValue().origen()).isEqualTo("PACIENTE");
        assertThat(alergiaCaptor.getValue().registradaPorUsuarioId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("inactivarMiAlergia: paciente puede inactivar su propia alergia autorreportada")
    void inactivarMiAlergia_autorreportadaPropia_exito() {
        Usuario usuario = new Usuario(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        Alergia alergia = new Alergia(30L, "ale-pac-30", 10L, "Chocolate", "Granos", "LEVE", "ACTIVA", "PACIENTE", 2L, null, null, null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(alergiaRepository.buscarEntidadPorPublicId("ale-pac-30")).thenReturn(Optional.of(alergia));
        when(alergiaRepository.inactivar(eq(30L), eq(2L), eq("Ya no me genera síntomas"), any())).thenReturn(1);

        AlergiaResponse response = new AlergiaResponse(
                "ale-pac-30", "pac-pub-1", "Chocolate", "Granos", "LEVE", "INACTIVA", "PACIENTE", true, null, now, now, "Ya no me genera síntomas"
        );
        when(alergiaRepository.buscarPorPublicId("ale-pac-30")).thenReturn(Optional.of(response));

        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Ya no me genera síntomas");
        AlergiaResponse res = allergyService.inactivarMiAlergia("ale-pac-30", req, "usr-pac-1", "127.0.0.1");

        assertThat(res.estado()).isEqualTo("INACTIVA");
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("inactivarMiAlergia: paciente NO puede inactivar una alergia diagnosticada por el profesional (403)")
    void inactivarMiAlergia_origenProfesional_lanzaAccesoNoAutorizado() {
        Usuario usuario = new Usuario(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        Alergia alergia = new Alergia(40L, "ale-med-40", 10L, "Penicilina", "Shock", "GRAVE", "ACTIVA", "PROFESIONAL", 1L, null, null, null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(alergiaRepository.buscarEntidadPorPublicId("ale-med-40")).thenReturn(Optional.of(alergia));

        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Deseo quitarla porque no me gusta");

        assertThatThrownBy(() -> allergyService.inactivarMiAlergia("ale-med-40", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("solo puede inactivar alergias autorreportadas");

        verify(alergiaRepository, never()).inactivar(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    @DisplayName("inactivarMiAlergia: paciente intentando inactivar alergia de otro paciente lanza AccesoNoAutorizadoException (403)")
    void inactivarMiAlergia_otroPaciente_lanzaAccesoNoAutorizado() {
        Usuario usuario = new Usuario(2L, "usr-pac-1", "pac@test.co", "hash", "ACTIVO", 0, null, now);
        Paciente paciente = new Paciente(10L, 2L, "pac-pub-1", "CC", "123456", "Juan", "Perez", null, null, now, now);
        Alergia alergiaAjena = new Alergia(50L, "ale-ajena-50", 99L, "Polen", "Rinitis", "LEVE", "ACTIVA", "PACIENTE", 99L, null, null, null, null, now, now);

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(alergiaRepository.buscarEntidadPorPublicId("ale-ajena-50")).thenReturn(Optional.of(alergiaAjena));

        InactivarAlergiaRequest req = new InactivarAlergiaRequest("Intento no autorizado");

        assertThatThrownBy(() -> allergyService.inactivarMiAlergia("ale-ajena-50", req, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("otro paciente");

        verify(alergiaRepository, never()).inactivar(anyLong(), anyLong(), anyString(), any());
    }
}

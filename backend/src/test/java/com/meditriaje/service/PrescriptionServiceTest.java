package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.prescription.CrearRecetaDetalleRequest;
import com.meditriaje.dto.prescription.CrearRecetaRequest;
import com.meditriaje.dto.prescription.MedicamentoResponse;
import com.meditriaje.dto.prescription.RecetaDetalleResponse;
import com.meditriaje.dto.prescription.RecetaResponse;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Atencion;
import com.meditriaje.model.EventoAuditoria;
import com.meditriaje.model.Medicamento;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Receta;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.AtencionRepository;
import com.meditriaje.repository.MedicamentoRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.RecetaRepository;
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
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrescriptionServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private AtencionRepository atencionRepository;

    @Mock
    private MedicamentoRepository medicamentoRepository;

    @Mock
    private RecetaRepository recetaRepository;

    @Mock
    private AccesoClinicoService accesoClinicoService;

    @Mock
    private AuditoriaService auditoriaService;

    private final Clock fixedClock = Clock.fixed(Instant.parse("2026-10-03T10:00:00Z"), ZoneId.of("America/Bogota"));

    private PrescriptionService service;

    private static final String USUARIO_PROF_UUID = "usr-prof-uuid";
    private static final String USUARIO_PACIENTE_UUID = "usr-pac-uuid";
    private static final String USUARIO_ADMIN_UUID = "usr-admin-uuid";

    private static final Long PROFESIONAL_ID = 10L;
    private static final Long PACIENTE_ID = 20L;
    private static final Long ATENCION_ID = 30L;

    @BeforeEach
    void setUp() {
        service = new PrescriptionService(
                usuarioRepository,
                profesionalRepository,
                pacienteRepository,
                atencionRepository,
                medicamentoRepository,
                recetaRepository,
                accesoClinicoService,
                auditoriaService,
                fixedClock
        );
    }

    @Test
    @DisplayName("emitirReceta - Emisión exitosa con snapshot de medicamentos y auditoría obligatoria CREACION_RECETA")
    void emitirReceta_exito_emiteRecetaYAudita() {
        Usuario usuario = new Usuario(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Profesional profesional = new Profesional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);
        Atencion atencion = new Atencion(ATENCION_ID, "atencion-uuid", 100L, PACIENTE_ID, PROFESIONAL_ID, "CERRADA");

        Medicamento med = new Medicamento(
                50L,
                "med-uuid-1",
                "MED-ACE-500",
                "Acetaminofen",
                "Acetaminofen",
                "Tableta",
                "500 mg",
                "ACTIVO"
        );

        CrearRecetaDetalleRequest detalleReq = new CrearRecetaDetalleRequest(
                "med-uuid-1",
                "500 mg",
                "Cada 8 horas",
                5,
                15,
                "Tomar despues de comidas"
        );
        CrearRecetaRequest request = new CrearRecetaRequest("atencion-uuid", 30, List.of(detalleReq));

        when(usuarioRepository.buscarPorPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(atencionRepository.buscarEntidadPorPublicId("atencion-uuid")).thenReturn(Optional.of(atencion));
        when(medicamentoRepository.buscarPorPublicId("med-uuid-1")).thenReturn(Optional.of(med));
        when(recetaRepository.crearReceta(any(Receta.class))).thenReturn(555L);

        RecetaDetalleResponse detalleResp = new RecetaDetalleResponse(
                "med-uuid-1",
                "MED-ACE-500",
                "Acetaminofen",
                "Acetaminofen",
                "Tableta",
                "500 mg",
                "500 mg",
                "Cada 8 horas",
                5,
                15,
                "Tomar despues de comidas"
        );
        RecetaResponse recetaResponse = new RecetaResponse(
                "receta-uuid-1",
                "atencion-uuid",
                "pac-uuid",
                "Juan Perez",
                "prof-uuid",
                "Carlos Gomez",
                "Medicina General",
                30,
                Instant.now(),
                List.of(detalleResp)
        );
        when(recetaRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(recetaResponse));

        RecetaResponse resultado = service.emitirReceta(request, USUARIO_PROF_UUID, "192.168.1.1");

        assertThat(resultado).isNotNull();
        assertThat(resultado.publicId()).isEqualTo("receta-uuid-1");
        assertThat(resultado.detalles()).hasSize(1);
        assertThat(resultado.detalles().get(0).nombreComercial()).isEqualTo("Acetaminofen");

        // Verificar que los detalles persistidos congelan el snapshot
        verify(recetaRepository).guardarDetalles(eq(555L), anyList());

        // Verificar auditoría inmutable
        ArgumentCaptor<EventoAuditoria> eventoCaptor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(eventoCaptor.capture());
        EventoAuditoria evento = eventoCaptor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.CREACION_RECETA);
        assertThat(evento.tipoRecurso()).isEqualTo("RECETA");
        assertThat(evento.usuarioId()).isEqualTo(1L);
        assertThat(evento.ipOrigen()).isEqualTo("192.168.1.1");
    }

    @Test
    @DisplayName("emitirReceta - Rechazo cuando medicamento no existe o no está activo")
    void emitirReceta_medicamentoInactivoOInexistente_lanzaExcepcion() {
        Usuario usuario = new Usuario(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Profesional profesional = new Profesional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);
        Atencion atencion = new Atencion(ATENCION_ID, "atencion-uuid", 100L, PACIENTE_ID, PROFESIONAL_ID, "CERRADA");

        Medicamento medInactivo = new Medicamento(
                50L,
                "med-inactivo-uuid",
                "MED-INA-100",
                "InactivoMed",
                "Inactivo",
                "Tableta",
                "100 mg",
                "INACTIVO"
        );

        CrearRecetaDetalleRequest detalleReq = new CrearRecetaDetalleRequest(
                "med-inactivo-uuid",
                "100 mg",
                "Cada 12 horas",
                3,
                6,
                null
        );
        CrearRecetaRequest request = new CrearRecetaRequest("atencion-uuid", 30, List.of(detalleReq));

        when(usuarioRepository.buscarPorPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(atencionRepository.buscarEntidadPorPublicId("atencion-uuid")).thenReturn(Optional.of(atencion));
        when(medicamentoRepository.buscarPorPublicId("med-inactivo-uuid")).thenReturn(Optional.of(medInactivo));

        assertThatThrownBy(() -> service.emitirReceta(request, USUARIO_PROF_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no existe o no se encuentra activo");

        verify(recetaRepository, never()).crearReceta(any());
        verify(auditoriaService, never()).auditar(any());
    }

    @Test
    @DisplayName("emitirReceta - Rechazo si usuario autenticado no está registrado como profesional")
    void emitirReceta_usuarioNoProfesional_lanza403() {
        Usuario usuario = new Usuario(2L, "usr-pac-1", "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.empty());

        CrearRecetaRequest request = new CrearRecetaRequest("atencion-uuid", 30, List.of(
                new CrearRecetaDetalleRequest("med-uuid-1", "500 mg", "8h", 3, 9, null)
        ));

        assertThatThrownBy(() -> service.emitirReceta(request, "usr-pac-1", "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no registrado como profesional asistencial");
    }

    @Test
    @DisplayName("emitirReceta - Médico no autor valida relación asistencial y es rechazado si no la tiene")
    void emitirReceta_medicoNoAutorSinRelacion_lanza403() {
        Usuario usuario = new Usuario(1L, USUARIO_PROF_UUID, "dr.otro@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Profesional profesional = new Profesional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Otro", "Medico", Instant.now(), null);
        // La atención fue realizada por otro profesional (ID 99L)
        Atencion atencion = new Atencion(ATENCION_ID, "atencion-uuid", 100L, PACIENTE_ID, 99L, "CERRADA");

        when(usuarioRepository.buscarPorPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(atencionRepository.buscarEntidadPorPublicId("atencion-uuid")).thenReturn(Optional.of(atencion));

        doThrow(new AccesoNoAutorizadoException("El profesional no cuenta con una relacion asistencial activa con el paciente."))
                .when(accesoClinicoService).validarRelacionAsistencial(PROFESIONAL_ID, PACIENTE_ID);

        CrearRecetaRequest request = new CrearRecetaRequest("atencion-uuid", 30, List.of(
                new CrearRecetaDetalleRequest("med-uuid-1", "500 mg", "8h", 3, 9, null)
        ));

        assertThatThrownBy(() -> service.emitirReceta(request, USUARIO_PROF_UUID, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("relacion asistencial activa");

        verify(recetaRepository, never()).crearReceta(any());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Profesional autor consulta exitosamente")
    void obtenerPorPublicId_medicoAutor_exito() {
        Receta receta = new Receta(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, 30, Instant.now());
        Usuario usuario = new Usuario(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Profesional profesional = new Profesional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);

        RecetaResponse recetaResponse = new RecetaResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid", "Juan", "prof-uuid", "Carlos", "General", 30, Instant.now(), List.of()
        );

        when(recetaRepository.buscarEntidadPorPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(usuarioRepository.buscarPorPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        when(recetaRepository.buscarPorPublicId("receta-uuid-1")).thenReturn(Optional.of(recetaResponse));

        RecetaResponse resp = service.obtenerPorPublicId(
                "receta-uuid-1",
                USUARIO_PROF_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isEqualTo("receta-uuid-1");
        verify(accesoClinicoService, never()).validarRelacionAsistencial(anyLong(), anyLong());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Profesional no autor con relación activa consulta exitosamente")
    void obtenerPorPublicId_medicoNoAutorConRelacion_exito() {
        Receta receta = new Receta(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, 99L, 30, Instant.now());
        Usuario usuario = new Usuario(1L, USUARIO_PROF_UUID, "dr.carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Profesional profesional = new Profesional(PROFESIONAL_ID, 1L, "prof-uuid", 1L, "RM-12345", "Carlos", "Gomez", Instant.now(), null);

        RecetaResponse recetaResponse = new RecetaResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid", "Juan", "prof-uuid-99", "Otro", "General", 30, Instant.now(), List.of()
        );

        when(recetaRepository.buscarEntidadPorPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(usuarioRepository.buscarPorPublicId(USUARIO_PROF_UUID)).thenReturn(Optional.of(usuario));
        when(profesionalRepository.buscarPorUsuarioId(1L)).thenReturn(Optional.of(profesional));
        doNothing().when(accesoClinicoService).validarRelacionAsistencial(PROFESIONAL_ID, PACIENTE_ID);
        when(recetaRepository.buscarPorPublicId("receta-uuid-1")).thenReturn(Optional.of(recetaResponse));

        RecetaResponse resp = service.obtenerPorPublicId(
                "receta-uuid-1",
                USUARIO_PROF_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PROFESIONAL"))
        );

        assertThat(resp).isNotNull();
        verify(accesoClinicoService).validarRelacionAsistencial(PROFESIONAL_ID, PACIENTE_ID);
    }

    @Test
    @DisplayName("obtenerPorPublicId - Administrador es rechazado categóricamente (ADR-007)")
    void obtenerPorPublicId_admin_lanza403() {
        Receta receta = new Receta(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, 30, Instant.now());
        when(recetaRepository.buscarEntidadPorPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));

        assertThatThrownBy(() -> service.obtenerPorPublicId(
                "receta-uuid-1",
                USUARIO_ADMIN_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"))
        )).isInstanceOf(AccesoNoAutorizadoException.class)
          .hasMessageContaining("El personal administrativo no tiene acceso a recetas ni contenido clinico");

        verify(recetaRepository, never()).buscarPorPublicId(anyString());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Paciente ajeno es rechazado (403)")
    void obtenerPorPublicId_pacienteAjeno_lanza403() {
        Receta receta = new Receta(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, 30, Instant.now());
        Usuario usuario = new Usuario(2L, USUARIO_PACIENTE_UUID, "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        // Paciente con ID distinto (999L != PACIENTE_ID)
        Paciente paciente = new Paciente(999L, 2L, "pac-uuid-ajeno", "CC", "12345", "Pedro", "Gomez", LocalDate.of(1990, 1, 1), null);

        when(recetaRepository.buscarEntidadPorPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(usuarioRepository.buscarPorPublicId(USUARIO_PACIENTE_UUID)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));

        assertThatThrownBy(() -> service.obtenerPorPublicId(
                "receta-uuid-1",
                USUARIO_PACIENTE_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        )).isInstanceOf(AccesoNoAutorizadoException.class)
          .hasMessageContaining("No tiene autorizacion para acceder a la receta de otro paciente");

        verify(recetaRepository, never()).buscarPorPublicId(anyString());
    }

    @Test
    @DisplayName("obtenerPorPublicId - Paciente dueño consulta su propia receta (200)")
    void obtenerPorPublicId_pacientePropio_exito() {
        Receta receta = new Receta(555L, "receta-uuid-1", ATENCION_ID, PACIENTE_ID, PROFESIONAL_ID, 30, Instant.now());
        Usuario usuario = new Usuario(2L, USUARIO_PACIENTE_UUID, "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Paciente paciente = new Paciente(PACIENTE_ID, 2L, "pac-uuid-propio", "CC", "12345", "Juan", "Perez", LocalDate.of(1990, 1, 1), null);

        RecetaResponse recetaResponse = new RecetaResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid-propio", "Juan Perez", "prof-uuid", "Carlos Gomez", "General", 30, Instant.now(), List.of()
        );

        when(recetaRepository.buscarEntidadPorPublicId("receta-uuid-1")).thenReturn(Optional.of(receta));
        when(usuarioRepository.buscarPorPublicId(USUARIO_PACIENTE_UUID)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(recetaRepository.buscarPorPublicId("receta-uuid-1")).thenReturn(Optional.of(recetaResponse));

        RecetaResponse resp = service.obtenerPorPublicId(
                "receta-uuid-1",
                USUARIO_PACIENTE_UUID,
                List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"))
        );

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isEqualTo("receta-uuid-1");
    }

    @Test
    @DisplayName("listarCatalogo - Paginación segura y retorno de datos")
    void listarCatalogo_retornaPagina() {
        MedicamentoResponse med = new MedicamentoResponse(
                "med-uuid-1", "MED-ACE-500", "Acetaminofen", "Acetaminofen", "Tableta", "500 mg", "ACTIVO"
        );
        when(medicamentoRepository.listarActivos(eq("aceta"), eq(0), eq(10)))
                .thenReturn(List.of(med));
        when(medicamentoRepository.contarActivos("aceta")).thenReturn(1);

        PaginatedResponse<MedicamentoResponse> resp = service.listarCatalogo("aceta", 0, 10);

        assertThat(resp).isNotNull();
        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1L);
        assertThat(resp.page()).isZero();
    }

    @Test
    @DisplayName("obtenerMisRecetas - Paciente autenticado consulta sus recetas exitosamente y audita CONSULTA_HISTORIA")
    void obtenerMisRecetas_exito() {
        Usuario usuario = new Usuario(2L, USUARIO_PACIENTE_UUID, "paciente@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        Paciente paciente = new Paciente(PACIENTE_ID, 2L, "pac-uuid-propio", "CC", "12345", "Juan", "Perez", LocalDate.of(1990, 1, 1), null);

        RecetaResponse recetaResponse = new RecetaResponse(
                "receta-uuid-1", "atencion-uuid", "pac-uuid-propio", "Juan Perez", "prof-uuid", "Carlos Gomez", "General", 30, Instant.now(), List.of()
        );

        when(usuarioRepository.buscarPorPublicId(USUARIO_PACIENTE_UUID)).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(2L)).thenReturn(Optional.of(paciente));
        when(recetaRepository.listarPorPacienteId(PACIENTE_ID, 0, 10)).thenReturn(List.of(recetaResponse));
        when(recetaRepository.contarPorPacienteId(PACIENTE_ID)).thenReturn(1);

        PaginatedResponse<RecetaResponse> resp = service.obtenerMisRecetas(USUARIO_PACIENTE_UUID, 0, 10, "192.168.1.50");

        assertThat(resp).isNotNull();
        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1L);
        assertThat(resp.content().get(0).publicId()).isEqualTo("receta-uuid-1");

        ArgumentCaptor<EventoAuditoria> captor = ArgumentCaptor.forClass(EventoAuditoria.class);
        verify(auditoriaService).auditar(captor.capture());
        EventoAuditoria evento = captor.getValue();
        assertThat(evento.accion()).isEqualTo(AccionAuditable.CONSULTA_HISTORIA);
        assertThat(evento.tipoRecurso()).isEqualTo("RECETA");
        assertThat(evento.recursoPublicId()).isEqualTo("pac-uuid-propio");
        assertThat(evento.ipOrigen()).isEqualTo("192.168.1.50");
    }

    @Test
    @DisplayName("obtenerMisRecetas - Falla si usuario autenticado no existe")
    void obtenerMisRecetas_rechazaSiUsuarioNoExiste() {
        when(usuarioRepository.buscarPorPublicId("uuid-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerMisRecetas("uuid-inexistente", 0, 10, "127.0.0.1"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Usuario autenticado no encontrado");
    }

    @Test
    @DisplayName("obtenerMisRecetas - Falla si usuario no está registrado como paciente")
    void obtenerMisRecetas_rechazaSiUsuarioNoEsPaciente() {
        Usuario usuario = new Usuario(10L, "uuid-no-paciente", "otro@test.com", "hash", "ACTIVO", 0, null, Instant.now());
        when(usuarioRepository.buscarPorPublicId("uuid-no-paciente")).thenReturn(Optional.of(usuario));
        when(pacienteRepository.buscarPorUsuarioId(10L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerMisRecetas("uuid-no-paciente", 0, 10, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no registrado como paciente");
    }

    @Test
    @DisplayName("obtenerMisRecetas - Falla si usuarioAutenticadoPublicId es nulo o blanco")
    void obtenerMisRecetas_rechazaSiUsuarioNuloOBlanco() {
        assertThatThrownBy(() -> service.obtenerMisRecetas(null, 0, 10, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no autenticado");

        assertThatThrownBy(() -> service.obtenerMisRecetas("   ", 0, 10, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("Usuario no autenticado");
    }
}

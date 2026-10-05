package com.meditriaje.service;

import com.meditriaje.dto.admin.ActualizarProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalRequest;
import com.meditriaje.dto.admin.CrearProfesionalResponse;
import com.meditriaje.dto.admin.ProfesionalResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import com.meditriaje.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminProfessionalServiceTest {

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private AuditoriaService auditoriaService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private EmailService emailService;

    private AdminProfessionalService service;

    private static final String ADMIN_PUBLIC_ID = "admin-uuid-1";
    private static final Long ADMIN_ID = 99L;
    private static final String IP_ORIGEN = "127.0.0.1";

    @BeforeEach
    void setUp() {
        service = new AdminProfessionalService(
                profesionalRepository,
                usuarioRepository,
                especialidadRepository,
                auditoriaService,
                passwordEncoder,
                emailService
        );
    }

    private void mockAdmin() {
        Usuario admin = new Usuario(ADMIN_ID, ADMIN_PUBLIC_ID, "admin@meditriaje.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
        when(usuarioRepository.buscarPorPublicId(ADMIN_PUBLIC_ID)).thenReturn(Optional.of(admin));
    }

    // =========================================================================
    // 1. ALTA DE PROFESIONAL
    // =========================================================================

    @Test
    void altaProfesional_conDatosValidos_creaUsuarioYProfesionalConPasswordTemporalYAudita() {
        mockAdmin();
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-med-int"
        );

        Especialidad esp = new Especialidad(10L, "esp-med-int", "Medicina Interna", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-med-int")).thenReturn(Optional.of(esp));
        when(usuarioRepository.existePorEmail("carlos.perez@hospital.com")).thenReturn(false);
        when(profesionalRepository.existePorRegistroMedico("RM-12345")).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("argon2id_mock_hash");
        when(usuarioRepository.crear(anyString(), eq("carlos.perez@hospital.com"), eq("argon2id_mock_hash"), eq(true))).thenReturn(101L);
        when(usuarioRepository.buscarRolIdPorNombre("ROLE_PROFESIONAL")).thenReturn(Optional.of(2L));
        when(profesionalRepository.crear(any())).thenReturn(201L);

        CrearProfesionalResponse resp = service.altaProfesional(req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isNotBlank();
        assertThat(resp.usuarioPublicId()).isNotBlank();
        assertThat(resp.registroMedico()).isEqualTo("RM-12345");
        assertThat(resp.nombres()).isEqualTo("Carlos");
        assertThat(resp.apellidos()).isEqualTo("Perez");
        assertThat(resp.email()).isEqualTo("carlos.perez@hospital.com");
        assertThat(resp.especialidadPublicId()).isEqualTo("esp-med-int");
        assertThat(resp.especialidadNombre()).isEqualTo("Medicina Interna");
        assertThat(resp.debeCambiarPassword()).isTrue();

        // Validar que la contraseña temporal sea segura
        assertThat(resp.passwordTemporal()).isNotBlank();
        assertThat(resp.passwordTemporal().length()).isGreaterThanOrEqualTo(12);
        assertThat(resp.passwordTemporal()).matches(".*[A-Z].*");
        assertThat(resp.passwordTemporal()).matches(".*[a-z].*");
        assertThat(resp.passwordTemporal()).matches(".*[0-9].*");
        assertThat(resp.passwordTemporal()).matches(".*[!@#$%&*+\\-_=].*");

        verify(usuarioRepository).asignarRol(101L, 2L);
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(resp.publicId()),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
        verify(emailService).enviarCredencialesIniciales(
                eq("carlos.perez@hospital.com"),
                eq("Carlos Perez"),
                eq("Profesional Asistencial (Medicina Interna)"),
                eq(resp.passwordTemporal())
        );
    }

    @Test
    void altaProfesional_conNombresInvalidos_lanzaDatosInvalidosException() {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345",
                "Carlos123",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-med-int"
        );

        assertThatThrownBy(() -> service.altaProfesional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("solo puede contener letras, espacios, tildes y guiones");
    }

    @Test
    void altaProfesional_especialidadInexistente_lanzaRecursoNoEncontradoException() {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-no-existe"
        );
        when(especialidadRepository.buscarPorPublicId("esp-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.altaProfesional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Especialidad no encontrada");

        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void altaProfesional_especialidadInactiva_lanzaDatosInvalidosException() {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-inactiva"
        );
        Especialidad espInactiva = new Especialidad(10L, "esp-inactiva", "Inactiva", 20, "INACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-inactiva")).thenReturn(Optional.of(espInactiva));

        assertThatThrownBy(() -> service.altaProfesional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no se encuentra activa");

        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void altaProfesional_emailDuplicado_lanzaDatosInvalidosException() {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-activa"
        );
        Especialidad esp = new Especialidad(10L, "esp-activa", "Medicina", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-activa")).thenReturn(Optional.of(esp));
        when(usuarioRepository.existePorEmail("carlos.perez@hospital.com")).thenReturn(true);

        assertThatThrownBy(() -> service.altaProfesional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El correo electronico ya se encuentra registrado");

        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString(), anyBoolean());
    }

    @Test
    void altaProfesional_registroMedicoDuplicado_lanzaDatosInvalidosException() {
        CrearProfesionalRequest req = new CrearProfesionalRequest(
                "RM-12345",
                "Carlos",
                "Perez",
                "carlos.perez@hospital.com",
                "esp-activa"
        );
        Especialidad esp = new Especialidad(10L, "esp-activa", "Medicina", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-activa")).thenReturn(Optional.of(esp));
        when(usuarioRepository.existePorEmail("carlos.perez@hospital.com")).thenReturn(false);
        when(profesionalRepository.existePorRegistroMedico("RM-12345")).thenReturn(true);

        assertThatThrownBy(() -> service.altaProfesional(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El registro medico ya se encuentra registrado");

        verify(usuarioRepository, never()).crear(anyString(), anyString(), anyString(), anyBoolean());
    }

    // =========================================================================
    // 2. ACTUALIZACIÓN DE PROFESIONAL
    // =========================================================================

    @Test
    void actualizarProfesional_conDatosValidos_actualizaYAudita() {
        mockAdmin();
        String profPublicId = "prof-uuid-1";
        ActualizarProfesionalRequest req = new ActualizarProfesionalRequest("Carlos Alberto", "Perez Gomez", "esp-ped");

        Profesional existente = new Profesional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        Especialidad nuevaEsp = new Especialidad(20L, "esp-ped", "Pediatría", 30, "ACTIVO");
        Usuario usuario = new Usuario(101L, "user-uuid-1", "carlos@hospital.com", "hash", "ACTIVO", 0, null, Instant.now(), false);

        when(profesionalRepository.buscarPorPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(especialidadRepository.buscarPorPublicId("esp-ped")).thenReturn(Optional.of(nuevaEsp));
        when(usuarioRepository.buscarPorId(101L)).thenReturn(Optional.of(usuario));

        ProfesionalResponse resp = service.actualizarProfesional(profPublicId, req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.nombres()).isEqualTo("Carlos Alberto");
        assertThat(resp.apellidos()).isEqualTo("Perez Gomez");
        assertThat(resp.especialidadPublicId()).isEqualTo("esp-ped");
        assertThat(resp.especialidadNombre()).isEqualTo("Pediatría");

        ArgumentCaptor<Profesional> captor = ArgumentCaptor.forClass(Profesional.class);
        verify(profesionalRepository).actualizar(captor.capture());
        assertThat(captor.getValue().nombres()).isEqualTo("Carlos Alberto");
        assertThat(captor.getValue().apellidos()).isEqualTo("Perez Gomez");
        assertThat(captor.getValue().especialidadId()).isEqualTo(20L);

        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(profPublicId),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void actualizarProfesional_especialidadInactiva_lanzaDatosInvalidosException() {
        String profPublicId = "prof-uuid-1";
        ActualizarProfesionalRequest req = new ActualizarProfesionalRequest("Carlos", "Perez", "esp-inactiva");
        Profesional existente = new Profesional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        Especialidad espInactiva = new Especialidad(20L, "esp-inactiva", "Inactiva", 30, "INACTIVO");

        when(profesionalRepository.buscarPorPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(especialidadRepository.buscarPorPublicId("esp-inactiva")).thenReturn(Optional.of(espInactiva));

        assertThatThrownBy(() -> service.actualizarProfesional(profPublicId, req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no se encuentra activa");
    }

    // =========================================================================
    // 3. CAMBIAR ESTADO (ACTIVAR / DESACTIVAR)
    // =========================================================================

    @Test
    void cambiarEstado_aInactivo_actualizaUsuarioYAudita() {
        mockAdmin();
        String profPublicId = "prof-uuid-1";
        Profesional existente = new Profesional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        Usuario usuario = new Usuario(101L, "user-uuid-1", "carlos@hospital.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
        Especialidad esp = new Especialidad(10L, "esp-med", "Medicina", 20, "ACTIVO");

        when(profesionalRepository.buscarPorPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(usuarioRepository.buscarPorId(101L)).thenReturn(Optional.of(usuario));
        when(especialidadRepository.buscarPorId(10L)).thenReturn(Optional.of(esp));

        ProfesionalResponse resp = service.cambiarEstado(profPublicId, "INACTIVO", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("INACTIVO");
        verify(usuarioRepository).actualizarEstado(101L, "INACTIVO");
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(profPublicId),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void cambiarEstado_estadoInvalido_lanzaDatosInvalidosException() {
        assertThatThrownBy(() -> service.cambiarEstado("prof-uuid-1", "ESTADO_RARO", ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Estado invalido");
    }

    // =========================================================================
    // 4. OBTENER Y LISTAR
    // =========================================================================

    @Test
    void obtenerPorPublicId_existente_retornaProfesionalResponse() {
        String profPublicId = "prof-uuid-1";
        Profesional existente = new Profesional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        Usuario usuario = new Usuario(101L, "user-uuid-1", "carlos@hospital.com", "hash", "ACTIVO", 0, null, Instant.now(), true);
        Especialidad esp = new Especialidad(10L, "esp-med", "Medicina", 20, "ACTIVO");

        when(profesionalRepository.buscarPorPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(usuarioRepository.buscarPorId(101L)).thenReturn(Optional.of(usuario));
        when(especialidadRepository.buscarPorId(10L)).thenReturn(Optional.of(esp));

        ProfesionalResponse resp = service.obtenerPorPublicId(profPublicId);

        assertThat(resp.publicId()).isEqualTo(profPublicId);
        assertThat(resp.usuarioPublicId()).isEqualTo("user-uuid-1");
        assertThat(resp.registroMedico()).isEqualTo("RM-12345");
        assertThat(resp.email()).isEqualTo("carlos@hospital.com");
        assertThat(resp.debeCambiarPassword()).isTrue();
    }

    @Test
    void listar_conFiltros_retornaPaginaCorrecta() {
        ProfesionalResponse item = new ProfesionalResponse(
                "prof-1", "user-1", "RM-1", "Juan", "Gomez", "juan@test.com", "esp-1", "Pediatría", "ACTIVO", false, Instant.now()
        );
        when(profesionalRepository.listar(0, 10, "esp-1", "ACTIVO")).thenReturn(List.of(item));
        when(profesionalRepository.contar("esp-1", "ACTIVO")).thenReturn(1);

        PaginatedResponse<ProfesionalResponse> resp = service.listar(0, 10, "esp-1", "ACTIVO");

        assertThat(resp.content()).hasSize(1);
        assertThat(resp.totalElements()).isEqualTo(1L);
        assertThat(resp.content().get(0).registroMedico()).isEqualTo("RM-1");
    }

    @Test
    void actualizarProfesional_profesionalInexistente_lanzaRecursoNoEncontradoException() {
        ActualizarProfesionalRequest req = new ActualizarProfesionalRequest("Carlos", "Perez", "esp-1");
        when(profesionalRepository.buscarPorPublicId("prof-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.actualizarProfesional("prof-no-existe", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void cambiarEstado_aActivo_actualizaUsuarioYAudita() {
        mockAdmin();
        String profPublicId = "prof-uuid-1";
        Profesional existente = new Profesional(50L, 101L, profPublicId, 10L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        Usuario usuario = new Usuario(101L, "user-uuid-1", "carlos@hospital.com", "hash", "INACTIVO", 0, null, Instant.now(), false);
        Especialidad esp = new Especialidad(10L, "esp-med", "Medicina", 20, "ACTIVO");

        when(profesionalRepository.buscarPorPublicId(profPublicId)).thenReturn(Optional.of(existente));
        when(usuarioRepository.buscarPorId(101L)).thenReturn(Optional.of(usuario));
        when(especialidadRepository.buscarPorId(10L)).thenReturn(Optional.of(esp));

        ProfesionalResponse resp = service.cambiarEstado(profPublicId, "ACTIVO", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("ACTIVO");
        verify(usuarioRepository).actualizarEstado(101L, "ACTIVO");
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("PROFESIONAL"),
                eq(profPublicId),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void cambiarEstado_profesionalInexistente_lanzaRecursoNoEncontradoException() {
        when(profesionalRepository.buscarPorPublicId("prof-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.cambiarEstado("prof-no-existe", "ACTIVO", ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void obtenerPorPublicId_inexistente_lanzaRecursoNoEncontradoException() {
        when(profesionalRepository.buscarPorPublicId("prof-no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerPorPublicId("prof-no-existe"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void listar_estadoInvalido_lanzaDatosInvalidosException() {
        assertThatThrownBy(() -> service.listar(0, 10, null, "ESTADO_DESCONOCIDO"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Estado invalido");
    }
}

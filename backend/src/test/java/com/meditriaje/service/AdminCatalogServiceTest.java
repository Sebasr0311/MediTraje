package com.meditriaje.service;

import com.meditriaje.dto.admin.ActualizarEspecialidadRequest;
import com.meditriaje.dto.admin.ActualizarInstitucionRequest;
import com.meditriaje.dto.admin.ActualizarSedeRequest;
import com.meditriaje.dto.admin.CrearEspecialidadRequest;
import com.meditriaje.dto.admin.CrearInstitucionRequest;
import com.meditriaje.dto.admin.CrearSedeRequest;
import com.meditriaje.dto.admin.EspecialidadResponse;
import com.meditriaje.dto.admin.InstitucionResponse;
import com.meditriaje.dto.admin.SedeResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Institucion;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.InstitucionRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminCatalogServiceTest {

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private InstitucionRepository institucionRepository;

    @Mock
    private SedeRepository sedeRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private AdminCatalogService catalogService;

    private static final String ADMIN_PUBLIC_ID = "admin-uuid-1";
    private static final Long ADMIN_ID = 10L;
    private static final String IP_ORIGEN = "192.168.1.50";

    @BeforeEach
    void setUp() {
        catalogService = new AdminCatalogService(
                especialidadRepository,
                institucionRepository,
                sedeRepository,
                usuarioRepository,
                auditoriaService
        );
    }

    private void mockAdminUsuario() {
        Usuario adminUsuario = new Usuario(
                ADMIN_ID,
                ADMIN_PUBLIC_ID,
                "admin@meditriaje.com",
                "hash",
                "ACTIVO",
                0,
                null,
                Instant.now()
        );
        when(usuarioRepository.buscarPorPublicId(ADMIN_PUBLIC_ID)).thenReturn(Optional.of(adminUsuario));
    }

    // =========================================================================
    // ESPECIALIDAD TESTS
    // =========================================================================

    @Test
    void crearEspecialidad_exito_creaYAudita() {
        mockAdminUsuario();
        CrearEspecialidadRequest req = new CrearEspecialidadRequest("Medicina General", 20);
        when(especialidadRepository.existePorNombre("Medicina General")).thenReturn(false);
        when(especialidadRepository.crear(anyString(), eq("Medicina General"), eq(20)))
                .thenReturn(new Especialidad(1L, "esp-uuid-1", "Medicina General", 20, "ACTIVO"));

        EspecialidadResponse resp = catalogService.crearEspecialidad(req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp).isNotNull();
        assertThat(resp.publicId()).isEqualTo("esp-uuid-1");
        assertThat(resp.nombre()).isEqualTo("Medicina General");
        assertThat(resp.duracionSlotMin()).isEqualTo(20);
        assertThat(resp.estado()).isEqualTo("ACTIVO");

        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("ESPECIALIDAD"),
                eq("esp-uuid-1"),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void crearEspecialidad_nombreDuplicado_lanzaDatosInvalidos() {
        CrearEspecialidadRequest req = new CrearEspecialidadRequest("Medicina General", 20);
        when(especialidadRepository.existePorNombre("Medicina General")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.crearEspecialidad(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe una especialidad");

        verify(especialidadRepository, never()).crear(anyString(), anyString(), anyInt());
        verify(auditoriaService, never()).registrarEvento(anyLong(), any(), any(), any(), any(), any());
    }

    @Test
    void actualizarEspecialidad_exito_actualizaYAudita() {
        mockAdminUsuario();
        ActualizarEspecialidadRequest req = new ActualizarEspecialidadRequest("Pediatría Integral", 30);
        Especialidad actual = new Especialidad(2L, "esp-uuid-2", "Pediatría", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-2")).thenReturn(Optional.of(actual));
        when(especialidadRepository.existePorNombreYNoPublicId("Pediatría Integral", "esp-uuid-2")).thenReturn(false);

        EspecialidadResponse resp = catalogService.actualizarEspecialidad("esp-uuid-2", req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.nombre()).isEqualTo("Pediatría Integral");
        assertThat(resp.duracionSlotMin()).isEqualTo(30);

        verify(especialidadRepository).actualizar("esp-uuid-2", "Pediatría Integral", 30);
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("ESPECIALIDAD"),
                eq("esp-uuid-2"),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void actualizarEspecialidad_noExiste_lanzaRecursoNoEncontrado() {
        ActualizarEspecialidadRequest req = new ActualizarEspecialidadRequest("Pediatría", 30);
        when(especialidadRepository.buscarPorPublicId("no-existe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.actualizarEspecialidad("no-existe", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void actualizarEspecialidad_nombreDuplicado_lanzaDatosInvalidos() {
        ActualizarEspecialidadRequest req = new ActualizarEspecialidadRequest("Cardiología", 30);
        Especialidad actual = new Especialidad(2L, "esp-uuid-2", "Pediatría", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-2")).thenReturn(Optional.of(actual));
        when(especialidadRepository.existePorNombreYNoPublicId("Cardiología", "esp-uuid-2")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.actualizarEspecialidad("esp-uuid-2", req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe otra especialidad");
    }

    @Test
    void desactivarEspecialidad_exito_cambiaEstadoAInactivo() {
        mockAdminUsuario();
        Especialidad actual = new Especialidad(1L, "esp-uuid-1", "Medicina", 20, "ACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-1")).thenReturn(Optional.of(actual));

        EspecialidadResponse resp = catalogService.desactivarEspecialidad("esp-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("INACTIVO");
        verify(especialidadRepository).cambiarEstado("esp-uuid-1", "INACTIVO");
        verify(auditoriaService).registrarEvento(eq(ADMIN_ID), eq(AccionAuditable.CAMBIO_ADMINISTRATIVO), eq("ESPECIALIDAD"), eq("esp-uuid-1"), eq(ResultadoAuditoria.EXITO), eq(IP_ORIGEN));
    }

    @Test
    void activarEspecialidad_exito_cambiaEstadoAActivo() {
        mockAdminUsuario();
        Especialidad actual = new Especialidad(1L, "esp-uuid-1", "Medicina", 20, "INACTIVO");
        when(especialidadRepository.buscarPorPublicId("esp-uuid-1")).thenReturn(Optional.of(actual));

        EspecialidadResponse resp = catalogService.activarEspecialidad("esp-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("ACTIVO");
        verify(especialidadRepository).cambiarEstado("esp-uuid-1", "ACTIVO");
    }

    @Test
    void listarEspecialidades_conFiltroValido_retornaPaginado() {
        Especialidad esp = new Especialidad(1L, "esp-1", "Cardiología", 25, "ACTIVO");
        when(especialidadRepository.listar(0, 10, "ACTIVO")).thenReturn(List.of(esp));
        when(especialidadRepository.contar("ACTIVO")).thenReturn(1L);

        PaginatedResponse<EspecialidadResponse> pag = catalogService.listarEspecialidades(0, 10, "ACTIVO");

        assertThat(pag.content()).hasSize(1);
        assertThat(pag.totalElements()).isEqualTo(1L);
        assertThat(pag.totalPages()).isEqualTo(1);
        assertThat(pag.hasNext()).isFalse();
        assertThat(pag.hasPrevious()).isFalse();
    }

    @Test
    void listarEspecialidades_estadoInvalido_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> catalogService.listarEspecialidades(0, 10, "PENDIENTE"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Estado invalido");
    }

    // =========================================================================
    // INSTITUCION TESTS
    // =========================================================================

    @Test
    void crearInstitucion_exito_creaYAudita() {
        mockAdminUsuario();
        CrearInstitucionRequest req = new CrearInstitucionRequest("900123456-1", "Hospital Central IPS");
        when(institucionRepository.existePorNit("900123456-1")).thenReturn(false);
        Institucion inst = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central IPS", "ACTIVO", Instant.now());
        when(institucionRepository.crear(anyString(), eq("900123456-1"), eq("Hospital Central IPS"))).thenReturn(inst);

        InstitucionResponse resp = catalogService.crearInstitucion(req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.publicId()).isEqualTo("inst-uuid-1");
        assertThat(resp.nit()).isEqualTo("900123456-1");
        assertThat(resp.razonSocial()).isEqualTo("Hospital Central IPS");
        assertThat(resp.estado()).isEqualTo("ACTIVO");

        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("INSTITUCION"),
                eq("inst-uuid-1"),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void crearInstitucion_nitDuplicado_lanzaDatosInvalidos() {
        CrearInstitucionRequest req = new CrearInstitucionRequest("900123456-1", "Hospital Central");
        when(institucionRepository.existePorNit("900123456-1")).thenReturn(true);

        assertThatThrownBy(() -> catalogService.crearInstitucion(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Ya existe una institución con el NIT");
    }

    @Test
    void actualizarInstitucion_exito_actualizaYAudita() {
        mockAdminUsuario();
        ActualizarInstitucionRequest req = new ActualizarInstitucionRequest("Hospital Central S.A.S.");
        Institucion actual = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "ACTIVO", Instant.now());
        when(institucionRepository.buscarPorPublicId("inst-uuid-1")).thenReturn(Optional.of(actual));

        InstitucionResponse resp = catalogService.actualizarInstitucion("inst-uuid-1", req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.razonSocial()).isEqualTo("Hospital Central S.A.S.");
        verify(institucionRepository).actualizar("inst-uuid-1", "Hospital Central S.A.S.");
        verify(auditoriaService).registrarEvento(eq(ADMIN_ID), eq(AccionAuditable.CAMBIO_ADMINISTRATIVO), eq("INSTITUCION"), eq("inst-uuid-1"), eq(ResultadoAuditoria.EXITO), eq(IP_ORIGEN));
    }

    @Test
    void desactivarInstitucion_exito() {
        mockAdminUsuario();
        Institucion actual = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "ACTIVO", Instant.now());
        when(institucionRepository.buscarPorPublicId("inst-uuid-1")).thenReturn(Optional.of(actual));

        InstitucionResponse resp = catalogService.desactivarInstitucion("inst-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("INACTIVO");
        verify(institucionRepository).cambiarEstado("inst-uuid-1", "INACTIVO");
    }

    @Test
    void activarInstitucion_exito() {
        mockAdminUsuario();
        Institucion actual = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "INACTIVO", Instant.now());
        when(institucionRepository.buscarPorPublicId("inst-uuid-1")).thenReturn(Optional.of(actual));

        InstitucionResponse resp = catalogService.activarInstitucion("inst-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("ACTIVO");
        verify(institucionRepository).cambiarEstado("inst-uuid-1", "ACTIVO");
    }

    // =========================================================================
    // SEDE TESTS
    // =========================================================================

    @Test
    void crearSede_exito_creaYAudita() {
        mockAdminUsuario();
        CrearSedeRequest req = new CrearSedeRequest("inst-uuid-1", "Sede Norte", "Calle 100 #15-20", "Bogotá");
        Institucion inst = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "ACTIVO", Instant.now());
        when(institucionRepository.buscarPorPublicId("inst-uuid-1")).thenReturn(Optional.of(inst));

        Sede sede = new Sede(100L, 1L, "sede-uuid-1", "Sede Norte", "Calle 100 #15-20", "Bogotá", "ACTIVO");
        when(sedeRepository.crear(eq(1L), anyString(), eq("Sede Norte"), eq("Calle 100 #15-20"), eq("Bogotá")))
                .thenReturn(sede);

        SedeResponse resp = catalogService.crearSede(req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.publicId()).isEqualTo("sede-uuid-1");
        assertThat(resp.institucionPublicId()).isEqualTo("inst-uuid-1");
        assertThat(resp.institucionRazonSocial()).isEqualTo("Hospital Central");
        assertThat(resp.nombre()).isEqualTo("Sede Norte");
        assertThat(resp.estado()).isEqualTo("ACTIVO");

        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("SEDE"),
                eq("sede-uuid-1"),
                eq(ResultadoAuditoria.EXITO),
                eq(IP_ORIGEN)
        );
    }

    @Test
    void crearSede_institucionNoExiste_lanzaRecursoNoEncontrado() {
        CrearSedeRequest req = new CrearSedeRequest("inst-inexistente", "Sede Norte", "Calle 100", "Bogotá");
        when(institucionRepository.buscarPorPublicId("inst-inexistente")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> catalogService.crearSede(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void crearSede_institucionInactiva_lanzaDatosInvalidos() {
        CrearSedeRequest req = new CrearSedeRequest("inst-uuid-1", "Sede Norte", "Calle 100", "Bogotá");
        Institucion instInactiva = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "INACTIVO", Instant.now());
        when(institucionRepository.buscarPorPublicId("inst-uuid-1")).thenReturn(Optional.of(instInactiva));

        assertThatThrownBy(() -> catalogService.crearSede(req, ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("No se pueden crear sedes en una institución inactiva");
    }

    @Test
    void actualizarSede_exito() {
        mockAdminUsuario();
        ActualizarSedeRequest req = new ActualizarSedeRequest("Sede Norte Renovada", "Cra 15 #100-10", "Bogotá");
        Sede actual = new Sede(100L, 1L, "sede-uuid-1", "Sede Norte", "Calle 100", "Bogotá", "ACTIVO");
        Institucion inst = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "ACTIVO", Instant.now());

        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(actual));
        when(institucionRepository.buscarPorId(1L)).thenReturn(Optional.of(inst));

        SedeResponse resp = catalogService.actualizarSede("sede-uuid-1", req, ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.nombre()).isEqualTo("Sede Norte Renovada");
        assertThat(resp.direccion()).isEqualTo("Cra 15 #100-10");
        verify(sedeRepository).actualizar("sede-uuid-1", "Sede Norte Renovada", "Cra 15 #100-10", "Bogotá");
    }

    @Test
    void desactivarSede_exito() {
        mockAdminUsuario();
        Sede actual = new Sede(100L, 1L, "sede-uuid-1", "Sede Norte", "Calle 100", "Bogotá", "ACTIVO");
        Institucion inst = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "ACTIVO", Instant.now());

        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(actual));
        when(institucionRepository.buscarPorId(1L)).thenReturn(Optional.of(inst));

        SedeResponse resp = catalogService.desactivarSede("sede-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("INACTIVO");
        verify(sedeRepository).cambiarEstado("sede-uuid-1", "INACTIVO");
    }

    @Test
    void activarSede_institucionActiva_exito() {
        mockAdminUsuario();
        Sede actual = new Sede(100L, 1L, "sede-uuid-1", "Sede Norte", "Calle 100", "Bogotá", "INACTIVO");
        Institucion inst = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "ACTIVO", Instant.now());

        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(actual));
        when(institucionRepository.buscarPorId(1L)).thenReturn(Optional.of(inst));

        SedeResponse resp = catalogService.activarSede("sede-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN);

        assertThat(resp.estado()).isEqualTo("ACTIVO");
        verify(sedeRepository).cambiarEstado("sede-uuid-1", "ACTIVO");
    }

    @Test
    void activarSede_institucionInactiva_lanzaDatosInvalidos() {
        Sede actual = new Sede(100L, 1L, "sede-uuid-1", "Sede Norte", "Calle 100", "Bogotá", "INACTIVO");
        Institucion instInactiva = new Institucion(1L, "inst-uuid-1", "900123456-1", "Hospital Central", "INACTIVO", Instant.now());

        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(actual));
        when(institucionRepository.buscarPorId(1L)).thenReturn(Optional.of(instInactiva));

        assertThatThrownBy(() -> catalogService.activarSede("sede-uuid-1", ADMIN_PUBLIC_ID, IP_ORIGEN))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("No se puede activar una sede de una institución inactiva");

        verify(sedeRepository, never()).cambiarEstado(anyString(), anyString());
    }

    @Test
    void listarSedes_conInstitucionCache_retornaPaginado() {
        Sede s1 = new Sede(100L, 1L, "sede-1", "Sede Norte", "Calle 100", "Bogotá", "ACTIVO");
        Sede s2 = new Sede(101L, 1L, "sede-2", "Sede Sur", "Calle 1", "Bogotá", "ACTIVO");
        Institucion inst = new Institucion(1L, "inst-1", "900123456-1", "Hospital Central", "ACTIVO", Instant.now());

        when(sedeRepository.listar(0, 10, "inst-1", "ACTIVO")).thenReturn(List.of(s1, s2));
        when(sedeRepository.contar("inst-1", "ACTIVO")).thenReturn(2L);
        when(institucionRepository.buscarPorId(1L)).thenReturn(Optional.of(inst));

        PaginatedResponse<SedeResponse> pag = catalogService.listarSedes(0, 10, "inst-1", "ACTIVO");

        assertThat(pag.content()).hasSize(2);
        assertThat(pag.totalElements()).isEqualTo(2L);
        assertThat(pag.content().get(0).institucionRazonSocial()).isEqualTo("Hospital Central");
        assertThat(pag.content().get(1).institucionRazonSocial()).isEqualTo("Hospital Central");
    }
}

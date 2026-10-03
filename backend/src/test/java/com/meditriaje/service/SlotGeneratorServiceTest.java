package com.meditriaje.service;

import com.meditriaje.dto.admin.GenerarSlotsRequest;
import com.meditriaje.dto.admin.GenerarSlotsResponse;
import com.meditriaje.dto.admin.SlotResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.Especialidad;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.EspecialidadRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SlotGeneratorServiceTest {

    @Mock
    private DisponibilidadSlotRepository slotRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SedeRepository sedeRepository;

    @Mock
    private EspecialidadRepository especialidadRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private SlotGeneratorService service;

    private static final ZoneId ZONE_BOGOTA = ZoneId.of("America/Bogota");
    private static final String ADMIN_UUID = "admin-uuid";
    private static final Long ADMIN_ID = 999L;
    private static final String PROF_UUID = "prof-uuid";
    private static final String SEDE_UUID = "sede-uuid";
    private static final String ESP_UUID = "esp-uuid";

    private Profesional profesional;
    private Usuario usuarioProf;
    private Sede sede;
    private Especialidad especialidad;
    private Usuario adminUser;

    @BeforeEach
    void setUp() {
        service = new SlotGeneratorService(
                slotRepository,
                profesionalRepository,
                usuarioRepository,
                sedeRepository,
                especialidadRepository,
                auditoriaService
        );

        profesional = new Profesional(10L, 100L, PROF_UUID, 5L, "RM-12345", "Carlos", "Perez", Instant.now(), null);
        usuarioProf = new Usuario(100L, "u-prof", "carlos@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
        sede = new Sede(20L, 1L, SEDE_UUID, "Sede Norte", "Calle 100", "Bogota", "ACTIVO");
        especialidad = new Especialidad(5L, ESP_UUID, "Medicina General", 20, "ACTIVO");
        adminUser = new Usuario(ADMIN_ID, ADMIN_UUID, "admin@test.com", "hash", "ACTIVO", 0, null, Instant.now(), false);
    }

    private void stubCatalogosValidos() {
        when(profesionalRepository.buscarPorPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(usuarioRepository.buscarPorId(100L)).thenReturn(Optional.of(usuarioProf));
        when(sedeRepository.buscarPorPublicId(SEDE_UUID)).thenReturn(Optional.of(sede));
        when(especialidadRepository.buscarPorId(5L)).thenReturn(Optional.of(especialidad));
    }

    private void mockAdmin() {
        when(usuarioRepository.buscarPorPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));
    }

    // =========================================================================
    // GENERACION DE SLOTS Y REGLAS DE NEGOCIO
    // =========================================================================

    @Test
    void generarSlots_casoExitoso_generaSlotsYAudita() {
        stubCatalogosValidos();
        mockAdmin();
        LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
        LocalDate fecha = hoy.plusDays(1);

        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        when(slotRepository.existeSolape(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerarSlotsResponse response = service.generarSlots(request, ADMIN_UUID, "127.0.0.1");

        assertThat(response.slotsGenerados()).isEqualTo(3);
        assertThat(response.slots()).hasSize(3);

        SlotResponse slot1 = response.slots().get(0);
        ZonedDateTime zdt1Inicio = slot1.fechaHoraInicio().atZone(ZONE_BOGOTA);
        ZonedDateTime zdt1Fin = slot1.fechaHoraFin().atZone(ZONE_BOGOTA);
        assertThat(zdt1Inicio.toLocalTime()).isEqualTo(LocalTime.of(8, 0));
        assertThat(zdt1Fin.toLocalTime()).isEqualTo(LocalTime.of(8, 20));
        assertThat(slot1.profesionalNombre()).isEqualTo("Carlos Perez");
        assertThat(slot1.sedeNombre()).isEqualTo("Sede Norte");
        assertThat(slot1.especialidadNombre()).isEqualTo("Medicina General");
        assertThat(slot1.modalidad()).isEqualTo("PRESENCIAL");
        assertThat(slot1.estado()).isEqualTo("LIBRE");

        verify(slotRepository).guardarLote(any());
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq(PROF_UUID),
                eq(ResultadoAuditoria.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void generarSlots_bordeVentanaNoDivisibleExacta_noSuperaHoraFin() {
        stubCatalogosValidos();
        mockAdmin();
        LocalDate fecha = LocalDate.now(ZONE_BOGOTA).plusDays(2);

        // Ventana de 08:00 a 08:50 con slots de 20 min -> debe generar 2 slots (08:00-08:20 y 08:20-08:40), omitiendo 08:40-09:00
        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 50),
                20, "PRESENCIAL", null
        );

        when(slotRepository.existeSolape(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerarSlotsResponse response = service.generarSlots(request, ADMIN_UUID, "127.0.0.1");

        assertThat(response.slotsGenerados()).isEqualTo(2);
        SlotResponse slot2 = response.slots().get(1);
        ZonedDateTime zdt2Fin = slot2.fechaHoraFin().atZone(ZONE_BOGOTA);
        assertThat(zdt2Fin.toLocalTime()).isEqualTo(LocalTime.of(8, 40));
    }

    @Test
    void generarSlots_filtroDiasSemana_aplicaSoloDiasSeleccionados() {
        stubCatalogosValidos();
        mockAdmin();
        LocalDate hoy = LocalDate.now(ZONE_BOGOTA);
        // Rango de 7 días
        LocalDate inicio = hoy.plusDays(1);
        LocalDate fin = inicio.plusDays(6);

        // Filtrar únicamente los días lunes
        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, inicio, fin,
                LocalTime.of(8, 0), LocalTime.of(8, 30),
                30, "PRESENCIAL", List.of(DayOfWeek.MONDAY)
        );

        when(slotRepository.existeSolape(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerarSlotsResponse response = service.generarSlots(request, ADMIN_UUID, "127.0.0.1");

        // En 7 días consecutivos hay exactamente 1 lunes
        assertThat(response.slotsGenerados()).isEqualTo(1);
        ZonedDateTime slotDate = response.slots().get(0).fechaHoraInicio().atZone(ZONE_BOGOTA);
        assertThat(slotDate.getDayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
    }

    @Test
    void generarSlots_duracionNula_tomaDuracionDeEspecialidad() {
        stubCatalogosValidos();
        mockAdmin();
        LocalDate fecha = LocalDate.now(ZONE_BOGOTA).plusDays(1);

        // Especialidad tiene duracion 20
        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 40),
                null, "TELEMEDICINA", null
        );

        when(slotRepository.existeSolape(eq(10L), any(Instant.class), any(Instant.class))).thenReturn(false);

        GenerarSlotsResponse response = service.generarSlots(request, ADMIN_UUID, "127.0.0.1");

        assertThat(response.slotsGenerados()).isEqualTo(2);
        assertThat(response.slots().get(0).modalidad()).isEqualTo("TELEMEDICINA");
    }

    @Test
    void generarSlots_detectaSolapeConBD_abortaYLanzaExcepcion() {
        stubCatalogosValidos();
        LocalDate fecha = LocalDate.now(ZONE_BOGOTA).plusDays(1);

        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 40),
                20, "PRESENCIAL", null
        );

        // Simular que el segundo slot colisiona
        when(slotRepository.existeSolape(eq(10L), any(Instant.class), any(Instant.class)))
                .thenReturn(false)
                .thenReturn(true);

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Existe un solape de horario para el profesional");

        verify(slotRepository, never()).guardarLote(any());
        verify(auditoriaService, never()).registrarEvento(any(), any(), any(), any(), any(), any());
    }

    // =========================================================================
    // VALIDACIONES DE ENTRADA Y ESTADOS INACTIVOS
    // =========================================================================

    @Test
    void generarSlots_profesionalInexistente_lanzaRecursoNoEncontrado() {
        when(profesionalRepository.buscarPorPublicId("prof-inexistente")).thenReturn(Optional.empty());

        GenerarSlotsRequest request = new GenerarSlotsRequest(
                "prof-inexistente", SEDE_UUID, LocalDate.now(ZONE_BOGOTA).plusDays(1),
                LocalDate.now(ZONE_BOGOTA).plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(RecursoNoEncontradoException.class)
                .hasMessageContaining("Profesional no encontrado");
    }

    @Test
    void generarSlots_profesionalConUsuarioInactivo_lanzaDatosInvalidos() {
        Usuario usuarioInactivo = new Usuario(100L, "u-prof", "carlos@test.com", "hash", "INACTIVO", 0, null, Instant.now(), false);
        when(profesionalRepository.buscarPorPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(usuarioRepository.buscarPorId(100L)).thenReturn(Optional.of(usuarioInactivo));

        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, LocalDate.now(ZONE_BOGOTA).plusDays(1),
                LocalDate.now(ZONE_BOGOTA).plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("El profesional no tiene un usuario activo");
    }

    @Test
    void generarSlots_sedeInactiva_lanzaDatosInvalidos() {
        Sede sedeInactiva = new Sede(20L, 1L, SEDE_UUID, "Sede Inactiva", "Calle 100", "Bogota", "INACTIVO");
        when(profesionalRepository.buscarPorPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(usuarioRepository.buscarPorId(100L)).thenReturn(Optional.of(usuarioProf));
        when(sedeRepository.buscarPorPublicId(SEDE_UUID)).thenReturn(Optional.of(sedeInactiva));

        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, LocalDate.now(ZONE_BOGOTA).plusDays(1),
                LocalDate.now(ZONE_BOGOTA).plusDays(1), LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La sede seleccionada no se encuentra activa");
    }

    @Test
    void generarSlots_fechaInicioAnteriorAHoy_lanzaDatosInvalidos() {
        when(profesionalRepository.buscarPorPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(usuarioRepository.buscarPorId(100L)).thenReturn(Optional.of(usuarioProf));
        when(sedeRepository.buscarPorPublicId(SEDE_UUID)).thenReturn(Optional.of(sede));

        LocalDate ayer = LocalDate.now(ZONE_BOGOTA).minusDays(1);
        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, ayer, ayer.plusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La fecha de inicio no puede ser anterior");
    }

    @Test
    void generarSlots_fechaFinMenorAInicio_lanzaDatosInvalidos() {
        when(profesionalRepository.buscarPorPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(usuarioRepository.buscarPorId(100L)).thenReturn(Optional.of(usuarioProf));
        when(sedeRepository.buscarPorPublicId(SEDE_UUID)).thenReturn(Optional.of(sede));

        LocalDate fecha = LocalDate.now(ZONE_BOGOTA).plusDays(5);
        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha.minusDays(1),
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La fecha de fin no puede ser anterior");
    }

    @Test
    void generarSlots_horaFinMenorOIgualAInicio_lanzaDatosInvalidos() {
        when(profesionalRepository.buscarPorPublicId(PROF_UUID)).thenReturn(Optional.of(profesional));
        when(usuarioRepository.buscarPorId(100L)).thenReturn(Optional.of(usuarioProf));
        when(sedeRepository.buscarPorPublicId(SEDE_UUID)).thenReturn(Optional.of(sede));

        LocalDate fecha = LocalDate.now(ZONE_BOGOTA).plusDays(1);
        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha,
                LocalTime.of(10, 0), LocalTime.of(9, 0),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("La hora de fin debe ser posterior");
    }

    @Test
    void generarSlots_modalidadInvalida_lanzaDatosInvalidos() {
        stubCatalogosValidos();
        LocalDate fecha = LocalDate.now(ZONE_BOGOTA).plusDays(1);

        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(9, 0),
                20, "DOMICILIARIA", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Modalidad invalida");
    }

    @Test
    void generarSlots_sinSlotsGenerados_lanzaDatosInvalidos() {
        stubCatalogosValidos();
        LocalDate fecha = LocalDate.now(ZONE_BOGOTA).plusDays(1);

        // Ventana de 10 min pero slot dura 20 min -> genera 0 slots
        GenerarSlotsRequest request = new GenerarSlotsRequest(
                PROF_UUID, SEDE_UUID, fecha, fecha,
                LocalTime.of(8, 0), LocalTime.of(8, 10),
                20, "PRESENCIAL", null
        );

        assertThatThrownBy(() -> service.generarSlots(request, ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("No fue posible generar ningun slot");
    }

    // =========================================================================
    // BLOQUEO, DESBLOQUEO Y ELIMINACION
    // =========================================================================

    @Test
    void bloquearSlot_slotEnEstadoLibre_cambiaABloqueado() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotRepository.buscarPorPublicId("slot-1")).thenReturn(Optional.of(slot));
        when(usuarioRepository.buscarPorPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));

        SlotResponse resultado = service.bloquearSlot("slot-1", ADMIN_UUID, "127.0.0.1");

        assertThat(resultado.estado()).isEqualTo("BLOQUEADO");
        verify(slotRepository).cambiarEstado("slot-1", "BLOQUEADO");
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq("slot-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void bloquearSlot_slotNoLibre_lanzaDatosInvalidos() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "OCUPADO"
        );
        when(slotRepository.buscarPorPublicId("slot-1")).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.bloquearSlot("slot-1", ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Solo se pueden bloquear slots en estado LIBRE");
    }

    @Test
    void desbloquearSlot_slotEnEstadoBloqueado_cambiaALibre() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "BLOQUEADO"
        );
        when(slotRepository.buscarPorPublicId("slot-1")).thenReturn(Optional.of(slot));
        when(usuarioRepository.buscarPorPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));

        SlotResponse resultado = service.desbloquearSlot("slot-1", ADMIN_UUID, "127.0.0.1");

        assertThat(resultado.estado()).isEqualTo("LIBRE");
        verify(slotRepository).cambiarEstado("slot-1", "LIBRE");
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq("slot-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void desbloquearSlot_slotNoBloqueado_lanzaDatosInvalidos() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotRepository.buscarPorPublicId("slot-1")).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.desbloquearSlot("slot-1", ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Solo se pueden desbloquear slots en estado BLOQUEADO");
    }

    @Test
    void eliminarSlot_slotEnEstadoLibre_eliminaFisicamente() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "LIBRE"
        );
        when(slotRepository.buscarPorPublicId("slot-1")).thenReturn(Optional.of(slot));
        when(slotRepository.eliminar("slot-1")).thenReturn(1);
        when(usuarioRepository.buscarPorPublicId(ADMIN_UUID)).thenReturn(Optional.of(adminUser));

        service.eliminarSlot("slot-1", ADMIN_UUID, "127.0.0.1");

        verify(slotRepository).eliminar("slot-1");
        verify(auditoriaService).registrarEvento(
                eq(ADMIN_ID),
                eq(AccionAuditable.CAMBIO_ADMINISTRATIVO),
                eq("DISPONIBILIDAD_SLOT"),
                eq("slot-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("127.0.0.1")
        );
    }

    @Test
    void eliminarSlot_slotNoLibre_lanzaDatosInvalidos() {
        SlotResponse slot = new SlotResponse(
                "slot-1", PROF_UUID, "Carlos Perez", SEDE_UUID, "Sede Norte",
                ESP_UUID, "Medicina General", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "BLOQUEADO"
        );
        when(slotRepository.buscarPorPublicId("slot-1")).thenReturn(Optional.of(slot));

        assertThatThrownBy(() -> service.eliminarSlot("slot-1", ADMIN_UUID, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Solo se pueden eliminar slots en estado LIBRE");
    }

    @Test
    void listar_conFiltrosValidos_retornaPaginado() {
        when(slotRepository.listar(eq(0), eq(10), any(), any(), any(), any(), any(), eq("LIBRE")))
                .thenReturn(List.of());
        when(slotRepository.contar(any(), any(), any(), any(), any(), eq("LIBRE")))
                .thenReturn(0);

        PaginatedResponse<SlotResponse> resp = service.listar(
                0, 10, null, null, null, null, null, "LIBRE"
        );

        assertThat(resp.content()).isEmpty();
        assertThat(resp.totalElements()).isZero();
    }

    @Test
    void listar_estadoInvalido_lanzaDatosInvalidos() {
        assertThatThrownBy(() -> service.listar(0, 10, null, null, null, null, null, "INVALIDO"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("Estado invalido");
    }
}

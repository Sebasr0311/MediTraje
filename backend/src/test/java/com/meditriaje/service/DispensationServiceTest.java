package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.pharmacy.DetalleEntregaRequest;
import com.meditriaje.dto.pharmacy.DispensacionDetalleResponse;
import com.meditriaje.dto.pharmacy.DispensacionResponse;
import com.meditriaje.dto.pharmacy.RecetaDispensacionResponse;
import com.meditriaje.dto.pharmacy.RegistrarDispensacionRequest;
import com.meditriaje.dto.pharmacy.SaldoMedicamentoDto;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Dispensacion;
import com.meditriaje.model.EstadoRecetaDispensacion;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Receta;
import com.meditriaje.model.ResultadoAuditoria;
import com.meditriaje.model.Sede;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.DispensacionRepository;
import com.meditriaje.repository.DispensacionRepository.ItemPrescritoInfo;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.RecetaRepository;
import com.meditriaje.repository.SedeRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DispensationServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private SedeRepository sedeRepository;

    @Mock
    private RecetaRepository recetaRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private DispensacionRepository dispensacionRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private final Instant now = Instant.parse("2026-10-04T12:00:00Z");
    private final Clock clock = Clock.fixed(now, ZoneId.of("America/Bogota"));

    private DispensationService service;

    private final List<GrantedAuthority> authoritiesFarmaceutico = List.of(new SimpleGrantedAuthority("ROLE_FARMACEUTICO"));
    private final List<GrantedAuthority> authoritiesPaciente = List.of(new SimpleGrantedAuthority("ROLE_PACIENTE"));
    private final List<GrantedAuthority> authoritiesAdmin = List.of(new SimpleGrantedAuthority("ROLE_ADMINISTRADOR"));

    @BeforeEach
    void setUp() {
        service = new DispensationService(
                usuarioRepository,
                sedeRepository,
                recetaRepository,
                pacienteRepository,
                dispensacionRepository,
                auditoriaService,
                clock
        );
    }

    @Test
    @DisplayName("registrarDispensacion - Éxito: dispensa medicamentos, guarda lote INVIMA y audita")
    void registrarDispensacion_exito() {
        Usuario farmaceutico = new Usuario(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Sede sede = new Sede(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Receta receta = new Receta(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(5, ChronoUnit.DAYS));

        when(usuarioRepository.buscarPorPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(sede));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        List<ItemPrescritoInfo> items = List.of(
                new ItemPrescritoInfo(501L, "med-uuid-1", "Acetaminofen", 20),
                new ItemPrescritoInfo(502L, "med-uuid-2", "Ibuprofeno", 10)
        );
        when(dispensacionRepository.buscarItemsPrescripcion(30L)).thenReturn(items);
        when(dispensacionRepository.obtenerTotalesDispensadosPorRecetaId(30L)).thenReturn(Map.of(501L, 5)); // previo 5 de 20
        when(dispensacionRepository.crearDispensacion(any(Dispensacion.class))).thenReturn(888L);

        DispensacionResponse mockResp = new DispensacionResponse(
                "disp-uuid-abc",
                "rec-uuid-1",
                "sede-uuid-1",
                "Sede Central",
                "usr-farm-1",
                "farmacia@ejemplo.com",
                "Primera entrega",
                now,
                List.of(new DispensacionDetalleResponse("med-uuid-1", "MED-ACE-500", "Acetaminofen", 10, "LOTE-INV-1", LocalDate.of(2027, 12, 31), now))
        );
        when(dispensacionRepository.buscarPorPublicId(anyString())).thenReturn(Optional.of(mockResp));

        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                "Primera entrega",
                List.of(new DetalleEntregaRequest("med-uuid-1", 10, "LOTE-INV-1", LocalDate.of(2027, 12, 31)))
        );

        DispensacionResponse response = service.registrarDispensacion(request, "usr-farm-1", authoritiesFarmaceutico, "192.168.1.10");

        assertThat(response).isNotNull();
        assertThat(response.publicId()).isEqualTo("disp-uuid-abc");
        assertThat(response.detalles()).hasSize(1);
        assertThat(response.detalles().get(0).cantidadEntregada()).isEqualTo(10);

        verify(dispensacionRepository).guardarDetalles(eq(888L), any());
        verify(auditoriaService).registrarEvento(
                eq(10L),
                eq(AccionAuditable.DISPENSACION_RECETA),
                eq("RECETA"),
                eq("rec-uuid-1"),
                eq(ResultadoAuditoria.EXITO),
                eq("192.168.1.10")
        );
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza solicitud si la receta ha expirado")
    void registrarDispensacion_rechazaRecetaExpirada() {
        Usuario farmaceutico = new Usuario(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Sede sede = new Sede(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        // Emitida hace 35 días con vigencia de 30 días -> Expirada
        Receta receta = new Receta(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(35, ChronoUnit.DAYS));

        when(usuarioRepository.buscarPorPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(sede));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                null,
                List.of(new DetalleEntregaRequest("med-uuid-1", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("ha expirado");

        verify(dispensacionRepository, never()).crearDispensacion(any());
        verify(auditoriaService, never()).registrarEvento(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza acceso si el usuario no tiene rol ROLE_FARMACEUTICO")
    void registrarDispensacion_rechazaRolNoFarmaceutico() {
        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                null,
                List.of(new DetalleEntregaRequest("med-uuid-1", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-admin-1", authoritiesAdmin, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("ROLE_FARMACEUTICO");

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-pac-1", authoritiesPaciente, "127.0.0.1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("ROLE_FARMACEUTICO");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza sobre-dispensación que supere el saldo pendiente")
    void registrarDispensacion_rechazaSobreDispensacion() {
        Usuario farmaceutico = new Usuario(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Sede sede = new Sede(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Receta receta = new Receta(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(usuarioRepository.buscarPorPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(sede));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        // Prescrito: 10. Ya entregado: 8. Saldo disponible: 2.
        List<ItemPrescritoInfo> items = List.of(
                new ItemPrescritoInfo(501L, "med-uuid-1", "Amoxicilina", 10)
        );
        when(dispensacionRepository.buscarItemsPrescripcion(30L)).thenReturn(items);
        when(dispensacionRepository.obtenerTotalesDispensadosPorRecetaId(30L)).thenReturn(Map.of(501L, 8));

        // Solicita 5 (supera saldo de 2)
        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                null,
                List.of(new DetalleEntregaRequest("med-uuid-1", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("excede el saldo pendiente (2)");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza entrega de medicamento ya dispensado en su totalidad")
    void registrarDispensacion_rechazaMedicamentoYaTotalmenteDispensado() {
        Usuario farmaceutico = new Usuario(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Sede sede = new Sede(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Receta receta = new Receta(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(usuarioRepository.buscarPorPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(sede));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        // Prescrito: 10. Ya entregado: 10. Saldo: 0.
        List<ItemPrescritoInfo> items = List.of(
                new ItemPrescritoInfo(501L, "med-uuid-1", "Omeprazol", 10)
        );
        when(dispensacionRepository.buscarItemsPrescripcion(30L)).thenReturn(items);
        when(dispensacionRepository.obtenerTotalesDispensadosPorRecetaId(30L)).thenReturn(Map.of(501L, 10));

        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                null,
                List.of(new DetalleEntregaRequest("med-uuid-1", 1, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("ya ha sido dispensado en su totalidad");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza medicamento que no pertenece a la receta")
    void registrarDispensacion_rechazaMedicamentoNoPrescrito() {
        Usuario farmaceutico = new Usuario(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Sede sede = new Sede(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Receta receta = new Receta(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(usuarioRepository.buscarPorPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(sede));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        List<ItemPrescritoInfo> items = List.of(
                new ItemPrescritoInfo(501L, "med-uuid-1", "Omeprazol", 10)
        );
        when(dispensacionRepository.buscarItemsPrescripcion(30L)).thenReturn(items);

        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                null,
                List.of(new DetalleEntregaRequest("med-uuid-999", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("no forma parte de la receta médica");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza medicamentos duplicados en la misma solicitud")
    void registrarDispensacion_rechazaMedicamentosDuplicadosEnSolicitud() {
        Usuario farmaceutico = new Usuario(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Sede sede = new Sede(20L, 1L, "sede-uuid-1", "Sede Central", "Calle 100", "Bogotá", "ACTIVO");
        Receta receta = new Receta(30L, "rec-uuid-1", 100L, 200L, 300L, 30, now.minus(2, ChronoUnit.DAYS));

        when(usuarioRepository.buscarPorPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(sede));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                null,
                List.of(
                        new DetalleEntregaRequest("med-uuid-1", 5, "LOTE-1", null),
                        new DetalleEntregaRequest("med-uuid-1", 5, "LOTE-2", null)
                )
        );

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("duplicados");
    }

    @Test
    @DisplayName("registrarDispensacion - Rechaza sede inactiva")
    void registrarDispensacion_rechazaSedeInactiva() {
        Usuario farmaceutico = new Usuario(10L, "usr-farm-1", "farmacia@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Sede sedeInactiva = new Sede(20L, 1L, "sede-uuid-1", "Sede Antigua", "Calle 50", "Medellín", "INACTIVO");

        when(usuarioRepository.buscarPorPublicId("usr-farm-1")).thenReturn(Optional.of(farmaceutico));
        when(sedeRepository.buscarPorPublicId("sede-uuid-1")).thenReturn(Optional.of(sedeInactiva));

        RegistrarDispensacionRequest request = new RegistrarDispensacionRequest(
                "rec-uuid-1",
                "sede-uuid-1",
                null,
                List.of(new DetalleEntregaRequest("med-uuid-1", 5, "LOTE-1", null))
        );

        assertThatThrownBy(() -> service.registrarDispensacion(request, "usr-farm-1", authoritiesFarmaceutico, "127.0.0.1"))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("inactiva");
    }

    @Test
    @DisplayName("consultarRecetaParaFarmacia - Éxito: retorna saldos y datos de reclamación")
    void consultarRecetaParaFarmacia_exito() {
        RecetaDispensacionResponse mockResp = new RecetaDispensacionResponse(
                "rec-uuid-1",
                "REC-A1B2C3D4",
                "atn-1",
                "pac-1",
                "CC 10203040",
                "Carlos Gomez",
                "prof-1",
                "Dra. Perez",
                "Medicina General",
                30,
                now.minus(5, ChronoUnit.DAYS),
                now.plus(25, ChronoUnit.DAYS),
                false,
                EstadoRecetaDispensacion.PENDIENTE,
                List.of(),
                List.of()
        );
        when(dispensacionRepository.buscarRecetaDispensacionPorPublicId("rec-uuid-1")).thenReturn(Optional.of(mockResp));

        RecetaDispensacionResponse res = service.consultarRecetaParaFarmacia("rec-uuid-1", authoritiesFarmaceutico);

        assertThat(res).isNotNull();
        assertThat(res.recetaPublicId()).isEqualTo("rec-uuid-1");
        assertThat(res.codigoReclamacion()).isEqualTo("REC-A1B2C3D4");
    }

    @Test
    @DisplayName("buscarRecetasParaFarmacia - Retorna lista paginada")
    void buscarRecetasParaFarmacia_retornaPaginado() {
        RecetaDispensacionResponse item = new RecetaDispensacionResponse(
                "rec-uuid-1", "REC-1", "atn-1", "pac-1", "CC 10203040", "Carlos Gomez", "prof-1", "Dra. Perez",
                "Medicina General", 30, now, now.plus(30, ChronoUnit.DAYS), false, EstadoRecetaDispensacion.PENDIENTE,
                List.of(), List.of()
        );
        when(dispensacionRepository.buscarRecetasDispensacion("10203040", 0, 10)).thenReturn(List.of(item));
        when(dispensacionRepository.contarRecetasDispensacion("10203040")).thenReturn(1);

        PaginatedResponse<RecetaDispensacionResponse> pag = service.buscarRecetasParaFarmacia("10203040", 0, 10, authoritiesFarmaceutico);

        assertThat(pag.content()).hasSize(1);
        assertThat(pag.totalElements()).isEqualTo(1L);
        assertThat(pag.page()).isEqualTo(0);
    }

    @Test
    @DisplayName("consultarDispensacionPaciente - Éxito: paciente consulta su propia receta")
    void consultarDispensacionPaciente_exito() {
        Usuario userPac = new Usuario(50L, "usr-pac-1", "carlos@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Paciente paciente = new Paciente(100L, 50L, "pac-uuid-1", "CC", "10203040", "Carlos", "Gomez", LocalDate.of(1990, 1, 1), "3001234567", now, null);
        Receta receta = new Receta(30L, "rec-uuid-1", 200L, 100L, 300L, 30, now);

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(userPac));
        when(pacienteRepository.buscarPorUsuarioId(50L)).thenReturn(Optional.of(paciente));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(receta));

        RecetaDispensacionResponse mockResp = new RecetaDispensacionResponse(
                "rec-uuid-1", "REC-12345678", "atn-1", "pac-uuid-1", "CC 10203040", "Carlos Gomez", "prof-1", "Dra. Perez",
                "Medicina General", 30, now, now.plus(30, ChronoUnit.DAYS), false, EstadoRecetaDispensacion.PENDIENTE,
                List.of(), List.of()
        );
        when(dispensacionRepository.buscarRecetaDispensacionPorPublicId("rec-uuid-1")).thenReturn(Optional.of(mockResp));

        RecetaDispensacionResponse res = service.consultarDispensacionPaciente("rec-uuid-1", "usr-pac-1");

        assertThat(res).isNotNull();
        assertThat(res.recetaPublicId()).isEqualTo("rec-uuid-1");
    }

    @Test
    @DisplayName("consultarDispensacionPaciente - Rechaza consulta de receta ajena")
    void consultarDispensacionPaciente_rechazaRecetaAjena() {
        Usuario userPac = new Usuario(50L, "usr-pac-1", "carlos@ejemplo.com", "hash", "ACTIVO", 0, null, null, false, false, null, null);
        Paciente paciente = new Paciente(100L, 50L, "pac-uuid-1", "CC", "10203040", "Carlos", "Gomez", LocalDate.of(1990, 1, 1), "3001234567", now, null);
        // Receta pertenece al paciente 999L, no al 100L
        Receta recetaAjena = new Receta(30L, "rec-uuid-1", 200L, 999L, 300L, 30, now);

        when(usuarioRepository.buscarPorPublicId("usr-pac-1")).thenReturn(Optional.of(userPac));
        when(pacienteRepository.buscarPorUsuarioId(50L)).thenReturn(Optional.of(paciente));
        when(recetaRepository.buscarEntidadPorPublicId("rec-uuid-1")).thenReturn(Optional.of(recetaAjena));

        assertThatThrownBy(() -> service.consultarDispensacionPaciente("rec-uuid-1", "usr-pac-1"))
                .isInstanceOf(AccesoNoAutorizadoException.class)
                .hasMessageContaining("otro paciente");
    }
}

package com.meditriaje.service;

import com.meditriaje.dto.admin.AdminCitaDetalleResponse;
import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.repository.CitaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAppointmentServiceTest {

    @Mock
    private CitaRepository citaRepository;

    private AdminAppointmentService service;

    @BeforeEach
    void setUp() {
        service = new AdminAppointmentService(citaRepository);
    }

    @Test
    @DisplayName("listarCitas rechaza page negativo")
    void listarCitas_pageNegativo_lanzaExcepcion() {
        assertThatThrownBy(() -> service.listarCitas(null, null, null, null, null, -1, 10))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("número de página");
    }

    @Test
    @DisplayName("listarCitas rechaza size inválido")
    void listarCitas_sizeInvalido_lanzaExcepcion() {
        assertThatThrownBy(() -> service.listarCitas(null, null, null, null, null, 0, 0))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("tamaño de página");

        assertThatThrownBy(() -> service.listarCitas(null, null, null, null, null, 0, 501))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("tamaño de página");
    }

    @Test
    @DisplayName("listarCitas rechaza fechaDesde posterior a fechaHasta")
    void listarCitas_fechasInvertidas_lanzaExcepcion() {
        LocalDate desde = LocalDate.of(2026, 10, 20);
        LocalDate hasta = LocalDate.of(2026, 10, 10);

        assertThatThrownBy(() -> service.listarCitas(desde, hasta, null, null, null, 0, 20))
                .isInstanceOf(DatosInvalidosException.class)
                .hasMessageContaining("fecha inicial no puede ser posterior");
    }

    @Test
    @DisplayName("listarCitas retorna paginación con citas mapeadas")
    void listarCitas_exitoso() {
        AdminCitaDetalleResponse cita = new AdminCitaDetalleResponse(
                "cita-1", "pac-1", "Ana Gómez", "CC", "102030", "3111234567", "ana@test.com",
                "prof-1", "Dr. Carlos Mendoza", "RM-100", "esp-1", "Medicina General",
                "sede-1", "Sede Centro", "Bogotá", Instant.now(), Instant.now().plusSeconds(1200),
                "PRESENCIAL", "PROGRAMADA", null, "tri-1", "IV", "Control rutinario", Instant.now()
        );

        when(citaRepository.listarCitasAdmin(any(), any(), eq("prof-1"), eq("esp-1"), eq("PROGRAMADA"), eq(0), eq(20)))
                .thenReturn(List.of(cita));
        when(citaRepository.contarCitasAdmin(any(), any(), eq("prof-1"), eq("esp-1"), eq("PROGRAMADA")))
                .thenReturn(1);

        PaginatedResponse<AdminCitaDetalleResponse> result = service.listarCitas(
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 31),
                "prof-1",
                "esp-1",
                "PROGRAMADA",
                0,
                20
        );

        assertThat(result.content()).hasSize(1);
        assertThat(result.content().get(0).pacienteNombre()).isEqualTo("Ana Gómez");
        assertThat(result.totalElements()).isEqualTo(1L);
    }

    @Test
    @DisplayName("exportarCitasExcelCsv genera CSV con UTF-8 BOM y escapa fórmulas (SEC-001: =, +, -, @, \\t, \\r)")
    void exportarCitasExcelCsv_formatoCorrecto() {
        AdminCitaDetalleResponse cita = new AdminCitaDetalleResponse(
                "cita-1", "pac-1", "=SUM(A1:A2)", "CC", "+57300", "311", "email@test.com",
                "prof-1", "-DrCarlos", "@RM100", "esp-1", "\tMedicina General",
                "sede-1", "\rSede Centro", "Bogotá", Instant.parse("2026-10-15T13:00:00Z"),
                Instant.parse("2026-10-15T13:30:00Z"), "PRESENCIAL", "PROGRAMADA", null,
                null, null, "Chequeo", Instant.parse("2026-10-10T10:00:00Z")
        );

        when(citaRepository.listarCitasAdmin(any(), any(), any(), any(), any(), eq(0), eq(10000)))
                .thenReturn(List.of(cita));

        byte[] bytes = service.exportarCitasExcelCsv(
                LocalDate.of(2026, 10, 15),
                LocalDate.of(2026, 10, 15),
                null,
                null,
                null
        );

        String csv = new String(bytes, StandardCharsets.UTF_8);
        // Debe iniciar con BOM UTF-8
        assertThat(csv.charAt(0)).isEqualTo('\uFEFF');
        // Encabezado presente
        assertThat(csv).contains("Fecha Cita;Hora Inicio;Hora Fin");
        // Fórmula prevenida con comilla simple dentro de delimitador CSV para todos los caracteres SEC-001
        assertThat(csv).contains("\"'=SUM(A1:A2)\"");
        assertThat(csv).contains("\"'+57300\"");
        assertThat(csv).contains("\"'-DrCarlos\"");
        assertThat(csv).contains("\"'@RM100\"");
        assertThat(csv).contains("\"'\tMedicina General\"");
        assertThat(csv).contains("\"'\rSede Centro\"");
    }
}

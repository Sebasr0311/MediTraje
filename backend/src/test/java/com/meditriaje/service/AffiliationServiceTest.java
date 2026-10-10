package com.meditriaje.service;

import com.meditriaje.dto.affiliation.*;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AffiliationServiceTest {

    @Mock
    private EntidadEpsRepository epsRepository;

    @Mock
    private AfiliacionPacienteRepository afiliacionRepository;

    @Mock
    private LoteImportacionEpsRepository loteRepository;

    @Mock
    private AusenciaMedicaRepository ausenciaRepository;

    @Mock
    private RepresentacionLegalRepository representacionRepository;

    @Mock
    private PacienteRepository pacienteRepository;

    @Mock
    private ProfesionalRepository profesionalRepository;

    @Mock
    private UsuarioRepository usuarioRepository;

    @Mock
    private AuditoriaService auditoriaService;

    private Clock clock;
    private AffiliationService service;

    private final Instant now = Instant.parse("2026-03-30T10:00:00Z");

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(now, ZoneOffset.UTC);
        service = new AffiliationService(
                epsRepository,
                afiliacionRepository,
                loteRepository,
                ausenciaRepository,
                representacionRepository,
                pacienteRepository,
                profesionalRepository,
                usuarioRepository,
                auditoriaService,
                clock
        );
    }

    private byte[] crearExcelPrueba() throws IOException {
        try (Workbook wb = new XSSFWorkbook();
             ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Afiliados");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("TIPO_DOCUMENTO");
            header.createCell(1).setCellValue("NUMERO_DOCUMENTO");
            header.createCell(2).setCellValue("NOMBRES");
            header.createCell(3).setCellValue("APELLIDOS");
            header.createCell(4).setCellValue("REGIMEN");
            header.createCell(5).setCellValue("TIPO_AFILIADO");

            // Fila 1: Válida con fórmula maliciosa en Nombres
            Row row1 = sheet.createRow(1);
            row1.createCell(0).setCellValue("CC");
            row1.createCell(1).setCellValue("1065123456");
            row1.createCell(2).setCellValue("=CMD|' /C calc'!A0");
            row1.createCell(3).setCellValue("Perez");
            row1.createCell(4).setCellValue("CONTRIBUTIVO");
            row1.createCell(5).setCellValue("COTIZANTE");

            // Fila 2: Inválida (Tipo doc inexistente)
            Row row2 = sheet.createRow(2);
            row2.createCell(0).setCellValue("PASAPORTE_INVALIDO");
            row2.createCell(1).setCellValue("999");
            row2.createCell(2).setCellValue("Carlos");
            row2.createCell(3).setCellValue("Gomez");
            row2.createCell(4).setCellValue("SUBSIDIADO");
            row2.createCell(5).setCellValue("BENEFICIARIO");

            wb.write(bos);
            return bos.toByteArray();
        }
    }

    @Test
    @DisplayName("A02/A03: procesarArchivoExcelPreview valida filas, sanitiza fórmulas y retorna preview en staging")
    void procesarArchivoExcelPreview_validaYSanitizaExitosamente() throws IOException {
        byte[] excel = crearExcelPrueba();

        EntidadEps eps = new EntidadEps(1L, "eps-uuid", "EPS001", "Nueva EPS", "900123456", "CONTRIBUTIVO", "ACTIVA", now);
        when(epsRepository.buscarPorPublicId("eps-uuid")).thenReturn(Optional.of(eps));

        Usuario usuario = new Usuario(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        LoteImportacionEps loteGuardado = new LoteImportacionEps(
                100L, "lote-uuid", "test.xlsx", "hash", 1L, 2, 1, 1, "PREVIEW", 10L, now, null
        );
        when(loteRepository.guardarLote(any(LoteImportacionEps.class))).thenReturn(loteGuardado);

        DetalleImportacionEps d1 = new DetalleImportacionEps(
                1L, 100L, 1, "CC", "1065123456", "'=CMD|' /C calc'!A0", "Perez", "CONTRIBUTIVO", "COTIZANTE", "VALIDA", null
        );
        when(loteRepository.listarDetalles(100L, 20)).thenReturn(List.of(d1));

        LotePreviewResponse resp = service.procesarArchivoExcelPreview(
                excel, "test.xlsx", "eps-uuid", "usr-admin", "127.0.0.1"
        );

        assertNotNull(resp);
        assertEquals(2, resp.totalFilas());
        assertEquals(1, resp.filasValidas());
        assertEquals(1, resp.filasFallidas());
        assertEquals("PREVIEW", resp.estado());

        // Verificar que las filas de staging enviadas a guardar incluyen sanitización
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<DetalleImportacionEps>> captor = ArgumentCaptor.forClass(List.class);
        verify(loteRepository).guardarDetallesBatch(eq(100L), captor.capture());
        List<DetalleImportacionEps> filasGuardadas = captor.getValue();
        assertEquals(2, filasGuardadas.size());

        DetalleImportacionEps f1 = filasGuardadas.get(0);
        assertEquals("VALIDO", f1.estadoFila());
        assertTrue(f1.nombres().startsWith("'="), "La celda con fórmula debe comenzar con apóstrofe");

        DetalleImportacionEps f2 = filasGuardadas.get(1);
        assertEquals("ERROR", f2.estadoFila());
        assertNotNull(f2.errorMotivo());
    }

    @Test
    @DisplayName("A04: commitLote en modo ATOMIC_ALL falla si hay filas con error")
    void commitLote_modoAtomicAll_fallaSiHayErrores() {
        LoteImportacionEps lote = new LoteImportacionEps(
                100L, "lote-uuid", "test.xlsx", "hash", 1L, 2, 1, 1, "PREVIEW", 10L, now, null
        );
        when(loteRepository.buscarPorPublicId("lote-uuid")).thenReturn(Optional.of(lote));

        Usuario usuario = new Usuario(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        CommitLoteRequest req = new CommitLoteRequest("ATOMIC_ALL");

        ConflictoOperacionException ex = assertThrows(ConflictoOperacionException.class, () ->
                service.commitLote("lote-uuid", req, "usr-admin", "127.0.0.1")
        );
        assertTrue(ex.getMessage().contains("filas con error"));
    }

    @Test
    @DisplayName("A04: commitLote en modo VALID_ROWS aplica registros válidos e ignora errores")
    void commitLote_modoValidRows_aplicaExitosamente() {
        LoteImportacionEps lote = new LoteImportacionEps(
                100L, "lote-uuid", "test.xlsx", "hash", 1L, 2, 1, 1, "PREVIEW", 10L, now, null
        );
        when(loteRepository.buscarPorPublicId("lote-uuid")).thenReturn(Optional.of(lote));

        Usuario usuario = new Usuario(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        DetalleImportacionEps d1 = new DetalleImportacionEps(
                1L, 100L, 1, "CC", "1065123456", "Juan", "Perez", "CONTRIBUTIVO", "COTIZANTE", "VALIDA", null
        );
        when(loteRepository.listarFilasValidas(100L)).thenReturn(List.of(d1));
        when(afiliacionRepository.buscarPorDocumento("CC", "1065123456")).thenReturn(Optional.empty());

        CommitLoteRequest req = new CommitLoteRequest("VALID_ROWS");
        service.commitLote("lote-uuid", req, "usr-admin", "127.0.0.1");

        verify(afiliacionRepository).guardar(any(AfiliacionPaciente.class));
        verify(loteRepository).cambiarEstadoLote(eq(100L), eq("PROCESADO"), any(Instant.class));
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("A05: consultarAfiliacion no bloquea ni falla si el paciente no tiene EPS activa")
    void consultarAfiliacion_noBloqueanteSiNoExiste() {
        Usuario usuario = new Usuario(20L, "usr-enf", "enf@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-enf")).thenReturn(Optional.of(usuario));
        when(afiliacionRepository.buscarDetallePorDocumento("CC", "99999999")).thenReturn(Optional.empty());

        AfiliacionResponse resp = service.consultarAfiliacion("CC", "99999999", "usr-enf", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("NO_ASEGURADO", resp.regimen());
        assertEquals("NO_AFILIADO", resp.estado());
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }

    @Test
    @DisplayName("C02: registrarAusenciaMedica rechaza si fechaFin es antes de fechaInicio")
    void registrarAusencia_fechasInconsistentes_lanzaConflicto() {
        RegistrarAusenciaRequest req = new RegistrarAusenciaRequest(
                "prof-uuid",
                now.plusSeconds(3600),
                now,
                "Vacaciones"
        );

        assertThrows(ConflictoOperacionException.class, () ->
                service.registrarAusenciaMedica(req, "usr-admin", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("C02: registrarAusenciaMedica rechaza si existe traslape horario")
    void registrarAusencia_traslape_lanzaConflicto() {
        RegistrarAusenciaRequest req = new RegistrarAusenciaRequest(
                "prof-uuid",
                now,
                now.plusSeconds(7200),
                "Congreso Médico"
        );

        Usuario usuario = new Usuario(10L, "usr-admin", "admin@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-admin")).thenReturn(Optional.of(usuario));

        Profesional prof = new Profesional(
                5L, 50L, "prof-uuid", 1L, "CC", "12345", "RM-1", "Carlos", "Mendoza", "300123", now, now
        );
        when(profesionalRepository.buscarPorPublicId("prof-uuid")).thenReturn(Optional.of(prof));
        when(ausenciaRepository.existeTraslapeAusencia(eq(5L), any(Instant.class), any(Instant.class))).thenReturn(true);

        assertThrows(ConflictoOperacionException.class, () ->
                service.registrarAusenciaMedica(req, "usr-admin", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("C03: registrarRepresentacionLegal rechaza si el menor y el tutor son la misma persona")
    void registrarRepresentacion_mismoPaciente_lanzaConflicto() {
        RepresentacionLegalRequest req = new RepresentacionLegalRequest(
                "pac-1", "pac-1", "MADRE", "REGISTRO_CIVIL"
        );

        Usuario usuario = new Usuario(10L, "usr-pac", "pac@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-pac")).thenReturn(Optional.of(usuario));

        Paciente pac = new Paciente(
                1L, 10L, "pac-1", "CC", "1065000", "Ana", "Torres", LocalDate.of(1990, 1, 1), "300", now, now
        );
        when(pacienteRepository.buscarPorPublicId("pac-1")).thenReturn(Optional.of(pac));

        assertThrows(ConflictoOperacionException.class, () ->
                service.registrarRepresentacionLegal(req, "usr-pac", "127.0.0.1")
        );
    }

    @Test
    @DisplayName("C03: registrarRepresentacionLegal guarda tutoría exitosamente")
    void registrarRepresentacion_exitoso() {
        RepresentacionLegalRequest req = new RepresentacionLegalRequest(
                "pac-menor", "pac-madre", "MADRE", "REGISTRO_CIVIL_FOLIO_123"
        );

        Usuario usuario = new Usuario(10L, "usr-pac", "pac@hospital.com", "HASH", "ACTIVO", 0, null, now, false);
        when(usuarioRepository.buscarPorPublicId("usr-pac")).thenReturn(Optional.of(usuario));

        Paciente menor = new Paciente(
                1L, 101L, "pac-menor", "RC", "1065111", "Lucas", "Torres", LocalDate.of(2020, 1, 1), "300", now, now
        );
        Paciente madre = new Paciente(
                2L, 10L, "pac-madre", "CC", "1065222", "Ana", "Torres", LocalDate.of(1990, 1, 1), "300", now, now
        );
        when(pacienteRepository.buscarPorPublicId("pac-menor")).thenReturn(Optional.of(menor));
        when(pacienteRepository.buscarPorPublicId("pac-madre")).thenReturn(Optional.of(madre));

        RepresentacionLegalResponse resp = service.registrarRepresentacionLegal(req, "usr-pac", "127.0.0.1");

        assertNotNull(resp);
        assertEquals("pac-menor", resp.menorPublicId());
        assertEquals("pac-madre", resp.representantePublicId());
        assertEquals("MADRE", resp.parentesco());
        verify(representacionRepository).guardar(any(RepresentacionLegal.class));
        verify(auditoriaService).auditar(any(EventoAuditoria.class));
    }
}

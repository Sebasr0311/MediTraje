package com.meditriaje.service;

import com.meditriaje.dto.affiliation.*;
import com.meditriaje.exception.ConflictoOperacionException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.*;
import com.meditriaje.repository.*;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Servicio de Afiliaciones EPS, Importación Masiva Segura y Citas Avanzadas (Fases A y C, ADR-025, ADR-026).
 */
@Service
public class AffiliationService {

    private final EntidadEpsRepository epsRepository;
    private final AfiliacionPacienteRepository afiliacionRepository;
    private final LoteImportacionEpsRepository loteRepository;
    private final AusenciaMedicaRepository ausenciaRepository;
    private final RepresentacionLegalRepository representacionRepository;
    private final PacienteRepository pacienteRepository;
    private final ProfesionalRepository profesionalRepository;
    private final UsuarioRepository usuarioRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    private static final Set<String> TIPOS_DOC_VALIDOS = Set.of("CC", "TI", "RC", "CE", "PA", "PE", "PPT");
    private static final Set<String> REGIMENES_VALIDOS = Set.of("CONTRIBUTIVO", "SUBSIDIADO", "ESPECIAL", "NO_ASEGURADO");
    private static final int MAX_FILAS_LOTE = 5000;

    public AffiliationService(
            EntidadEpsRepository epsRepository,
            AfiliacionPacienteRepository afiliacionRepository,
            LoteImportacionEpsRepository loteRepository,
            AusenciaMedicaRepository ausenciaRepository,
            RepresentacionLegalRepository representacionRepository,
            PacienteRepository pacienteRepository,
            ProfesionalRepository profesionalRepository,
            UsuarioRepository usuarioRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.epsRepository = Objects.requireNonNull(epsRepository);
        this.afiliacionRepository = Objects.requireNonNull(afiliacionRepository);
        this.loteRepository = Objects.requireNonNull(loteRepository);
        this.ausenciaRepository = Objects.requireNonNull(ausenciaRepository);
        this.representacionRepository = Objects.requireNonNull(representacionRepository);
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository);
        this.profesionalRepository = Objects.requireNonNull(profesionalRepository);
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository);
        this.auditoriaService = Objects.requireNonNull(auditoriaService);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Procesa y valida un archivo Excel XLSX en staging (A02, A03).
     * Aplica protección contra Zip Bomb, sanitización de fórmulas y aislamiento transaccional.
     */
    @Transactional
    public LotePreviewResponse procesarArchivoExcelPreview(
            byte[] archivoBytes,
            String nombreArchivo,
            String epsPublicId,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(archivoBytes, "archivoBytes no puede ser nulo");
        if (archivoBytes.length == 0) {
            throw new ConflictoOperacionException("El archivo suministrado está vacío.");
        }
        if (archivoBytes.length > 10 * 1024 * 1024) {
            throw new ConflictoOperacionException("El archivo supera el tamaño máximo permitido de 10 MB.");
        }

        EntidadEps eps = epsRepository.buscarPorPublicId(epsPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("EPS no encontrada: " + epsPublicId));

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        String hashSha256 = calcularSha256(archivoBytes);

        // Mitigación Zip Bomb (CVE-2014-3529 / POI threshold)
        ZipSecureFile.setMinInflateRatio(0.01);

        List<DetalleImportacionEps> filasStaging = new ArrayList<>();
        int totalFilas = 0;
        int filasValidas = 0;
        int filasFallidas = 0;

        try (InputStream is = new ByteArrayInputStream(archivoBytes);
             Workbook workbook = WorkbookFactory.create(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            if (sheet == null) {
                throw new ConflictoOperacionException("El archivo Excel no contiene hojas de cálculo legibles.");
            }

            int lastRow = sheet.getLastRowNum();
            if (lastRow > MAX_FILAS_LOTE) {
                throw new ConflictoOperacionException("El archivo supera el límite de " + MAX_FILAS_LOTE + " filas por lote.");
            }

            // Fila 0 es encabezado (TIPO_DOCUMENTO, NUMERO_DOCUMENTO, NOMBRES, APELLIDOS, REGIMEN, TIPO_AFILIADO)
            for (int r = 1; r <= lastRow; r++) {
                Row row = sheet.getRow(r);
                if (row == null || celdaVacia(row.getCell(0))) {
                    continue;
                }

                totalFilas++;
                String tipoDoc = sanitizarValorCelda(obtenerValorCelda(row.getCell(0)));
                String numDoc = sanitizarValorCelda(obtenerValorCelda(row.getCell(1)));
                String nombres = sanitizarValorCelda(obtenerValorCelda(row.getCell(2)));
                String apellidos = sanitizarValorCelda(obtenerValorCelda(row.getCell(3)));
                String regimen = sanitizarValorCelda(obtenerValorCelda(row.getCell(4)));
                String tipoAfiliado = sanitizarValorCelda(obtenerValorCelda(row.getCell(5)));

                String errorMotivo = validarFila(tipoDoc, numDoc, regimen);
                boolean esValida = errorMotivo == null;

                if (esValida) {
                    filasValidas++;
                } else {
                    filasFallidas++;
                }

                filasStaging.add(new DetalleImportacionEps(
                        null,
                        null,
                        r + 1,
                        tipoDoc,
                        numDoc,
                        nombres,
                        apellidos,
                        regimen != null ? regimen.toUpperCase() : eps.regimenHabitual(),
                        tipoAfiliado != null ? tipoAfiliado.toUpperCase() : "COTIZANTE",
                        esValida ? "VALIDO" : "ERROR",
                        errorMotivo
                ));
            }

        } catch (Exception e) {
            if (e instanceof ConflictoOperacionException) throw (ConflictoOperacionException) e;
            throw new ConflictoOperacionException("Error al procesar el archivo Excel: " + e.getMessage());
        }

        if (totalFilas == 0) {
            throw new ConflictoOperacionException("El archivo Excel no contiene filas de datos para procesar.");
        }

        String lotePubId = UUID.randomUUID().toString();
        Instant ahora = Instant.now(clock);
        LoteImportacionEps lote = new LoteImportacionEps(
                null,
                lotePubId,
                nombreArchivo != null ? nombreArchivo : "afiliados.xlsx",
                hashSha256,
                eps.id(),
                totalFilas,
                filasValidas,
                filasFallidas,
                "PREVIEW",
                usuario.id(),
                ahora,
                null
        );

        LoteImportacionEps guardado = loteRepository.guardarLote(lote);
        loteRepository.guardarDetallesBatch(guardado.id(), filasStaging);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.IMPORTACION_EPS_PREVIEW,
                "LOTE_IMPORTACION_EPS",
                lotePubId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        List<DetalleImportacionEps> muestra = loteRepository.listarDetalles(guardado.id(), 20);
        List<LotePreviewResponse.FilaPreviewItem> itemsMuestra = muestra.stream().map(d -> new LotePreviewResponse.FilaPreviewItem(
                d.numeroFila(),
                d.tipoDocumento(),
                d.numeroDocumento(),
                d.nombres(),
                d.apellidos(),
                d.regimen(),
                d.tipoAfiliado(),
                d.estadoFila(),
                d.errorMotivo()
        )).toList();

        return new LotePreviewResponse(
                lotePubId,
                guardado.nombreArchivo(),
                eps.codigoMinSalud(),
                eps.nombre(),
                totalFilas,
                filasValidas,
                filasFallidas,
                "PREVIEW",
                itemsMuestra
        );
    }

    /**
     * Aplica el commit definitivo de un lote en staging a la base de datos (A03, A04).
     */
    @Transactional
    public void commitLote(
            String lotePublicId,
            CommitLoteRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        LoteImportacionEps lote = loteRepository.buscarPorPublicId(lotePublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Lote no encontrado: " + lotePublicId));

        if (!"PREVIEW".equals(lote.estado())) {
            throw new ConflictoOperacionException("El lote ya fue procesado o no se encuentra en estado PREVIEW (Estado: " + lote.estado() + ").");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        String modo = request != null && request.modoCommit() != null ? request.modoCommit() : "VALID_ROWS";
        if ("ATOMIC_ALL".equals(modo) && lote.filasFallidas() > 0) {
            throw new ConflictoOperacionException("El lote contiene " + lote.filasFallidas() + " filas con error y el modo seleccionado es ATOMIC_ALL.");
        }

        List<DetalleImportacionEps> filasValidas = loteRepository.listarFilasValidas(lote.id());
        Instant ahora = Instant.now(clock);

        for (DetalleImportacionEps fila : filasValidas) {
            // Verificar si el paciente ya tiene registro civil en PACIENTE
            Optional<Paciente> pacienteOpt = pacienteRepository.buscarPorDocumento(fila.tipoDocumento(), fila.numeroDocumento());
            Long pacienteId = pacienteOpt.map(Paciente::id).orElse(null);

            Optional<AfiliacionPaciente> previa = afiliacionRepository.buscarPorDocumento(fila.tipoDocumento(), fila.numeroDocumento());
            if (previa.isPresent()) {
                // Actualizar cobertura EPS existente
                afiliacionRepository.actualizarAfiliacion(
                        previa.get().id(),
                        lote.epsId(),
                        fila.regimen(),
                        fila.tipoAfiliado(),
                        "ACTIVO",
                        ahora
                );
            } else {
                // Insertar nueva afiliación
                AfiliacionPaciente nueva = new AfiliacionPaciente(
                        null,
                        UUID.randomUUID().toString(),
                        pacienteId,
                        fila.tipoDocumento(),
                        fila.numeroDocumento(),
                        lote.epsId(),
                        fila.regimen(),
                        fila.tipoAfiliado(),
                        "ACTIVO",
                        LocalDate.now(clock),
                        "CARGA_MASIVA",
                        ahora,
                        ahora,
                        ahora
                );
                afiliacionRepository.guardar(nueva);
            }
        }

        loteRepository.cambiarEstadoLote(lote.id(), "PROCESADO", ahora);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.IMPORTACION_EPS_COMMIT,
                "LOTE_IMPORTACION_EPS",
                lote.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));
    }

    /**
     * Consulta el estado de afiliación y aseguramiento EPS de un paciente (A05).
     * Nunca bloquea la atención médica de urgencias si el paciente no tiene EPS activa.
     */
    public AfiliacionResponse consultarAfiliacion(
            String tipoDocumento,
            String numeroDocumento,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario autenticado no encontrado."));

        Optional<AfiliacionResponse> detalle = afiliacionRepository.buscarDetallePorDocumento(tipoDocumento, numeroDocumento);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.AFILIACION_CONSULTADA,
                "AFILIACION_PACIENTE",
                tipoDocumento + ":" + numeroDocumento,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return detalle.orElse(new AfiliacionResponse(
                null,
                null,
                tipoDocumento,
                numeroDocumento,
                null,
                "NO_EPS",
                "PARTICULAR / NO ASEGURADO",
                "NO_ASEGURADO",
                "NO_AFILIADO",
                "NO_AFILIADO",
                null,
                "CONSULTA_DIRECTA",
                Instant.now(clock)
        ));
    }

    /**
     * Registra una ausencia médica o bloqueo de agenda (C02).
     */
    @Transactional
    public AusenciaResponse registrarAusenciaMedica(
            RegistrarAusenciaRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        if (request.fechaFin().isBefore(request.fechaInicio())) {
            throw new ConflictoOperacionException("fechaFin no puede ser anterior a fechaInicio.");
        }

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Profesional prof = profesionalRepository.buscarPorPublicId(request.profesionalPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional no encontrado: " + request.profesionalPublicId()));

        if (ausenciaRepository.existeTraslapeAusencia(prof.id(), request.fechaInicio(), request.fechaFin())) {
            throw new ConflictoOperacionException("Ya existe una ausencia médica activa registrada en ese rango horario.");
        }

        Instant ahora = Instant.now(clock);
        String ausenciaPubId = UUID.randomUUID().toString();
        AusenciaMedica ausencia = new AusenciaMedica(
                null,
                ausenciaPubId,
                prof.id(),
                request.fechaInicio(),
                request.fechaFin(),
                request.motivo(),
                "ACTIVA",
                usuario.id(),
                ahora
        );
        ausenciaRepository.guardar(ausencia);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.AUSENCIA_MEDICA_REGISTRADA,
                "AUSENCIA_MEDICA",
                ausenciaPubId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return new AusenciaResponse(
                ausenciaPubId,
                prof.publicId(),
                prof.nombres() + " " + prof.apellidos(),
                request.fechaInicio(),
                request.fechaFin(),
                request.motivo(),
                "ACTIVA"
        );
    }

    /**
     * Registra el vínculo de representación legal para menores de edad (C03).
     */
    @Transactional
    public RepresentacionLegalResponse registrarRepresentacionLegal(
            RepresentacionLegalRequest request,
            String usuarioAutenticadoPublicId,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Paciente menor = pacienteRepository.buscarPorPublicId(request.menorPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Menor de edad no encontrado: " + request.menorPublicId()));

        Paciente rep = pacienteRepository.buscarPorPublicId(request.representantePublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Representante legal no encontrado: " + request.representantePublicId()));

        if (menor.id().equals(rep.id())) {
            throw new ConflictoOperacionException("El menor y el representante legal no pueden ser el mismo paciente.");
        }

        Instant ahora = Instant.now(clock);
        String repPubId = UUID.randomUUID().toString();
        RepresentacionLegal rl = new RepresentacionLegal(
                null,
                repPubId,
                menor.id(),
                rep.id(),
                request.parentesco(),
                request.documentoSoporte(),
                true,
                ahora
        );
        representacionRepository.guardar(rl);

        auditoriaService.auditar(new EventoAuditoria(
                usuario.id(),
                AccionAuditable.REPRESENTACION_LEGAL_REGISTRADA,
                "REPRESENTACION_LEGAL",
                repPubId,
                ResultadoAuditoria.EXITO,
                ipOrigen
        ));

        return new RepresentacionLegalResponse(
                repPubId,
                menor.publicId(),
                menor.nombres() + " " + menor.apellidos(),
                menor.numeroDocumento(),
                rep.publicId(),
                rep.nombres() + " " + rep.apellidos(),
                rep.numeroDocumento(),
                request.parentesco(),
                true
        );
    }

    public List<EntidadEps> listarEpsActivas() {
        return epsRepository.listarActivas();
    }

    public List<AusenciaResponse> listarAusenciasProfesional(String profesionalPublicId) {
        Profesional prof = profesionalRepository.buscarPorPublicId(profesionalPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Profesional no encontrado: " + profesionalPublicId));
        return ausenciaRepository.listarPorProfesionalId(prof.id());
    }

    public List<RepresentacionLegalResponse> listarRepresentacionesPorTutor(String tutorPublicId) {
        Paciente tutor = pacienteRepository.buscarPorPublicId(tutorPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Tutor no encontrado: " + tutorPublicId));
        return representacionRepository.listarPorRepresentanteId(tutor.id());
    }

    // --- Métodos de Utilidad y Seguridad ---

    private String validarFila(String tipoDoc, String numDoc, String regimen) {
        if (tipoDoc == null || !TIPOS_DOC_VALIDOS.contains(tipoDoc.toUpperCase())) {
            return "Tipo de documento no válido. Esperado: CC, TI, RC, CE, PA, PPT.";
        }
        if (numDoc == null || numDoc.isBlank() || numDoc.length() < 3 || numDoc.length() > 20) {
            return "Número de documento inválido o de longitud incorrecta.";
        }
        if (regimen != null && !REGIMENES_VALIDOS.contains(regimen.toUpperCase())) {
            return "Régimen no válido. Esperado: CONTRIBUTIVO, SUBSIDIADO, ESPECIAL.";
        }
        return null;
    }

    private String obtenerValorCelda(Cell cell) {
        if (cell == null) return null;
        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> null;
        };
    }

    private boolean celdaVacia(Cell cell) {
        String val = obtenerValorCelda(cell);
        return val == null || val.trim().isEmpty();
    }

    /**
     * Sanitiza el valor de celda para neutralizar inyecciones de fórmulas (D1, SEC-001).
     */
    private String sanitizarValorCelda(String val) {
        if (val == null) return null;
        String s = val.trim();
        if (s.startsWith("=") || s.startsWith("+") || s.startsWith("-") || s.startsWith("@")) {
            return "'" + s;
        }
        return s;
    }

    private String calcularSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }
}

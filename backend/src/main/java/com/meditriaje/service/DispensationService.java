package com.meditriaje.service;

import com.meditriaje.dto.common.PaginatedResponse;
import com.meditriaje.dto.pharmacy.DetalleEntregaRequest;
import com.meditriaje.dto.pharmacy.DispensacionResponse;
import com.meditriaje.dto.pharmacy.RecetaDispensacionResponse;
import com.meditriaje.dto.pharmacy.RegistrarDispensacionRequest;
import com.meditriaje.exception.AccesoNoAutorizadoException;
import com.meditriaje.exception.DatosInvalidosException;
import com.meditriaje.exception.RecursoNoEncontradoException;
import com.meditriaje.model.AccionAuditable;
import com.meditriaje.model.Dispensacion;
import com.meditriaje.model.DispensacionDetalle;
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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Servicio de dispensación farmacéutica de recetas médicas y control de saldos (F2.4, ADR-016).
 * Aplica reglas de vigencia, control estricto de entregas parciales y totales, trazabilidad INVIMA y auditoría.
 */
@Service
public class DispensationService {

    private final UsuarioRepository usuarioRepository;
    private final SedeRepository sedeRepository;
    private final RecetaRepository recetaRepository;
    private final PacienteRepository pacienteRepository;
    private final DispensacionRepository dispensacionRepository;
    private final AuditoriaService auditoriaService;
    private final Clock clock;

    public DispensationService(
            UsuarioRepository usuarioRepository,
            SedeRepository sedeRepository,
            RecetaRepository recetaRepository,
            PacienteRepository pacienteRepository,
            DispensacionRepository dispensacionRepository,
            AuditoriaService auditoriaService,
            Clock clock
    ) {
        this.usuarioRepository = Objects.requireNonNull(usuarioRepository, "usuarioRepository no puede ser nulo");
        this.sedeRepository = Objects.requireNonNull(sedeRepository, "sedeRepository no puede ser nulo");
        this.recetaRepository = Objects.requireNonNull(recetaRepository, "recetaRepository no puede ser nulo");
        this.pacienteRepository = Objects.requireNonNull(pacienteRepository, "pacienteRepository no puede ser nulo");
        this.dispensacionRepository = Objects.requireNonNull(dispensacionRepository, "dispensacionRepository no puede ser nulo");
        this.auditoriaService = Objects.requireNonNull(auditoriaService, "auditoriaService no puede ser nulo");
        this.clock = Objects.requireNonNull(clock, "clock no puede ser nulo");
    }

    /**
     * Registra un evento de dispensación en farmacia con control estricto de saldos y vigencia (ADR-016).
     *
     * @param request                   Datos de la dispensación y cantidades por medicamento
     * @param usuarioAutenticadoPublicId UUID del dispensador autenticado
     * @param authorities               Roles del usuario autenticado
     * @param ipOrigen                  IP del cliente
     * @return DispensacionResponse con detalles consolidados
     */
    @Transactional
    public DispensacionResponse registrarDispensacion(
            RegistrarDispensacionRequest request,
            String usuarioAutenticadoPublicId,
            Collection<? extends GrantedAuthority> authorities,
            String ipOrigen
    ) {
        Objects.requireNonNull(request, "request no puede ser nulo");
        validarRolFarmaceutico(authorities);

        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario dispensador no encontrado."));

        if (!"ACTIVO".equalsIgnoreCase(usuario.estado())) {
            throw new AccesoNoAutorizadoException("El usuario dispensador se encuentra inactivo o bloqueado.");
        }

        Sede sede = sedeRepository.buscarPorPublicId(request.sedePublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Sede farmacéutica no encontrada: " + request.sedePublicId()));

        if (!"ACTIVO".equalsIgnoreCase(sede.estado())) {
            throw new DatosInvalidosException("La sede seleccionada se encuentra inactiva.");
        }

        Receta receta = recetaRepository.buscarEntidadPorPublicId(request.recetaPublicId())
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta médica no encontrada: " + request.recetaPublicId()));

        // 1. Validación estricta de vigencia de la receta
        Instant fechaEmision = receta.createdAt() != null ? receta.createdAt() : Instant.now(clock);
        Instant fechaVencimiento = fechaEmision.plus(receta.vigenciaDias(), ChronoUnit.DAYS);
        Instant ahora = Instant.now(clock);

        if (ahora.isAfter(fechaVencimiento)) {
            throw new DatosInvalidosException("La receta médica ha expirado (vigencia de " + receta.vigenciaDias()
                    + " días finalizada el " + fechaVencimiento + "). No es posible dispensar medicamentos.");
        }

        // 2. Validación de lista no vacía y sin duplicados
        List<DetalleEntregaRequest> detallesRequest = request.detalles();
        if (detallesRequest == null || detallesRequest.isEmpty()) {
            throw new DatosInvalidosException("Debe especificar al menos un medicamento para dispensar.");
        }

        Set<String> medicamentosEnSolicitud = new HashSet<>();
        for (DetalleEntregaRequest det : detallesRequest) {
            if (!medicamentosEnSolicitud.add(det.medicamentoPublicId().trim())) {
                throw new DatosInvalidosException("La solicitud contiene medicamentos duplicados: " + det.medicamentoPublicId());
            }
        }

        // 3. Validación de correspondencia con la receta y control matemático de saldos
        List<ItemPrescritoInfo> itemsPrescritos = dispensacionRepository.buscarItemsPrescripcion(receta.id());
        Map<String, ItemPrescritoInfo> itemsMap = itemsPrescritos.stream()
                .collect(Collectors.toMap(ItemPrescritoInfo::medicamentoPublicId, Function.identity()));

        Map<Long, Integer> previosDispensados = dispensacionRepository.obtenerTotalesDispensadosPorRecetaId(receta.id());

        for (DetalleEntregaRequest det : detallesRequest) {
            ItemPrescritoInfo item = itemsMap.get(det.medicamentoPublicId().trim());
            if (item == null) {
                throw new DatosInvalidosException("El medicamento con ID " + det.medicamentoPublicId()
                        + " no forma parte de la receta médica especificada.");
            }

            int yaDispensado = previosDispensados.getOrDefault(item.recetaDetalleId(), 0);
            int saldoDisponible = item.cantidadPrescrita() - yaDispensado;

            if (saldoDisponible <= 0) {
                throw new DatosInvalidosException("El medicamento '" + item.medicamentoNombre()
                        + "' ya ha sido dispensado en su totalidad (saldo: 0).");
            }

            if (det.cantidadEntregada() > saldoDisponible) {
                throw new DatosInvalidosException("La cantidad a entregar (" + det.cantidadEntregada()
                        + ") excede el saldo pendiente (" + saldoDisponible + ") para el medicamento '"
                        + item.medicamentoNombre() + "'.");
            }
        }

        // 4. Persistencia atómica de la cabecera y detalles de dispensación
        String dispensacionPublicId = UUID.randomUUID().toString();
        Dispensacion dispensacion = new Dispensacion(
                null,
                dispensacionPublicId,
                receta.id(),
                sede.id(),
                usuario.id(),
                request.observaciones() != null ? request.observaciones().trim() : null,
                ahora
        );

        Long dispensacionId = dispensacionRepository.crearDispensacion(dispensacion);

        List<DispensacionDetalle> detallesEntities = detallesRequest.stream().map(d -> {
            ItemPrescritoInfo item = itemsMap.get(d.medicamentoPublicId().trim());
            return new DispensacionDetalle(
                    null,
                    dispensacionId,
                    item.recetaDetalleId(),
                    d.cantidadEntregada(),
                    d.lote() != null ? d.lote().trim() : null,
                    d.fechaVencimientoLote(),
                    ahora
            );
        }).toList();

        dispensacionRepository.guardarDetalles(dispensacionId, detallesEntities);

        // 5. Auditoría inmutable obligatoria (ADR-011, ADR-016)
        auditoriaService.registrarEvento(
                usuario.id(),
                AccionAuditable.DISPENSACION_RECETA,
                "RECETA",
                receta.publicId(),
                ResultadoAuditoria.EXITO,
                ipOrigen
        );

        return dispensacionRepository.buscarPorPublicId(dispensacionPublicId)
                .orElseThrow(() -> new IllegalStateException("Error al recuperar la dispensación recién registrada."));
    }

    /**
     * Consulta el detalle y saldos de una receta médica para ventanilla de farmacia.
     *
     * @param recetaPublicId UUID público de la receta
     * @param authorities    Roles del usuario solicitante
     * @return RecetaDispensacionResponse con saldos y entregas previas
     */
    public RecetaDispensacionResponse consultarRecetaParaFarmacia(
            String recetaPublicId,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validarRolFarmaceutico(authorities);
        return dispensacionRepository.buscarRecetaDispensacionPorPublicId(recetaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta médica no encontrada: " + recetaPublicId));
    }

    /**
     * Búsqueda paginada de recetas para ventanilla de farmacia por documento o código.
     *
     * @param query       Término de búsqueda (código de reclamación REC-XXXXXXXX, documento o nombre del paciente)
     * @param page        Página (0-indexada)
     * @param size        Tamaño de página
     * @param authorities Roles del solicitante
     * @return PaginatedResponse con recetas para dispensación
     */
    public PaginatedResponse<RecetaDispensacionResponse> buscarRecetasParaFarmacia(
            String query,
            int page,
            int size,
            Collection<? extends GrantedAuthority> authorities
    ) {
        validarRolFarmaceutico(authorities);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));

        List<RecetaDispensacionResponse> content = dispensacionRepository.buscarRecetasDispensacion(query, safePage, safeSize);
        int total = dispensacionRepository.contarRecetasDispensacion(query);

        return PaginatedResponse.of(content, safePage, safeSize, total);
    }

    /**
     * Consulta del estado de entrega y código de reclamación de una receta por parte del paciente titular.
     *
     * @param recetaPublicId             UUID público de la receta
     * @param usuarioAutenticadoPublicId UUID del paciente autenticado
     * @return RecetaDispensacionResponse del paciente
     */
    public RecetaDispensacionResponse consultarDispensacionPaciente(
            String recetaPublicId,
            String usuarioAutenticadoPublicId
    ) {
        Usuario usuario = usuarioRepository.buscarPorPublicId(usuarioAutenticadoPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Usuario no encontrado."));

        Paciente paciente = pacienteRepository.buscarPorUsuarioId(usuario.id())
                .orElseThrow(() -> new AccesoNoAutorizadoException("Usuario no registrado como paciente en el sistema."));

        Receta receta = recetaRepository.buscarEntidadPorPublicId(recetaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta médica no encontrada: " + recetaPublicId));

        if (!receta.pacienteId().equals(paciente.id())) {
            throw new AccesoNoAutorizadoException("No tiene autorización para consultar recetas médicas de otro paciente.");
        }

        return dispensacionRepository.buscarRecetaDispensacionPorPublicId(recetaPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Receta médica no encontrada: " + recetaPublicId));
    }

    /**
     * Consulta una dispensación individual por su UUID público.
     *
     * @param dispensacionPublicId UUID público de la dispensación
     * @return DispensacionResponse
     */
    public DispensacionResponse consultarDispensacionPorPublicId(String dispensacionPublicId) {
        return dispensacionRepository.buscarPorPublicId(dispensacionPublicId)
                .orElseThrow(() -> new RecursoNoEncontradoException("Dispensación no encontrada: " + dispensacionPublicId));
    }

    private void validarRolFarmaceutico(Collection<? extends GrantedAuthority> authorities) {
        if (authorities == null) {
            throw new AccesoNoAutorizadoException("Acceso restringido: credenciales de autorización ausentes.");
        }

        boolean esFarmaceutico = authorities.stream()
                .anyMatch(a -> "ROLE_FARMACEUTICO".equals(a.getAuthority()));

        if (!esFarmaceutico) {
            throw new AccesoNoAutorizadoException("Solo personal de farmacia autorizado (ROLE_FARMACEUTICO) puede acceder a esta operación.");
        }
    }
}

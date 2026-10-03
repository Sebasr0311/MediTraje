package com.meditriaje.service;

import com.meditriaje.dto.appointment.CitaResponse;
import com.meditriaje.dto.appointment.ReservarCitaRequest;
import com.meditriaje.exception.CitaNoDisponibleException;
import com.meditriaje.model.Cita;
import com.meditriaje.model.DisponibilidadSlot;
import com.meditriaje.model.Paciente;
import com.meditriaje.model.Profesional;
import com.meditriaje.model.Usuario;
import com.meditriaje.repository.CitaRepository;
import com.meditriaje.repository.DisponibilidadSlotRepository;
import com.meditriaje.repository.PacienteRepository;
import com.meditriaje.repository.ProfesionalRepository;
import com.meditriaje.repository.UsuarioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Prueba determinista de concurrencia para la reserva de citas (M4.4, HU-04, ADR-006).
 *
 * <p>Verifica que ante N solicitudes simultáneas sobre el MISMO slot:
 * <ul>
 *   <li>Exactamente una solicitud tiene éxito (201 / {@link CitaResponse}).</li>
 *   <li>Las N - 1 solicitudes restantes son rechazadas con {@link CitaNoDisponibleException} (HTTP 409).</li>
 *   <li>El comportamiento es 100% determinista y corre en CI sin depender de infraestructura externa.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AppointmentConcurrencyTest {

    private static final int NUM_HILOS = 12; // Al menos 10 hilos según criterio M4.4
    private static final Instant TIEMPO_FIJO = Instant.parse("2026-10-10T10:00:00Z");

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private PacienteRepository pacienteRepository;
    @Mock
    private DisponibilidadSlotRepository disponibilidadSlotRepository;
    @Mock
    private ProfesionalRepository profesionalRepository;
    @Mock
    private CitaRepository citaRepository;
    @Mock
    private AuditoriaService auditoriaService;

    @Test
    @DisplayName("M4.4: 12 hilos concurrentes compitiendo por el mismo slot -> exactamente 1 gana y 11 reciben CitaNoDisponible")
    void concurrencia_doceHilosMismoSlot_exactamenteUnoTieneExito() throws Exception {
        ejecutarPruebaConcurrencia();
    }

    @RepeatedTest(value = 5, name = "Repetición {currentRepetition} de {totalRepetitions} - Determinismo de concurrencia M4.4")
    @DisplayName("M4.4: Repetición para garantizar ausencia total de flakiness en concurrencia")
    void concurrencia_repetida_mantieneDeterminismo() throws Exception {
        ejecutarPruebaConcurrencia();
    }

    private void ejecutarPruebaConcurrencia() throws Exception {
        Clock clock = Clock.fixed(TIEMPO_FIJO, ZoneOffset.UTC);
        AppointmentService service = new AppointmentService(
                usuarioRepository,
                pacienteRepository,
                disponibilidadSlotRepository,
                profesionalRepository,
                citaRepository,
                auditoriaService,
                clock
        );

        String slotPublicId = "slot-uuid-concurrente";
        Long slotId = 100L;
        Long profId = 200L;
        Long espId = 10L;

        DisponibilidadSlot slot = new DisponibilidadSlot(
                slotId,
                slotPublicId,
                profId,
                1L,
                espId,
                TIEMPO_FIJO.plusSeconds(3600), // En el futuro
                TIEMPO_FIJO.plusSeconds(4800),
                "PRESENCIAL",
                "LIBRE"
        );

        Profesional profesional = new Profesional(
                profId, 50L, "prof-uuid", espId, "RM-12345", "Dra. Laura", "Pérez", TIEMPO_FIJO, null
        );

        when(disponibilidadSlotRepository.buscarEntidadPorPublicId(slotPublicId)).thenReturn(Optional.of(slot));
        when(profesionalRepository.buscarPorId(profId)).thenReturn(Optional.of(profesional));

        // Modelado atómico fiel de la base de datos:
        // 1. UPDATE DISPONIBILIDAD_SLOT SET ESTADO = 'OCUPADO' WHERE ID = ? AND ESTADO = 'LIBRE'
        AtomicReference<String> estadoSlotEnBd = new AtomicReference<>("LIBRE");
        when(disponibilidadSlotRepository.reservarSlot(slotId)).thenAnswer(inv -> {
            boolean gano = estadoSlotEnBd.compareAndSet("LIBRE", "OCUPADO");
            return gano ? 1 : 0;
        });

        // 2. Índice funcional único UQ_CITA_SLOT_ACTIVA: sólo una cita activa por slot
        Set<Long> slotsConCitaActiva = ConcurrentHashMap.newKeySet();
        when(citaRepository.crear(any(Cita.class))).thenAnswer(inv -> {
            Cita c = inv.getArgument(0);
            if (!slotsConCitaActiva.add(c.slotId())) {
                throw new DataIntegrityViolationException("ORA-00001: unique constraint (MEDITRIAJE_OWNER.UQ_CITA_SLOT_ACTIVA) violated");
            }
            return 999L;
        });

        lenient().when(citaRepository.buscarPorPublicId(anyString())).thenAnswer(inv -> {
            String pubId = inv.getArgument(0);
            return Optional.of(new CitaResponse(
                    pubId,
                    slotPublicId,
                    "pac-uuid",
                    "Juan Pérez",
                    "prof-uuid",
                    "Dra. Laura Pérez",
                    "esp-uuid",
                    "Medicina General",
                    "sede-uuid",
                    "Sede Norte",
                    "Calle 100",
                    TIEMPO_FIJO.plusSeconds(3600),
                    TIEMPO_FIJO.plusSeconds(4800),
                    "PRESENCIAL",
                    "PROGRAMADA",
                    null,
                    null,
                    TIEMPO_FIJO
            ));
        });

        // Configuración para cada hilo / paciente
        for (int i = 0; i < NUM_HILOS; i++) {
            String userPubId = "user-pac-" + i;
            Long usuarioId = (long) (1000 + i);
            Long pacienteId = (long) (2000 + i);

            Usuario u = new Usuario(
                    usuarioId, userPubId, "pac" + i + "@mail.com", "hash", "ACTIVO", 0, null, TIEMPO_FIJO, false
            );
            Paciente p = new Paciente(
                    pacienteId, usuarioId, "pac-pub-" + i, "CC", "100000" + i, "Paciente" + i, "Prueba",
                    java.time.LocalDate.of(1990, 1, 1), "3000000000", TIEMPO_FIJO, null
            );

            when(usuarioRepository.buscarPorPublicId(userPubId)).thenReturn(Optional.of(u));
            when(pacienteRepository.buscarPorUsuarioId(usuarioId)).thenReturn(Optional.of(p));
        }

        // Ejecución concurrente
        ExecutorService executor = Executors.newFixedThreadPool(NUM_HILOS);
        CountDownLatch readyLatch = new CountDownLatch(NUM_HILOS);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(NUM_HILOS);

        AtomicInteger exitos = new AtomicInteger(0);
        AtomicInteger citasNoDisponibles = new AtomicInteger(0);
        AtomicInteger otrosErrores = new AtomicInteger(0);

        ReservarCitaRequest request = new ReservarCitaRequest(slotPublicId, null);

        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < NUM_HILOS; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await(); // Barrera de inicio simultáneo
                    service.reservarCita(request, "user-pac-" + index, "192.168.1." + index);
                    exitos.incrementAndGet();
                } catch (CitaNoDisponibleException e) {
                    citasNoDisponibles.incrementAndGet();
                } catch (Throwable t) {
                    otrosErrores.incrementAndGet();
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        readyLatch.await(5, TimeUnit.SECONDS);
        startLatch.countDown(); // ¡Disparo de salida concurrente!
        boolean terminaronTodos = finishLatch.await(10, TimeUnit.SECONDS);

        executor.shutdown();

        assertThat(terminaronTodos).as("Todos los hilos concurrentes deben culminar").isTrue();
        assertThat(exitos.get())
                .as("Exactamente un hilo debe tener éxito en reservar el slot libre")
                .isEqualTo(1);
        assertThat(citasNoDisponibles.get())
                .as("Los N - 1 hilos restantes deben recibir CitaNoDisponibleException")
                .isEqualTo(NUM_HILOS - 1);
        assertThat(otrosErrores.get())
                .as("No debe haber excepciones inesperadas")
                .isEqualTo(0);
        assertThat(estadoSlotEnBd.get())
                .as("El slot debe quedar en estado OCUPADO")
                .isEqualTo("OCUPADO");
        assertThat(slotsConCitaActiva)
                .as("Debe haber exactamente un registro de cita activa para el slot")
                .hasSize(1);
    }
}

package com.meditriaje.exception;

import com.meditriaje.dto.ApiError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Manejador global de excepciones.
 * Toda respuesta de error retorna {@link ApiError} en JSON.
 * Nunca expone stack traces ni datos clínicos/personales al cliente.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ApiError> handleRecursoNoEncontrado(RecursoNoEncontradoException ex) {
        log.warn("Recurso no encontrado: codigo={}", ex.getCodigo());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(ex.getCodigo(), ex.getMessage(), null));
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResourceFound(org.springframework.web.servlet.resource.NoResourceFoundException ex) {
        log.warn("Ruta o recurso no encontrado: {}", ex.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiError.of("RECURSO_NO_ENCONTRADO", "El recurso solicitado no fue encontrado.", null));
    }

    @ExceptionHandler(DatosInvalidosException.class)
    public ResponseEntity<ApiError> handleDatosInvalidos(DatosInvalidosException ex) {
        log.warn("Datos invalidos: codigo={}", ex.getCodigo());
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of(ex.getCodigo(), ex.getMessage(), null));
    }

    @ExceptionHandler(AccesoNoAutorizadoException.class)
    public ResponseEntity<ApiError> handleAccesoNoAutorizado(AccesoNoAutorizadoException ex) {
        log.warn("Acceso no autorizado: codigo={}", ex.getCodigo());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiError.of(ex.getCodigo(), ex.getMessage(), null));
    }

    @ExceptionHandler(CitaNoDisponibleException.class)
    public ResponseEntity<ApiError> handleCitaNoDisponible(CitaNoDisponibleException ex) {
        log.warn("Cita no disponible: codigo={}", ex.getCodigo());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiError.of(ex.getCodigo(), ex.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidacion(MethodArgumentNotValidException ex) {
        String detalle = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("Validacion fallida: {}", detalle);
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiError.of("VALIDACION_FALLIDA", detalle, null));
    }

    /**
     * Captura cualquier excepción no manejada explícitamente.
     * Logea el error internamente pero nunca expone el mensaje al cliente.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleGenerico(Exception ex) {
        log.error("Error inesperado: tipo={}", ex.getClass().getSimpleName());
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiError.of("ERROR_INTERNO", "Ocurrio un error interno. Intente mas tarde.", null));
    }
}

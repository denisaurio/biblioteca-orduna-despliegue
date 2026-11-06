package com.biblioteca.web;

import com.biblioteca.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Convierte las excepciones en respuestas JSON (ErrorResponse) con el código HTTP correcto.
 *
 *   409 Conflict ......... RecursoDuplicadoException, DataIntegrityViolationException
 *   404 Not Found ........ RecursoNoEncontradoException, ruta inexistente
 *   403 Forbidden ........ UsuarioSancionadoException, AccesoDenegadoException, AccessDeniedException
 *   401 Unauthorized ..... CredencialesInvalidasException
 *   400 Bad Request ...... EjemplarNoDisponible, ReservaActivaDuplicada, TurnoPendiente,
 *                          OperacionInvalida, validaciones (@Valid), JSON mal formado,
 *                          parámetros con tipo incorrecto o faltantes
 *   500 .................. cualquier otro error inesperado (se registra en el log)
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    // ===================== Excepciones de negocio (servicios) =====================

    @ExceptionHandler(RecursoDuplicadoException.class)
    public ResponseEntity<ErrorResponse> conflicto(RecursoDuplicadoException ex, HttpServletRequest req) {
        return responder(HttpStatus.CONFLICT, ex.getMessage(), req);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(RecursoNoEncontradoException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, ex.getMessage(), req);
    }

    @ExceptionHandler({UsuarioSancionadoException.class, AccesoDenegadoException.class})
    public ResponseEntity<ErrorResponse> prohibido(NegocioException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, ex.getMessage(), req);
    }

    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credenciales(CredencialesInvalidasException ex, HttpServletRequest req) {
        return responder(HttpStatus.UNAUTHORIZED, ex.getMessage(), req);
    }

    @ExceptionHandler({
            EjemplarNoDisponibleException.class,
            ReservaActivaDuplicadaException.class,
            TurnoPendienteException.class,
            OperacionInvalidaException.class
    })
    public ResponseEntity<ErrorResponse> reglaDeNegocio(NegocioException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, ex.getMessage(), req);
    }

    // ===================== Validación y formato de la petición =====================

    /** @Valid falló en un @RequestBody: devuelve campo -> mensaje. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex, HttpServletRequest req) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errores.putIfAbsent(e.getField(), e.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errores.putIfAbsent(e.getObjectName(), e.getDefaultMessage()));
        return ResponseEntity.badRequest()
                .body(ErrorResponse.deValidacion("Hay datos inválidos en la petición", req.getRequestURI(), errores));
    }

    /** Validaciones en @RequestParam / @PathVariable. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> validacionParametros(ConstraintViolationException ex, HttpServletRequest req) {
        Map<String, String> errores = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> {
            String ruta = v.getPropertyPath().toString();
            errores.putIfAbsent(ruta.substring(ruta.lastIndexOf('.') + 1), v.getMessage());
        });
        return ResponseEntity.badRequest()
                .body(ErrorResponse.deValidacion("Hay parámetros inválidos", req.getRequestURI(), errores));
    }

    /** JSON mal escrito, fecha con formato incorrecto o valor de enum inexistente (ej. metodo_pago). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> jsonInvalido(HttpMessageNotReadableException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "El cuerpo de la petición no es válido. Revise el formato del JSON, las fechas (yyyy-MM-dd) "
                        + "y los valores permitidos", req);
    }

    /** Ej: /api/libros/abc cuando se espera un número, o ?desde=01-10-2026. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> tipoIncorrecto(MethodArgumentTypeMismatchException ex, HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST,
                "El parámetro '" + ex.getName() + "' tiene un valor inválido: " + ex.getValue(), req);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ErrorResponse> parametroFaltante(MissingServletRequestParameterException ex,
                                                           HttpServletRequest req) {
        return responder(HttpStatus.BAD_REQUEST, "Falta el parámetro '" + ex.getParameterName() + "'", req);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoPermitido(HttpRequestMethodNotSupportedException ex,
                                                           HttpServletRequest req) {
        return responder(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " no permitido en esta ruta", req);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> rutaInexistente(NoResourceFoundException ex, HttpServletRequest req) {
        return responder(HttpStatus.NOT_FOUND, "La ruta no existe", req);
    }

    // ===================== Base de datos / seguridad / otros =====================

    /** Respaldo: un UNIQUE de la BD que se escapó de las validaciones del servicio. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> integridad(DataIntegrityViolationException ex, HttpServletRequest req) {
        log.warn("Violación de integridad en {}: {}", req.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return responder(HttpStatus.CONFLICT, "El registro entra en conflicto con datos existentes", req);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccessDeniedException ex, HttpServletRequest req) {
        return responder(HttpStatus.FORBIDDEN, "Su rol no tiene permiso para esta operación", req);
    }

    /**
     * Fallo del almacenamiento externo (Cloudflare R2) o de la descarga de una portada:
     * no es una regla incumplida, es una caida. Se devuelve el motivo (los mensajes de
     * AlmacenamientoException estan escritos para el usuario y no llevan secretos), asi
     * el bibliotecario sabe si fue R2, la red o el archivo.
     */
    @ExceptionHandler(AlmacenamientoException.class)
    public ResponseEntity<ErrorResponse> almacenamiento(AlmacenamientoException ex, HttpServletRequest req) {
        log.error("Error de almacenamiento en {}: {}", req.getRequestURI(), ex.getMessage());
        String mensaje = ex.getMessage() == null || ex.getMessage().isBlank()
                ? "No se pudo guardar el archivo en el almacenamiento. Intente nuevamente"
                : ex.getMessage();
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, mensaje, req);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> inesperado(Exception ex, HttpServletRequest req) {
        log.error("Error no controlado en {}", req.getRequestURI(), ex);
        return responder(HttpStatus.INTERNAL_SERVER_ERROR, "Ocurrió un error interno. Intente nuevamente", req);
    }

    private static ResponseEntity<ErrorResponse> responder(HttpStatus status, String mensaje, HttpServletRequest req) {
        return ResponseEntity.status(status).body(ErrorResponse.de(status, mensaje, req.getRequestURI()));
    }
}

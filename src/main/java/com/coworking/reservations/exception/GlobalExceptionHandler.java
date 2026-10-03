package com.coworking.reservations.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String TYPE_BASE = "https://coworking.local/problems/";
    private static final String PG_EXCLUSION_VIOLATION = "23P01";
    private static final String PG_UNIQUE_VIOLATION = "23505";

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<Object> handleBusiness(BusinessException ex, WebRequest request) {
        log.warn("{}: {}", ex.getCode(), ex.getMessage());
        return build(ex, ex.getStatus(), ex.getCode(), ex.getMessage(), null, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<Object> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
            String path = v.getPropertyPath().toString();
            errors.add(new ApiFieldError(path.substring(path.lastIndexOf('.') + 1), v.getMessage()));
        }
        return build(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay datos invalidos en la peticion", errors, request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Object> handleBadCredentials(BadCredentialsException ex, WebRequest request) {
        return build(ex, HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Correo o contraseña incorrectos", null, request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, WebRequest request) {
        return build(ex, HttpStatus.FORBIDDEN, "ACCESS_DENIED", "No tienes permisos para realizar esta accion", null, request);
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, WebRequest request) {
        log.warn("conflicto de version: {}", ex.getMessage());
        return build(ex, HttpStatus.CONFLICT, "CONCURRENT_UPDATE",
                "El recurso fue modificado por otra operacion, vuelve a intentarlo", null, request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, WebRequest request) {
        String sqlState = ex.getMostSpecificCause() instanceof SQLException sql ? sql.getSQLState() : null;
        if (PG_EXCLUSION_VIOLATION.equals(sqlState)) {
            return handleBusiness(new OverlappingReservationException(ex), request);
        }
        if (PG_UNIQUE_VIOLATION.equals(sqlState)) {
            log.warn("violacion de unicidad: {}", ex.getMostSpecificCause().getMessage());
            return build(ex, HttpStatus.CONFLICT, "DUPLICATE_RESOURCE", "Ya existe un registro con esos datos", null, request);
        }
        return handleUnexpected(ex, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Object> handleUnexpected(Exception ex, WebRequest request) {
        log.error("error no controlado", ex);
        return build(ex, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Ocurrió un error inesperado", null, request);
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers, HttpStatusCode status,
                                                                  WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(e -> errors.add(new ApiFieldError(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors()
                .forEach(e -> errors.add(new ApiFieldError(e.getObjectName(), e.getDefaultMessage())));
        return build(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay datos invalidos en la peticion", errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                           HttpHeaders headers, HttpStatusCode status,
                                                                           WebRequest request) {
        List<ApiFieldError> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            String name = result.getMethodParameter().getParameterName();
            result.getResolvableErrors()
                    .forEach(e -> errors.add(new ApiFieldError(name, e.getDefaultMessage())));
        });
        return build(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Hay datos inválidos en la peticion", errors, request);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        // los errores que arma spring solos traen el body nulo hasta aqui, por eso se completa despues
        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            if (problem.getInstance() == null && request instanceof ServletWebRequest servletRequest) {
                problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
            }
            if (problem.getProperties() == null || !problem.getProperties().containsKey("timestamp")) {
                problem.setProperty("timestamp", OffsetDateTime.now(ZoneOffset.UTC));
            }
            if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
                problem.setProperty("code", HttpStatus.valueOf(statusCode.value()).name());
            }
        }
        return response;
    }

    private ResponseEntity<Object> build(Exception ex, HttpStatus status, String code, String detail,
                                         List<ApiFieldError> errors, WebRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create(TYPE_BASE + code.toLowerCase().replace('_', '-')));
        problem.setProperty("code", code);
        if (errors != null) {
            problem.setProperty("errors", errors);
        }
        return handleExceptionInternal(ex, problem, new HttpHeaders(), status, request);
    }
}

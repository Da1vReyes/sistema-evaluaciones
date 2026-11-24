package com.universidad.tests.controller;

import com.universidad.tests.application.usecase.RegistroCuentaException;
import com.universidad.tests.dto.response.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Conserva el formato de error aprobado para errores de entrada HTTP de acceso. */
@RestControllerAdvice(assignableTypes = {SesionAuthController.class, RegistroAuthController.class})
public class ManejadorEntradaAuth extends ResponseEntityExceptionHandler {

    @ExceptionHandler({IllegalStateException.class, DataAccessException.class})
    public ResponseEntity<ErrorResponse> interno(Exception ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(
                        new ErrorResponse(
                                Instant.now(),
                                500,
                                "ERROR_INTERNO",
                                "No se pudo completar la solicitud",
                                request.getRequestURI()));
    }

    @ExceptionHandler(RegistroCuentaException.class)
    public ResponseEntity<ErrorResponse> registro(
            RegistroCuentaException ex, HttpServletRequest request) {
        HttpStatus status =
                switch (ex.getCodigo()) {
                    case CUENTA_EXISTENTE -> HttpStatus.CONFLICT;
                    case ROL_NO_AUTORIZADO -> HttpStatus.FORBIDDEN;
                    case SOLICITUD_INVALIDA -> HttpStatus.BAD_REQUEST;
                };
        return ResponseEntity.status(status)
                .body(
                        new ErrorResponse(
                                Instant.now(),
                                status.value(),
                                ex.getCodigo().name(),
                                ex.getMessage(),
                                request.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        String ruta = ((ServletWebRequest) request).getRequest().getRequestURI();
        return ResponseEntity.status(status)
                .headers(headers)
                .body(
                        new ErrorResponse(
                                Instant.now(),
                                status.value(),
                                "SOLICITUD_INVALIDA",
                                "La solicitud no es valida",
                                ruta));
    }
}

package com.universidad.tests.exception.gen;

import com.universidad.tests.controller.gen.EvaluacionController;
import com.universidad.tests.domain.exception.gen.ReglaEvaluacionException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice(assignableTypes = EvaluacionController.class)
@Profile("gen02-draft")
public class EvaluacionErrorHandler {
    private final Clock clock;

    public EvaluacionErrorHandler(Clock clock) {
        this.clock = clock;
    }

    public record ErrorApi(
            Instant timestamp,
            int status,
            String codigo,
            String mensaje,
            String ruta,
            Map<String, Object> detalles) {}

    @ExceptionHandler(ReglaEvaluacionException.class)
    ResponseEntity<ErrorApi> regla(ReglaEvaluacionException e, HttpServletRequest request) {
        return error(
                e.status(),
                e.codigo(),
                e.getMessage(),
                request,
                e.evaluacionId() == null ? Map.of() : Map.of("evaluacionId", e.evaluacionId()));
    }

    @ExceptionHandler({
        MethodArgumentNotValidException.class,
        HttpMessageNotReadableException.class,
        ConstraintViolationException.class,
        HandlerMethodValidationException.class
    })
    ResponseEntity<ErrorApi> invalida(Exception e, HttpServletRequest request) {
        return error(
                400,
                "SOLICITUD_INVALIDA",
                "Verifique los campos y tipos de la solicitud.",
                request,
                Map.of());
    }

    private ResponseEntity<ErrorApi> error(
            int status,
            String codigo,
            String mensaje,
            HttpServletRequest request,
            Map<String, Object> detalles) {
        return ResponseEntity.status(status)
                .body(
                        new ErrorApi(
                                clock.instant(),
                                status,
                                codigo,
                                mensaje,
                                request.getRequestURI(),
                                detalles));
    }
}

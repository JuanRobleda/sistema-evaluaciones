package com.universidad.tests.controller.gen;

import com.universidad.tests.application.usecase.gen.EvaluacionService;
import com.universidad.tests.dto.gen.EvaluacionResponse;
import com.universidad.tests.dto.gen.IniciarEvaluacionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.security.Principal;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@Profile("gen02-draft")
@RequestMapping("/api/v1/evaluaciones")
public class EvaluacionController {
    private final EvaluacionService service;

    public EvaluacionController(EvaluacionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<EvaluacionResponse> crear(
            @Valid @RequestBody IniciarEvaluacionRequest body, Principal principal) {
        var response = service.crear(body, principal == null ? null : principal.getName());
        return ResponseEntity.created(URI.create("/api/v1/evaluaciones/" + response.id()))
                .body(response);
    }

    @GetMapping("/{id}")
    public EvaluacionResponse recuperar(@PathVariable @Positive long id, Principal principal) {
        return service.recuperar(id, principal == null ? null : principal.getName());
    }
}

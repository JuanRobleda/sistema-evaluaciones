package com.universidad.tests.application.usecase.gen;

import com.universidad.tests.domain.exception.gen.ReglaEvaluacionException;
import com.universidad.tests.domain.model.gen.CrearEvaluacion;
import com.universidad.tests.domain.model.gen.Evaluacion;
import com.universidad.tests.domain.repository.gen.Evaluaciones;
import com.universidad.tests.dto.gen.EvaluacionResponse;
import com.universidad.tests.dto.gen.IniciarEvaluacionRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("gen02-draft")
public class EvaluacionService {
    private final Evaluaciones repository;
    private final Clock clock;

    public EvaluacionService(Evaluaciones repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Transactional
    public EvaluacionResponse crear(IniciarEvaluacionRequest request, String actor) {
        autorizar(actor, request.sujetoId(), request.periodoId(), "ASIGNAR_APLICACION");
        if (!"INDIVIDUAL".equals(request.modalidad())) {
            throw new ReglaEvaluacionException(
                    422,
                    "MODALIDAD_NO_SOPORTADA",
                    "La coordinación colectiva requiere la resolución de D02.");
        }
        if (request.subpruebas() != null
                && (request.subpruebas().isEmpty()
                        || new HashSet<>(request.subpruebas()).size()
                                != request.subpruebas().size())) {
            throw new ReglaEvaluacionException(
                    400, "SOLICITUD_INVALIDA", "Selección vacía o duplicada.");
        }
        Instant ahora = clock.instant();
        Evaluacion evaluacion =
                repository.crear(
                        new CrearEvaluacion(
                                request.testId(),
                                request.versionFormularioId(),
                                request.sujetoId(),
                                request.periodoId(),
                                request.modalidad(),
                                request.subpruebas(),
                                request.fluidez() == null
                                        ? null
                                        : request.fluidez().letraInicial()),
                        actor,
                        ahora);
        repository.auditar(evaluacion.id(), actor, "CREACION", ahora);
        return respuesta(evaluacion, ahora);
    }

    @Transactional
    public EvaluacionResponse recuperar(long id, String actor) {
        if (actor == null || actor.isBlank()) {
            throw new ReglaEvaluacionException(
                    401, "NO_AUTENTICADO", "Se requiere una sesión válida.");
        }
        Evaluacion evaluacion =
                repository
                        .buscar(id)
                        .orElseThrow(
                                () ->
                                        new ReglaEvaluacionException(
                                                404,
                                                "EVALUACION_NO_ENCONTRADA",
                                                "Evaluación inexistente."));
        autorizar(actor, evaluacion.sujetoId(), evaluacion.periodoId(), "EVALUACION_LEER");
        Instant ahora = clock.instant();
        repository.reconciliarVencimientos(id, ahora);
        evaluacion = repository.buscar(id).orElseThrow();
        repository.auditar(id, actor, "CONSULTA_RECUPERACION", ahora);
        return respuesta(evaluacion, ahora);
    }

    private void autorizar(String actor, long sujeto, long periodo, String permiso) {
        if (actor == null || actor.isBlank()) {
            throw new ReglaEvaluacionException(
                    401, "NO_AUTENTICADO", "Se requiere una sesión válida.");
        }
        if (!repository.tieneAlcance(actor, sujeto, periodo, permiso)) {
            throw new ReglaEvaluacionException(
                    403, "SIN_PERMISO", "Operación fuera del alcance autorizado.");
        }
    }

    private EvaluacionResponse respuesta(Evaluacion e, Instant ahora) {
        var activa = e.subpruebas().stream().filter(s -> "ACTIVA".equals(s.estado())).findFirst();
        EvaluacionResponse.Activa detalle =
                activa.map(
                                s -> {
                                    Duration restante = Duration.between(ahora, s.finLimite());
                                    long segundos =
                                            restante.isNegative()
                                                    ? 0
                                                    : restante.getSeconds()
                                                            + (restante.getNano() > 0 ? 1 : 0);
                                    return new EvaluacionResponse.Activa(
                                            s.codigo(),
                                            s.configuracionSubpruebaId(),
                                            s.inicio(),
                                            s.finLimite(),
                                            segundos);
                                })
                        .orElse(null);
        // No se concede una señal de inicio: E5 deberá comprobar preparación y ventanas.
        var siguiente =
                activa.isPresent()
                        ? null
                        : e.subpruebas().stream()
                                .filter(s -> "PENDIENTE".equals(s.estado()))
                                .findFirst()
                                .map(
                                        s ->
                                                new EvaluacionResponse.Configuracion(
                                                        s.configuracionSubpruebaId(),
                                                        s.codigo(),
                                                        s.orden()))
                                .orElse(null);
        return new EvaluacionResponse(
                e.id(),
                e.testId(),
                e.versionFormularioId(),
                e.sujetoId(),
                e.periodoId(),
                e.modalidad(),
                e.alcance(),
                e.estado(),
                e.fechaInicio(),
                e.fechaFin(),
                detalle,
                siguiente,
                e.subpruebas().stream()
                        .map(
                                s ->
                                        new EvaluacionResponse.Subprueba(
                                                s.codigo(),
                                                s.configuracionSubpruebaId(),
                                                s.orden(),
                                                s.estado()))
                        .toList());
    }
}

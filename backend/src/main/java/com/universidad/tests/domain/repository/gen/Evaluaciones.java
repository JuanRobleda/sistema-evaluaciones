package com.universidad.tests.domain.repository.gen;

import com.universidad.tests.domain.model.gen.CrearEvaluacion;
import com.universidad.tests.domain.model.gen.Evaluacion;
import java.time.Instant;
import java.util.Optional;

public interface Evaluaciones {
    Evaluacion crear(CrearEvaluacion solicitud, String actor, Instant ahora);

    Optional<Evaluacion> buscar(long id);

    void reconciliarVencimientos(long id, Instant ahora);

    void cerrarVencidas(Instant ahora);

    boolean tieneAlcance(String actor, long sujetoId, long periodoId, String permiso);

    void auditar(long id, String actor, String accion, Instant ahora);
}

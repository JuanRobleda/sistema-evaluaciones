package com.universidad.tests.domain.model.gen;

import java.time.Instant;
import java.util.List;

public record Evaluacion(
        long id,
        long testId,
        long versionFormularioId,
        long sujetoId,
        long periodoId,
        String modalidad,
        String alcance,
        String estado,
        Instant fechaInicio,
        Instant fechaFin,
        List<Ejecucion> subpruebas) {
    public record Ejecucion(
            long configuracionSubpruebaId,
            String codigo,
            int orden,
            int duracionSegundos,
            String estado,
            Instant inicio,
            Instant finLimite) {}
}

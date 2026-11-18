package com.universidad.tests.dto.gen;

import java.time.Instant;
import java.util.List;

public record EvaluacionResponse(
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
        Activa subpruebaActiva,
        Configuracion siguienteConfiguracion,
        List<Subprueba> subpruebas) {
    public record Activa(
            String codigo,
            long configuracionSubpruebaId,
            Instant inicio,
            Instant finLimite,
            long segundosRestantes) {}

    public record Configuracion(long configuracionSubpruebaId, String codigo, int orden) {}

    public record Subprueba(
            String codigo, long configuracionSubpruebaId, int orden, String estado) {}
}

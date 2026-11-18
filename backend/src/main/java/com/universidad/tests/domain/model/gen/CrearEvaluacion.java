package com.universidad.tests.domain.model.gen;

import java.util.List;

public record CrearEvaluacion(
        Long testId,
        long versionFormularioId,
        long sujetoId,
        long periodoId,
        String modalidad,
        List<String> subpruebas,
        String letraInicial) {}

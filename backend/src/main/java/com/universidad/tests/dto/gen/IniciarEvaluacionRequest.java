package com.universidad.tests.dto.gen;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record IniciarEvaluacionRequest(
        @Positive Long testId,
        @NotNull @Positive Long versionFormularioId,
        @NotNull @Positive Long sujetoId,
        @NotNull @Positive Long periodoId,
        @NotBlank @Pattern(regexp = "INDIVIDUAL|COLECTIVA") String modalidad,
        @Size(min = 1) List<@NotBlank String> subpruebas,
        @Valid Fluidez fluidez) {
    public record Fluidez(@NotBlank @Pattern(regexp = "[A-ZÑ]") String letraInicial) {}
}

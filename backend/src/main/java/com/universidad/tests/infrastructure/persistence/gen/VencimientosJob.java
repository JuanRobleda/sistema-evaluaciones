package com.universidad.tests.infrastructure.persistence.gen;

import com.universidad.tests.domain.repository.gen.Evaluaciones;
import java.time.Clock;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("gen02-draft")
public class VencimientosJob {
    private final Evaluaciones repository;
    private final Clock clock;

    public VencimientosJob(Evaluaciones repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${gen02.vencimientos-ms:5000}")
    @Transactional
    public void cerrar() {
        repository.cerrarVencidas(clock.instant());
    }
}

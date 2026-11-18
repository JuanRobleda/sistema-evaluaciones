package com.universidad.tests.application.usecase.gen;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.universidad.tests.domain.exception.gen.ReglaEvaluacionException;
import com.universidad.tests.domain.model.gen.CrearEvaluacion;
import com.universidad.tests.domain.model.gen.Evaluacion;
import com.universidad.tests.domain.repository.gen.Evaluaciones;
import com.universidad.tests.dto.gen.IniciarEvaluacionRequest;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class EvaluacionServiceTest {
    private final Instant ahora = Instant.parse("2026-11-04T12:02:00Z");
    private Evaluaciones repository;
    private EvaluacionService service;

    @BeforeEach
    void setup() {
        repository = mock(Evaluaciones.class);
        service = new EvaluacionService(repository, Clock.fixed(ahora, ZoneOffset.UTC));
    }

    private Evaluacion evaluacion(String estado, Instant inicio, Instant fin) {
        return new Evaluacion(
                15,
                1,
                10,
                25,
                3,
                "INDIVIDUAL",
                "COMPLETA",
                "EN_CURSO",
                ahora,
                null,
                List.of(new Evaluacion.Ejecucion(101, "VOC1", 1, 300, estado, inicio, fin)));
    }

    private void permitir() {
        when(repository.tieneAlcance(eq("ana"), eq(25L), eq(3L), anyString())).thenReturn(true);
    }

    @Test
    void recuperacionConservaVencimientoYDescuentaDesconexion() {
        permitir();
        var e = evaluacion("ACTIVA", ahora.minusSeconds(120), ahora.plusSeconds(180));
        when(repository.buscar(15)).thenReturn(Optional.of(e));
        var response = service.recuperar(15, "ana");
        assertThat(response.subpruebaActiva().segundosRestantes()).isEqualTo(180);
        assertThat(response.subpruebaActiva().finLimite()).isEqualTo(ahora.plusSeconds(180));
        assertThat(response.siguienteConfiguracion()).isNull();
        verify(repository).reconciliarVencimientos(15, ahora);
        verify(repository, never()).crear(any(), anyString(), any());
    }

    @Test
    void recuperacionRedondeaFraccionSinDarSegundosNegativos() {
        permitir();
        when(repository.buscar(15))
                .thenReturn(
                        Optional.of(
                                evaluacion("ACTIVA", ahora.minusSeconds(299), ahora.plusNanos(1))));
        assertThat(service.recuperar(15, "ana").subpruebaActiva().segundosRestantes()).isEqualTo(1);
    }

    @Test
    void recuperacionDevuelveEstadoPosteriorAlCierre() {
        permitir();
        when(repository.buscar(15))
                .thenReturn(
                        Optional.of(evaluacion("ACTIVA", ahora.minusSeconds(300), ahora)),
                        Optional.of(evaluacion("CERRADA", ahora.minusSeconds(300), ahora)));
        var response = service.recuperar(15, "ana");
        assertThat(response.subpruebaActiva()).isNull();
        assertThat(response.subpruebas().get(0).estado()).isEqualTo("CERRADA");
    }

    @Test
    void consultaAnonimaNoBuscaRecursos() {
        assertThatThrownBy(() -> service.recuperar(15, null))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class, e -> assertThat(e.status()).isEqualTo(401));
        verifyNoInteractions(repository);
    }

    @Test
    void consultaAjenaNoCierraNiAuditaComoRecuperacion() {
        when(repository.buscar(15)).thenReturn(Optional.of(evaluacion("PENDIENTE", null, null)));
        assertThatThrownBy(() -> service.recuperar(15, "intruso"))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class, e -> assertThat(e.status()).isEqualTo(403));
        verify(repository, never()).reconciliarVencimientos(anyLong(), any());
    }

    @Test
    void inexistenteDevuelve404() {
        when(repository.buscar(15)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.recuperar(15, "ana"))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class, e -> assertThat(e.status()).isEqualTo(404));
    }

    @Test
    void crearNoIniciaRelojYRespetaOmitirSeleccion() {
        permitir();
        when(repository.crear(any(), eq("ana"), eq(ahora)))
                .thenReturn(evaluacion("PENDIENTE", null, null));
        var response =
                service.crear(
                        new IniciarEvaluacionRequest(null, 10L, 25L, 3L, "INDIVIDUAL", null, null),
                        "ana");
        assertThat(response.subpruebaActiva()).isNull();
        assertThat(response.siguienteConfiguracion().codigo()).isEqualTo("VOC1");
        var captor = ArgumentCaptor.forClass(CrearEvaluacion.class);
        verify(repository).crear(captor.capture(), eq("ana"), eq(ahora));
        assertThat(captor.getValue().subpruebas()).isNull();
        verify(repository).auditar(15, "ana", "CREACION", ahora);
    }

    @Test
    void duplicadosSeRechazanAntesDePersistir() {
        permitir();
        assertThatThrownBy(
                        () ->
                                service.crear(
                                        new IniciarEvaluacionRequest(
                                                1L,
                                                10L,
                                                25L,
                                                3L,
                                                "INDIVIDUAL",
                                                List.of("VOC1", "VOC1"),
                                                null),
                                        "ana"))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class,
                        e -> assertThat(e.codigo()).isEqualTo("SOLICITUD_INVALIDA"));
        verify(repository, never()).crear(any(), any(), any());
    }

    @Test
    void colectivaNoSeHabilitaSilenciosamente() {
        permitir();
        assertThatThrownBy(
                        () ->
                                service.crear(
                                        new IniciarEvaluacionRequest(
                                                1L, 10L, 25L, 3L, "COLECTIVA", null, null),
                                        "ana"))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class,
                        e -> assertThat(e.codigo()).isEqualTo("MODALIDAD_NO_SOPORTADA"));
        verify(repository, never()).crear(any(), any(), any());
    }

    @Test
    void asignacionSinPermisoNoPersiste() {
        assertThatThrownBy(
                        () ->
                                service.crear(
                                        new IniciarEvaluacionRequest(
                                                1L, 10L, 25L, 3L, "INDIVIDUAL", null, null),
                                        "intruso"))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class, e -> assertThat(e.status()).isEqualTo(403));
        verify(repository, never()).crear(any(), any(), any());
    }
}

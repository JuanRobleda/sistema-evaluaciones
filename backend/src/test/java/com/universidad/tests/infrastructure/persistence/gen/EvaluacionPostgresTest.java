package com.universidad.tests.infrastructure.persistence.gen;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.universidad.tests.application.usecase.gen.EvaluacionService;
import com.universidad.tests.domain.exception.gen.ReglaEvaluacionException;
import com.universidad.tests.dto.gen.IniciarEvaluacionRequest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "GEN02_LOCAL_USER=tester",
            "GEN02_LOCAL_PASSWORD=only-in-tests",
            "gen02.vencimientos-ms=3600000"
        })
@ActiveProfiles("gen02-draft")
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "GEN02_RUN_DB_TESTS", matches = "true")
class EvaluacionPostgresTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired EvaluacionService service;
    @Autowired JdbcEvaluaciones repository;
    @Autowired MockMvc mvc;

    @BeforeEach
    void fixtures() {
        // Ejecutar exclusivamente contra una base de pruebas dedicada.
        jdbc.execute(
                "TRUNCATE gen02_cierre_pendiente, gen02_auditoria, gen02_permiso, gen02_ejecucion, gen02_evaluacion, gen02_configuracion, gen02_version, gen02_test, gen02_periodo, gen02_sujeto RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO gen02_sujeto(id,referencia) VALUES (25,'FICTICIO-25')");
        jdbc.update(
                "INSERT INTO gen02_periodo(id,nombre,activo) VALUES (3,'PERIODO FICTICIO',true)");
        jdbc.update("INSERT INTO gen02_test(id,codigo) VALUES (1,'BFA')");
        jdbc.update("INSERT INTO gen02_version(id,test_id,estado) VALUES (10,1,'PUBLICADA')");
        jdbc.update(
                "INSERT INTO gen02_configuracion(id,version_id,codigo,orden,duracion_segundos) VALUES (101,10,'VOC1',1,300),(102,10,'VOC2',2,300),(103,10,'FV',3,300)");
        for (String permiso : List.of("ASIGNAR_APLICACION", "EVALUACION_LEER"))
            jdbc.update(
                    "INSERT INTO gen02_permiso(usuario,rol,sujeto_id,periodo_id,permiso) VALUES ('tester','ACADEMICO',25,3,?)",
                    permiso);
    }

    private IniciarEvaluacionRequest request(List<String> seleccion) {
        return new IniciarEvaluacionRequest(1L, 10L, 25L, 3L, "INDIVIDUAL", seleccion, null);
    }

    private long crear() {
        return service.crear(request(List.of("VOC1", "VOC2")), "tester").id();
    }

    private long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Test
    void creacionCompletaFvYLecturaDesdeOtraInstancia() {
        var r =
                service.crear(
                        new IniciarEvaluacionRequest(
                                1L,
                                10L,
                                25L,
                                3L,
                                "INDIVIDUAL",
                                null,
                                new IniciarEvaluacionRequest.Fluidez("M")),
                        "tester");
        assertThat(r.alcance()).isEqualTo("COMPLETA");
        assertThat(r.subpruebas()).hasSize(3);
        assertThat(r.subpruebaActiva()).isNull();
        var guardada = new JdbcEvaluaciones(jdbc).buscar(r.id()).orElseThrow();
        assertThat(guardada.subpruebas())
                .allMatch(e -> e.inicio() == null && e.finLimite() == null);
        assertThat(guardada.versionFormularioId()).isEqualTo(10);
    }

    @Test
    void parcialOrdenaYDuplicadoDevuelveIdentidadOriginal() {
        var r = service.crear(request(List.of("VOC2", "VOC1")), "tester");
        assertThat(r.alcance()).isEqualTo("PARCIAL");
        assertThat(r.subpruebas()).extracting(s -> s.codigo()).containsExactly("VOC1", "VOC2");
        assertThatThrownBy(this::crear)
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class,
                        e -> {
                            assertThat(e.codigo()).isEqualTo("EVALUACION_EN_CURSO");
                            assertThat(e.evaluacionId()).isEqualTo(r.id());
                        });
        assertThat(count("gen02_evaluacion")).isEqualTo(1);
    }

    @Test
    void concurrenciaNoDuplicaEvaluaciones() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch inicio = new CountDownLatch(1);
        Callable<String> task =
                () -> {
                    inicio.await();
                    try {
                        crear();
                        return "OK";
                    } catch (ReglaEvaluacionException e) {
                        return e.codigo();
                    }
                };
        try {
            Future<String> a = pool.submit(task);
            Future<String> b = pool.submit(task);
            inicio.countDown();
            assertThat(List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("OK", "EVALUACION_EN_CURSO");
            assertThat(count("gen02_evaluacion")).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void validacionesNoDejanFilasParciales() {
        assertThatThrownBy(() -> service.crear(request(List.of("AJENA")), "tester"))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class,
                        e -> assertThat(e.codigo()).isEqualTo("SUBPRUEBA_NO_INCLUIDA"));
        assertThat(count("gen02_evaluacion")).isZero();
        assertThatThrownBy(() -> service.crear(request(null), "tester"))
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class,
                        e -> assertThat(e.codigo()).isEqualTo("LETRA_NO_ASIGNADA"));
        assertThat(count("gen02_ejecucion")).isZero();
    }

    @Test
    void periodoInactivoYVersionNoPublicada() {
        jdbc.update("UPDATE gen02_periodo SET activo=false");
        assertThatThrownBy(this::crear)
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class,
                        e -> assertThat(e.codigo()).isEqualTo("PERIODO_INACTIVO"));
        jdbc.update("UPDATE gen02_periodo SET activo=true");
        jdbc.update("UPDATE gen02_version SET estado='BORRADOR'");
        assertThatThrownBy(this::crear)
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class,
                        e -> assertThat(e.codigo()).isEqualTo("VERSION_NO_PUBLICADA"));
        assertThat(count("gen02_evaluacion")).isZero();
    }

    @Test
    void falloAlGuardarEjecucionRevierteTodaLaTransaccion() {
        // Inyectar un fallo real después del INSERT de evaluación.
        jdbc.execute(
                "ALTER TABLE gen02_ejecucion ADD CONSTRAINT gen02_test_fallo CHECK(codigo <> 'VOC2')");
        try {
            assertThatThrownBy(this::crear)
                    .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
            assertThat(count("gen02_evaluacion")).isZero();
            assertThat(count("gen02_ejecucion")).isZero();
            assertThat(count("gen02_auditoria")).isZero();
        } finally {
            jdbc.execute("ALTER TABLE gen02_ejecucion DROP CONSTRAINT gen02_test_fallo");
        }
    }

    @Test
    void vencimientoCierraUnaVezSinCambiarReloj() {
        long id = crear();
        Instant fin =
                Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update(
                "UPDATE gen02_ejecucion SET estado='ACTIVA',inicio=?,fin_limite=? WHERE evaluacion_id=? AND codigo='VOC1'",
                Timestamp.from(fin.minusSeconds(300)),
                Timestamp.from(fin),
                id);
        service.recuperar(id, "tester");
        service.recuperar(id, "tester");
        assertThat(count("gen02_cierre_pendiente")).isEqualTo(1);
        assertThat(repository.buscar(id).orElseThrow().subpruebas().get(0).finLimite())
                .isEqualTo(fin.truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThat(repository.buscar(id).orElseThrow().subpruebas().get(0).estado())
                .isEqualTo("CERRADA");
    }

    @Test
    void jobCierraSinConsultaDelFrontend() {
        long id = crear();
        Instant fin =
                Instant.now().minusSeconds(1).truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        jdbc.update(
                "UPDATE gen02_ejecucion SET estado='ACTIVA',inicio=?,fin_limite=? WHERE evaluacion_id=? AND codigo='VOC1'",
                Timestamp.from(fin.minusSeconds(300)),
                Timestamp.from(fin),
                id);
        repository.cerrarVencidas(Instant.now());
        repository.cerrarVencidas(Instant.now());
        assertThat(count("gen02_cierre_pendiente")).isEqualTo(1);
        assertThat(repository.buscar(id).orElseThrow().subpruebas().get(0).estado())
                .isEqualTo("CERRADA");
    }

    @Test
    void sujetoNoPuedeAsignarAunqueSeLeConcedaPermisoErroneo() {
        jdbc.update("UPDATE gen02_permiso SET rol='SUJETO'");
        assertThatThrownBy(this::crear)
                .isInstanceOfSatisfying(
                        ReglaEvaluacionException.class, e -> assertThat(e.status()).isEqualTo(403));
    }

    @Test
    void httpBasicLocalCreacionYLocation() throws Exception {
        String auth =
                "Basic "
                        + Base64.getEncoder()
                                .encodeToString(
                                        "tester:only-in-tests"
                                                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
        mvc.perform(
                        post("/api/v1/evaluaciones")
                                .header("Authorization", auth)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"versionFormularioId\":10,\"sujetoId\":25,\"periodoId\":3,\"modalidad\":\"INDIVIDUAL\",\"subpruebas\":[\"VOC1\"]}"))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.versionFormularioId").value(10))
                .andExpect(jsonPath("$.subpruebaActiva").isEmpty());
    }

    @Test
    void httpSinIdentidadEIdentidadFalsificadaSon401() throws Exception {
        mvc.perform(get("/api/v1/evaluaciones/1").header("X-User", "tester"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NO_AUTENTICADO"));
        mvc.perform(get("/api/v1/evaluaciones/1").header("Authorization", "Basic !!!"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void httpRechazaJsonInvalido() throws Exception {
        mvc.perform(
                        post("/api/v1/evaluaciones")
                                .principal(() -> "tester")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("SOLICITUD_INVALIDA"));
    }
}

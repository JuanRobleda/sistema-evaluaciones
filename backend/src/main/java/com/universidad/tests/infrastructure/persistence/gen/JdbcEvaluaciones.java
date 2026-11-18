package com.universidad.tests.infrastructure.persistence.gen;

import com.universidad.tests.domain.exception.gen.ReglaEvaluacionException;
import com.universidad.tests.domain.model.gen.CrearEvaluacion;
import com.universidad.tests.domain.model.gen.Evaluacion;
import com.universidad.tests.domain.repository.gen.Evaluaciones;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Profile("gen02-draft")
public class JdbcEvaluaciones implements Evaluaciones {
    private final JdbcTemplate jdbc;

    public JdbcEvaluaciones(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private record Version(long testId, String estado) {}

    private record Config(long id, String codigo, int orden, int segundos) {}

    @Override
    public Evaluacion crear(CrearEvaluacion s, String actor, Instant ahora) {
        // Todas las creaciones del mismo contexto se serializan en PostgreSQL dentro de la
        // transacción.
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                rs -> {},
                s.sujetoId() + ":" + s.periodoId() + ":" + s.versionFormularioId());
        if (!existe("SELECT count(*) FROM gen02_sujeto WHERE id = ?", s.sujetoId()))
            fail(404, "SUJETO_NO_ENCONTRADO", "Sujeto inexistente.");
        var periodos =
                jdbc.query(
                        "SELECT activo FROM gen02_periodo WHERE id = ? FOR SHARE",
                        (rs, n) -> rs.getBoolean(1),
                        s.periodoId());
        if (periodos.isEmpty()) fail(404, "PERIODO_NO_ENCONTRADO", "Período inexistente.");
        if (!periodos.get(0))
            fail(409, "PERIODO_INACTIVO", "Período no disponible para asignaciones.");
        if (s.testId() != null
                && !existe("SELECT count(*) FROM gen02_test WHERE id = ?", s.testId()))
            fail(404, "TEST_NO_ENCONTRADO", "Test inexistente.");
        var versiones =
                jdbc.query(
                        "SELECT test_id, estado FROM gen02_version WHERE id = ? FOR SHARE",
                        (rs, n) -> new Version(rs.getLong(1), rs.getString(2)),
                        s.versionFormularioId());
        if (versiones.isEmpty()) fail(404, "VERSION_NO_ENCONTRADA", "Versión inexistente.");
        var version = versiones.get(0);
        if (s.testId() != null && s.testId() != version.testId())
            fail(422, "VERSION_INCOMPATIBLE", "La edición no corresponde al test.");
        if (!"PUBLICADA".equals(version.estado()))
            fail(409, "VERSION_NO_PUBLICADA", "La edición no está publicada.");
        // Segunda cerradura: versiones diferentes del mismo test comparten unicidad de contexto.
        jdbc.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 1))",
                rs -> {},
                s.sujetoId() + ":" + s.periodoId() + ":" + version.testId());
        var anteriores =
                jdbc.query(
                        "SELECT id, estado FROM gen02_evaluacion WHERE sujeto_id=? AND periodo_id=? AND test_id=? ORDER BY id DESC",
                        (rs, n) -> Map.entry(rs.getLong(1), rs.getString(2)),
                        s.sujetoId(),
                        s.periodoId(),
                        version.testId());
        var abierta = anteriores.stream().filter(e -> "EN_CURSO".equals(e.getValue())).findFirst();
        if (abierta.isPresent())
            throw new ReglaEvaluacionException(
                    409,
                    "EVALUACION_EN_CURSO",
                    "Ya existe una evaluación abierta.",
                    abierta.get().getKey());
        if (!anteriores.isEmpty())
            fail(
                    409,
                    "REASIGNACION_NO_AUTORIZADA",
                    "Un nuevo intento requiere el contrato de autorización y motivo aún pendiente.");
        var configuraciones =
                jdbc.query(
                        "SELECT id, codigo, orden, duracion_segundos FROM gen02_configuracion WHERE version_id=? ORDER BY orden FOR SHARE",
                        (rs, n) ->
                                new Config(
                                        rs.getLong(1), rs.getString(2), rs.getInt(3), rs.getInt(4)),
                        s.versionFormularioId());
        if (configuraciones.isEmpty())
            fail(422, "VERSION_INCOMPATIBLE", "Edición sin configuraciones.");
        var seleccion =
                s.subpruebas() == null
                        ? configuraciones
                        : configuraciones.stream()
                                .filter(c -> s.subpruebas().contains(c.codigo()))
                                .toList();
        if (s.subpruebas() != null && seleccion.size() != s.subpruebas().size())
            fail(422, "SUBPRUEBA_NO_INCLUIDA", "Subprueba ajena a la edición.");
        boolean fv = seleccion.stream().anyMatch(c -> "FV".equals(c.codigo()));
        if (fv && s.letraInicial() == null)
            fail(409, "LETRA_NO_ASIGNADA", "FV requiere letra inicial autorizada.");
        if (!fv && s.letraInicial() != null)
            fail(422, "VERSION_INCOMPATIBLE", "La selección no incluye FV.");
        String alcance = seleccion.size() == configuraciones.size() ? "COMPLETA" : "PARCIAL";
        Long id =
                jdbc.queryForObject(
                        "INSERT INTO gen02_evaluacion(test_id, version_id, sujeto_id, periodo_id, modalidad, alcance, estado, fecha_inicio, responsable, letra_inicial) VALUES (?,?,?,?,?,?,'EN_CURSO',?,?,?) RETURNING id",
                        Long.class,
                        version.testId(),
                        s.versionFormularioId(),
                        s.sujetoId(),
                        s.periodoId(),
                        s.modalidad(),
                        alcance,
                        Timestamp.from(ahora),
                        actor,
                        fv ? s.letraInicial() : null);
        for (Config c : seleccion)
            jdbc.update(
                    "INSERT INTO gen02_ejecucion(evaluacion_id, configuracion_id, codigo, orden, duracion_segundos, estado) VALUES (?,?,?,?,?,'PENDIENTE')",
                    id,
                    c.id(),
                    c.codigo(),
                    c.orden(),
                    c.segundos());
        return buscar(id).orElseThrow();
    }

    @Override
    public Optional<Evaluacion> buscar(long id) {
        var ejecuciones =
                jdbc.query(
                        "SELECT * FROM gen02_ejecucion WHERE evaluacion_id=? ORDER BY orden",
                        (rs, n) ->
                                new Evaluacion.Ejecucion(
                                        rs.getLong("configuracion_id"),
                                        rs.getString("codigo"),
                                        rs.getInt("orden"),
                                        rs.getInt("duracion_segundos"),
                                        rs.getString("estado"),
                                        instant(rs.getTimestamp("inicio")),
                                        instant(rs.getTimestamp("fin_limite"))),
                        id);
        return jdbc
                .query(
                        "SELECT * FROM gen02_evaluacion WHERE id=?",
                        (rs, n) ->
                                new Evaluacion(
                                        rs.getLong("id"),
                                        rs.getLong("test_id"),
                                        rs.getLong("version_id"),
                                        rs.getLong("sujeto_id"),
                                        rs.getLong("periodo_id"),
                                        rs.getString("modalidad"),
                                        rs.getString("alcance"),
                                        rs.getString("estado"),
                                        instant(rs.getTimestamp("fecha_inicio")),
                                        instant(rs.getTimestamp("fecha_fin")),
                                        ejecuciones),
                        id)
                .stream()
                .findFirst();
    }

    @Override
    public boolean tieneAlcance(String actor, long sujeto, long periodo, String permiso) {
        return existe(
                "SELECT count(*) FROM gen02_permiso WHERE usuario=? AND sujeto_id=? AND periodo_id=? AND permiso=? AND activo=true AND (permiso <> 'ASIGNAR_APLICACION' OR rol IN ('ACADEMICO','COORDINADOR'))",
                actor,
                sujeto,
                periodo,
                permiso);
    }

    @Override
    public void reconciliarVencimientos(long id, Instant ahora) {
        cerrar(ahora, id);
    }

    @Override
    public void cerrarVencidas(Instant ahora) {
        cerrar(ahora, null);
    }

    private void cerrar(Instant ahora, Long id) {
        // UPDATE es la transición atómica; el outbox se inserta solo para filas recién cerradas.
        String sql =
                "WITH cerradas AS (UPDATE gen02_ejecucion SET estado='CERRADA', motivo_cierre='TIEMPO_AGOTADO' WHERE estado='ACTIVA' AND fin_limite<=?"
                        + (id == null ? "" : " AND evaluacion_id=?")
                        + " RETURNING id, fin_limite) INSERT INTO gen02_cierre_pendiente(ejecucion_id, instante_oficial) SELECT id, fin_limite FROM cerradas ON CONFLICT (ejecucion_id) DO NOTHING";
        if (id == null) jdbc.update(sql, Timestamp.from(ahora));
        else jdbc.update(sql, Timestamp.from(ahora), id);
    }

    @Override
    public void auditar(long id, String actor, String accion, Instant ahora) {
        jdbc.update(
                "INSERT INTO gen02_auditoria(evaluacion_id, usuario, accion, instante) VALUES (?,?,?,?)",
                id,
                actor,
                accion,
                Timestamp.from(ahora));
    }

    private boolean existe(String sql, Object... args) {
        return jdbc.queryForObject(sql, Long.class, args) > 0;
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    private static void fail(int status, String codigo, String mensaje) {
        throw new ReglaEvaluacionException(status, codigo, mensaje);
    }
}

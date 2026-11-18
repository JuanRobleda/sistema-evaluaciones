# GEN-02-BE: propuesta de contrato y plan de implementación

Estado: propuesta para revisión; no acredita aprobación ni endpoints implementados.

## 1. Fuentes y alcance

- Documentación BFA, sección 5.3, E3/E4, páginas 58–60 del PDF facilitado.
- Documentación BFA, sección 5.4.1: ampliaciones del DTO de recuperación.
- DOC-04, revisión 2, CU-BFA-P03/P04, páginas 59–60: asignación y recuperación.
- DOC-03 facilitado: borrador del núcleo común, entidades y cardinalidades.
- Revisión DOC-05 facilitada: observaciones de modalidad, recuperación, errores y reintentos.

GEN-02-BE cubre creación y consulta/recuperación de evaluaciones, persistencia y validaciones. No implementa inicio de subpruebas E5, captura de respuestas E7, calificación, resultados ni publicación. Esas operaciones son dependencias con las que debe integrarse.

El alta mínima de sujetos y períodos aparece en la tarea, pero sus contratos E18/E20 están en el Documento general, no en E3/E4. No inventar esos endpoints ni duplicar las entidades comunes: acordar su responsable y obtener sus campos antes de implementarlos.

## 2. Decisiones que requieren conciliación

| Tema | Diferencia observada | Propuesta para revisión |
| --- | --- | --- |
| Modalidad | AplicacionBFA la requiere y E3 no la incluye | Recibir `modalidad` obligatoria: `INDIVIDUAL` o `COLECTIVA`. No habilitar coordinación colectiva hasta resolver D02; devolver error documentado para una modalidad aún no soportada. |
| Estados de subprueba | E4 usa PENDIENTE/ACTIVA/CERRADA; DOC-04 usa HABILITADA/EN_CURSO/CERRADA | Conservar PENDIENTE/ACTIVA/CERRADA en la API propuesta y documentar equivalencias HABILITADA→PENDIENTE, EN_CURSO→ACTIVA. Ratificar con DOC-04 e integración antes de programar los enum. |
| Alcance | E3 permite selección; AplicacionBFA requiere alcance | Derivar COMPLETA si la selección coincide con todas las configuraciones publicadas; en otro caso PARCIAL. No aceptarlo como una segunda entrada contradictoria. |
| Identificación del test | El ejemplo incluye testId; las reglas lo hacen opcional | Derivar el test de versionFormularioId. Si llega testId, comprobar coincidencia. |
| Período | Modelo común permite ausencia; DOC-04 y E3 lo requieren en BFA | Exigir período existente y activo para crear BFA; no imponer esa obligatoriedad global a todos los instrumentos. |
| Recuperación | El ejemplo E4 omite edición y siguiente configuración | Añadir versionFormularioId, modalidad, alcance y siguienteConfiguracion; incluir inicio y configuración de la activa. |
| Reintento de creación | DOC-04 pide recuperar identidad; E3 define 409 EVALUACION_EN_CURSO | Una evaluación abierta equivalente devuelve 409 con su ID en detalles. Para reintentos técnicos, proponer Idempotency-Key con reproducción de la respuesta original. Ratificar esta distinción. |
| Nuevo intento cerrado | DOC-04 exige nueva autorización y motivo; E3 no define campos | Acordar cómo se registra la autorización y el motivo; no permitir crear un nuevo intento solo porque el anterior esté cerrado. |
| Autenticación | No existe implementación en el esqueleto | Integrar identidad, rol activo, permisos y alcance del componente común. Nunca confiar en actorId o permisos enviados por el cliente. |

Cada decisión debe registrar responsable, resolución, fecha y referencia de aprobación antes de tratar el contrato como definitivo.

## 3. E3: crear evaluación

`POST /api/v1/evaluaciones`

Ejemplo de solicitud propuesta (IDs ficticios; modalidad es una ampliación pendiente):

```json
{
  "testId": 1,
  "versionFormularioId": 10,
  "sujetoId": 25,
  "periodoId": 3,
  "modalidad": "INDIVIDUAL",
  "subpruebas": ["VOC1", "VOC2", "CV"]
}
```

- IDs enteros positivos en esta propuesta; conciliar los tipos con el modelo común.
- `versionFormularioId`, `sujetoId`, `periodoId` y la modalidad propuesta son obligatorios.
- Omitir `subpruebas` incluye todas las configuraciones de la versión publicada.
- Una lista explícita debe ser no vacía, sin duplicados y con códigos pertenecientes a esa versión. Propuesta: rechazar `null` explícito; no interpretarlo como selección completa.
- El servidor conserva el orden oficial, aunque la lista llegue en otro orden.
- Si se incluye FV, recibir `fluidez: {"letraInicial": "M"}` o resolver una asignación previa autorizada. Sin letra autorizada: 409 LETRA_NO_ASIGNADA. El alfabeto y normalización deben acordarse; no inventar una letra aleatoria.
- Verificar sesión, rol elegible, ASIGNAR_APLICACION y alcance sobre sujeto/período. Un sujeto no puede asignarse evaluaciones según DOC-04 rev. 2.
- Verificar existencia, período activo, versión PUBLICADA, pertenencia al test y disponibilidad/compatibilidad de configuraciones.
- Crear Evaluacion, AplicacionBFA y EvaluacionSubprueba de forma atómica. Si una validación falla, no dejar filas parciales.
- Fijar edición y parámetros aplicados para evitar que una edición posterior modifique la ejecución.
- Registrar responsable e instante del servidor. Ninguna subprueba recibe inicio o finLimite al crear.

Respuesta inicial: `201 Created`, cabecera `Location: /api/v1/evaluaciones/15`, DTO descrito en E4. `fechaInicio` representa aquí creación de la evaluación global; no el inicio del reloj formal de una subprueba. Esta semántica requiere ratificación.

### Reintentos y concurrencia propuestos

Si se adopta `Idempotency-Key`, persistir su alcance (actor/operación), huella de solicitud, estado y respuesta junto con la creación. Misma clave y mismo cuerpo reproducen el resultado original; misma clave con otro cuerpo produce 409 IDEMPOTENCIA_CONFLICTO, código propuesto. Acordar obligatoriedad, retención y comportamiento de solicitudes simultáneas antes de publicar OpenAPI.

La comprobación de evaluación abierta necesita una garantía en PostgreSQL, no solo un `exists` en Java. Proponer restricción única parcial por sujeto/test/período para los estados abiertos definitivos. Solicitudes simultáneas con claves diferentes no pueden crear dos evaluaciones abiertas. Traducir el conflicto de unicidad a 409 EVALUACION_EN_CURSO sin exponer SQL.

## 4. E4: consultar y recuperar

`GET /api/v1/evaluaciones/{evaluacionId}`; sin cuerpo.

Verificar sesión, EVALUACION_LEER y propiedad o alcance profesional. La continuación requiere RECUPERAR_INTENTO; consultar no concede permiso para responder.

Ejemplo propuesto, ampliado respecto al ejemplo original. Todos los datos son ficticios:

```json
{
  "id": 15,
  "testId": 1,
  "versionFormularioId": 10,
  "sujetoId": 25,
  "periodoId": 3,
  "modalidad": "INDIVIDUAL",
  "alcance": "PARCIAL",
  "estado": "EN_CURSO",
  "fechaInicio": "2026-10-30T15:30:00Z",
  "fechaFin": null,
  "subpruebaActiva": {
    "codigo": "VOC2",
    "configuracionSubpruebaId": 102,
    "inicio": "2026-10-30T15:40:00Z",
    "finLimite": "2026-10-30T15:45:00Z",
    "segundosRestantes": 180
  },
  "siguienteConfiguracion": null,
  "subpruebas": [
    {"codigo": "VOC1", "configuracionSubpruebaId": 101, "orden": 1, "estado": "CERRADA"},
    {"codigo": "VOC2", "configuracionSubpruebaId": 102, "orden": 2, "estado": "ACTIVA"},
    {"codigo": "CV", "configuracionSubpruebaId": 108, "orden": 8, "estado": "PENDIENTE"}
  ]
}
```

El ejemplo supone hora del servidor 15:42:00Z. Propuesta: `segundosRestantes = max(0, ceil((finLimite - ahoraServidor) en segundos))`, usando un único instante por respuesta y un Clock inyectable en Java. Ratificar el redondeo. No aceptar tiempos del cliente.

Al crear, `subpruebaActiva` es null y todas las ejecuciones están PENDIENTE. `siguienteConfiguracion` identifica la primera configuración cuya preparación/inicio es permitido; no concede automáticamente la señal de inicio. Propuesta de forma: `{ "configuracionSubpruebaId": 101, "codigo": "VOC1", "orden": 1 }`. Es null mientras haya una activa, si terminó la selección o si las condiciones de secuencia/ventana impiden habilitar la siguiente.

### Garantías de recuperación

- Leer datos persistidos: no crear evaluación ni ejecuciones nuevas.
- No cambiar inicio ni finLimite; una recarga no devuelve tiempo adicional.
- Si venció la activa, invocar o confirmar el mismo proceso de cierre idempotente de CU-BFA-P07 y devolver el estado actualizado. La calificación pertenece al responsable de ese proceso.
- El proceso automático de vencimientos debe funcionar sin consultas GET; E4 no puede ser su única garantía.
- No reabrir ejecuciones cerradas ni adelantar la siguiente por una entrega anticipada.
- Auditar recuperación/denegación sin registrar secretos o contenido psicológico sensible.
- E4 no debe exponer claves, respuestas correctas ni resultados no autorizados.
- DOC-04 pide recuperar posición y respuestas confirmadas; el DTO E4 original no los define. Acordar si se incorporan con sus DTO o se consultan por E6/E7, incluyendo el caso vencido. No declarar recuperación completa hasta cubrir esta dependencia.

## 5. Errores

Usar el formato común del Documento general, sección 5.2.2; la forma siguiente es una propuesta pendiente de verificar contra esa fuente:

```json
{
  "timestamp": "2026-10-30T15:42:00Z",
  "status": 409,
  "codigo": "EVALUACION_EN_CURSO",
  "mensaje": "Ya existe una evaluación abierta para este contexto.",
  "ruta": "/api/v1/evaluaciones",
  "detalles": {"evaluacionId": 15}
}
```

Solo incluir la identidad existente si el actor está autorizado para conocerla. El tipo exacto de detalles depende del contrato común.

| HTTP | Código | Condición |
| --- | --- | --- |
| 400 | SOLICITUD_INVALIDA | JSON inválido, campos obligatorios ausentes o valores estructuralmente inválidos |
| 401 | Por acordar con autenticación | Sesión ausente o vencida |
| 403 | SIN_PERMISO | Falta de rol, permiso, propiedad o alcance |
| 404 | TEST_NO_ENCONTRADO / SUJETO_NO_ENCONTRADO / PERIODO_NO_ENCONTRADO | Referencia inexistente en E3 |
| 404 | EVALUACION_NO_ENCONTRADA | Evaluación inexistente en E4 |
| 404 | Código de versión por acordar | versionFormularioId inexistente |
| 409 | VERSION_NO_PUBLICADA | Edición no disponible para asignación |
| 409 | EVALUACION_EN_CURSO | Evaluación abierta duplicada |
| 409 | LETRA_NO_ASIGNADA | FV sin letra autorizada |
| 422 | VERSION_INCOMPATIBLE / SUBPRUEBA_NO_INCLUIDA | Selección o edición incompatibles |
| Por acordar | PERIODO_INACTIVO, MODALIDAD_NO_SOPORTADA, REASIGNACION_NO_AUTORIZADA (propuestos) | Reglas de DOC-04 sin respuesta completa en E3 |

Conciliar la política 403/404 para evitar revelar recursos ajenos. No atribuir aprobación a los códigos propuestos.

## 6. Distribución técnica prevista

Base: `backend/src/main/java/com/universidad/tests/`.

| Capa | Responsabilidad |
| --- | --- |
| controller | Adaptar HTTP, validar DTO y devolver status/Location; no acceder directamente a JPA |
| dto / mapper | Separar JSON de modelos internos; no serializar entidades JPA |
| application/usecase | Orquestar creación/recuperación, autorización y transacción, sin usar JPA directamente |
| domain/model y puertos de dominio | Reglas, estados e interfaces de repositorio/servicios externos, sin Spring ni JPA |
| infrastructure | Entidades JPA, repositorios y adaptadores que implementan los puertos |

La ubicación del núcleo GEN común debe acordarse con integración. No meter Sujeto, Periodo y Evaluacion como duplicados dentro de BFA. Las extensiones propias sí pertenecen a los subpaquetes bfa de cada capa. Las dependencias compartidas necesarias se entregan en PR separado antes del módulo, según las reglas de trabajo.

Antes de crear código, comprobar que la distribución respeta ArquitecturaTest: application no depende de infrastructure/JPA y controller no depende de infrastructure ni domain.model.

## 7. Casos de prueba de aceptación previstos

Son especificaciones, todavía no pruebas ejecutables.

| ID | Escenario | Resultado esperado |
| --- | --- | --- |
| GEN02-01 | Omitir selección con edición publicada | 201, todas las configuraciones incluidas, sin reloj iniciado |
| GEN02-02 | Seleccionar subconjunto válido en orden distinto | 201, alcance PARCIAL y orden oficial persistido |
| GEN02-03 | Lista vacía, null explícito, códigos repetidos o IDs inválidos | Rechazo según contrato final, sin persistencia parcial |
| GEN02-04 | Sujeto/período inexistentes o período inactivo | Error acordado; ninguna evaluación creada |
| GEN02-05 | Versión inexistente, no publicada o de otro test | Error específico; sin creación |
| GEN02-06 | Subprueba ajena a la edición | 422 SUBPRUEBA_NO_INCLUIDA |
| GEN02-07 | FV sin letra ni asignación previa | 409 LETRA_NO_ASIGNADA |
| GEN02-08 | Actor sin sesión, sin permiso o fuera de alcance | 401/403 conforme a política común |
| GEN02-09 | Sujeto intenta asignar evaluación | Denegación; ninguna fila creada |
| GEN02-10 | Dos creaciones simultáneas para igual contexto | Una sola evaluación abierta en PostgreSQL |
| GEN02-11 | Repetir clave técnica con igual/distinto cuerpo | Misma respuesta original / conflicto, si se aprueba Idempotency-Key |
| GEN02-12 | Crear nuevo intento después del cierre sin autorización/motivo | Rechazo sin alterar historial |
| GEN02-13 | Recuperar antes del inicio formal | Misma evaluación, activa null, relojes sin iniciar |
| GEN02-14 | Recuperar a los 120 s de una prueba de 300 s | 180 s restantes; mismo inicio y finLimite |
| GEN02-15 | Recuperar al límite y después | 0 s, cierre idempotente confirmado, sin aceptar respuestas tardías |
| GEN02-16 | Dos recuperaciones simultáneas de una prueba vencida | Un solo cierre; no duplicar calificación ni alterar plazo |
| GEN02-17 | Recuperar evaluación cerrada | Consulta autorizada, sin reapertura ni nuevos intentos |
| GEN02-18 | Reiniciar backend y volver a consultar | Edición, ejecuciones y vencimientos conservados en BD |
| GEN02-19 | Fallar al guardar una ejecución de subprueba | Rollback de toda la creación |
| GEN02-20 | Entrega anticipada sin señal de siguiente inicio | No habilitar inicio fuera de secuencia/ventana |
| GEN02-21 | Inspeccionar respuesta y errores | Sin claves, SQL, datos ajenos ni información sensible |
| GEN02-22 | Recuperar tras guardar respuestas en E7 | Solo respuestas confirmadas y posición persistida según integración acordada |

Usar pruebas unitarias de reglas con Clock fijo, pruebas HTTP de autorización/DTO y pruebas de integración con PostgreSQL para transacciones, unicidad y concurrencia. Las pruebas de recuperación de respuestas/cierre necesitan los componentes responsables o dobles explícitos; los dobles no acreditan la integración real.

## 8. Orden de trabajo

1. Revisar y resolver las decisiones de la sección 2 con Documentación e Integración.
2. Obtener modelo común vigente, contratos de sujetos/períodos y formato de errores/autenticación. Acordar responsables.
3. Entregar dependencias compartidas en PR separado cuando corresponda.
4. Actualizar docs/api/openapi.yaml con el contrato acordado antes de consumirlo desde frontend.
5. Implementar persistencia aprobada, casos de uso, adaptadores HTTP y pruebas anteriores.
6. Ejecutar `mvn -f backend/pom.xml spotless:apply` y `mvn -f backend/pom.xml verify`.
7. Abrir PR con título `[GEN-02-BE] Crear y recuperar evaluaciones`, ID real de ClickUp y evidencia de pruebas; pasar Testing e Integración.

Esta propuesta original se conserva para trazabilidad. Existe ahora una implementación local experimental y un OpenAPI draft; consultar GEN-02-BE-desarrollo-local.md para funcionalidades, diferencias y pendientes. No se incorporaron migraciones aprobadas ni se afirma que la tarea esté lista para integración.

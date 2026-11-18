# GEN-02-BE: implementación local de desarrollo

## Estado y límites

Esta rama contiene una implementación experimental de E3/E4 en Java y Spring Boot. Está desactivada por defecto y solo se habilita con `gen02-draft`. No está aprobada para producción, no sustituye las decisiones de DOC-03/DOC-05 y no se ha subido a GitHub.

No se modificó el pom.xml ni se agregaron dependencias. La persistencia usa JdbcTemplate (incluido transitivamente por el starter JPA existente), SQL parametrizado y transacciones Spring, detrás de un puerto de dominio. No es obligatorio usar entidades JPA para persistir en Spring Boot; cambiar al adaptador JPA común no debe alterar los casos de uso.

Las tablas `gen02_*` constituyen un esquema local provisional, no el modelo canónico compartido. El script es de inicialización para una BD nueva de desarrollo; CREATE IF NOT EXISTS no actualiza tablas antiguas ni sustituye migraciones versionadas. No se añadió una migración a database/migrations porque DOC-03 sigue pendiente de conciliación/aprobación.

## Funcionalidades implementadas

1. E3 crea una evaluación completa o parcial, con edición fija y subpruebas en orden oficial. Guarda responsable, modalidad individual, alcance derivado, parámetros de ejecución y letra de FV si corresponde. Los relojes permanecen sin iniciar.
2. Valida selección, referencias existentes, período activo, edición publicada, coincidencia de test, permisos y alcance.
3. Serializa las creaciones del mismo contexto mediante locks transaccionales PostgreSQL y agrega un índice único para evitar dos evaluaciones abiertas. Un reenvío devuelve 409 EVALUACION_EN_CURSO con el ID existente; el cliente autorizado puede consultarlo. No se implementó Idempotency-Key ni reproducción de 201 porque esa ampliación sigue pendiente de contrato.
4. E4 consulta la misma evaluación, su versión, alcance, subpruebas, configuración activa y próxima candidata. Deriva el tiempo restante de finLimite y un Clock del servidor, sin reescribir el reloj.
5. Al vencer, cierra la ejecución de forma atómica y deja un registro único en gen02_cierre_pendiente para integración con calificación. Un job ejecuta el mismo cierre cada cinco segundos aunque no haya consultas GET.
6. Guarda auditoría de creación y consulta/recuperación.
7. Ofrece errores de negocio estructurados y validación HTTP.

El cierre de ejecución no es calificación: el outbox queda PENDIENTE_CALIFICACION. No hay consumidor de cálculo en esta tarea. Tampoco se finaliza globalmente la evaluación al cerrar una subprueba: E8 corresponde a otra operación.

## Archivos principales

Todas las rutas Java parten de backend/src/main/java/com/universidad/tests/.

| Archivo | Función |
| --- | --- |
| controller/gen/EvaluacionController.java | POST/GET y cabecera Location |
| dto/gen/IniciarEvaluacionRequest.java | Solicitud y validaciones estructurales |
| dto/gen/EvaluacionResponse.java | Respuesta pública |
| application/usecase/gen/EvaluacionService.java | Creación, consulta, permisos y transacciones |
| domain/model/gen/ | Modelos Java independientes de Spring |
| domain/repository/gen/Evaluaciones.java | Puerto para persistencia, alcance y auditoría |
| infrastructure/persistence/gen/JdbcEvaluaciones.java | PostgreSQL y cierre atómico |
| infrastructure/persistence/gen/VencimientosJob.java | Vencimientos sin intervención del frontend |
| infrastructure/security/gen/LocalDraftAuthentication.java | Adaptador de credenciales exclusivamente local |
| exception/gen/EvaluacionErrorHandler.java | Formato de errores |
| config/gen/Gen02Configuration.java | Clock y activación del job |

## Preparar una demostración

Requisitos: JDK 17 o superior, Maven y PostgreSQL 17. Se compila para Java 17. Usar una base dedicada a desarrollo, nunca datos reales.

1. Crear una base PostgreSQL vacía para el prototipo. Puede utilizarse la instancia del compose.yaml existente, sin cambiar main ni subir nada.
2. Desde PowerShell en la raíz, configurar la conexión correspondiente:

```powershell
$env:DB_HOST = "127.0.0.1"
$env:POSTGRES_PORT = "5432"
$env:POSTGRES_DB = "evaluaciones"
$env:POSTGRES_USER = "evaluaciones"
# Usar las credenciales locales reales de esa BD; no guardarlas en Git.
$env:POSTGRES_PASSWORD = Read-Host "Contraseña de PostgreSQL"
$env:GEN02_LOCAL_USER = "demo-gen02"
$env:GEN02_LOCAL_PASSWORD = Read-Host "Contraseña temporal para la API local"
mvn -f backend/pom.xml spring-boot:run "-Dspring-boot.run.profiles=gen02-draft"
```

El perfil escucha solo en 127.0.0.1. Si Maven no está en PATH, ejecutar mvn.cmd desde su instalación o configurar Maven en el IDE. Si se usa JAVA_HOME, debe apuntar a un JDK compatible, no al JRE antiguo.

3. El primer arranque crea las tablas provisionales. Desde otra terminal o el editor SQL de pgAdmin, ejecutar docs/api/gen02-demo.sql en esa misma base. Solo contiene datos ficticios y tres configuraciones de demostración; no representa la batería oficial completa.
4. Desde otra terminal PowerShell, probar la API. El usuario debe tener filas de alcance en gen02_permiso como las del SQL de demostración:

```powershell
$gen02Usuario = "demo-gen02"
$gen02Clave = Read-Host "Contraseña temporal para la API local"
$gen02Token = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("${gen02Usuario}:${gen02Clave}"))
$gen02Headers = @{ Authorization = "Basic $gen02Token" }
$gen02Body = @{
  versionFormularioId = 10
  sujetoId = 25
  periodoId = 3
  modalidad = "INDIVIDUAL"
  subpruebas = @("VOC1", "VOC2")
} | ConvertTo-Json
$gen02Evaluacion = Invoke-RestMethod -Method Post -Uri "http://127.0.0.1:8080/api/v1/evaluaciones" -Headers $gen02Headers -ContentType "application/json" -Body $gen02Body
$gen02Evaluacion | ConvertTo-Json -Depth 8
Invoke-RestMethod -Method Get -Uri "http://127.0.0.1:8080/api/v1/evaluaciones/$($gen02Evaluacion.id)" -Headers $gen02Headers | ConvertTo-Json -Depth 8
```

Repetir el POST devuelve 409 con la identidad existente, no otra evaluación. Para crear una selección con FV hay que enviar `fluidez: {"letraInicial":"M"}`; la asignación previa de letra no está integrada.

El adaptador local comprueba credenciales contra variables de entorno y crea un Principal solo si coinciden. No acepta X-User, actorId ni permisos del cliente. Sin adaptador local configurado ni Principal del contenedor, la API deniega acceso con 401. La contraseña no se persiste en las tablas del prototipo.

Para probar el reloj sin E5 se puede preparar una ejecución ACTIVA directamente en una BD ficticia, como hacen las pruebas. Eso es una preparación de test, no una API de inicio: E5 deberá validar orden, preparación, permiso y señal. La siguienteConfiguracion de E4 es una candidata en orden, no una autorización para comenzar.

## Ejecutar pruebas

Pruebas unitarias y arquitectura, sin base:

```powershell
mvn -f backend/pom.xml "-Dtest=EvaluacionServiceTest,ArquitecturaTest" test
```

Para las pruebas PostgreSQL usar OTRA base dedicada exclusivamente a testing. La suite vacía las tablas gen02_* de esa base antes de cada caso; no ejecutarla sobre una BD con trabajo que deba conservarse.

Configurar DB_HOST, POSTGRES_PORT, POSTGRES_DB, POSTGRES_USER y POSTGRES_PASSWORD hacia esa base y ejecutar:

```powershell
$env:GEN02_RUN_DB_TESTS = "true"
mvn -f backend/pom.xml spotless:apply
mvn -f backend/pom.xml verify
Remove-Item Env:GEN02_RUN_DB_TESTS
```

EvaluacionPostgresTest activa gen02-draft mediante anotación y usa credenciales ficticias internas al test. No exige configurar credenciales locales de la API. Sin GEN02_RUN_DB_TESTS=true, sus casos se omiten explícitamente; un verify sin esa variable no demuestra persistencia/concurrencia. MigracionesTest existente sí requiere la conexión PostgreSQL habitual.

## Verificación realizada en esta rama

`mvn -f backend/pom.xml verify` con GEN02_RUN_DB_TESTS=true terminó en BUILD SUCCESS: 31 pruebas, 0 fallos, 0 errores y 0 omitidas. Incluye 10 pruebas de casos de uso, 12 de PostgreSQL/HTTP, 8 de arquitectura y 1 de migraciones existente. Spotless pasó. Se ejecutó con JDK 22 compilando para Java 17 y una instancia PostgreSQL 17.4 temporal en localhost:55432, sin usar datos reales.

Las pruebas verifican concurrencia, rollback después de insertar la evaluación, cierre único, orden/alcance, FV, autenticación local y denegación de identidad falsificada. La lectura desde una instancia nueva del repositorio comprueba persistencia; no sustituye una prueba de reinicio completo de procesos.

## Pendientes para completar la entrega de equipo

- Conciliar el contrato draft con Documentación y registrar aprobación de modalidad, estados, campos y errores.
- Sustituir esquema gen02_* y autenticación local por el modelo y seguridad comunes; preservar las garantías transaccionales. Los permisos actuales son grants explícitos por usuario/sujeto/período, no el sistema institucional de roles activos.
- Alta mínima de sujetos y períodos: el prototipo usa registros SQL ficticios; no implementa E18/E20. Obtener esos contratos del Documento general y acordar si pertenecen a GEN-02-BE u otro responsable antes de agregar sus endpoints.
- Recuperar posición y respuestas confirmadas mediante la integración E6/E7; E4 por sí solo implementa estado y reloj, no esa recuperación completa.
- Integrar el consumidor del outbox de cierres con CU-BFA-P07; no afirmar calificación automática terminada.
- Acordar nuevos intentos con autorización/motivo. Por ahora se deniega una nueva asignación si existe historial cerrado.
- La única modalidad implementada es INDIVIDUAL; COLECTIVA devuelve 422. La selección null se trata como omitida; la propuesta documental original sugería rechazarla y debe conciliarse.
- Preparar PR separado de los componentes comunes antes del PR del módulo. En esta rama local están juntos para poder revisar/probar el prototipo; no están listos para fusionarse como un único PR.

No se hicieron commits, push, PR ni merge. main permanece sin cambios.

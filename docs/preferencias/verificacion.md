# Verificación de SCRUM-41

Ejecutada el **13 de octubre de 2026** sobre `feature/SCRUM-41-perfil-preferencias`,
base `3cd8005`, con los cambios locales de esta historia. Java 17, Maven y Docker Desktop.

| Componente | Pruebas distintas aprobadas | Fallos | Errores | Omitidas |
|---|---:|---:|---:|---:|
| Servicio de Comportamiento | 378 | 0 | 0 | 0 |
| API Gateway | 54 | 0 | 0 | 0 |
| Total | 432 | 0 | 0 | 0 |

Se ejecutó `mvn test` completo en ambos servicios. Después de reforzar la validación de
devoluciones, se añadieron dos casos y se repitieron las 40 pruebas de preferencias mediante
`mvn "-Dtest=ConstructorPreferenciasTest,IdentificadoresPreferenciasTest,PreferenciasIntegracionTest" package`.
La regresión completa inicial de Comportamiento contenía 376 casos; el total de 378 cuenta
los dos casos nuevos una sola vez. Gateway también se empaquetó correctamente tras sus pruebas.

## Cobertura nueva

| Clase | Casos | Qué verifica |
|---|---:|---|
| `ConstructorPreferenciasTest` | 12 | Orden, empates, conteo por compra, exclusión de categorías sin asignar, perfiles vacíos, devoluciones y consistencia |
| `IdentificadoresPreferenciasTest` | 14 | Contrato HTTP de Perfil, deduplicación de identificadores, cliente incorrecto, consolidado o inexistente, respuestas inválidas y caída/timeout |
| `PreferenciasIntegracionTest` | 14 | PostgreSQL real, migraciones, API, persistencia, consultas, reconstrucción, separación entre clientes, concurrencia y rollback |
| `AccesoPorRolTest` | 2 nuevos | Construcción restringida a administrador con permiso y lectura restringida por rol y permiso |

La prueba de rollback induce un fallo después de comenzar a reemplazar las categorías y
comprueba que el perfil anterior se conserva completo. La prueba concurrente construye el
mismo perfil desde dos hilos y verifica versiones 1 y 2 sin categorías duplicadas.

Las pruebas de integración usaron bases temporales de Testcontainers, no la base de desarrollo.
Al finalizar no quedaron contenedores con la etiqueta `org.testcontainers=true`.

## Evidencia reproducible

Los informes JUnit se generan en `target/surefire-reports/` de cada servicio. Los registros locales
de esta ejecución, ignorados por Git, están en:

- Comportamiento: `target/scrum41-regresion.log` y `target/scrum41-verificacion-final.log`.
- Gateway: `target/scrum41-gateway.log` y `target/scrum41-empaquetado.log`.

También se verificaron `git diff --check` y la sintaxis de los bloques PowerShell de la
[guía manual](prueba-manual.md). No se modificó el frontend: no se declara una prueba de navegador
ni una pantalla de SCRUM-43 como parte de esta entrega.

Los cambios permanecen sin commits. El cierre formal requiere revisión de un par, merge y
adjuntar a Jira los [diagramas](diagramas.md) que se entregan en el repositorio.

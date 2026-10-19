# Verificación de SCRUM-30

Ejecución inicial: 19 de octubre de 2026, America/La_Paz. Implementación sobre la copia local `6d366f0`; esa primera ejecución se realizó sin pull, commits ni push.

## Resultados iniciales

| Validación | Resultado |
|---|---|
| `mvn package` en servicio-segmentacion | 51 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| `mvn test` en api-gateway | 57 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| `npm run build` | Compilación Angular correcta, sin avisos del compilador. |
| Playwright Chromium, `e2e/scrum30.spec.ts` | 7 pruebas correctas. |
| `git diff --check` | Correcto. |

Total: 115 pruebas correctas. PostgreSQL 16, RabbitMQ 3.13 y el emulador oficial de Google Cloud Pub/Sub se ejecutaron mediante Docker; las pruebas de integración no fueron sustituidas por mocks ni omitidas.

## Cobertura

- Backend: asignación, cambio, retiro, recálculo sin cambio, reentrega, evento atrasado, regla original después de edición/desactivación, criterios de frecuencia/valor/categoría, orden estable ante fechas iguales, aislamiento por cliente, paginación, parámetros inválidos, permisos, métodos de escritura, inmutabilidad SQL, rollback completo y duplicados concurrentes.
- Migración: base previa a SCRUM-30 con auditoría real; conserva el instante original, marca la regla incompleta y no repite la carga al volver a migrar.
- Brokers: las pruebas de RabbitMQ y Pub/Sub comprueban que una reentrega no genera otra fila de historial.
- Gateway: lectura autorizada para gerente/administrador; 401 sin sesión; 403 sin permiso y al intentar escribir incluso como administrador.
- Navegador: eventos reales publicados en RabbitMQ generan 23 cambios consultables desde Angular; paginación, instantánea de versión anterior, retiro, importación histórica, historial vacío, permisos, cabeceras falsificadas, error y reintento, identificador inválido y diseño móvil sin desbordamiento.

## Evidencias

- [Captura de escritorio](evidencias/scrum30-escritorio.png).
- [Captura móvil](evidencias/scrum30-movil.png).
- Diagramas de clases y secuencia: [historial-segmentos-diagramas.md](historial-segmentos-diagramas.md).
- Prueba reproducible: `herramientas/pruebas-scrum30/ejecutar.ps1`.
- Reportes detallados: `backend/*/target/surefire-reports/`, `frontend/crm-web/playwright-report/` y `frontend/crm-web/test-results/`.

Los procesos y contenedores de la ejecución de navegador se detuvieron al terminar. La verificación utilizó bases aisladas; no aplicó cambios sobre una base de producción. La ejecución inicial no incluyó commits ni revisión por un par.

## Revalidación al integrar main en el PR #44

El 19 de octubre de 2026 se incorporó `main` (`fa8471e`) en `feature/SCRUM-30-historial-segmentos` y se resolvieron los conflictos de `AsignacionSegmento` y `AuditoriaSegmentos` conservando la clasificación por frecuencia de SCRUM-38 y el historial transaccional de SCRUM-30.

- La selección recibe la lista de criterios evaluados y `clasificacionFrecuencia`.
- Las instantáneas conservan la clasificación de la regla y de las métricas, con la política `VALOR_FRECUENCIA_CLASIFICACION_CATEGORIA_V2`.
- Se adaptaron los constructores de métricas de las pruebas al contrato actualizado.
- Un nuevo caso de integración prueba que la clasificación participa en la asignación, tiene prioridad sobre categoría y conserva la regla original después de editarla.

Se ejecutó `herramientas/pruebas-scrum30/ejecutar.ps1` completo, sin reutilizar compilaciones:

| Validación después del merge | Resultado |
|---|---|
| Servicio de segmentación | 77 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| API Gateway | 58 pruebas, 0 fallos, 0 errores, 0 omitidas. |
| Angular | Compilación correcta. |
| Playwright Chromium | 7 pruebas correctas. |

Total revalidado: **142 pruebas correctas**. El diff de SCRUM-30 respecto a `origin/main` pasó la comprobación de espacios y no contiene marcadores de conflicto. Logs de esta ejecución: `logs-scrum30-merge-e2e.log` (archivo local ignorado por Git).

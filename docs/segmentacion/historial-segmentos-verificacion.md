# Verificación de SCRUM-30

Ejecución: 19 de octubre de 2026, America/La_Paz. Implementación sobre la copia local `6d366f0`; no se ejecutaron pull, commits ni push.

## Resultados

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

Los procesos y contenedores de la ejecución de navegador se detuvieron al terminar. La verificación utilizó bases aisladas; no aplicó cambios sobre una base de producción. La revisión por un par y la integración mediante commits quedan fuera de esta ejecución por la instrucción de trabajar sin commits.

# Historial de cambios de segmento — SCRUM-30 / RF-22

## Comportamiento

El sistema conserva una fila por asignación inicial, cambio efectivo o retiro de segmento. La fecha corresponde al registro en el CRM; `calculadasEn` en las métricas identifica el cálculo que originó la decisión.

- Primera asignación: `Sin segmento → nombre`.
- Cambio: varía el identificador del criterio o el nombre del segmento, igual que en SCRUM-26.
- Retiro: `nombre → Sin segmento`, con motivo `SIN_CRITERIO_COINCIDENTE` y una copia de los criterios activos evaluados. La lista puede estar vacía.
- Recálculo sin cambio: no escribe historial, aunque el criterio haya cambiado de versión.
- Mensaje duplicado o desactualizado: no agrega historial.

La política actual prioriza valor, frecuencia y categoría; los desempates usan el orden de consumo de categorías y luego el criterio más antiguo. La instantánea registra `VALOR_FRECUENCIA_CATEGORIA_V1`. La edición de criterios mantiene su propia auditoría.

## Persistencia y transacción

`segmentacion.historial_segmento` contiene cliente, fecha, operación, segmento anterior/nuevo, regla, métricas, política, motivo, evento de origen y vínculo único al evento de auditoría. `regla` guarda la copia completa del criterio, incluidos identificador, versión, nombre, indicador, condición, límites y categoría. No consulta el criterio actual para explicar cambios anteriores.

La migración `V5__historial_segmentos.sql` crea la tabla y el índice `(cliente_id, fecha DESC, id DESC)`. V4 está reservada por la clasificación por frecuencia en el main remoto. El esquema de auditoría del gateway debe estar migrado antes de iniciar segmentación, como ya requiere SCRUM-26.

La asignación bloquea el cliente y escribe segmento, auditoría, historial y marca de evento en una sola transacción. La unicidad `(cliente_id, id_evento)` y `auditoria_evento_id` evita duplicados. El fallo de cualquiera de las escrituras revierte toda la operación. No hay claves foráneas al criterio ni al perfil que puedan borrar el historial en cascada.

`AuditoriaSegmentos` sigue generando exactamente un evento de auditoría por cambio. `HistorialSegmentos` genera la fila funcional asociada a ese evento, sin emitir una segunda auditoría del mismo cambio.

Triggers rechazan `UPDATE`, `DELETE` y `TRUNCATE`. El repositorio JDBC solo expone inserción transaccional y consulta. La API no expone escritura y el gateway rechaza expresamente intentos de modificación incluso con permisos de administrador.

## Registros anteriores

La migración importa los eventos `SEGMENTO_CLIENTE` existentes y conserva su fecha original, antes/después, métricas e identificador del evento. No inventa versiones ni parámetros que la auditoría anterior no guardaba. Estas filas tienen `reglaCompleta=false` y la pantalla indica que la explicación histórica está incompleta. Ejecutar las migraciones nuevamente no repite la carga.

La carga exige auditoría válida generada por el sistema; datos dañados o eventos duplicados que violen las restricciones detienen la migración para revisión, sin omitirlos silenciosamente.

Para actualizar una instalación con consumidores en marcha, pausar el consumo antes de aplicar la migración/carga y activar el nuevo servicio antes de reanudarlo. Así no quedan cambios entre la importación y la activación del escritor nuevo. Las pruebas usan bases aisladas; no se migró una base de producción.

## Consulta

`GET /api/segmentacion/clientes/{clienteId}/historial-segmentos?pagina=0&tamanio=20`

- Cliente positivo; página desde 0; tamaño de 1 a 100.
- Respuesta: `contenido`, `pagina`, `tamanio`, `totalElementos`, `totalPaginas`.
- Cada fila incluye `fecha`, `operacion`, `segmentoAnterior`, `segmentoNuevo`, `regla`, `criteriosEvaluados`, `metricas`, `politica`, `motivo`, `idEvento`, `tipoEventoOrigen` y `reglaCompleta`.
- Orden: `fecha DESC, id DESC`, estable cuando varias fechas coinciden.
- Conteo y filas se leen con la misma instantánea transaccional.
- Sin registros: 200 con una lista vacía; esto no determina si el perfil existe.
- Identificador o paginación inválidos: 400; sin identidad: 401; sin permiso: 403.

El gateway valida el JWT, reemplaza cabeceras de identidad suministradas por el navegador y exige `SEGMENTOS_CONSULTAR`. El servicio comprueba también la identidad interna y el permiso. Sus puertos deben seguir en la red interna. La consulta queda en la auditoría de accesos por cliente.

Los roles actuales que tienen el permiso son Administrador de CRM y Gerente Comercial. El proyecto no tiene un rol separado llamado Analista de Datos; se utiliza el permiso existente, sin ampliar el catálogo de roles.

## Pantalla

Clientes ofrece el enlace **Segmentos** y la navegación del cliente incluye **Historial de segmentos**, ambos visibles con `SEGMENTOS_CONSULTAR`. La ruta `/clientes/:id/historial-segmentos` está protegida por el mismo permiso.

La pantalla muestra operación, fecha local, antes/después, explicación de regla, detalle desplegable con versión y métricas, y paginación. Incluye estados de carga, vacío, error y reintento; no ofrece edición ni borrado. Al cambiar de cliente o página cancela la solicitud anterior para evitar mostrar datos de otra consulta.

## Trazabilidad de subtareas

| Subtarea | Implementación |
|---|---|
| SCRUM-290 | Modelo y decisiones de este documento. |
| SCRUM-569 | Migración V5, índices, unicidad y triggers. |
| SCRUM-289 | Instantáneas en `AuditoriaSegmentos`. |
| SCRUM-291 | Registro vinculado dentro de la transacción de `AsignacionSegmento`. |
| SCRUM-292 | `HistorialSegmentosController` y consulta paginada. |
| SCRUM-570 | Servicio Angular, componente, pestaña, enlace y guard. |
| SCRUM-293 | Integración PostgreSQL/RabbitMQ/PubSub, seguridad y navegador. |
| SCRUM-578 | Diagramas de clases y secuencia en `historial-segmentos-diagramas.md`. |

## Verificación

Desde la raíz del repositorio:

```powershell
mvn -f backend/servicio-segmentacion/pom.xml test
mvn -f backend/api-gateway/pom.xml test
npm --prefix frontend/crm-web run build
./herramientas/pruebas-scrum30/ejecutar.ps1
```

Las pruebas de backend requieren Docker y no omiten integración cuando falta. El ejecutor de navegador crea PostgreSQL y RabbitMQ aislados, compila, inicia gateway/segmentación/Angular, publica métricas reales y ejecuta `e2e/scrum30.spec.ts`. Al terminar detiene sus procesos y elimina solo sus contenedores. Los puertos 18080, 18083 y 14200 deben estar libres. `-ReutilizarCompilacion` reutiliza los JAR ya verificados; vuelve a compilar Angular.

El ejecutor conserva logs en `logs/scrum30/` y Playwright deja capturas y resultados en `frontend/crm-web/test-results/` y `playwright-report/`.

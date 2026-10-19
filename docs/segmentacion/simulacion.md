# SCRUM-29 · Simulación de reglas de segmentación

Historia: [SCRUM-29](https://grupobcrm.atlassian.net/browse/SCRUM-29), RF-21. Diseño de SCRUM-282, implementación de SCRUM-281/283/284/285/286/287 y verificación de SCRUM-288. Los [diagramas de clases y secuencia](simulacion-diagramas.md) cubren SCRUM-577.

## Flujo del administrador

1. Iniciar sesión como Administrador de CRM con el permiso `SEGMENTACION_CONFIGURAR` y abrir **Criterios de segmentación** (`/segmentacion/criterios`).
2. Crear un borrador o editar un criterio existente. Elegir frecuencia, valor acumulado o categoría, y sus condiciones.
3. Pulsar **Simular impacto**. Se muestran clientes evaluados, clientes que cambiarían de segmento y, por segmento, cantidad actual, cantidad simulada y diferencia. Se incluyen los segmentos vacíos y **Sin segmento**.
4. Revisar las fechas de las métricas y las asignaciones excluidas por falta de métricas. Cambiar cualquier campo cancela una solicitud pendiente y descarta su resultado para no guardar con una previsualización desactualizada.
5. Pulsar **Guardar regla simulada** en el mismo formulario. Usa el alta o edición existente, que vuelve a validar la regla y su versión; si otro administrador creó un conflicto después de la simulación, se rechaza el guardado.

Simular no guarda criterios, segmentos, métricas, eventos procesados ni historial. Guardar sí registra la auditoría habitual del criterio. La reasignación de clientes ocurre posteriormente con nuevos eventos de métricas a través de `AsignacionSegmento`; guardar no ejecuta una reclasificación masiva. La opción existente de guardar directamente y la comprobación manual de un caso siguen disponibles.

## Datos y cálculo

La implementación usa las tablas existentes de segmentación y consultas JDBC directas en el backend. No necesita nuevas migraciones, acceso a la base desde el navegador ni llamadas por cada cliente a otros servicios.

- Población evaluada: una fila por cliente presente en `segmentacion.metricas_cliente`. Se compara con su asignación persistida en `segmentacion.segmento_cliente`; sin asignación pertenece a **Sin segmento**.
- Las asignaciones sin métricas se cuentan en `asignacionesSinMetricas` y se excluyen de ambos lados de la comparación. Los clientes de otros servicios todavía no recibidos en segmentación no se inventan ni se contabilizan como cero compras.
- Escenario: criterios activos actuales más el alta propuesta, o sustitución del criterio editado por su borrador. Un criterio inactivo conserva ese estado; su fila aparece con cero clientes simulados y la pantalla informa que no participa.
- Se reutiliza `ReglaPrioridadSegmento.elegir` de SCRUM-26: valor antes que frecuencia, frecuencia antes que categoría, categoría ordenada por consumo y, en empate, menor ID. No se duplican las reglas de pertenencia de SCRUM-25.
- Cada cliente tiene un solo segmento estimado. `diferencia = simulado - actual`; ambos totales suman `clientesEvaluados`. `clientesQueCambiarian` cuenta cambios de identidad del segmento, no cambios en el nombre de un mismo criterio.
- Las claves `criterio:<id>`, `legado:<nombre>` y `sin-segmento` distinguen grupos incluso si comparten nombre visible. El alta usa un ID hipotético `Long.MAX_VALUE` para respetar la prioridad por antigüedad sin consumir la secuencia de la base. Las claves del resultado son opacas para el frontend.
- La transacción `readOnly` con aislamiento `REPEATABLE_READ` mantiene una instantánea coherente de reglas, métricas y asignaciones. No invoca el asignador, los repositorios de escritura ni las auditorías de negocio. No bloquea la configuración para escritura.

Jira declara SCRUM-28 como antecedente funcional. El simulador obtiene la distribución desde las tablas que ya están en la base de `main`, y reutiliza directamente SCRUM-25 y SCRUM-26. No incorpora código de otras ramas ni depende de una pantalla de listado de clientes para calcular el impacto.

## Contrato HTTP

`POST /api/segmentacion/criterios/simular`, a través del Gateway y con `Authorization: Bearer <token>`:

```json
{
  "regla": {
    "nombre": "Alto valor",
    "descripcion": "Vista previa",
    "indicador": "VALOR",
    "condicion": "ENTRE",
    "minimo": 500,
    "maximo": 2000
  }
}
```

Para editar se añade `criterioId` en la raíz y `version` dentro de `regla`, con la versión obtenida al cargar el criterio. Los indicadores y condiciones son los mismos del contrato de [criterios](criterios.md).

Respuesta `200`: `clientesEvaluados`, `asignacionesSinMetricas`, `clientesQueCambiarian`, `reglaActiva`, `calculadoEn`, `metricasMasAntiguas`, `metricasMasRecientes`, `duracionMs` y `distribucion`. Cada fila contiene `clave`, `segmento`, `propuesta`, `actual`, `simulado` y `diferencia`. Sin población se devuelve una distribución de ceros y fechas de métricas nulas.

| Estado | Motivo y comportamiento |
| --- | --- |
| 400 | Campos incompletos, valores negativos, frecuencias decimales, rango inverso, indicador o condición inválidos. Se señala el problema. |
| 401 / 403 | Sin autenticación, rol distinto de Administrador o falta del permiso de configuración. El Gateway valida JWT y reemplaza las cabeceras de identidad. |
| 404 | Se intenta editar un criterio inexistente. |
| 409 | Nombre duplicado, superposición, versión obsoleta o métricas inconsistentes. No se devuelve una estimación parcial. |
| 503 | Límite de población, tiempo de cálculo excedido o fallo de lectura de datos. La UI permite reintentar. |

Los errores usan el `ApiError` existente y su propiedad `mensaje`. Los logs registran conteos y duración, o el tipo de fallo, sin identificadores ni datos personales de clientes. Los puertos internos mantienen el mismo modelo de confianza del resto del CRM; el navegador consume el Gateway.

## Límites y operación

`SIMULACION_MAX_CLIENTES` vale 100000 por defecto (entre 1 y 1000000). `SIMULACION_MAX_SEGUNDOS` vale 10 (entre 1 y 60). Se leen hasta límite + 1 filas en lotes de 500 para detectar exceso sin cargar toda la población en memoria. El cálculo y las consultas de población tienen controles de tiempo; la transacción tiene un techo de 65 segundos y el frontend también abandona la espera a los 65 segundos. Las consultas previas de validación comparten el techo transaccional. No se presenta un resultado incompleto como válido.

La estimación representa las métricas disponibles en esa instantánea; cambios posteriores en datos o criterios pueden modificar el resultado efectivo. La pantalla muestra el intervalo de fechas de las métricas y el guardado siempre vuelve a validar.

## Cobertura de las nueve subtareas

| Subtarea | Entrega |
| --- | --- |
| SCRUM-281 | Endpoint en `CriterioController`, solicitud y resultado tipados. |
| SCRUM-282 | Flujo, contrato, cálculo, errores y límites descritos en este documento. |
| SCRUM-283 | Acción Simular impacto dentro del formulario existente, estado de carga y cancelación. |
| SCRUM-284 | `SimulacionSegmentacion`, lectura de métricas y distribución sin persistencia. |
| SCRUM-285 | Totales, tabla comparativa, diferencia, segmentos vacíos y fechas de métricas. |
| SCRUM-286 | `CriterioService.prepararSimulacion`, validaciones reutilizadas y control de versión. |
| SCRUM-287 | Respuestas controladas, logs, límites de filas/tiempo y reintento en UI. |
| SCRUM-288 | Pruebas de integración PostgreSQL, fallos unitarios, seguridad Gateway y navegador. Ver [verificación](simulacion-verificacion.md). |
| SCRUM-577 | [Clases y secuencia](simulacion-diagramas.md), incluyendo guardado y aplicación posterior. |

Los diagramas de clases y secuencia en SVG se adjuntaron a SCRUM-29 el 18 de octubre de 2026. La revisión por un par y el merge son pasos de entrega posteriores; tener la implementación y las pruebas locales no acredita por sí solo esos pasos del DoD.

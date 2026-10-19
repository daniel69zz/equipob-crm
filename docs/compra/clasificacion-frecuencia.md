# Clasificación del cliente por frecuencia de compra (SCRUM-38)

Cada cliente queda clasificado como **ocasional**, **habitual** o **frecuente** según su frecuencia de
compra, para verlo en la ficha integral y usarlo como indicador en los criterios de segmentación
(RF-30, EPIC-06 Frecuencia y Valor de Compra). Diagramas en
`docs/compra/clasificacion-frecuencia-diagramas.md`.

## Reglas (SCRUM-341)

- **Base:** la frecuencia de compra de SCRUM-37 (`docs/compra/frecuencia-compra.md`): compras vigentes
  acumuladas de todos los identificadores del cliente. Una compra anulada por completo no cuenta
  (SCRUM-523); una devolución parcial deja la compra vigente. No se recalcula la frecuencia: se
  reutiliza (SCRUM-337).
- **Umbrales** (compras vigentes, límites inclusivos):

| Clasificación | Frecuencia | Configuración |
|---|---|---|
| Sin clasificación | 0 | — |
| `OCASIONAL` | 1 a 3 | — |
| `HABITUAL` | 4 a 9 | desde `CLASIFICACION_FRECUENCIA_UMBRAL_HABITUAL` (4) |
| `FRECUENTE` | 10 o más | desde `CLASIFICACION_FRECUENCIA_UMBRAL_FRECUENTE` (10) |

  Un valor exactamente en un umbral pertenece a la clasificación superior (4 es habitual, 10 es
  frecuente). Son los mismos cortes de los ejemplos de criterios de SCRUM-25. El umbral habitual
  debe ser mayor que 1 y menor que el frecuente; si no, el servicio no arranca.
- **Sin compras vigentes:** "sin clasificación" (`sinClasificacion = true`), no un error
  (criterio de aceptación 3).
- **Actualización:** la clasificación se calcula con la frecuencia vigente en el momento de la
  consulta y viaja en las métricas que se publican con cada compra o anulación (SCRUM-26), así que una
  compra nueva o una anulación la actualizan sin pasos manuales (criterio de aceptación 2).

## Dónde se usa

### Ficha integral (SCRUM-338, SCRUM-583)

`GET /api/comportamiento/clientes/{id}/indicadores` incluye el bloque `clasificacionFrecuencia`, y la
ficha integral (`GET /api/perfil/clientes/{id}/ficha-integral`) lo muestra junto con la frecuencia que
la determina. Si el servicio de Comportamiento no responde, la ficha carga igual con el bloque vacío
(CA2 de SCRUM-10).

```json
"clasificacionFrecuencia": { "clasificacion": "HABITUAL", "frecuencia": 5, "sinClasificacion": false }
```

### Criterios de segmentación (SCRUM-340)

Las métricas que Comportamiento publica para la segmentación (`metricas.actualizadas`, SCRUM-26) llevan
`clasificacionFrecuencia`, que Segmentación guarda en `segmentacion.metricas_cliente`. El Administrador
de CRM puede crear criterios con el indicador **Clasificación por frecuencia**
(`CLASIFICACION_FRECUENCIA`), condición `IGUAL` y una clasificación (`OCASIONAL`, `HABITUAL` o
`FRECUENTE`):

```json
{"nombre": "Clientes frecuentes", "indicador": "CLASIFICACION_FRECUENCIA", "condicion": "IGUAL", "clasificacion": "FRECUENTE"}
```

- Dos criterios activos con la misma clasificación se superponen y se rechazan, como en SCRUM-25.
- **Prioridad** (SCRUM-260): valor, frecuencia, **clasificación por frecuencia** y categoría. La
  clasificación es un resumen de la frecuencia, por eso va justo después de ella.
- La asignación automática (SCRUM-26), la comprobación de un caso y la simulación (SCRUM-29) evalúan el
  nuevo indicador. Un cliente cuyas métricas se guardaron antes de esta historia no tiene clasificación
  hasta su próxima compra o anulación y no cumple estos criterios.

## Pruebas (SCRUM-339)

- `CriterioClasificacionFrecuenciaTest`: límites 0, 1, 3, 4, 9, 10 y más, y umbrales inválidos.
- `ClasificacionFrecuenciaIntegracionTest` (PostgreSQL real): compras nuevas, anulación total y
  devolución parcial cambian la clasificación; un cliente sin compras queda sin clasificación.
- Segmentación: criterios del nuevo indicador (alta, superposición, comprobación), prioridad,
  asignación automática y simulación.
- Perfil y frontend: bloque de la ficha integral, también cuando Comportamiento no responde.

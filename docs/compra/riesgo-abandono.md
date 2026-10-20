# Riesgo de abandono del cliente (SCRUM-35)

Indicador de riesgo de abandono **bajo, medio o alto** de cada cliente, calculado a partir de la
recencia, la frecuencia y el valor de sus compras, para que el Gerente Comercial priorice las acciones
de retención (RF-27, EPIC-05 Análisis del Comportamiento). Junta los indicadores del Sprint 1 y las
detecciones de cambio de comportamiento del Sprint 2, por eso es la última historia del plan.
Diagramas en `docs/compra/riesgo-abandono-diagramas.md`.

## Fórmula y ponderaciones (SCRUM-324)

El riesgo se calcula por **cliente del CRM** (perfil), sumando las compras vigentes de todos sus
identificadores en Marketplace y Ventas, igual que los indicadores de la ficha integral. Cada factor
aporta puntos hasta su peso; el puntaje total va de 0 a 100.

| Factor | De dónde sale | Puntos | Máximo |
|---|---|---|---|
| Recencia | Días desde la última compra vigente (SCRUM-19) frente al umbral de inactividad (SCRUM-31, 90 días) | `50 × min(días / 90, 1)` | 50 |
| Frecuencia | Clasificación por frecuencia (SCRUM-38) de las compras vigentes (SCRUM-37) | Ocasional 25 · Habitual 12,5 · Frecuente 0 | 25 |
| Valor | Nivel de consumo (SCRUM-27) del valor acumulado vigente (SCRUM-33) | Bajo 25 · Medio 12,5 · Alto 0 | 25 |
| Cambio de comportamiento | Disminución de la frecuencia o del monto detectada en los últimos 90 días (SCRUM-34) | 15 si la hay, 0 si no | 15 |

```
puntaje = min(100, recencia + frecuencia + valor + cambio)
```

Cada factor se redondea a dos decimales antes de sumar. Solo una **disminución** suma puntos: un
aumento de la frecuencia o del monto no es una señal de abandono.

### Niveles

| Nivel | Puntaje |
|---|---|
| `BAJO` | menor que 35 |
| `MEDIO` | desde 35 y menor que 65 |
| `ALTO` | desde 65 |

- Un puntaje exactamente en un umbral pertenece al nivel superior (35 es `MEDIO`, 65 es `ALTO`).
- **Regla de inactividad:** un cliente **inactivo** (su última compra vigente es de hace más de 90
  días, el mismo criterio de SCRUM-31) queda siempre en `ALTO`, aunque haya sido frecuente y de alto
  valor: ya dejó de comprar, que es lo que se quiere evitar.
- Un cliente **sin compras vigentes** (nunca compró o se le anularon todas) no tiene riesgo
  calculado: no hay indicadores que evaluar (criterio de aceptación 1). Tampoco lo tiene una compra
  sin perfil vinculado.

### Ejemplos

Con los valores por defecto (los mismos casos de las pruebas de SCRUM-323):

| Cliente | Última compra | Compras | Valor | Caída detectada | Recencia | Frec. | Valor | Cambio | Puntaje | Nivel |
|---|---|---|---|---|---|---|---|---|---|---|
| Activo | hace 3 días | 12 (frecuente) | 6.000 (alto) | no | 1,67 | 0 | 0 | 0 | 1,67 | `BAJO` |
| En descenso | hace 50 días | 5 (habitual) | 1.500 (medio) | no | 27,78 | 12,5 | 12,5 | 0 | 52,78 | `MEDIO` |
| En descenso, con caída detectada | hace 50 días | 5 (habitual) | 1.500 (medio) | sí | 27,78 | 12,5 | 12,5 | 15 | 67,78 | `ALTO` |
| Inactivo | hace 120 días | 2 (ocasional) | 300 (bajo) | no | 50 | 25 | 25 | 0 | 100 | `ALTO` |
| Inactivo, antes frecuente y de alto valor | hace 120 días | 12 (frecuente) | 6.000 (alto) | no | 50 | 0 | 0 | 0 | 50 | `ALTO` (regla de inactividad) |

### Por qué estos pesos

- **La recencia pesa la mitad:** es la señal más directa de abandono y la que cambia sola con el
  paso del tiempo, sin esperar nuevas compras. Se mide contra el umbral de inactividad para que el
  riesgo sea máximo justo cuando el cliente pasa a figurar como inactivo.
- **Frecuencia y valor pesan lo mismo:** describen el vínculo del cliente con el negocio. Reutilizan
  las clasificaciones que ya existen (SCRUM-38 y SCRUM-27) para no definir cortes nuevos.
- **El cambio de comportamiento es una penalización y no un peso:** solo existe cuando hay historial
  suficiente para compararlo (SCRUM-34); un cliente nuevo no queda castigado por no tenerlo.

> **Pendiente de validación:** pesos, penalización y umbrales son una propuesta. La definición de
> terminado exige que el Product Owner los valide. Se pueden cambiar sin recompilar (ver
> Configuración).

## Cuándo se recalcula (SCRUM-322)

| Momento | Qué pasa |
|---|---|
| Llega una compra o se anula o devuelve una | Después de confirmar la transacción, `RecalculoRiesgoPorCompra` recalcula al cliente con sus compras vigentes (criterio de aceptación 2). Si falla, la compra ya quedó registrada y el riesgo se corrige en la ejecución programada. |
| Ejecución programada | `RiesgoAbandonoScheduler` recalcula a **todos** los clientes con compras vigentes, por defecto a las 04:30, después de la detección de cambios de comportamiento (04:00). Así la recencia avanza cada día y una detección nueva se refleja en el riesgo (criterio de aceptación 2). También aplica un cambio de umbrales. |

Los clientes que se quedan sin compras vigentes se quitan del cálculo.

## Seguimiento de los clientes en riesgo alto (SCRUM-326)

- Cuando un cliente **entra** en `ALTO` se guarda desde cuándo (`enSeguimientoDesde`) y se deja una
  línea en el log del servicio. Mientras siga en `ALTO` conserva esa fecha; si baja de nivel, se borra.
- El listado `GET /api/comportamiento/clientes?riesgoAbandono=ALTO` devuelve los clientes de un nivel,
  del puntaje más alto al más bajo: es la lista de trabajo de retención (criterio de aceptación 3).

## Persistencia (SCRUM-571)

Tabla `comportamiento.riesgo_abandono_cliente` (migración `V13__riesgo_abandono.sql`): una fila por
cliente del CRM con compras vigentes, con el puntaje, el nivel, los datos de entrada (días sin
comprar, última compra, compras, valor acumulado, clasificación por frecuencia, nivel de consumo,
caída detectada, inactividad), los puntos de cada factor, `en_seguimiento_desde` y `calculado_en`.
Se sobrescribe en cada recálculo. Guardar los puntos de cada factor permite explicar el resultado
sin recalcularlo.

## Consultas

### Riesgo de un cliente (SCRUM-325)

```
GET /api/comportamiento/clientes/{clienteId}/riesgo-abandono
```

`{clienteId}` es el número de cliente del CRM.

```json
{
  "clienteId": 42,
  "nivel": "ALTO",
  "puntaje": 67.78,
  "inactivo": false,
  "enSeguimientoDesde": "2026-10-20T08:30:00Z",
  "calculadoEn": "2026-10-20T08:30:00Z",
  "factores": [
    { "factor": "RECENCIA", "puntos": 27.78, "maximo": 50, "descripcion": "50 días sin comprar (se considera inactivo después de 90)" },
    { "factor": "FRECUENCIA", "puntos": 12.50, "maximo": 25, "descripcion": "Habitual: 5 compras vigentes" },
    { "factor": "VALOR", "puntos": 12.50, "maximo": 25, "descripcion": "Nivel de consumo medio: Bs 1500.00 acumulados" },
    { "factor": "CAMBIO_COMPORTAMIENTO", "puntos": 15, "maximo": 15, "descripcion": "Bajó su frecuencia o su monto de compra en los últimos 90 días" }
  ],
  "sinDatos": false
}
```

Sin riesgo calculado responde `sinDatos: true`, sin error.

### Clientes por nivel de riesgo (SCRUM-326)

```
GET /api/comportamiento/clientes?riesgoAbandono=ALTO&pagina=0&tamanio=20
```

Paginado (por defecto 20, máximo 100), del puntaje más alto al más bajo y, a igual puntaje, por
número de cliente. `riesgoAbandono` acepta `BAJO`, `MEDIO` o `ALTO`; otro valor responde 400. Como
los listados de inactivos y de nivel de consumo, usa un parámetro y no un segmento de ruta propio
(`docs/seguridad/convencion-rutas-clientes.md`).

```json
{
  "umbrales": { "medio": 35, "alto": 65 },
  "content": [
    { "clienteId": 42, "nivel": "ALTO", "puntaje": 100.00, "diasSinComprar": 120,
      "ultimaCompra": "2026-06-22T12:00:00Z", "inactivo": true, "cambioDetectado": false,
      "enSeguimientoDesde": "2026-10-20T08:30:00Z", "calculadoEn": "2026-10-20T08:30:00Z" }
  ],
  "pagina": 0,
  "tamanio": 20,
  "total": 1
}
```

Ambas rutas quedan bajo `GET /api/comportamiento/**`, así que el Gateway exige
`INDICADORES_CONSULTAR` y audita el acceso, sin cambios de permisos.

### Ficha integral (SCRUM-325, SCRUM-572)

`GET /api/perfil/clientes/{clienteId}/ficha-integral` suma el bloque `riesgoAbandono`, consultado a
servicio-comportamiento. Llega con `sinDatos: true` cuando el cliente no tiene identificadores de
origen, no tiene riesgo calculado o el servicio no responde; el resto de la ficha carga igual. La
pantalla de la ficha muestra el nivel, el puntaje, desde cuándo está en seguimiento y cada factor
con sus puntos y su explicación (criterio de aceptación 4). La ven los roles con acceso a la ficha
integral (`FICHA_INTEGRAL_CONSULTAR`).

## Configuración

| Variable | Por defecto | Uso |
|---|---|---|
| `RIESGO_ABANDONO_PESO_RECENCIA` | 50 | Puntos máximos de la recencia |
| `RIESGO_ABANDONO_PESO_FRECUENCIA` | 25 | Puntos máximos de la frecuencia |
| `RIESGO_ABANDONO_PESO_VALOR` | 25 | Puntos máximos del valor |
| `RIESGO_ABANDONO_PENALIZACION_CAMBIO` | 15 | Puntos por una disminución detectada |
| `RIESGO_ABANDONO_UMBRAL_MEDIO` | 35 | Puntaje desde el que el riesgo es medio |
| `RIESGO_ABANDONO_UMBRAL_ALTO` | 65 | Puntaje desde el que el riesgo es alto |
| `RIESGO_ABANDONO_CRON` | `0 30 4 * * *` | Recálculo de todos los clientes |

Los tres pesos deben sumar 100, la penalización estar entre 0 y 100, y los umbrales cumplir
`0 < medio < alto ≤ 100`; si no, el servicio no arranca. El umbral de inactividad, las
clasificaciones por frecuencia y por nivel de consumo y la ventana de los cambios de comportamiento
son los de sus propias historias (`INACTIVIDAD_UMBRAL_DIAS`, `CLASIFICACION_FRECUENCIA_UMBRAL_*`,
`NIVEL_CONSUMO_UMBRAL_*`, `CAMBIO_COMPORTAMIENTO_VENTANA_DIAS`).

## Pruebas (SCRUM-323)

- `CriterioRiesgoAbandonoTest`: la fórmula con los casos de la tabla de ejemplos, los valores límite
  en cada umbral, la regla de inactividad y la configuración inválida.
- `RiesgoAbandonoClienteTest`: entrada y salida del seguimiento de riesgo alto.
- `RiesgoAbandonoIntegracionTest` (PostgreSQL real): historiales simulados de un cliente activo, uno
  en descenso y uno inactivo; una caída detectada que sube el riesgo; una compra nueva que lo baja;
  un cliente al que se le anula todo; la consulta del riesgo con sus factores y el listado por nivel.
- `RiesgoAbandonoDelClienteTest` y `FichaIntegralIntegracionTest` (perfil): el bloque de la ficha
  integral, también cuando comportamiento no responde.

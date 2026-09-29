# Idempotencia de los eventos de venta

Cómo evita el CRM procesar dos veces un mismo evento de venta. RabbitMQ entrega **al menos una vez** y Marketplace y Ventas puede reenviar un evento (por un reintento, una caída o un reenvío manual), así que el CRM debe tolerar recibir la misma transacción varias veces.

## Clave de idempotencia

| Parte | Campo del evento | Por qué |
|---|---|---|
| Tipo de evento | `tipoEvento` | Una compra y su anulación (RIO-CRM-05) son hechos distintos, aunque se refieran a la misma compra |
| Canal | `origen` | Marketplace y Ventas numeran sus transacciones por separado: `V-100` de Ventas y `V-100` de Marketplace son compras distintas |
| Transacción | `compra.idCompra` en una compra; `anulacion.idAnulacion` en una anulación o devolución | Identifica la transacción en el sistema de origen. Una anulación usa su propio identificador, no el de la compra, porque una compra puede tener varias devoluciones parciales |

**Clave = `tipoEvento` + `origen` + identificador de la transacción** (`COMPRA_CONFIRMADA` + `VENTAS` + `V-100234`, o `COMPRA_ANULADA` + `VENTAS` + `V-100234-D1`).

No se usa `idEvento` como clave: identifica el **mensaje**, no la compra. Un reenvío desde el sistema de origen puede llegar con un `idEvento` nuevo para la misma compra, y debe descartarse igual. Una reentrega de RabbitMQ trae el mismo `idEvento` y la misma compra, así que también queda cubierta por la clave.

## Dónde se guarda

Tabla `comportamiento.evento_procesado`, con la clave como llave primaria:

| Columna | Contenido |
|---|---|
| `tipo_evento`, `origen`, `id_transaccion` | Clave de idempotencia |
| `id_evento_recibido` | Mensaje de la bitácora que la procesó |
| `procesado_en` | Fecha y hora del procesamiento |

La fila se inserta **en la misma transacción** que la compra (o la anulación) y sus ítems: o se guardan las dos cosas, o ninguna.

## Criterio de descarte

1. El mensaje se anota en la bitácora (siempre, incluso si es un duplicado).
2. Se lee y se valida (reglas de SCRUM-21). Un evento inválido queda `FALLIDO` y **no** ocupa la clave.
3. Si la clave ya está en `evento_procesado`, el mensaje queda **`DESCARTADO`** y no se toca el historial.
4. Si no está, se guardan la compra (o la anulación) y la clave. Si en ese instante otro mensaje con la misma clave se adelantó (dos copias procesándose a la vez), la llave primaria lo impide y el mensaje también queda `DESCARTADO`.

Solo cuentan como procesadas las transacciones que terminaron bien. Si el primer envío quedó `FALLIDO`, un reenvío posterior **sí** se procesa: es justamente la forma de recuperarse del error.

## Traza del descarte

Cada descarte queda en la bitácora de ingesta (`comportamiento.evento_recibido`) con:

| Dato | Columna |
|---|---|
| Identificador de la transacción | `id_transaccion` |
| Identificador del mensaje | `id_evento_origen` |
| Origen | `origen` |
| Fecha del descarte | `procesado_en` |
| Qué evento la había procesado antes | `causa` (por ejemplo: `La compra VENTAS/V-100234 ya fue registrada por el evento 1`) |

La consulta de la bitácora (`GET /api/comportamiento/eventos`) muestra cuántos eventos se descartaron en el periodo (`resumen.DESCARTADO`) y permite buscar todos los mensajes de una transacción con `?transaccion=V-100234`.

## Ventana de retención

**Sin vencimiento.** La clave de una compra procesada se conserva mientras exista la compra en el historial: el historial de compras del CRM no se purga, y un reenvío tardío (por ejemplo, una reconciliación de fin de mes de Marketplace y Ventas) debe seguir reconociéndose. El costo es bajo: una fila pequeña por compra.

Si en el futuro se definiera una política de depuración del historial, la clave de idempotencia debe borrarse **junto con** su compra, nunca antes.

## Fuera de esta historia

- No acreditar puntos dos veces por una compra reentregada: se resuelve con la acumulación de puntos (SCRUM-45), que solo actúa sobre compras recién registradas.
- No volver a segmentar al cliente por un reenvío: se resuelve con la asignación automática de segmento (SCRUM-26), por la misma razón.

Como los reenvíos se descartan antes de registrar la compra, esas historias solo deben reaccionar a compras **nuevas** para heredar la protección.

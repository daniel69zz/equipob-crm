# RIO-CRM-06 · Saldo de puntos actualizado

SCRUM-594 / SCRUM-616. El emisor es `servicio-fidelizacion` del CRM y el receptor es
Marketplace y Ventas. Transporte exclusivo de salida: Google Cloud Pub/Sub; tópico
`ucb-sis323-sub-b`, suscripción del curso `ucb-sis323-sub-b-sub`, atributo
`ruta=puntos.saldo.actualizado`. No requiere una respuesta del receptor.

## Mensaje JSON UTF-8

```json
{
  "idEvento": "fe3a3cc1-9315-4c07-8fba-11d9dbcd1060",
  "tipoEvento": "SALDO_PUNTOS_ACTUALIZADO",
  "fechaEmision": "2026-11-08T20:00:00Z",
  "idCliente": "CLI-5521",
  "idTransaccion": "V-100234",
  "tipoMovimiento": "ACUMULACION",
  "puntos": 35,
  "variacionSaldo": 35,
  "saldo": 85,
  "nivel": null,
  "secuencia": 123
}
```

- `idEvento`: UUID único del CRM, estable en todos los reintentos. También atributo del mensaje.
- `tipoEvento`: siempre `SALDO_PUNTOS_ACTUALIZADO`.
- `fechaEmision`: fecha ISO-8601 con zona de la creación del evento en la transacción local;
  no cambia si la entrega se retrasa.
- `idCliente`: identificador de Marketplace y Ventas, nunca el ID interno del CRM.
- `idTransaccion`: identificador del movimiento; en ajustes empieza por `AJUSTE-`, en caducidad
  es el identificador generado por el CRM. Es nulo únicamente para `RECALCULO_NIVEL`.
- `tipoMovimiento`: `ACUMULACION`, `REDENCION`, `REVERSA`, `CADUCIDAD`, `AJUSTE` o
  `RECALCULO_NIVEL` (cambio de nivel por vencimiento del período o cambio de umbrales).
- `puntos`: entero no negativo del movimiento, valor absoluto para ajustes; cero en recálculos.
- `variacionSaldo`: entero con signo del efecto real. En una reversa excluye puntos no recuperados;
  cero cuando solo cambia el nivel.
- `saldo`: entero no negativo, instantánea del saldo confirmado para ese movimiento.
- `nivel`: `BRONCE`, `PLATA`, `ORO` o nulo cuando el cliente no alcanza un nivel.
- `secuencia`: entero creciente de la bandeja, con posibles huecos. Comparar por cliente,
  no interpretar como un conteo de sus movimientos.

Atributos adicionales: `contentType=application/json`. El `messageId` de Pub/Sub identifica una
publicación, pero **no** sustituye a `idEvento` para deduplicar. Los consumidores deben tolerar campos nuevos.

## Confirmación, entrega e idempotencia

El evento se inserta en `fidelizacion.salida_saldo_puntos` después de calcular el saldo y el nivel,
en la misma transacción del movimiento. Un rollback elimina también la salida. El control existente
de movimientos duplicados impide una segunda salida. También se notifica un movimiento válido
de cero puntos. El recálculo general solo genera salida si cambia el nivel.

La publicación se ejecuta en otro momento, con lectura de filas confirmadas y bloqueo
`FOR UPDATE SKIP LOCKED`. Cada fila se procesa en una transacción separada, con espera acotada
de Pub/Sub. Se guarda el ID del mensaje al recibir la confirmación del broker. Un fallo conserva
la fila pendiente, incrementa `intentos` y programa un reintento exponencial desde 5 segundos,
con máximo de una hora y sin descartar el evento por número de fallos.

Solo se toma el primer pendiente de cada cliente: un fallo bloquea las salidas posteriores de ese
cliente, no las de otros clientes. La entrega es **al menos una vez**: una caída después de publicar
y antes de confirmar el estado en PostgreSQL puede repetir el mensaje con el mismo `idEvento`.
El receptor deduplica por ese UUID y evita sustituir un estado más reciente con una `secuencia` menor.

Para ajustes, caducidades originadas por ajustes y recálculos, puede faltar el identificador de
Marketplace en el historial. El despachador consulta el perfil fuera de la transacción del movimiento.
Solo publica si encuentra un identificador único; si no existe, hay varios o Perfil está caído,
mantiene la salida pendiente con su error. La primera resolución exitosa queda persistida para los
reintentos. No publica el ID del CRM como sustituto ni escoge arbitrariamente entre vínculos.

Configuración, pruebas y operación: [publicación de saldo](../fidelizacion/publicacion-saldo-puntos.md).
Diagramas: [clases y secuencia](../fidelizacion/diagramas/publicacion-saldo-puntos.md).

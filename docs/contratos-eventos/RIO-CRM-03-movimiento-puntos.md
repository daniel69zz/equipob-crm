# RIO-CRM-03 · Movimiento de puntos

Evento que el módulo **Marketplace y Ventas** del ERP publica por cada movimiento de puntos de
fidelización de un cliente: la **acumulación** por una transacción, la **redención** de puntos y la
**reversa** por anulación. En una acumulación el CRM calcula los puntos a partir del valor consumido;
en una redención Marketplace y Ventas informa los puntos redimidos. El CRM lleva el saldo.

| | |
|---|---|
| **Emisor** | Módulo Marketplace y Ventas del ERP |
| **Receptor** | `servicio-fidelizacion` |
| **Transporte** | RabbitMQ, mensaje JSON en UTF-8 (o Google Cloud Pub/Sub, `docs/mensajeria/pubsub.md`) |
| **Exchange** | `ventas.eventos` (tipo `topic`, durable) |
| **Routing key** | `puntos.movimiento` |
| **Cola del CRM** | `crm.fidelizacion.movimientos` (durable), con respaldo `crm.fidelizacion.movimientos.respaldo` |

## Campos

| Campo | Tipo | Obligatorio | Descripción |
|---|---|:---:|---|
| `idEvento` | texto (UUID, máx. 64) | ✅ | Identificador único del mensaje, asignado por el emisor |
| `tipoEvento` | texto | ✅ | Siempre `MOVIMIENTO_PUNTOS` |
| `fechaEmision` | fecha y hora ISO-8601 con zona | ✅ | Momento en que se publicó el evento |
| `movimiento.idTransaccion` | texto (máx. 64) | ✅ | **Identificador de la transacción** en Marketplace y Ventas (por ejemplo, la compra). Junto con el tipo, identifica el movimiento de forma única |
| `movimiento.idCliente` | texto (máx. 64) | ✅ | Identificador del cliente en Marketplace y Ventas; el CRM lo vincula a su perfil |
| `movimiento.tipoMovimiento` | texto | ✅ | `ACUMULACION`, `REDENCION` o `REVERSA` |
| `movimiento.valorConsumido` | decimal (12,2) | ✅ / — | Valor consumido en la transacción, en bolivianos; mayor que cero. Obligatorio en `ACUMULACION` y `REVERSA`; en `REDENCION` es opcional e informativo (valor del beneficio canjeado) |
| `movimiento.puntos` | entero (máx. 12 dígitos) | ✅ en `REDENCION` | Puntos redimidos; mayor que cero y sin decimales. En `ACUMULACION` se ignora: los puntos los calcula el CRM |
| `movimiento.fecha` | fecha y hora ISO-8601 con zona | ✅ | Momento de la transacción |

Los campos desconocidos se ignoran. El CRM procesa los tipos `ACUMULACION` (SCRUM-45) y `REDENCION`
(SCRUM-48); la reversa por anulación corresponde a SCRUM-524. Mientras tanto, un movimiento `REVERSA`
queda `FALLIDO` con la causa y se puede reprocesar cuando esté disponible.

## Ejemplos

Acumulación:

```json
{
  "idEvento": "7c1e2d3f-4a5b-4c6d-8e9f-0a1b2c3d4e5f",
  "tipoEvento": "MOVIMIENTO_PUNTOS",
  "fechaEmision": "2026-10-18T10:15:05-04:00",
  "movimiento": {
    "idTransaccion": "V-100234",
    "idCliente": "CLI-5521",
    "tipoMovimiento": "ACUMULACION",
    "valorConsumido": 350.50,
    "fecha": "2026-10-18T10:14:50-04:00"
  }
}
```

Redención de 20 puntos:

```json
{
  "idEvento": "4d5e6f7a-8b9c-4d0e-a1f2-3b4c5d6e7f8a",
  "tipoEvento": "MOVIMIENTO_PUNTOS",
  "fechaEmision": "2026-10-18T13:00:00-04:00",
  "movimiento": {
    "idTransaccion": "R-5001",
    "idCliente": "CLI-5521",
    "tipoMovimiento": "REDENCION",
    "puntos": 20,
    "valorConsumido": 20.00,
    "fecha": "2026-10-18T12:59:40-04:00"
  }
}
```

Más ejemplos, incluidos casos de error, en `herramientas/simulador-eventos/eventos/movimiento-*.json`.

## Qué hace el CRM al recibirlo

| Situación | Resultado en la bitácora de movimientos |
|---|---|
| Acumulación correcta | `PROCESADO`: se calculan los puntos, se suman al saldo y queda el detalle por transacción |
| Redención con saldo suficiente | `PROCESADO`: se descuentan los puntos del saldo y queda el movimiento con su transacción de origen |
| La transacción ya acreditó o descontó puntos (mismo `idTransaccion` y tipo) | `DESCARTADO`: no se aplica dos veces |
| Redención mayor al saldo disponible | `FALLIDO`: queda la causa con el saldo disponible; el saldo no cambia |
| JSON ilegible, campo faltante o inválido, tipo aún no soportado, cliente sin perfil en el CRM, o Perfil no disponible | `FALLIDO`: queda la causa y el mensaje original, disponible para reproceso; el saldo no cambia |

Cada mensaje recibido queda en la bitácora con su contenido original. Detalle de la regla y del
procesamiento en `docs/fidelizacion/acumulacion-puntos.md` y `docs/fidelizacion/redencion-puntos.md`.

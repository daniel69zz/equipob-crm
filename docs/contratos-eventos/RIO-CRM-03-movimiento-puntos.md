# RIO-CRM-03 · Movimiento de puntos

Evento que el módulo **Marketplace y Ventas** del ERP publica por cada movimiento de puntos de
fidelización de un cliente: la **acumulación** por una transacción, la **redención** de puntos y la
**reversa** por anulación. El CRM calcula los puntos a partir del valor consumido y lleva el saldo.

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
| `movimiento.valorConsumido` | decimal (12,2) | ✅ | Valor consumido en la transacción, en bolivianos; mayor que cero |
| `movimiento.fecha` | fecha y hora ISO-8601 con zona | ✅ | Momento de la transacción |

Los campos desconocidos se ignoran. Por ahora el CRM procesa el tipo `ACUMULACION` (SCRUM-45); la
redención corresponde a SCRUM-48 y la reversa por anulación a SCRUM-524. Mientras tanto, un movimiento
de esos tipos queda `FALLIDO` con la causa y se puede reprocesar cuando estén disponibles.

## Ejemplo

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

Más ejemplos, incluidos casos de error, en `herramientas/simulador-eventos/eventos/movimiento-*.json`.

## Qué hace el CRM al recibirlo

| Situación | Resultado en la bitácora de movimientos |
|---|---|
| Acumulación correcta | `PROCESADO`: se calculan los puntos, se suman al saldo y queda el detalle por transacción |
| La transacción ya acreditó puntos (mismo `idTransaccion` y tipo) | `DESCARTADO`: no se acredita dos veces |
| JSON ilegible, campo faltante o inválido, tipo aún no soportado, cliente sin perfil en el CRM, o Perfil no disponible | `FALLIDO`: queda la causa y el mensaje original, disponible para reproceso; el saldo no cambia |

Cada mensaje recibido queda en la bitácora con su contenido original. Detalle de la regla y del
procesamiento en `docs/fidelizacion/acumulacion-puntos.md`.

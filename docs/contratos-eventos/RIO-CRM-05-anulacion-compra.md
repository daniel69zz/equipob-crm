# RIO-CRM-05 · Anulación o devolución de compra

Evento que el módulo **Marketplace y Ventas** del ERP publica cuando una compra confirmada se **anula por completo** o se **devuelve en parte**. El CRM lo usa para que el historial y los indicadores del cliente reflejen solo las compras vigentes.

| | |
|---|---|
| **Emisor** | Módulo Marketplace y Ventas del ERP |
| **Receptor** | `servicio-comportamiento` |
| **Transporte** | RabbitMQ, mensaje JSON en UTF-8 |
| **Exchange** | `ventas.eventos` (tipo `topic`, durable), el mismo de RIO-CRM-02 |
| **Routing key** | `compra.anulada` |
| **Cola del CRM** | `crm.comportamiento.anulaciones` (durable) |
| **Cola de fallidos** | `crm.comportamiento.anulaciones.respaldo` |

## Anulación total y devolución parcial

| `tipo` | Qué significa | Qué se revierte |
|---|---|---|
| `TOTAL` | La compra se anula entera | Todo el monto que seguía vigente. `items` no se usa |
| `PARCIAL` | El cliente devuelve parte de lo que compró | Solo los ítems de `items`. Una compra puede tener varias devoluciones parciales |

Cada anulación o devolución tiene **su propio identificador** (`idAnulacion`) y apunta a la compra original con `idCompra`.

## Campos

| Campo | Tipo | Obligatorio | Descripción |
|---|---|:---:|---|
| `idEvento` | texto (UUID, máx. 64) | ✅ | Identificador único del mensaje, asignado por el emisor |
| `tipoEvento` | texto | ✅ | Siempre `COMPRA_ANULADA` |
| `fechaEmision` | fecha y hora ISO-8601 con zona | ✅ | Momento en que se publicó el evento |
| `anulacion.idAnulacion` | texto (máx. 64) | ✅ | **Identificador de la anulación o devolución** en Marketplace y Ventas. La identifica de forma única |
| `anulacion.idCompra` | texto (máx. 64) | ✅ | Identificador de la compra original (el `compra.idCompra` de RIO-CRM-02) |
| `anulacion.idCliente` | texto (máx. 64) | ✅ | Identificador del cliente en Marketplace y Ventas. Debe ser el mismo de la compra original |
| `anulacion.tipo` | `TOTAL` \| `PARCIAL` | ✅ | Anulación total o devolución parcial |
| `anulacion.fecha` | fecha y hora ISO-8601 con zona | ✅ | Momento de la anulación o devolución |
| `anulacion.montoRevertido` | decimal (12,2) | ✅ | Monto que se revierte, en bolivianos |
| `anulacion.motivo` | texto (máx. 300) | ✅ | Motivo informado por Marketplace y Ventas |
| `anulacion.items` | lista (mín. 1) | Solo en `PARCIAL` | Ítems devueltos, por categoría |
| `anulacion.items[].categoria` | texto (máx. 100) | ✅ | Categoría del ítem devuelto, con el mismo nombre que en la compra |
| `anulacion.items[].cantidad` | entero | ✅ | Unidades devueltas |
| `anulacion.items[].monto` | decimal (12,2) | ✅ | Monto devuelto de ese ítem |

Los campos desconocidos se ignoran: el emisor puede agregar campos sin romper al CRM.

## Reglas de validación

Son las mismas de RIO-CRM-02 (SCRUM-21) aplicadas a este contrato. Un evento que no las cumple queda `FALLIDO` con el campo y la regla incumplida:

- `idEvento` con formato UUID; textos dentro de su largo máximo.
- `montoRevertido` y cada `items[].monto` mayores que cero y con formato decimal (12,2); cada `items[].cantidad` mayor que cero.
- En una devolución `PARCIAL`, al menos un ítem, y la suma de `items[].monto` igual a `montoRevertido`.

Además, el CRM la compara con la compra registrada. Si no cuadra, el evento queda `FALLIDO` con la causa:

| Regla | Ejemplo de causa |
|---|---|
| La compra original debe estar registrada | `La compra V-100234 no está registrada` |
| El cliente debe ser el de la compra | `El cliente CLI-9999 no es el de la compra V-100234 (CLI-5521)` |
| La anulación no puede ser anterior a la compra | `La anulación (2026-09-20T10:00-04:00) no puede ser anterior a la compra (2026-09-27T15:28:10-04:00)` |
| No se anula una compra ya anulada por completo | `La compra V-100234 ya está anulada` |
| Una anulación `TOTAL` revierte exactamente lo que sigue vigente | `La anulación total debe revertir 300.00, lo que sigue vigente de la compra V-100234` |
| Una devolución `PARCIAL` no supera lo que sigue vigente | `La devolución (400.00) supera lo que sigue vigente de la compra V-100234 (350.50)` |
| Cada ítem devuelto existe en la compra | `La compra V-100234 no tiene ítems de la categoría 'Hogar'` |
| No se devuelven más unidades ni más monto de los comprados en una categoría | `Se devuelven 3 unidades de 'Accesorios', pero solo quedan 2 de la compra` |

Una anulación que llega **antes** que su compra queda `FALLIDO`. Cuando la compra llegue, se reprocesa desde la bitácora y se registra.

## Ejemplos

Devolución parcial:

```json
{
  "idEvento": "8d2f6a4c-1b3e-4c5d-9e7f-0a1b2c3d4e5f",
  "tipoEvento": "COMPRA_ANULADA",
  "fechaEmision": "2026-09-28T11:05:00-04:00",
  "anulacion": {
    "idAnulacion": "V-100234-D1",
    "idCompra": "V-100234",
    "idCliente": "CLI-5521",
    "tipo": "PARCIAL",
    "fecha": "2026-09-28T11:02:30-04:00",
    "montoRevertido": 50.50,
    "motivo": "Accesorios con falla de fábrica",
    "items": [
      { "categoria": "Accesorios", "cantidad": 2, "monto": 50.50 }
    ]
  }
}
```

Anulación total del resto de la misma compra:

```json
{
  "idEvento": "3a9c7e1f-5d2b-4f8a-b6c4-7e8f9a0b1c2d",
  "tipoEvento": "COMPRA_ANULADA",
  "fechaEmision": "2026-09-29T09:15:00-04:00",
  "anulacion": {
    "idAnulacion": "V-100234-A1",
    "idCompra": "V-100234",
    "idCliente": "CLI-5521",
    "tipo": "TOTAL",
    "fecha": "2026-09-29T09:12:00-04:00",
    "montoRevertido": 300.00,
    "motivo": "El cliente desistió de la compra"
  }
}
```

Más ejemplos en `herramientas/simulador-eventos/eventos/` (archivos `anulacion-*.json`).

## Qué hace el CRM al recibirlo

| Situación | Resultado en la bitácora de ingesta |
|---|---|
| Evento correcto | `PROCESADO`: la anulación queda registrada con la compra original, la fecha, el monto revertido, el motivo y sus ítems. La compra pasa a `DEVOLUCION_PARCIAL` o, si no queda nada vigente, a `ANULADA` |
| La anulación (`idAnulacion`) ya estaba registrada | `DESCARTADO`: no tiene efectos; queda la causa (ver `docs/ingesta/idempotencia-eventos-venta.md`) |
| JSON ilegible, regla incumplida o error al guardar | `FALLIDO`: queda la causa y el mensaje original, disponible para reproceso |
| Ni siquiera se puede anotar en la bitácora (por ejemplo, la base no responde) | Se reintenta y, agotados los reintentos, el mensaje pasa a `crm.comportamiento.anulaciones.respaldo`. Se reinyecta con `POST /api/comportamiento/eventos/respaldo/reinyectar?cola=anulaciones` |

El historial de compras conserva la compra original con su monto total. El monto vigente de una compra es `montoTotal − montoRevertido`.

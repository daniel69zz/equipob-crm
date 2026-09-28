# RIO-CRM-02 · Compra confirmada

Evento que Marketplace y Ventas publican cada vez que se confirma una compra. El CRM lo usa para construir el historial de compras y los indicadores de comportamiento del cliente.

| | |
|---|---|
| **Emisor** | Marketplace y Ventas (canal `MARKETPLACE` o `VENTAS`) |
| **Receptor** | `servicio-comportamiento` |
| **Transporte** | RabbitMQ, mensaje JSON en UTF-8 |
| **Exchange** | `ventas.eventos` (tipo `topic`, durable) |
| **Routing key** | `compra.confirmada` |
| **Cola del CRM** | `crm.comportamiento.compras` (durable) |

## Campos

| Campo | Tipo | Obligatorio | Descripción |
|---|---|:---:|---|
| `idEvento` | texto (UUID, máx. 64) | ✅ | Identificador único del mensaje, asignado por el emisor |
| `tipoEvento` | texto | ✅ | Siempre `COMPRA_CONFIRMADA` |
| `origen` | `MARKETPLACE` \| `VENTAS` | ✅ | Canal donde se hizo la compra |
| `fechaEmision` | fecha y hora ISO-8601 con zona | ✅ | Momento en que se publicó el evento |
| `compra.idCompra` | texto (máx. 64) | ✅ | **Identificador de la transacción** en el sistema de origen. Junto con `origen`, identifica la compra de forma única |
| `compra.idCliente` | texto (máx. 64) | ✅ | Identificador del cliente en el sistema de origen. El CRM lo vincula a su perfil mediante los identificadores de origen del servicio de Perfil |
| `compra.fecha` | fecha y hora ISO-8601 con zona | ✅ | Momento en que se confirmó la compra |
| `compra.montoTotal` | decimal (12,2) | ✅ | Monto total de la compra, en bolivianos |
| `compra.items` | lista (mín. 1) | ✅ | Detalle de la compra por categoría |
| `compra.items[].categoria` | texto (máx. 100) | ✅ | Nombre de la categoría del producto, tal como está en el catálogo al momento de la compra |
| `compra.items[].cantidad` | entero | ✅ | Unidades compradas |
| `compra.items[].monto` | decimal (12,2) | ✅ | Monto del ítem (cantidad × precio unitario) |

Notas:

- El evento **no transporta identificadores de producto**, solo categorías (ver `ACTUALIZACION_RF_CRM_V3`, observación sobre RF-35). El CRM guarda el nombre de la categoría tal como llegó, para que un cambio posterior en el catálogo no altere el historial.
- Los campos desconocidos se ignoran: el emisor puede agregar campos sin romper al CRM.
- Las reglas de validación de contenido (montos positivos, suma de ítems, formatos por canal) son de la historia **SCRUM-21**.

## Ejemplo

```json
{
  "idEvento": "5b7a8c1e-3f2d-4e6a-9b1c-2d3e4f5a6b7c",
  "tipoEvento": "COMPRA_CONFIRMADA",
  "origen": "VENTAS",
  "fechaEmision": "2026-09-27T15:30:05-04:00",
  "compra": {
    "idCompra": "V-100234",
    "idCliente": "CLI-5521",
    "fecha": "2026-09-27T15:28:10-04:00",
    "montoTotal": 350.50,
    "items": [
      { "categoria": "Electrónica", "cantidad": 1, "monto": 300.00 },
      { "categoria": "Accesorios", "cantidad": 2, "monto": 50.50 }
    ]
  }
}
```

Más ejemplos, de los dos canales y de casos de error, en `herramientas/simulador-eventos/eventos/`.

## Qué hace el CRM al recibirlo

| Situación | Resultado en la bitácora de ingesta |
|---|---|
| Evento correcto | `PROCESADO`: la compra y sus ítems quedan guardados |
| La compra (`origen` + `idCompra`) ya estaba registrada | `DESCARTADO`: no se duplica; queda la causa (ver `docs/ingesta/idempotencia-eventos-venta.md`) |
| JSON ilegible, campo obligatorio faltante o error al guardar | `FALLIDO`: queda la causa y el mensaje original, disponible para reproceso |

Cada mensaje recibido queda en la bitácora con su contenido original, aunque no se haya podido leer.

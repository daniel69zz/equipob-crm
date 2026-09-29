# Formato de la respuesta del historial de compras (SCRUM-197)

Contrato final de `GET /api/comportamiento/clientes/{clienteId}/compras` (ver diseño y ruta en
`docs/compra/historial-compras.md`). Fija los campos que pide SCRUM-197 — fecha, productos,
importe, estado y referencia de compra — para que la consulta sea consistente sin
importar quién la use (agente, y en el futuro un portal de autoservicio, ver SCRUM-193).

## Campos de cada compra

| Campo | Tipo | Origen | Descripción |
|---|---|---|---|
| `fecha` | `OffsetDateTime` (ISO-8601 con zona) | `compra.fecha` | Fecha y hora de la compra en Marketplace y Ventas. Es el campo de orden (de la más reciente a la más antigua) |
| `referencia` | `String` | `compra.idCompraOrigen` | Identificador de la compra en Marketplace y Ventas (por ejemplo `V-100234`, `MP-88120`); permite ubicarla en ese módulo ante un reclamo |
| `montoTotal` | `BigDecimal` | `compra.montoTotal` | Importe original de la compra, sin descontar anulaciones ni devoluciones |
| `estado` | `CONFIRMADA` \| `DEVOLUCION_PARCIAL` \| `ANULADA` | `compra.estado` | Estado vigente, actualizado por las anulaciones y devoluciones (RIO-CRM-05) |
| `items` | Lista de `{ categoria, cantidad, monto }` | `compra.items` | Productos de la compra, agrupados por categoría (no hay detalle a nivel de SKU en el modelo actual) |

No se incluye `montoRevertido` ni el detalle de cada anulación: el criterio de aceptación 1 pide
fecha, monto y productos, y `estado` ya comunica si la compra está vigente, parcialmente
devuelta o anulada. Si en el futuro se necesita el detalle de las anulaciones, es un campo aparte
a agregar a `CompraResponse`, no un cambio de formato.

## Forma de la página

```json
{
  "content": [
    {
      "fecha": "2026-09-27T16:02:12-04:00",
      "referencia": "MP-88120",
      "montoTotal": 129.90,
      "estado": "CONFIRMADA",
      "items": [
        { "categoria": "Hogar", "cantidad": 1, "monto": 89.90 },
        { "categoria": "Limpieza", "cantidad": 4, "monto": 40.00 }
      ]
    },
    {
      "fecha": "2026-09-27T15:28:10-04:00",
      "referencia": "V-100234",
      "montoTotal": 350.50,
      "estado": "CONFIRMADA",
      "items": [
        { "categoria": "Electrónica", "cantidad": 1, "monto": 300.00 },
        { "categoria": "Accesorios", "cantidad": 2, "monto": 50.50 }
      ]
    }
  ],
  "pagina": 0,
  "tamanio": 20,
  "total": 2
}
```

Un cliente sin identificadores o sin compras registradas recibe la misma forma con `content: []`
y `total: 0` — no un error (criterio de aceptación 2).

## Dónde se usa

- Backend: `HistorialComprasController.CompraResponse` / `ItemResponse` en `servicio-comportamiento`.
- Frontend: `Compra` / `ItemCompra` en `frontend/crm-web/src/app/features/clientes/compras.service.ts`,
  mostrado por `historial-compras.component.ts` (fecha, referencia y estado como
  encabezado de cada compra; ítems en una tabla por categoría, cantidad e importe).

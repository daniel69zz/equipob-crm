# Categorías más consumidas por cliente (SCRUM-18)

Lógica con la que el CRM ordena las categorías de producto que un cliente consume más, para orientar campañas y recomendaciones (RF-10, EPIC-02). Parte del historial descrito en `docs/compra/historial-compras.md` y comparte con el ticket promedio la regla de qué compras cuentan (`docs/compra/ticket-promedio.md`).

## Qué se calcula

Por cada categoría en la que el cliente tiene compras vigentes:

| Campo | Significado |
|---|---|
| `categoria` | Nombre de la categoría tal como llegó en el evento, sin espacios a los lados. No se unifican mayúsculas ni tildes: `Hogar` y `hogar` son categorías distintas |
| `compras` | En cuántas compras distintas aparece la categoría (frecuencia) |
| `unidades` | Unidades vigentes de esa categoría |
| `monto` | Monto vigente acumulado de esa categoría |
| `sinCategoria` | Verdadero solo para el grupo «Sin categoría» (ver más abajo) |

## Qué entra

- Cuentan las compras `CONFIRMADA` y `DEVOLUCION_PARCIAL`. Una compra `ANULADA` no aporta nada, ni siquiera lo que se había devuelto antes por partes.
- De una compra con devolución parcial se descuenta, **por categoría**, lo devuelto: `unidades = comprado - devuelto` y `monto = comprado - devuelto`. Si de una categoría no queda nada (ni unidades ni monto), esa compra deja de contar para esa categoría, aunque la compra siga vigente por otras.
- La suma de los montos de todas las categorías coincide con el monto vigente acumulado que usa el ticket promedio.
- El cálculo abarca todo el historial: no hay ventana de tiempo.

## Criterio de ranking

El orden va de mayor a menor consumo:

1. **Monto** vigente, de mayor a menor. Es el criterio principal: mide cuánto gasta el cliente en cada categoría.
2. **Compras** (frecuencia), de mayor a menor, si dos categorías tienen el mismo monto.
3. **Nombre** de la categoría, en orden alfabético sin distinguir mayúsculas, para que el orden sea siempre el mismo ante un empate total.

La cantidad de compras y de unidades se devuelve junto al monto para que quien consulta pueda leer también la frecuencia; el orden, en cambio, es único y no cambia según cómo se muestre.

## Compras sin categoría

La ingesta rechaza los eventos con una categoría vacía (`docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md`) y la base lo impide (`compra_item_categoria_no_vacia`): un evento así queda `FALLIDO` en la bitácora de ingesta con su causa, visible para corregirlo y reprocesarlo, y no llega al historial. Aun así el cálculo no depende de eso: si un ítem llegara con la categoría nula o en blanco, se agrupa como **«Sin categoría»** (`sinCategoria: true`), aparece en el ranking como cualquier otra y no interrumpe el cálculo.

## Cuándo se actualiza

Igual que el ticket promedio, el ranking **se calcula al consultarlo** sobre `compra`, `compra_item` y `anulacion_item`; no se guarda en otra tabla. Una compra nueva, una devolución o una anulación se reflejan en la consulta siguiente y nunca queda un resultado desfasado. Si más adelante el volumen lo exigiera, el único punto que habría que alimentar es `AgregadoCategoriasCliente`.

## Consulta

```
GET /api/comportamiento/clientes/{clienteId}/categorias
      ?identificador=MARKETPLACE:mp-user-3307
      &identificador=VENTAS:CLI-5521
      &limite=5
```

Sigue la misma composición que el historial y el ticket promedio: el frontend obtiene `identificadoresOrigen` del perfil y los pasa como `identificador` repetible; `{clienteId}` solo identifica al cliente para la auditoría del Gateway. El permiso es `INDICADORES_CONSULTAR`. `limite` es opcional: sin él se devuelven todas las categorías; con él, las primeras del ranking. Un `limite` menor que 1 se rechaza con `400`.

```json
{
  "categorias": [
    { "categoria": "Electrónica", "compras": 1, "unidades": 1, "monto": 300.00, "sinCategoria": false },
    { "categoria": "Hogar", "compras": 1, "unidades": 1, "monto": 89.90, "sinCategoria": false },
    { "categoria": "Accesorios", "compras": 1, "unidades": 2, "monto": 50.50, "sinCategoria": false },
    { "categoria": "Limpieza", "compras": 1, "unidades": 4, "monto": 40.00, "sinCategoria": false }
  ]
}
```

Un cliente sin compras vigentes recibe `{ "categorias": [] }`, no un error.

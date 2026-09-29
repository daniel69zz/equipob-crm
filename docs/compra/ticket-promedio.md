# Ticket promedio del cliente (SCRUM-17)

Regla de cálculo del ticket promedio que usa el Analista Comercial para ver cuánto gasta un cliente por compra (RF-09, EPIC-02). El historial que lo alimenta está descrito en `docs/compra/historial-compras.md`.

## Definición

```
ticket promedio = suma de lo vigente de cada compra ÷ cantidad de compras vigentes
```

El resultado se redondea a 2 decimales (`HALF_UP`), igual que los montos del contrato de eventos.

## Qué compras entran

| Compra | ¿Entra? | Monto que aporta |
|---|---|---|
| `CONFIRMADA` | Sí | `montoTotal` |
| `DEVOLUCION_PARCIAL` | Sí | `montoTotal - montoRevertido`: lo que el cliente sigue pagando |
| `ANULADA` | No | No cuenta ni como compra ni como monto. Una compra anulada no fue un gasto del cliente |

En términos de datos, una compra entra cuando lo vigente (`monto_total - monto_revertido`) es mayor que cero. Es lo mismo que excluir las `ANULADA`, pero no depende del texto del estado.

## Casos límite

- **Sin compras vigentes** (cliente sin compras, o con todas anuladas): no hay ticket promedio. La respuesta trae `valor: null` y `sinDatos: true`; no es un error ni un cero, porque un ticket de `0` se confundiría con un cliente que compró y no gastó nada.
- **Una sola compra:** el ticket es el monto vigente de esa compra.
- **Monto cero:** el historial no admite compras con `monto_total` menor o igual que cero (restricción `compra_monto_positivo` y validación del evento). Lo único que llega a cero es lo vigente de una compra anulada, y esa ya queda fuera.
- **Devolución:** una devolución parcial baja el monto vigente pero la compra sigue contando una vez; una anulación total la saca del cálculo.
- **Compras `PENDIENTE` de vinculación:** entran por su identificador de origen, igual que en el historial, porque la consulta es por los pares `ORIGEN:idCliente` del perfil.

## Cuándo se actualiza

El ticket **se calcula al consultarlo** sobre `comportamiento.compra`; no se guarda en otra tabla. Así una compra nueva, una devolución o una anulación se reflejan en la siguiente consulta sin un proceso de recálculo que pueda quedar desfasado, y el valor no depende de que la compra ya esté vinculada al perfil. La consulta usa el índice `idx_compra_cliente (origen, id_cliente_origen, fecha DESC)`.

Si más adelante el volumen exige guardarlo, la agregación (`AgregadoComprasCliente`) es el único punto que habría que alimentar.

## Agregación reutilizable

`AgregadoComprasCliente.resumir(identificadores)` devuelve la cantidad de compras vigentes y su monto vigente acumulado. Es la base común del ticket promedio y del valor acumulado del cliente (SCRUM-33): cada indicador solo aplica su propia fórmula sobre ese resumen.

## Consulta

```
GET /api/comportamiento/clientes/{clienteId}/indicadores
      ?identificador=MARKETPLACE:mp-user-3307
      &identificador=VENTAS:CLI-5521
```

Sigue la misma composición que el historial: el frontend obtiene `identificadoresOrigen` del perfil y los pasa como `identificador` repetible. `{clienteId}` solo identifica al cliente para la auditoría del Gateway. El permiso es `INDICADORES_CONSULTAR`, el mismo del historial.

```json
{
  "ticketPromedio": {
    "valor": 240.25,
    "compras": 2,
    "montoAcumulado": 480.50,
    "sinDatos": false
  }
}
```

Sin compras vigentes:

```json
{
  "ticketPromedio": { "valor": null, "compras": 0, "montoAcumulado": 0, "sinDatos": true }
}
```

Ya se agregaron como campos hermanos de `ticketPromedio`: la recencia (`docs/compra/recencia-compra.md`, SCRUM-19) y el valor acumulado (`docs/compra/valor-acumulado.md`, SCRUM-33). La frecuencia de compra se agregará de la misma forma.

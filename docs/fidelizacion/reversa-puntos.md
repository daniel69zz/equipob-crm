# Reversa de puntos por compra anulada o devuelta (SCRUM-524)

Cuando Marketplace y Ventas anula una compra por completo o el cliente devuelve parte de ella, el CRM le
quita los puntos que esa compra le había dado, para que no conserve puntos de compras revertidas (RF-60,
EPIC-08). Es una historia de integración asíncrona: Marketplace y Ventas publica en RabbitMQ y no espera
respuesta del CRM.

## Regla de reversa (SCRUM-538)

Los puntos a revertir se recalculan con la **misma regla con la que se acumuló la compra** (la tasa y el
monto mínimo de su versión, SCRUM-46), no con la regla vigente, sobre el monto que sigue vigente:

```
vigente  = valor de la compra − montos ya revertidos − monto de esta reversa
revertir = puntos de la compra − puntos(vigente) − puntos ya revertidos        (nunca menos de 0)
```

| Caso | Compra de Ana: Bs 350.50, 1 punto por cada Bs 10 → 35 puntos |
|---|---|
| Anulación total | Vigente 0 → se revierten los 35 puntos (criterio 1) |
| Devolución parcial de Bs 50.50 | Vigente 300.00 → 30 puntos → se revierten 5 (criterio 2) |
| Anulación del resto (Bs 300.00) después de la devolución | Vigente 0 → se revierten los 30 que quedaban: en total 35 |
| Lo que queda no llega al monto mínimo de la regla | Se revierten todos los puntos que quedaban |
| La reversa supera lo que sigue vigente de la compra | `FALLIDO`: `La reversa A2 (100.00) supera lo que sigue vigente de la transacción V-100234 (50.50)` |

Recalcular sobre lo vigente, en lugar de multiplicar el monto devuelto por la tasa, evita que el redondeo
hacia abajo quite o deje puntos de más: la suma de todas las reversas de una compra nunca supera los
puntos que generó, y una anulación total siempre deja la compra en 0 puntos.

### Si el cliente ya redimió parte de esos puntos

La reversa **se aplica igual**: se descuentan los puntos que el saldo permita, el saldo queda en 0 (nunca
negativo) y la diferencia queda en el movimiento como `puntosNoRecuperados`, para auditoría. No se deja
`FALLIDO`, porque la compra ya está anulada y reprocesarla más tarde quitaría puntos de compras que sí
están vigentes.

| Saldo | Puntos a revertir | Se descuentan | Saldo final | `puntosNoRecuperados` |
|---|---|---|---|---|
| 35 | 35 | 35 | 0 | 0 |
| 15 (redimió 20) | 35 | 15 | 0 | 20 |

Para el nivel se restan siempre todos los puntos de la reversa (ver la sección del nivel): la compra
anulada deja de contar para el nivel, aunque sus puntos ya se hubieran usado.

# Valor acumulado del cliente (SCRUM-33)

Regla de cálculo del valor acumulado que usa el Analista Comercial para identificar a los
clientes más valiosos (RF-25, EPIC-05 Análisis del Comportamiento). Depende de HU-09/RF-07 y
reutiliza la misma agregación que el ticket promedio (HU-11, `docs/compra/ticket-promedio.md`).

## Definición (SCRUM-308)

```
valor acumulado = suma de lo vigente de cada compra del cliente
```

"Vigente" es exactamente lo mismo que usa el ticket promedio: `montoTotal - montoRevertido` de
cada compra (`CONFIRMADA` o `DEVOLUCION_PARCIAL`); una compra `ANULADA` no aporta nada. Ver la
tabla de qué compras entran en `docs/compra/ticket-promedio.md#qué-compras-entran` — es la misma
para los dos indicadores, porque ambos parten de `AgregadoComprasCliente.resumir` (SCRUM-202,
reutilizado tal como lo pide SCRUM-311).

## Diferencia con el ticket promedio sin compras

El ticket promedio no está definido sin compras vigentes (dividir entre cero), así que su `valor`
es `null`. Una suma sí está definida sin compras: es `0`. Por eso, sin compras vigentes,
`valorAcumulado.valor` es `0` (no `null`) y `sinDatos: true` solo avisa que ese `0` no viene de
compras reales, para que el frontend no lo confunda con "no se pudo calcular".

## Casos límite

- **Cliente con compras confirmadas:** el valor es la suma de sus montos totales (criterio de
  aceptación 1).
- **Una compra se anula:** al recalcular (la siguiente consulta), su monto deja de sumar
  (criterio de aceptación 2) — igual que el ticket promedio, no hace falta un proceso aparte.
- **Devolución parcial:** baja el valor acumulado en lo revertido, sin sacar la compra del conteo.
- **Cliente sin compras, o con todas anuladas:** valor `0`, `sinDatos: true`.

## Persistencia (SCRUM-312)

No se agrega una tabla nueva. Igual que el ticket promedio y la recencia (ver
"Cuándo se actualiza" en `docs/compra/ticket-promedio.md`), el valor acumulado **se calcula al
consultarlo** sobre `comportamiento.compra`, reutilizando la misma agregación
(`AgregadoComprasCliente.resumir`) que ya alimentaba al ticket promedio. Persistirlo sería
duplicar un dato que ya se obtiene con una sola consulta agregada y arriesgar que quede
desactualizado tras una anulación; "actualizar el indicador" (como dice el título de SCRUM-312)
ya ocurre en cada consulta, sin necesidad de guardarlo ni de un job de recálculo.

## Consulta (SCRUM-309)

Se agrega como un campo hermano más de `IndicadoresCliente`, sobre el mismo endpoint que ya usan
el ticket promedio y la recencia:

```
GET /api/comportamiento/clientes/{clienteId}/indicadores
      ?identificador=MARKETPLACE:mp-user-3307
      &identificador=VENTAS:CLI-5521
```

```json
{
  "ticketPromedio": { "valor": 240.25, "compras": 2, "montoAcumulado": 480.50, "sinDatos": false },
  "recencia": { "ultimaCompra": "2026-09-27T19:28:10Z", "tiempoTranscurrido": "PT49H30M", "sinDatos": false },
  "valorAcumulado": { "valor": 480.50, "compras": 2, "sinDatos": false }
}
```

Sin compras vigentes:

```json
{
  "valorAcumulado": { "valor": 0, "compras": 0, "sinDatos": true }
}
```

Mismo permiso (`INDICADORES_CONSULTAR`) y misma auditoría por `{clienteId}` que el resto de los
indicadores — criterio de aceptación 3 ("un usuario autorizado ve su valor acumulado" al consultar
el perfil del cliente en el frontend).

# Caducidad de puntos de fidelización (SCRUM-50)

Los puntos que un cliente no redime caducan al vencer su vigencia, para que el pasivo del programa de
fidelización no crezca sin control (RF-42, EPIC-08). Usa la acumulación (SCRUM-45), la redención (SCRUM-48),
la reversa (SCRUM-524) y los niveles (SCRUM-49). Diagramas en `docs/fidelizacion/caducidad-puntos-diagramas.md`.

## Regla de caducidad (SCRUM-418)

Es la misma regla que ya informa la consulta de Marketplace y Ventas (RIO-CRM-04,
`docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md`):

```
fechaCaducidad = fecha de la última ACUMULACION con puntos > 0 + vigenciaMeses
vencido        = fechaCaducidad <= momento evaluado
```

- **La vigencia es del saldo completo, no de cada compra.** Cada compra que acumula puntos renueva la
  vigencia de todo el saldo. Un cliente que compra al menos una vez por periodo nunca pierde sus puntos.
- **Una compra bajo el monto mínimo** (que no acumuló puntos) no renueva la vigencia.
- **Las redenciones y las reversas no renuevan la vigencia.**
- **`vigenciaMeses`** es de 12 meses por defecto (`FIDELIZACION_VIGENCIA_MESES`, entre 1 y 120). Pasará a la
  configuración general del CRM con SCRUM-65.
- La fecha se calcula en UTC, sumando meses de calendario. Si el día no existe en el mes final, se usa el
  último día de ese mes: una compra del 31 de enero con 1 mes de vigencia caduca el 28 o el 29 de febrero.
- **Al caducar se descuenta todo el saldo** que el cliente tenga en ese momento, y el saldo queda en 0.

### Movimiento de caducidad

Cada caducidad deja un movimiento `CADUCIDAD` en el historial del cliente, junto a las acumulaciones,
redenciones y reversas:

| Campo | Valor |
|---|---|
| `idTransaccion` | `CADUCIDAD-{clienteId}-{fechaCaducidad}`, por ejemplo `CADUCIDAD-42-20271018T141450Z` |
| `tipoMovimiento` | `CADUCIDAD` |
| `puntos` / `variacionSaldo` | Los puntos caducados / su valor negativo |
| `saldoResultante` | `0` |
| `fechaTransaccion` | La `fechaCaducidad`, el momento en que vencieron los puntos |
| `registradoEn` | El momento en que el CRM descontó los puntos |

No viene de un evento de Marketplace y Ventas: no tiene evento en la bitácora, ni valor en bolivianos, ni
regla de acumulación. Un evento RIO-CRM-03 con `tipoMovimiento: CADUCIDAD` se rechaza como inválido.

### Una sola vez por vencimiento (criterio de aceptación 3)

El identificador del movimiento depende solo del cliente y de la `fechaCaducidad`, y la clave primaria del
historial no admite dos movimientos con el mismo identificador. Si el proceso corre dos veces el mismo día (o
dos copias a la vez), la segunda encuentra el saldo en 0 o choca con la clave, y no descuenta nada. Si el
cliente vuelve a comprar, su nueva fecha de caducidad es otra, así que el vencimiento siguiente tiene su propio
movimiento.

### Puntos vigentes (criterio de aceptación 2)

Un cliente con `fechaCaducidad` posterior al momento evaluado no se toca: ni su saldo ni su historial
cambian. Tampoco se toca a un cliente con saldo 0.

### Caducidad antes de cada movimiento

El proceso automático corre una vez al día, así que un saldo puede estar vencido unas horas sin que se haya
descontado todavía. Para que esos puntos no se usen:

- **Consultas.** La consulta del Agente y del Administrador, la de la ficha integral y la de Marketplace y
  Ventas (RIO-CRM-04) informan el saldo vencido como `puntosCaducados` y el saldo disponible en 0. Las
  consultas no descuentan nada.
- **Movimientos.** Antes de aplicar una acumulación, una redención o una reversa, el CRM comprueba si el saldo
  del cliente ya estaba vencido **en la fecha del movimiento**, y si es así lo caduca en la misma transacción.
  Así:
  - una compra después del vencimiento no "revive" los puntos vencidos: solo quedan los suyos;
  - una redención fechada después del vencimiento queda `FALLIDO` por saldo insuficiente, y la causa indica
    que los puntos caducaron;
  - una redención hecha antes del vencimiento, aunque llegue tarde, se aplica con el saldo que tenía.
- **Reversa de una compra cuyos puntos ya caducaron.** Si después de la compra hubo una caducidad, sus puntos
  ya salieron del saldo. La reversa los registra como `puntosNoRecuperados` y no descuenta puntos de compras
  posteriores.

### Nivel

La caducidad no cambia los puntos del periodo de evaluación del nivel (SCRUM-49), porque esos puntos se
calculan con las compras del periodo y no con el saldo. Aun así, el nivel se recalcula con la caducidad. Si
cambia, el cambio queda en el historial de niveles con la caducidad como origen.

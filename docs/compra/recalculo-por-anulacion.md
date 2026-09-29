# Recálculo de indicadores por anulación (SCRUM-523)

Reglas con las que los indicadores del cliente excluyen el monto revertido cuando una compra se
anula o se devuelve en parte (RF-59, EPIC-02). El evento que las dispara es RIO-CRM-05
(`docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md`), que `ProcesadorAnulaciones` registra y
descuenta de la compra original.

## La compra no se borra

Una anulación nunca elimina la compra del historial. `ProcesadorAnulaciones` conserva el registro
original y solo acumula lo revertido:

| Dato | Efecto de la anulación |
|------|------------------------|
| `compra.monto_total` | No cambia: es el importe original |
| `compra.monto_revertido` | Suma el monto revertido |
| `compra.estado` | `DEVOLUCION_PARCIAL` si aún queda monto vigente; `ANULADA` si no queda nada |
| `comportamiento.anulacion` | Una fila por evento, con su fecha, tipo, monto, motivo y ítems devueltos |

Lo **vigente** de una compra es `monto_total - monto_revertido`. Todos los indicadores parten de
esa misma definición (`ComprasDelCliente.vigente`).

## Reglas de recálculo

| Caso | Compra | Ticket promedio | Valor acumulado | Frecuencia | Recencia |
|------|--------|-----------------|-----------------|------------|----------|
| **Devolución parcial** | Pasa a `DEVOLUCION_PARCIAL` | Su monto vigente baja; sigue contando como una compra | Baja en lo devuelto | No cambia | No cambia |
| **Anulación total** | Pasa a `ANULADA` | La compra sale del conteo y del monto | Deja de sumar su monto | Baja en 1 | Retrocede a la compra vigente anterior |
| **Devolución parcial y luego anulación total** | `ANULADA` | Igual que una anulación total | Igual que una anulación total | Baja en 1, una sola vez | Igual que una anulación total |
| **Se anula la única compra del cliente** | `ANULADA` | `sinDatos: true`, `valor: null` | `valor: 0`, `sinDatos: true` | `0` | `sinDatos: true` |

Una devolución parcial nunca cambia el conteo de compras: el cliente sigue habiendo comprado, solo
que por menos. La anulación total sobre una compra que ya tenía devoluciones parciales revierte lo
que seguía vigente (`exigirTotal`), no el monto original.

## Cómo se recalcula cada indicador

Los indicadores no se recalculan todos de la misma manera, según cómo se guardan:

- **Ticket promedio, recencia y valor acumulado** se calculan **al consultarlos** sobre
  `comportamiento.compra`, considerando solo las compras vigentes
  (`AgregadoComprasCliente`). No hay nada que actualizar: la consulta siguiente a una anulación ya
  excluye el monto revertido (`docs/compra/ticket-promedio.md`, `docs/compra/recencia-compra.md`,
  `docs/compra/valor-acumulado.md`).
- **Frecuencia** es un contador persistido en `comportamiento.frecuencia_compra`
  (`docs/compra/frecuencia-compra.md`) y sí hay que ajustarlo: cuando una compra queda `ANULADA`,
  `ProcesadorAnulaciones` llama a `FrecuenciaCompraRepository.decrementar`. La actualización es
  atómica y nunca baja de cero.
- **Listado de clientes inactivos** (`docs/compra/clientes-inactivos.md`) es también un dato
  persistido, derivado de la última compra vigente. Cuando una compra queda `ANULADA`,
  `DetectorClientesInactivos.reevaluar` vuelve a evaluar a ese cliente: si se quedó sin compras
  vigentes sale del listado, y si su última compra vigente es ahora anterior al umbral entra o
  actualiza su fecha. La ejecución programada sigue corrigiendo cualquier desfase.

Una devolución parcial no toca la frecuencia ni el listado de inactivos: la compra sigue vigente y
su fecha no cambia.

## Consistencia

Todo ocurre en la transacción que registra la anulación: la fila de `anulacion`, el descuento de
la compra, el contador de frecuencia y la reevaluación de inactividad se confirman o se revierten
juntos. Por eso:

- Una anulación rechazada (monto que no cuadra, compra inexistente) no cambia ningún indicador, y
  su reproceso posterior lo aplica una sola vez.
- Una anulación repetida se descarta por idempotencia antes de llegar al descuento, así que no
  baja la frecuencia dos veces.
- La compra se bloquea (`CompraRepository.bloquear`) mientras se procesa, de modo que dos
  anulaciones simultáneas de la misma compra se aplican una tras otra y solo la que la deja
  `ANULADA` descuenta la frecuencia.

## Fuera de alcance

La reevaluación del segmento del cliente tras una anulación pertenece a la historia de asignación
automática de segmento (backlog); esta historia solo deja los indicadores correctos para cuando se
implemente.

## Pruebas

`RecalculoPorAnulacionIntegracionTest` (PostgreSQL real) cubre los tres casos de la definición de
terminado: anulación de la única compra, de una entre varias y devolución parcial, comprobando los
cuatro indicadores por el endpoint `GET /api/comportamiento/clientes/{clienteId}/indicadores`.
Añade la devolución seguida de anulación total, la idempotencia, una anulación rechazada y el
listado de inactivos. `DetectorClientesInactivosTest` cubre `reevaluar` sin base de datos.

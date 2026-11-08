# Reversa de puntos por compra anulada o devuelta (SCRUM-524)

Cuando Marketplace y Ventas anula una compra por completo o el cliente devuelve parte de ella, el CRM le
quita los puntos que esa compra le había dado, para que no conserve puntos de compras revertidas (RF-60,
EPIC-08). Es una historia de integración asíncrona: Marketplace y Ventas publica en RabbitMQ y no espera
respuesta del CRM. Usa el mismo consumidor, la misma bitácora y el mismo reproceso que la acumulación
(`docs/fidelizacion/acumulacion-puntos.md`) y la redención (`docs/fidelizacion/redencion-puntos.md`).
Diagramas en `docs/fidelizacion/reversa-puntos-diagramas.md`.

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

## Eventos que revierten puntos (SCRUM-540)

La reversa llega por dos eventos, y los dos terminan en el mismo movimiento `REVERSA`:

| Evento | Cola de Fidelización | Transacción de la reversa | Compra que revierte | Monto |
|---|---|---|---|---|
| RIO-CRM-03 `REVERSA` (`docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md`) | `crm.fidelizacion.movimientos` | `movimiento.idTransaccion` | `movimiento.idTransaccionOriginal` | `movimiento.valorConsumido` |
| RIO-CRM-05 anulación o devolución (`docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md`) | `crm.fidelizacion.anulaciones` | `anulacion.idAnulacion` | `anulacion.idCompra` | `anulacion.montoRevertido` |

La clave del movimiento es `idTransaccion` + `REVERSA`. Si Marketplace y Ventas publica los dos eventos
de una misma anulación, el segundo queda `DESCARTADO`. Fidelización tiene su propia cola de RIO-CRM-05,
así que recibe su copia aparte de Comportamiento (historial e indicadores), y la bitácora guarda el
mensaje original para reprocesarlo.

## Procesamiento

1. El mensaje se anota en la bitácora de movimientos con su contenido original (transacción propia).
2. En otra transacción: se valida el evento y se vincula el cliente con su perfil del CRM.
3. Se busca la **acumulación de la compra** (`idTransaccion` de la compra + `ACUMULACION`) y se bloquea
   hasta el fin de la transacción: las reversas de una misma compra se aplican de a una, así que dos
   devoluciones simultáneas no pueden revertir entre las dos más de lo que sigue vigente.
4. Se **reserva** la reversa en el historial, con la referencia a la acumulación (SCRUM-539) y la regla
   de esa compra. Una reversa reentregada choca con la clave y queda `DESCARTADO` (criterio 3).
5. Se calculan los puntos con la regla de reversa (SCRUM-538) y se descuentan hasta donde alcance el
   saldo; se anota el saldo resultante, se reevalúa el nivel y el evento queda `PROCESADO`. Todo se
   guarda junto o no se guarda nada.

| Situación | Resultado |
|---|---|
| Reversa correcta | `PROCESADO`; el movimiento queda con su referencia a la compra (criterio 1) |
| La reversa ya se aplicó (otra copia, otro `idEvento` o el otro evento) | `DESCARTADO`: `La transacción V-100234-D1 ya revirtió sus puntos (REVERSA)` |
| La compra no tiene acumulación registrada | `FALLIDO`: `La transacción V-100234 no tiene una acumulación de puntos registrada; la reversa V-100234-D1 queda para reproceso` |
| El cliente no es el de la compra | `FALLIDO`: `El cliente CLI-9999 no es el de la transacción V-100234` |
| La reversa supera lo que sigue vigente de la compra (por ejemplo, la compra ya está anulada por completo) | `FALLIDO` con lo que sigue vigente |
| Falta `movimiento.idTransaccionOriginal`, el monto no es válido o el cliente no tiene perfil | `FALLIDO` con el campo y la regla |

Una reversa `FALLIDO` no cambia el saldo ni deja movimiento, y se puede reprocesar: si llegó antes que
la acumulación de su compra, se reprocesa después y se aplica.

| Método | Ruta | Permiso (Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/fidelizacion/clientes/{clienteId}/movimientos` | `PUNTOS_CONSULTAR` | Historial con las reversas y la compra que revierte cada una |
| GET | `/api/fidelizacion/eventos?estado=FALLIDO&tipo=REVERSA` | `PUNTOS_CONSULTAR` | Reversas fallidas, con su causa |
| POST | `/api/fidelizacion/eventos/{id}/reprocesar` | `FIDELIZACION_CONFIGURAR` | Vuelve a procesar un evento `FALLIDO` |

## Nivel tras la reversa (SCRUM-541, criterio de aceptación 4)

En la misma transacción de la reversa se recalcula el nivel con `AsignacionNiveles` (SCRUM-49). La
reversa resta **todos** sus puntos de los puntos del periodo, también los no recuperados: la compra
anulada deja de contar para el nivel. Cuenta con la fecha de la compra que revierte, así que sale del
periodo junto con ella. Si el cliente desciende (o cambia) de nivel, el cambio queda en
`fidelizacion.cambio_nivel` con la reversa como origen:

```
GET /api/fidelizacion/clientes/{clienteId}/cambios-nivel      (PUNTOS_CONSULTAR)
```

Una reversa `FALLIDO` o `DESCARTADO` no cambia el nivel.

## Historial de movimientos (SCRUM-539)

La reversa queda en `fidelizacion.movimiento_puntos` con la referencia a la acumulación que revierte
(`id_transaccion_revertida`, con clave foránea), el monto revertido, la regla de la compra, los puntos
de la reversa y los que no se pudieron descontar:

```json
{
  "idTransaccion": "V-100234-D1",
  "tipoMovimiento": "REVERSA",
  "idTransaccionRevertida": "V-100234",
  "idClienteOrigen": "CLI-5521",
  "valorConsumido": 50.50,
  "puntos": 5,
  "puntosNoRecuperados": 0,
  "variacionSaldo": -5,
  "bolivianosPorPunto": 10.00,
  "montoMinimo": 0.00,
  "versionRegla": 1,
  "saldoResultante": 30,
  "fechaTransaccion": "2026-10-20T15:02:30Z",
  "registradoEn": "2026-10-20T15:05:01Z"
}
```

`variacionSaldo` es lo que realmente se descontó: `−(puntos − puntosNoRecuperados)`.

## Prueba con eventos simulados

Con `servicio-fidelizacion` y `servicio-perfil` levantados y el perfil de Ana creado
(`cliente-ana-alta.json`):

```bash
cd herramientas/simulador-eventos
python3 publicar.py eventos/movimiento-acumulacion-ana.json      # saldo 35
python3 publicar.py eventos/movimiento-reversa-parcial.json      # devolución de Bs 50.50: 35 → 30
python3 publicar.py eventos/anulacion-parcial.json               # la misma devolución por RIO-CRM-05: DESCARTADO
python3 publicar.py eventos/movimiento-reversa-total.json        # anulación del resto: 30 → 0
```

`ReversaPuntosIntegracionTest` (PostgreSQL y RabbitMQ reales con Testcontainers) reproduce los cuatro
criterios con eventos RIO-CRM-03 y RIO-CRM-05: anulación total, devolución parcial, regla de la compra
original, reentrega, el mismo evento por las dos fuentes, copias y devoluciones simultáneas, compra ya
anulada, saldo insuficiente por puntos ya redimidos, nivel y su historial, compra fuera del periodo,
reproceso y filtro de la bitácora. `ReglaReversaTest` cubre la regla y el redondeo.

```bash
cd backend/servicio-fidelizacion
mvn test   # requiere Docker
```

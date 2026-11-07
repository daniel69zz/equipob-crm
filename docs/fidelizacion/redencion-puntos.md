# Redención de puntos de fidelización (SCRUM-48)

Cuando un cliente usa sus puntos en Marketplace y Ventas, ese módulo publica un movimiento de
**redención** (RIO-CRM-03, `docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md`) y el CRM descuenta
los puntos de su saldo, para que el saldo refleje los puntos que ya usó (RF-40, EPIC-08). Es una
historia de integración asíncrona: Marketplace y Ventas publica en RabbitMQ y no espera respuesta del
CRM. Usa el mismo consumidor, la misma bitácora y el mismo reproceso que la acumulación
(`docs/fidelizacion/acumulacion-puntos.md`). Diagramas en `docs/fidelizacion/redencion-puntos-diagramas.md`.

## Solicitud de redención (SCRUM-405)

Un movimiento `REDENCION` trae, además de los campos comunes del contrato:

| Campo | Regla |
|---|---|
| `movimiento.puntos` | **Obligatorio.** Puntos redimidos: entero mayor que cero, hasta 12 dígitos (`20` y `20.0` valen; `20.5`, `0` o `-5` no) |
| `movimiento.valorConsumido` | Opcional e informativo: valor en bolivianos del beneficio canjeado. Si viene, debe ser mayor que cero con hasta 2 decimales |

Los puntos los decide Marketplace y Ventas; el CRM no les aplica la tasa de acumulación.

## Procesamiento (SCRUM-407, SCRUM-404)

1. El mensaje se anota en la bitácora de movimientos con su contenido original (transacción propia).
2. En otra transacción: se valida el evento, se vincula el cliente con su perfil del CRM y se **reserva**
   la transacción en el historial (`idTransaccion` + `REDENCION`).
3. Se **descuentan los puntos solo si el saldo alcanza** (SCRUM-407). La condición y la resta van en una
   sola sentencia:

   ```sql
   UPDATE fidelizacion.saldo_puntos SET saldo = saldo - :puntos
   WHERE cliente_id = :cliente AND saldo >= :puntos
   RETURNING saldo
   ```

   Así, dos redenciones simultáneas del mismo cliente nunca dejan el saldo en negativo. La tabla además
   tiene `CHECK (saldo >= 0)`.
4. Se anota el saldo resultante en el movimiento, se reevalúa el nivel y el evento queda `PROCESADO`.
   Todo se guarda junto o no se guarda nada.

## Historial de movimientos (SCRUM-403)

La redención queda en `fidelizacion.movimiento_puntos` con su transacción de origen, los puntos
redimidos, el valor del beneficio (si vino) y el saldo resultante. No tiene tasa aplicada
(`bolivianosPorPunto` nulo). El detalle por transacción suma `variacionSaldo`: positiva en una
acumulación y negativa en una redención.

```
GET /api/fidelizacion/clientes/{clienteId}/movimientos      (PUNTOS_CONSULTAR)
```

```json
[
  {
    "idTransaccion": "R-5001",
    "tipoMovimiento": "REDENCION",
    "idClienteOrigen": "CLI-5521",
    "valorConsumido": 20.00,
    "puntos": 20,
    "variacionSaldo": -20,
    "bolivianosPorPunto": null,
    "saldoResultante": 15,
    "fechaTransaccion": "2026-10-18T16:59:40Z",
    "registradoEn": "2026-10-18T17:00:01Z"
  },
  {
    "idTransaccion": "V-100234",
    "tipoMovimiento": "ACUMULACION",
    "puntos": 35,
    "variacionSaldo": 35,
    "saldoResultante": 35
  }
]
```

## Saldo insuficiente y errores (SCRUM-408, criterio de aceptación 2)

| Situación | Resultado |
|---|---|
| La redención pide más puntos que el saldo | `FALLIDO` con la causa: `Saldo insuficiente para la redención R-5002: se solicitan 500 puntos y el saldo disponible es 35`. El saldo y el nivel no cambian |
| El cliente todavía no tiene saldo | `FALLIDO`, con saldo disponible 0 |
| Falta `movimiento.puntos` o no es un entero positivo | `FALLIDO` con el campo y la regla |
| Cliente sin perfil en el CRM o Perfil no responde | `FALLIDO`, igual que en la acumulación |

Una redención `FALLIDO` no deja movimiento en el historial y **se puede reprocesar**. Por ejemplo, si
llegó antes que la acumulación que le daba saldo, se reprocesa después y se aplica:

| Método | Ruta | Permiso (Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/fidelizacion/eventos?estado=FALLIDO&tipo=REDENCION` | `PUNTOS_CONSULTAR` | Redenciones fallidas, con su causa |
| POST | `/api/fidelizacion/eventos/{id}/reprocesar` | `FIDELIZACION_CONFIGURAR` | Vuelve a procesar un evento `FALLIDO` |

## Idempotencia (criterio de aceptación 3)

La clave `idTransaccion` + `tipoMovimiento` del historial detiene una segunda aplicación: un evento
reentregado, o la misma redención con otro `idEvento`, queda `DESCARTADO` con la causa
`La transacción R-5001 ya descontó sus puntos (REDENCION)` y el saldo no se descuenta dos veces, aunque
las copias se procesen a la vez. Una acumulación y una redención con el mismo `idTransaccion` son
movimientos distintos.

## Nivel (criterio de aceptación 4)

Después de cada cambio de saldo, en la misma transacción, el procesador pide a `ReevaluacionNivel` el
nivel que corresponde al nuevo saldo y lo guarda en `fidelizacion.saldo_puntos.nivel`. Si al saldo no le
corresponde ningún nivel, el cliente queda sin nivel. Una redención fallida no cambia el nivel.

Los niveles bronce, plata y oro con sus umbrales son **SCRUM-49**: esa historia implementa
`ReevaluacionNivel` como un bean de Spring. Mientras no exista, el nivel no se toca. Las pruebas usan
niveles de prueba (plata desde 30 puntos, bronce desde 1) para verificar el criterio.

## Confirmación de la redención (SCRUM-406)

Marketplace y Ventas no espera respuesta al publicar. Para confirmar al cliente que la redención se
aplicó, consulta por la transacción de origen:

```
GET /api/fidelizacion/redenciones/{idTransaccion}      (PUNTOS_CONSULTAR)
```

| `estado` | Significado | Campos |
|---|---|---|
| `APLICADA` | Los puntos se descontaron | `puntos`, `saldoResultante`, `nivel` (actual), `fechaTransaccion`, `registradaEn`, `idEvento` |
| `FALLIDA` | No se aplicó; se puede reprocesar | `causa`, `idEvento` (para el reproceso) |
| `EN_PROCESO` | Recibida y todavía sin resultado | `idEvento` |

Responde `404` si nunca llegó una redención con esa transacción.

```json
{
  "idTransaccion": "R-5001",
  "estado": "APLICADA",
  "causa": null,
  "idClienteOrigen": "CLI-5521",
  "puntos": 20,
  "saldoResultante": 15,
  "nivel": null,
  "fechaTransaccion": "2026-10-18T16:59:40Z",
  "registradaEn": "2026-10-18T17:00:01Z",
  "idEvento": 2
}
```

La publicación asíncrona del saldo hacia Marketplace y Ventas es SCRUM-594.

## Prueba con eventos simulados

Con `servicio-fidelizacion` y `servicio-perfil` levantados y el perfil de Ana creado
(`cliente-ana-alta.json`):

```bash
cd herramientas/simulador-eventos
python3 publicar.py eventos/movimiento-acumulacion-ana.json eventos/movimiento-redencion.json   # saldo 35 → 15
python3 publicar.py eventos/movimiento-redencion.json                       # reentrega: DESCARTADO
python3 publicar.py eventos/movimiento-redencion-saldo-insuficiente.json    # FALLIDO: pide 500 y hay 15
```

`RedencionPuntosIntegracionTest` (PostgreSQL y RabbitMQ reales con Testcontainers) reproduce los cuatro
criterios: redención con su transacción en el historial, saldo insuficiente, cliente sin saldo,
reentrega, copias y redenciones simultáneas, nivel, confirmación, reproceso y filtro de la bitácora.

```bash
cd backend/servicio-fidelizacion
mvn test   # requiere Docker
```

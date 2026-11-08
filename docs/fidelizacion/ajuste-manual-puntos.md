# Ajuste manual del saldo de puntos (SCRUM-51)

El Administrador de CRM ajusta a mano el saldo de puntos de un cliente para corregir errores, por ejemplo una
compra que no acumuló o una redención registrada dos veces (RF-43, EPIC-08). Indica un motivo, y cada ajuste
queda con trazabilidad completa. Usa la consulta de saldo (SCRUM-47) y la caducidad (SCRUM-50). Diagramas en
`docs/fidelizacion/ajuste-manual-puntos-diagramas.md`.

## Modelo de auditoría (SCRUM-421)

Cada ajuste deja rastro en tres lugares, y los tres se guardan en la misma transacción que el cambio del saldo.
Si uno falla, el ajuste no se aplica.

### 1. Registro del ajuste: `fidelizacion.ajuste_puntos`

Es de **solo inserción**: un trigger impide modificar o borrar sus filas, como en la auditoría compartida.

| Columna | Contenido |
|---|---|
| `id` | Número del ajuste |
| `id_solicitud` | UUID que envía la pantalla al confirmar (único): un doble clic o un reintento no aplica dos veces el ajuste |
| `id_transaccion` | `AJUSTE-{idSolicitud}`: el movimiento del historial de puntos que lo aplicó |
| `cliente_id` | Cliente del CRM |
| `puntos` | Variación con signo: positiva suma y negativa resta (nunca 0) |
| `saldo_anterior`, `saldo_resultante` | Saldo disponible antes y después del ajuste |
| `motivo` | Por qué se ajustó (obligatorio) |
| `referencia` | Opcional: la transacción, el ticket o el caso que justifica el ajuste |
| `usuario`, `rol` | Responsable: el usuario autenticado en el API Gateway (`X-Usuario`, `X-Usuario-Rol`) |
| `registrado_en` | Fecha y hora del ajuste, que pone el servidor |

El responsable y la fecha nunca los envía la pantalla: salen de la sesión validada por el Gateway y del reloj
del servidor (criterio de aceptación 1).

### 2. Movimiento en el historial de puntos: tipo `AJUSTE`

El ajuste aparece en `fidelizacion.movimiento_puntos` junto a las acumulaciones, redenciones, reversas y
caducidades. Así, el saldo resultante de cada movimiento sigue cuadrando y la consulta del saldo muestra el ajuste
entre los últimos movimientos.

| Campo | Valor |
|---|---|
| `idTransaccion` | `AJUSTE-{idSolicitud}` |
| `puntos` / `variacionSaldo` | El valor absoluto del ajuste / el ajuste con su signo |
| `saldoResultante` | El saldo después del ajuste |
| `fechaTransaccion` | El momento del ajuste |

No viene de un evento de Marketplace y Ventas, así que no tiene evento en la bitácora, cliente de Ventas ni
regla de acumulación. Un evento RIO-CRM-03 con `tipoMovimiento: AJUSTE` se rechaza como inválido.

### 3. Auditoría compartida: `auditoria.evento`

En la auditoría de solo inserción que consulta el Administrador (`docs/seguridad/alcance-auditoria.md`) quedan dos
registros:

- **El del API Gateway:** guarda el acceso a la ruta del cliente, `CLIENTE_MODIFICADO`, o `ACCESO_DENEGADO` si
  el usuario no tiene permiso.
- **El del servicio:** `PUNTOS_AJUSTADOS`, con entidad `CLIENTE` y el número del cliente. El detalle lleva
  `idAjuste`, `idTransaccion`, `puntos`, `saldoAnterior`, `saldoResultante`, `motivo`, `referencia` y `rol`. A
  diferencia de las consultas, aquí el detalle sí incluye el saldo, porque lo que se audita es su cambio.

### Historial de ajustes (criterio de aceptación 4)

`GET /api/fidelizacion/clientes/{clienteId}/ajustes` devuelve los ajustes del cliente, del más reciente al más
antiguo, con su motivo, su responsable y su fecha. La ficha integral los muestra en el bloque **Ajustes de
puntos**.

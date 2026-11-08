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

## Reglas de validación (SCRUM-429)

```
POST /api/fidelizacion/clientes/{clienteId}/ajustes
```

```json
{
  "idSolicitud": "3f0c2a4e-8d1b-4c55-9a8e-2b7d6c1e0f42",
  "puntos": 20,
  "motivo": "Compra V-100236 del 18/10 que no acumuló puntos",
  "referencia": "V-100236",
  "saldoActual": 15
}
```

| Campo | Regla | Si no se cumple |
|---|---|---|
| `idSolicitud` | Obligatorio, UUID. La pantalla genera uno nuevo para cada ajuste | `400`; si ya se aplicó, `409` "Este ajuste ya se aplicó" |
| `puntos` | Entero distinto de 0 y de hasta 100.000 en valor absoluto: positivo suma, negativo resta | `400` |
| `motivo` | Obligatorio, de 10 a 250 caracteres sin contar los espacios de los extremos | `400` |
| `referencia` | Opcional, hasta 64 caracteres | `400` |
| `saldoActual` | Obligatorio: el saldo disponible que el Administrador vio al confirmar | `409` si el saldo cambió mientras revisaba: debe volver a revisar |

Los errores de campo vienen en `campos`, como en las demás pantallas de configuración. Además:

1. **Saldo negativo (criterio de aceptación 2).** Si el ajuste deja el saldo por debajo de 0, se rechaza con
   `400` y el saldo no cambia. Ejemplo:
   `El ajuste dejaría el saldo en -5: el saldo disponible es 15 y se quieren restar 20 puntos`. Para dejar el saldo
   en 0 hay que restar exactamente el saldo disponible.
2. **Solo el Administrador de CRM (criterio de aceptación 3).** El API Gateway exige el rol `ADMINISTRADOR_CRM` y
   el permiso `FIDELIZACION_CONFIGURAR`. Sin sesión responde `401`, y con otro rol o sin el permiso, `403`, que
   queda en la auditoría como `ACCESO_DENEGADO`. El servicio vuelve a exigir lo mismo con la identidad que
   reenvía el Gateway. La consulta del historial de ajustes exige `PUNTOS_CONSULTAR`, como las demás consultas de
   puntos.
3. **Primero la caducidad.** Antes de ajustar, si el saldo del cliente ya estaba vencido, se caduca en la misma
   transacción (SCRUM-50). El ajuste parte del saldo disponible, el mismo que muestran las consultas, y un saldo
   vencido no se puede ajustar como si siguiera vigente.
4. **Bloqueo del saldo.** El saldo se bloquea (`SELECT … FOR UPDATE`) mientras se ajusta. Si al mismo tiempo
   llega una redención o una caducidad del mismo cliente, se aplican de a una, y la segunda ve el saldo que dejó
   la primera.
5. **Vigencia.** Un ajuste positivo renueva la vigencia del saldo, como una compra que acumula puntos: los
   puntos que se abonan tienen la vigencia completa. Un ajuste negativo no la cambia.
6. **Nivel.** El ajuste cuenta en los puntos del periodo de evaluación con su signo y su fecha, igual que una
   acumulación o una redención. El nivel se recalcula, y si cambia queda en el historial de niveles con el
   ajuste como origen.
7. **Cliente sin cuenta de puntos.** Un ajuste positivo le crea la cuenta. Uno negativo se rechaza porque el
   saldo disponible es 0.

Respuesta `201`:

```json
{
  "id": 7,
  "idTransaccion": "AJUSTE-3f0c2a4e-8d1b-4c55-9a8e-2b7d6c1e0f42",
  "clienteId": 42,
  "puntos": 20,
  "saldoAnterior": 15,
  "saldoResultante": 35,
  "motivo": "Compra V-100236 del 18/10 que no acumuló puntos",
  "referencia": "V-100236",
  "usuario": "admin",
  "rol": "ADMINISTRADOR_CRM",
  "registradoEn": "2026-11-08T15:20:11Z",
  "nivel": "BRONCE"
}
```

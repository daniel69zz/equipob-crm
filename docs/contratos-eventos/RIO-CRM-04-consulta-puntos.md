# RIO-CRM-04 · Consulta del saldo, el nivel y la caducidad de puntos

Consulta **síncrona y de solo lectura** que el CRM expone y que el módulo **Marketplace y Ventas** del ERP invoca
antes de aplicar una redención en una venta (RF-61, SCRUM-525). Es la única excepción al flujo unidireccional
Ventas → CRM: no transfiere la propiedad del dato ni permite escribir. Con la respuesta, Ventas verifica que los
puntos alcancen; si confirma la venta, notifica la redención por RIO-CRM-03 (SCRUM-48).

| | |
|---|---|
| **Consumidor** | Módulo Marketplace y Ventas del ERP |
| **Proveedor** | `servicio-fidelizacion`, a través del API Gateway |
| **Transporte** | HTTP (REST), JSON en UTF-8 |
| **Autenticación** | Token JWT **de servicio** emitido por el API Gateway (no el de un usuario interno) |
| **Auditoría** | Cada consulta queda en `auditoria.evento` (RF-56, `docs/seguridad/alcance-auditoria.md`) |
| **OpenAPI** | [`RIO-CRM-04-consulta-puntos.openapi.yaml`](RIO-CRM-04-consulta-puntos.openapi.yaml) |

## 1. Obtener el token de servicio

```
POST /api/auth/servicio/token
Content-Type: application/json

{ "cliente": "marketplace-ventas", "secreto": "<secreto entregado al equipo de Ventas>" }
```

```json
{ "token": "eyJhbGciOiJIUzI1NiJ9...", "tipo": "Bearer", "expiraEn": "2026-11-08T15:00:00Z",
  "cliente": "marketplace-ventas", "permisos": ["PUNTOS_CONSULTAR_VENTAS"] }
```

- El secreto se configura en el Gateway con la variable de entorno `SERVICIO_VENTAS_SECRETO` (mínimo 32
  caracteres) y nunca se sube al repositorio. Sin esa variable el cliente de servicio queda deshabilitado.
- Un cliente o secreto incorrecto responde `401` con el mismo mensaje en ambos casos.
- El token dura lo mismo que el de los usuarios (`JWT_EXPIRACION_MINUTOS`, 60 por defecto); al vencer, Ventas
  pide otro. En cada petición el Gateway comprueba que el cliente de servicio siga habilitado.

## 2. Consultar los puntos del cliente

```
GET /api/fidelizacion/ventas/clientes/{idClienteVentas}/puntos
Authorization: Bearer <token de servicio>
```

| Parámetro | Regla |
|---|---|
| `idClienteVentas` | Identificador del cliente **en Marketplace y Ventas** (el mismo `idCliente` de RIO-CRM-01 y RIO-CRM-03, por ejemplo `CLI-5521`). El CRM lo vincula con su perfil |

Respuesta `200` de un cliente con puntos vigentes (criterio de aceptación 1):

```json
{
  "idClienteVentas": "CLI-5521",
  "clienteId": 42,
  "tieneCuenta": true,
  "saldoDisponible": 15,
  "nivel": "BRONCE",
  "fechaCaducidad": "2027-10-18T14:14:50Z",
  "puntosCaducados": 0,
  "vigenciaMeses": 12,
  "consultadoEn": "2026-11-08T14:02:11Z"
}
```

| Campo | Tipo | Descripción |
|---|---|---|
| `idClienteVentas` | texto | El identificador consultado |
| `clienteId` | entero o `null` | Cliente del CRM; `null` si Ventas aún no lo sincronizó (RIO-CRM-01) |
| `tieneCuenta` | booleano | `false` si el cliente nunca tuvo puntos |
| `saldoDisponible` | entero | Puntos que se pueden redimir **ahora**. Ventas no debe aplicar una redención mayor |
| `nivel` | `BRONCE` \| `PLATA` \| `ORO` \| `null` | Nivel de fidelización vigente (SCRUM-49) |
| `fechaCaducidad` | fecha y hora ISO-8601 o `null` | Momento en que caduca el saldo si el cliente no vuelve a acumular |
| `puntosCaducados` | entero | Puntos del saldo cuya vigencia ya venció y que no se pueden redimir |
| `vigenciaMeses` | entero | Vigencia configurada de los puntos |
| `consultadoEn` | fecha y hora ISO-8601 | Momento de la consulta: el saldo puede cambiar después |

### Caducidad

Los puntos de un cliente vencen **`vigenciaMeses` meses (12 por defecto) después de su última acumulación**:
cada compra que acumula puntos renueva la vigencia de todo el saldo, y también un ajuste manual positivo del
Administrador de CRM (SCRUM-51). `fechaCaducidad` es la fecha de la última
acumulación más la vigencia. Es la regla que aplica el proceso diario de caducidad (SCRUM-50,
`docs/fidelizacion/caducidad-puntos.md`). La vigencia se configura con `FIDELIZACION_VIGENCIA_MESES` hasta que
exista la configuración general (SCRUM-65).

Si `fechaCaducidad` ya pasó y el proceso de caducidad todavía no descontó esos puntos, la consulta los informa
como `puntosCaducados` y responde `saldoDisponible: 0`, para que Ventas no acepte una redención con puntos
vencidos. Una vez que el proceso los descuenta, el saldo queda en 0 y `puntosCaducados` también vuelve a 0. En
el historial del cliente queda el movimiento `CADUCIDAD`. Si Ventas notifica de todos modos una redención
fechada después del vencimiento, el CRM la rechaza: queda `FALLIDO` por saldo insuficiente.

### Cliente sin cuenta de puntos (criterio de aceptación 3)

Responde `200` con saldo cero, no un error. Vale igual para un cliente que el CRM todavía no conoce:

```json
{ "idClienteVentas": "CLI-9999", "clienteId": null, "tieneCuenta": false, "saldoDisponible": 0,
  "nivel": null, "fechaCaducidad": null, "puntosCaducados": 0, "vigenciaMeses": 12,
  "consultadoEn": "2026-11-08T14:02:11Z" }
```

## Errores

| Código | Cuándo |
|---|---|
| `401` | Sin token, con un token inválido o vencido, o de un cliente de servicio deshabilitado (criterio 2). La consulta no llega a fidelización |
| `403` | Token válido pero sin el permiso `PUNTOS_CONSULTAR_VENTAS`; por ejemplo, el de un usuario interno |
| `503` | El servicio de Perfil no responde y no se puede vincular el cliente. Ventas no debe aplicar la redención |

## Auditoría (criterio de aceptación 4)

Cada consulta queda registrada dos veces en la auditoría compartida de solo inserción:

- el API Gateway registra el acceso a la ruta del cliente (`CLIENTE_CONSULTADO`, o `ACCESO_DENEGADO` con
  `401`/`403`), con el cliente de servicio como usuario y `idClienteVentas` como identificador;
- fidelización registra `PUNTOS_CONSULTADOS_POR_VENTAS` con el cliente de servicio, el cliente del CRM y si
  tenía cuenta o puntos caducados. El detalle no lleva el saldo.

## Ruta

El documento de requerimientos menciona la ruta tentativa `GET /api/v1/clientes/{id}/puntos`. La ruta
definitiva sigue la convención del Gateway (`/api/{servicio}/...`, `docs/seguridad/convencion-rutas-clientes.md`)
y lleva `ventas` para separarla de la consulta interna `GET /api/fidelizacion/clientes/{clienteId}/puntos`, que
usa el identificador del CRM y los permisos de los usuarios internos.

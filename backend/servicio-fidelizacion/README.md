# servicio-fidelizacion — Fidelización y Puntos

Administra el programa de puntos.

- **Guarda:** saldo y movimientos de puntos, reglas de acumulación, niveles y vigencia.
- **Consume de RabbitMQ (o Pub/Sub, perfil `pubsub`):** los movimientos de puntos RIO-CRM-03 de Marketplace y Ventas
  (cola `crm.fidelizacion.movimientos`, routing key `puntos.movimiento`) y las anulaciones de compra RIO-CRM-05
  (cola `crm.fidelizacion.anulaciones`, routing key `compra.anulada`). Procesa la acumulación (SCRUM-45), la
  redención (SCRUM-48) y la reversa por compra anulada o devuelta (SCRUM-524).
- **Caduca** cada día los puntos no redimidos cuya vigencia venció (SCRUM-50).
- **Responde** la consulta síncrona de saldo, nivel y caducidad de Marketplace y Ventas (RIO-CRM-04, SCRUM-525) a
  través del API Gateway, con token de servicio.
- **Esquema en la base:** `fidelizacion`

## Acumulación de puntos (SCRUM-45)

1 punto por cada tramo completo de la tasa de la regla vigente (inicialmente Bs 10), redondeando hacia abajo. Un
movimiento reentregado no acredita dos veces; uno inválido queda `FALLIDO` con la causa, sin cambiar el
saldo, y se puede reprocesar. Cada transacción deja su detalle de puntos. Regla, API y pruebas en
`docs/fidelizacion/acumulacion-puntos.md`; contrato en `docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md`.

## Redención de puntos (SCRUM-48)

Una `REDENCION` trae los puntos redimidos y los descuenta del saldo solo si alcanza, con la condición y la
resta en una sola sentencia. Si no alcanza queda `FALLIDO` con la causa, sin cambiar el saldo, y se puede
reprocesar. Una reentrega no descuenta dos veces. Después de cada cambio de saldo se reevalúa el nivel
(`AsignacionNiveles`, SCRUM-49): la redención resta sus puntos de los del periodo, así que el
cliente queda en el nivel de su nuevo saldo. Detalle en `docs/fidelizacion/redencion-puntos.md`.

| Método | Ruta | Permiso (Gateway) |
|---|---|---|
| GET | `/api/fidelizacion/clientes/{clienteId}/puntos` | `PUNTOS_CONSULTAR` |
| GET | `/api/fidelizacion/clientes/{clienteId}/movimientos` | `PUNTOS_CONSULTAR` |
| GET | `/api/fidelizacion/redenciones/{idTransaccion}` | `PUNTOS_CONSULTAR` |
| GET | `/api/fidelizacion/eventos?estado=FALLIDO&tipo=REDENCION` | `PUNTOS_CONSULTAR` |
| POST | `/api/fidelizacion/eventos/{id}/reprocesar` | `FIDELIZACION_CONFIGURAR` |

## Reglas de acumulación (SCRUM-46)

El Administrador de CRM configura los bolivianos por punto y el monto mínimo de compra. Cada cambio es
una versión nueva que se aplica a los movimientos siguientes, sin recalcular los puntos ya otorgados, y
queda en el historial con quién y cuándo la cambió. Detalle en `docs/fidelizacion/reglas-acumulacion.md`.

| Método | Ruta | Permiso (Gateway) |
|---|---|---|
| GET | `/api/fidelizacion/reglas/acumulacion` | `PUNTOS_CONSULTAR` |
| PUT | `/api/fidelizacion/reglas/acumulacion` | `FIDELIZACION_CONFIGURAR` + rol Administrador de CRM |
| GET | `/api/fidelizacion/reglas/acumulacion/historial` | rol Administrador de CRM + `FIDELIZACION_CONFIGURAR` |

## Niveles de fidelización (SCRUM-49)

Bronce, Plata y Oro con umbrales de puntos acumulados en el periodo de evaluación (12 meses por defecto).
El nivel se recalcula con cada cambio de saldo, al cambiar los umbrales y cada noche
(`FIDELIZACION_NIVELES_CRON`, por defecto `0 30 2 * * *`, zona `FIDELIZACION_ZONA_HORARIA`). Los umbrales
que se superponen o dejan huecos se rechazan indicando el conflicto. Detalle en
`docs/fidelizacion/niveles-fidelizacion.md`.

| Método | Ruta | Permiso (Gateway) |
|---|---|---|
| GET | `/api/fidelizacion/niveles` | `PUNTOS_CONSULTAR` |
| PUT | `/api/fidelizacion/niveles` | `FIDELIZACION_CONFIGURAR` + rol Administrador de CRM |
| GET | `/api/fidelizacion/niveles/historial` | rol Administrador de CRM + `FIDELIZACION_CONFIGURAR` |
| POST | `/api/fidelizacion/niveles/recalcular` | `FIDELIZACION_CONFIGURAR` + rol Administrador de CRM |

## Ejecutar

PostgreSQL, RabbitMQ y servicio-perfil disponibles. Variables `DB_URL`, `DB_USUARIO`, `DB_PASSWORD`,
`RABBIT_HOST`, `RABBIT_PUERTO`, `RABBIT_USUARIO`, `RABBIT_PASSWORD`, `SERVICIO_PERFIL_URL`; puerto 8084
(`PUERTO`). Con `SPRING_PROFILES_ACTIVE=pubsub` usa Google Cloud Pub/Sub (`docs/mensajeria/pubsub.md`).

```bash
mvn spring-boot:run
mvn test   # requiere Docker (PostgreSQL, RabbitMQ y el emulador de Pub/Sub con Testcontainers)
```

## Consulta de puntos por Marketplace y Ventas (SCRUM-525)

`GET /api/fidelizacion/ventas/clientes/{idClienteVentas}/puntos`, solo con el token de servicio de Ventas.
Devuelve el saldo disponible, el nivel y la fecha de caducidad (la última acumulación más
`FIDELIZACION_VIGENCIA_MESES`, 12 por defecto); un cliente sin cuenta responde saldo cero. Contrato y errores en
`docs/contratos-eventos/RIO-CRM-04-consulta-puntos.md`.

## Caducidad de puntos (SCRUM-50)

El saldo completo de un cliente caduca `FIDELIZACION_VIGENCIA_MESES` meses (12 por defecto) después de su última
compra con puntos. El proceso diario (`FIDELIZACION_CADUCIDAD_CRON`, a las 02:00 de La Paz) descuenta el saldo
vencido y deja un movimiento `CADUCIDAD` en el historial. Ejecutarlo dos veces no caduca dos veces los mismos
puntos. El Administrador de CRM lo puede ejecutar en el momento con `POST /api/fidelizacion/caducidad/ejecutar`,
y consultar la vigencia con `GET /api/fidelizacion/caducidad/configuracion`. Las consultas de saldo y los
movimientos aplican la misma regla. Regla y casos en `docs/fidelizacion/caducidad-puntos.md`.

## Ajuste manual de puntos (SCRUM-51)

El Administrador de CRM ajusta el saldo de un cliente con `POST /api/fidelizacion/clientes/{clienteId}/ajustes`,
indicando un motivo. El ajuste se rechaza si deja el saldo negativo o si el saldo cambió mientras se revisaba.
Cada ajuste queda como movimiento `AJUSTE` en el historial, en `fidelizacion.ajuste_puntos` (de solo inserción) y
en la auditoría compartida (`PUNTOS_AJUSTADOS`). El historial se consulta con
`GET /api/fidelizacion/clientes/{clienteId}/ajustes`. Reglas y modelo en `docs/fidelizacion/ajuste-manual-puntos.md`.

## Publicación asíncrona del saldo (SCRUM-594)

Cada movimiento confirmado y cada cambio de nivel por recálculo guarda un evento RIO-CRM-06
en una bandeja de salida transaccional. El perfil `salida-pubsub` habilita el envío a Google Cloud
Pub/Sub sin cambiar el transporte de entrada; `pubsub,salida-pubsub` usa Pub/Sub en ambos sentidos.
Un fallo del broker deja la publicación pendiente y no revierte el movimiento.

Contrato, variables de entorno, credenciales, emulador y validación en el tópico del curso:
[publicación de saldo](../../docs/fidelizacion/publicacion-saldo-puntos.md).
Diagramas: [clases y secuencia](../../docs/fidelizacion/diagramas/publicacion-saldo-puntos.md).

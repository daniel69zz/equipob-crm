# servicio-fidelizacion — Fidelización y Puntos

Administra el programa de puntos.

- **Guarda:** saldo y movimientos de puntos, reglas de acumulación, niveles y vigencia.
- **Consume de RabbitMQ (o Pub/Sub, perfil `pubsub`):** los movimientos de puntos RIO-CRM-03 de Marketplace y Ventas
  (cola `crm.fidelizacion.movimientos`, routing key `puntos.movimiento`). Hoy procesa la acumulación (SCRUM-45);
  la redención y la reversa por anulación son SCRUM-48 y SCRUM-524.
- **Responde** la consulta síncrona de saldo y nivel de Marketplace y Ventas (RIO-CRM-04) a través del API Gateway.
- **Esquema en la base:** `fidelizacion`

## Acumulación de puntos (SCRUM-45)

1 punto por cada tramo completo de la tasa de la regla vigente (inicialmente Bs 10), redondeando hacia abajo. Un
movimiento reentregado no acredita dos veces; uno inválido queda `FALLIDO` con la causa, sin cambiar el
saldo, y se puede reprocesar. Cada transacción deja su detalle de puntos. Regla, API y pruebas en
`docs/fidelizacion/acumulacion-puntos.md`; contrato en `docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md`.

| Método | Ruta | Permiso (Gateway) |
|---|---|---|
| GET | `/api/fidelizacion/clientes/{clienteId}/puntos` | `PUNTOS_CONSULTAR` |
| GET | `/api/fidelizacion/clientes/{clienteId}/movimientos` | `PUNTOS_CONSULTAR` |
| GET | `/api/fidelizacion/eventos?estado=FALLIDO` | `PUNTOS_CONSULTAR` |
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

# servicio-fidelizacion — Fidelización y Puntos

Administra el programa de puntos.

- **Guarda:** saldo y movimientos de puntos, reglas de acumulación, niveles y vigencia.
- **Consume de RabbitMQ (o Pub/Sub, perfil `pubsub`):** los movimientos de puntos RIO-CRM-03 de Marketplace y Ventas
  (cola `crm.fidelizacion.movimientos`, routing key `puntos.movimiento`). Hoy procesa la acumulación (SCRUM-45);
  la redención y la reversa por anulación son SCRUM-48 y SCRUM-524.
- **Responde** la consulta síncrona de saldo y nivel de Marketplace y Ventas (RIO-CRM-04) a través del API Gateway.
- **Esquema en la base:** `fidelizacion`

## Acumulación de puntos (SCRUM-45)

1 punto por cada Bs 10 consumidos (`FIDELIZACION_BOLIVIANOS_POR_PUNTO`), redondeando hacia abajo. Un
movimiento reentregado no acredita dos veces; uno inválido queda `FALLIDO` con la causa, sin cambiar el
saldo, y se puede reprocesar. Cada transacción deja su detalle de puntos. Regla, API y pruebas en
`docs/fidelizacion/acumulacion-puntos.md`; contrato en `docs/contratos-eventos/RIO-CRM-03-movimiento-puntos.md`.

| Método | Ruta | Permiso (Gateway) |
|---|---|---|
| GET | `/api/fidelizacion/clientes/{clienteId}/puntos` | `PUNTOS_CONSULTAR` |
| GET | `/api/fidelizacion/clientes/{clienteId}/movimientos` | `PUNTOS_CONSULTAR` |
| GET | `/api/fidelizacion/eventos?estado=FALLIDO` | `PUNTOS_CONSULTAR` |
| POST | `/api/fidelizacion/eventos/{id}/reprocesar` | `FIDELIZACION_CONFIGURAR` |

## Ejecutar

PostgreSQL, RabbitMQ y servicio-perfil disponibles. Variables `DB_URL`, `DB_USUARIO`, `DB_PASSWORD`,
`RABBIT_HOST`, `RABBIT_PUERTO`, `RABBIT_USUARIO`, `RABBIT_PASSWORD`, `SERVICIO_PERFIL_URL`; puerto 8084
(`PUERTO`). Con `SPRING_PROFILES_ACTIVE=pubsub` usa Google Cloud Pub/Sub (`docs/mensajeria/pubsub.md`).

```bash
mvn spring-boot:run
mvn test   # requiere Docker (PostgreSQL, RabbitMQ y el emulador de Pub/Sub con Testcontainers)
```

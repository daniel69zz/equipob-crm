# servicio-comportamiento — Comportamiento de Compra

Registra las compras de cada cliente y calcula sus indicadores.

- **Guarda:** historial de compras (ítems y categorías), ticket promedio, recencia, frecuencia, valor acumulado e inactividad.
- **Consume de RabbitMQ:** compras confirmadas y anulaciones o devoluciones.
- **Esquema en la base:** `comportamiento`

Historias del Sprint 1: recepción de eventos de compra, validación, idempotencia, reproceso (cola de fallidos), registro y consulta del historial, anulaciones, ticket promedio, categorías, recencia, frecuencia, valor acumulado, clientes inactivos y recálculo por anulación.

## Ingesta de compras confirmadas (SCRUM-20)

Consume el evento **RIO-CRM-02** (`docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md`) de la cola `crm.comportamiento.compras`.

1. Cada mensaje se anota en la **bitácora de ingesta** (`comportamiento.evento_recibido`) con su contenido original.
2. Se lee, se aplican las reglas de validación (SCRUM-131) y se guarda la compra con sus ítems (`compra`, `compra_item`).
3. El mensaje queda `PROCESADO`, `DESCARTADO` (la transacción ya había sido procesada; ver `docs/ingesta/idempotencia-eventos-venta.md`) o `FALLIDO` (con su causa, disponible para reproceso).

Si ni siquiera se puede escribir en la bitácora, el mensaje se reintenta 3 veces y después pasa a la cola `crm.comportamiento.compras.respaldo`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/eventos` | `EVENTOS_REPROCESAR` | Bitácora de ingesta por periodo, con el resumen por estado. Filtros: `desde`, `hasta` (`AAAA-MM-DD`, por defecto los últimos 7 días), `estado`, `origen`, `transaccion` (identificador de la compra), `pagina`, `tamanio` (máx. 100) |
| POST | `/api/comportamiento/eventos/{id}/reprocesar` | `EVENTOS_REPROCESAR` | Reprocesa un evento `FALLIDO` conservando la validación y la idempotencia del flujo normal |
| GET | `/api/comportamiento/eventos/{id}/intentos` | `EVENTOS_REPROCESAR` | Historial de intentos manuales del evento, del más reciente al más antiguo |
| POST | `/api/comportamiento/eventos/respaldo/reinyectar` | `EVENTOS_REPROCESAR` | Reinyecta un único mensaje de la cola de respaldo; responde `204` si está vacía. `cola=compras` (por defecto) o `cola=anulaciones` |

Cada intento manual se registra en `comportamiento.intento_reproceso`, incluyendo resultado,
causa y el usuario recibido en `X-Usuario`. Solo puede haber un intento activo por evento. Un
reproceso correcto cambia el evento original a `PROCESADO`; si vuelve a fallar permanece `FALLIDO`.

La reinyección de respaldo publica directamente en la cola principal y espera la confirmación de
RabbitMQ antes de retirar el mensaje de `crm.comportamiento.compras.respaldo`. Desde la cola
principal vuelve a pasar por lectura, validación e idempotencia.

## Anulaciones y devoluciones (SCRUM-522)

Consume el evento **RIO-CRM-05** (`docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md`) de la cola `crm.comportamiento.anulaciones`, con el mismo flujo que las compras: bitácora, validación (mismas reglas que RIO-CRM-02), idempotencia por `origen` + `idAnulacion`, y reproceso de los `FALLIDO`.

La anulación se guarda en `anulacion` (y sus ítems devueltos en `anulacion_item`) y se descuenta de la compra original: `compra.monto_revertido` acumula lo revertido y `compra.estado` pasa a `DEVOLUCION_PARCIAL` o `ANULADA`. Una anulación que no cuadra con la compra (no existe, otro cliente, revierte de más) queda `FALLIDO` con la causa; si llegó antes que su compra, se reprocesa cuando la compra ya esté registrada.

Si ni siquiera se puede escribir en la bitácora, el mensaje pasa a `crm.comportamiento.anulaciones.respaldo` y se reinyecta con `POST /api/comportamiento/eventos/respaldo/reinyectar?cola=anulaciones`.

## Ejecutar en local

Requiere PostgreSQL y RabbitMQ. Con Docker:

```bash
docker run -d --name crm-postgres -p 5432:5432 \
  -e POSTGRES_DB=crm_maxiconecta -e POSTGRES_USER=crm -e POSTGRES_PASSWORD=crm postgres:16
docker run -d --name crm-rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3.13-management
```

Luego, con las variables de `.env.example`:

```bash
mvn spring-boot:run
```

El servicio escucha en el puerto **8082**. La aplicación web llega a él a través del API Gateway (`/api/comportamiento/**`).

## Pruebas

```bash
mvn test
```

Las pruebas de integración levantan PostgreSQL y RabbitMQ con **Testcontainers**; si Docker no está disponible, se omiten.

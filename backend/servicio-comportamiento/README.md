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
3. El mensaje queda `PROCESADO`, `DESCARTADO` (la compra ya existía) o `FALLIDO` (con su causa, disponible para reproceso).

Si ni siquiera se puede escribir en la bitácora, el mensaje se reintenta 3 veces y después pasa a la cola `crm.comportamiento.compras.respaldo`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/eventos` | `EVENTOS_REPROCESAR` | Bitácora de ingesta por periodo, con el resumen por estado. Filtros: `desde`, `hasta` (`AAAA-MM-DD`, por defecto los últimos 7 días), `estado`, `origen`, `pagina`, `tamanio` (máx. 100) |

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

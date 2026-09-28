# servicio-comportamiento — Comportamiento de Compra

Registra las compras de cada cliente y calcula sus indicadores.

- **Guarda:** historial de compras (ítems y categorías), ticket promedio, recencia, frecuencia, valor acumulado e inactividad.
- **Consume de RabbitMQ:** compras confirmadas y anulaciones o devoluciones.
- **Esquema en la base:** `comportamiento`

Historias del Sprint 1: recepción de eventos de compra, validación, idempotencia, reproceso (cola de fallidos), registro y consulta del historial, anulaciones, ticket promedio, categorías, recencia, frecuencia, valor acumulado, clientes inactivos y recálculo por anulación.

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

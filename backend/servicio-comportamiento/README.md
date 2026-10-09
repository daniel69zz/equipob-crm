# servicio-comportamiento — Comportamiento de Compra

Registra las compras de cada cliente y calcula sus indicadores.

- **Guarda:** historial de compras (ítems y categorías), ticket promedio, recencia, frecuencia, valor acumulado e inactividad.
- **Analiza:** evolución del consumo de cada cliente por categoría y periodo.
- **Consume de RabbitMQ:** compras confirmadas y anulaciones o devoluciones.
- **Esquema en la base:** `comportamiento`

Historias del Sprint 1: recepción de eventos de compra, validación, idempotencia, reproceso (cola de fallidos), registro y consulta del historial, anulaciones, ticket promedio, categorías, recencia, frecuencia, valor acumulado, clientes inactivos y recálculo por anulación.

## Ingesta de compras confirmadas (SCRUM-20)

Consume el evento **RIO-CRM-02** (`docs/contratos-eventos/RIO-CRM-02-compra-confirmada.md`) de la cola `crm.comportamiento.compras`.

1. Cada mensaje se anota en la **bitácora de ingesta** (`comportamiento.evento_recibido`) con su contenido original.
2. Se lee, se aplican las reglas de validación (SCRUM-131) y se guarda la compra con sus ítems (`compra`, `compra_item`).
3. El mensaje queda `PROCESADO`, `DESCARTADO` (la transacción ya había sido procesada; ver `docs/ingesta/idempotencia-eventos-venta.md`) o `FALLIDO` (con su causa, disponible para reproceso).

Cada compra se vincula al perfil del cliente consultando el servicio de Perfil (`SERVICIO_PERFIL_URL`, `PERFIL_TIEMPO_ESPERA`); si el perfil no existe o el servicio no responde, queda `PENDIENTE` de vinculación. Estructura y reglas en `docs/ingesta/historial-compras.md`.

Si ni siquiera se puede escribir en la bitácora, el mensaje se reintenta 3 veces y después pasa a la cola `crm.comportamiento.compras.respaldo`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/eventos` | `EVENTOS_REPROCESAR` | Bitácora de ingesta por periodo, con el resumen por estado. Filtros: `desde`, `hasta` (`AAAA-MM-DD`, por defecto los últimos 7 días), `estado`, `transaccion` (identificador de la compra), `pagina`, `tamanio` (máx. 100) |
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

Consume el evento **RIO-CRM-05** (`docs/contratos-eventos/RIO-CRM-05-anulacion-compra.md`) de la cola `crm.comportamiento.anulaciones`, con el mismo flujo que las compras: bitácora, validación (mismas reglas que RIO-CRM-02), idempotencia por `idAnulacion`, y reproceso de los `FALLIDO`.

La anulación se guarda en `anulacion` (y sus ítems devueltos en `anulacion_item`) y se descuenta de la compra original: `compra.monto_revertido` acumula lo revertido y `compra.estado` pasa a `DEVOLUCION_PARCIAL` o `ANULADA`. Una anulación que no cuadra con la compra (no existe, otro cliente, revierte de más) queda `FALLIDO` con la causa; si llegó antes que su compra, se reprocesa cuando la compra ya esté registrada.

Si ni siquiera se puede escribir en la bitácora, el mensaje pasa a `crm.comportamiento.anulaciones.respaldo` y se reinyecta con `POST /api/comportamiento/eventos/respaldo/reinyectar?cola=anulaciones`.

## Historial de compras (SCRUM-16)

Ver diseño en `docs/compra/historial-compras.md` y el formato de la respuesta en
`docs/compra/formato-historial-compras.md`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/clientes/{clienteId}/compras` | `INDICADORES_CONSULTAR` | Compras del cliente, de la más reciente a la más antigua. `{clienteId}` solo identifica al cliente para la auditoría del Gateway; la búsqueda usa `identificador` (repetible: cada identificador del cliente en Marketplace y Ventas, tal como los devuelve `identificadoresOrigen` en `GET /api/perfil/clientes/{clienteId}`). Sin identificadores, o si ninguno tiene compras, responde una página vacía. `pagina`, `tamanio` (por defecto 20, máx. 100) |

## Indicadores del cliente (SCRUM-17, SCRUM-19, SCRUM-33, SCRUM-37)

Reglas de cálculo en `docs/compra/ticket-promedio.md`, `docs/compra/recencia-compra.md`,
`docs/compra/frecuencia-compra.md` y `docs/compra/valor-acumulado.md`. Ticket, recencia y valor
acumulado se derivan del historial y se calculan al consultarlos, así que reflejan de inmediato
las compras nuevas, devoluciones y anulaciones; frecuencia es la excepción, se persiste y se
incrementa atómicamente al registrar una compra confirmada.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/clientes/{clienteId}/indicadores` | `INDICADORES_CONSULTAR` | Indicadores del cliente. Devuelve `ticketPromedio`, `recencia`, `frecuencia` y `valorAcumulado`. La recencia contiene la última compra vigente, la duración ISO-8601 transcurrida y `sinDatos`; frecuencia es la suma persistida de compras vigentes; el valor acumulado es la suma de lo vigente de sus compras (`valor`, `compras`, `sinDatos`). Usa el parámetro repetible `identificador` |

## Clientes inactivos (SCRUM-31)

Diseño y criterio en `docs/compra/clientes-inactivos.md`. Un cliente es inactivo cuando su última
compra vigente tiene más de `comportamiento.inactividad.umbral-dias` días (por defecto 90). Se
recalcula con una tarea programada (`comportamiento.inactividad.cron`, por defecto una vez al día)
y se corrige de inmediato cuando el cliente vuelve a comprar.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/clientes?estado=inactivo` | `INDICADORES_CONSULTAR` | Clientes sin compras vigentes desde hace más del umbral configurado, del que lleva más tiempo sin comprar al que lleva menos. Devuelve el identificador del cliente (`idClienteOrigen`), la última compra y los días transcurridos. `pagina`, `tamanio` (por defecto 20, máx. 100) |

## Evolución del consumo por categoría (SCRUM-32)

Lógica de cálculo, periodos, comparación y calidad de los datos en
`docs/compra/evolucion-consumo-categoria.md`. Se calcula al consultarla sobre el historial, así que
refleja de inmediato las compras nuevas, devoluciones y anulaciones. Cada compra se asigna a su
periodo según su fecha en `comportamiento.evolucion.zona-horaria` (por defecto `America/La_Paz`).

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/clientes/{clienteId}/evolucion-consumo` | `EVOLUCION_CONSUMO_CONSULTAR` | Monto, compras y unidades vigentes de cada categoría en cada periodo del rango, en cero los periodos sin compras, con la variación contra el periodo anterior y contra el periodo base y la tendencia de la categoría. Usa `identificador` (repetible) como el historial. Filtros: `desde`, `hasta` (`AAAA-MM-DD`, por defecto los últimos 12 meses), `periodo` (`MENSUAL`, `TRIMESTRAL` o `ANUAL`), `categoria` (repetible) y `periodoBase` |

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

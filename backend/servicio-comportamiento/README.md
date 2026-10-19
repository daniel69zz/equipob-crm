# servicio-comportamiento — Comportamiento de Compra

Registra las compras de cada cliente y calcula sus indicadores.

- **Guarda:** historial de compras (ítems y categorías), ticket promedio, recencia, frecuencia, valor acumulado, inactividad, nivel de consumo y detecciones de cambio de comportamiento.
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

| Método | Ruta                                              | Permiso (en el Gateway) | Descripción                                                                                                                                                                                                                |
| ------ | ------------------------------------------------- | ----------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/comportamiento/eventos`                     | `EVENTOS_REPROCESAR`    | Bitácora de ingesta por periodo, con el resumen por estado. Filtros: `desde`, `hasta` (`AAAA-MM-DD`, por defecto los últimos 7 días), `estado`, `transaccion` (identificador de la compra), `pagina`, `tamanio` (máx. 100) |
| POST   | `/api/comportamiento/eventos/{id}/reprocesar`     | `EVENTOS_REPROCESAR`    | Reprocesa un evento `FALLIDO` conservando la validación y la idempotencia del flujo normal                                                                                                                                 |
| GET    | `/api/comportamiento/eventos/{id}/intentos`       | `EVENTOS_REPROCESAR`    | Historial de intentos manuales del evento, del más reciente al más antiguo                                                                                                                                                 |
| POST   | `/api/comportamiento/eventos/respaldo/reinyectar` | `EVENTOS_REPROCESAR`    | Reinyecta un único mensaje de la cola de respaldo; responde `204` si está vacía. `cola=compras` (por defecto) o `cola=anulaciones`                                                                                         |

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

| Método | Ruta                                               | Permiso (en el Gateway) | Descripción                                                                                                                                                                                                                                                                                                                                                                                                                                                |
| ------ | -------------------------------------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/comportamiento/clientes/{clienteId}/compras` | `INDICADORES_CONSULTAR` | Compras del cliente, de la más reciente a la más antigua. `{clienteId}` solo identifica al cliente para la auditoría del Gateway; la búsqueda usa `identificador` (repetible: cada identificador del cliente en Marketplace y Ventas, tal como los devuelve `identificadoresOrigen` en `GET /api/perfil/clientes/{clienteId}`). Sin identificadores, o si ninguno tiene compras, responde una página vacía. `pagina`, `tamanio` (por defecto 20, máx. 100) |

## Indicadores del cliente (SCRUM-17, SCRUM-19, SCRUM-33, SCRUM-37)

Reglas de cálculo en `docs/compra/ticket-promedio.md`, `docs/compra/recencia-compra.md`,
`docs/compra/frecuencia-compra.md` y `docs/compra/valor-acumulado.md`. Ticket, recencia y valor
acumulado se derivan del historial y se calculan al consultarlos, así que reflejan de inmediato
las compras nuevas, devoluciones y anulaciones; frecuencia es la excepción, se persiste y se
incrementa atómicamente al registrar una compra confirmada.

| Método | Ruta                                                   | Permiso (en el Gateway) | Descripción                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| ------ | ------------------------------------------------------ | ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/comportamiento/clientes/{clienteId}/indicadores` | `INDICADORES_CONSULTAR` | Indicadores del cliente. Devuelve `ticketPromedio`, `recencia`, `frecuencia`, `valorAcumulado` y `nivelConsumo`. La recencia contiene la última compra vigente, la duración ISO-8601 transcurrida y `sinDatos`; frecuencia es la suma persistida de compras vigentes; el valor acumulado es la suma de lo vigente de sus compras (`valor`, `compras`, `sinDatos`); el nivel de consumo (`nivel`, `valorAcumulado`, `sinNivel`) clasifica ese valor como BAJO, MEDIO o ALTO. Usa el parámetro repetible `identificador` |

## Clasificación por frecuencia (SCRUM-38)

`GET /api/comportamiento/clientes/{id}/indicadores` incluye `clasificacionFrecuencia`: OCASIONAL (1 a 3
compras vigentes), HABITUAL (4 a 9) o FRECUENTE (10 o más), calculada sobre la frecuencia de SCRUM-37, o
"sin clasificación" sin compras vigentes. Umbrales configurables con
`CLASIFICACION_FRECUENCIA_UMBRAL_HABITUAL` y `CLASIFICACION_FRECUENCIA_UMBRAL_FRECUENTE`. También viaja en
las métricas que se publican para la segmentación. Reglas en `docs/compra/clasificacion-frecuencia.md`.

## Valor consumido por periodo (SCRUM-39)

Diseño, validaciones y formato de la respuesta en `docs/compra/consumo-por-periodo.md`; diagramas en
`docs/compra/consumo-por-periodo-diagramas.md`. Devuelve el valor total y el promedio por compra que
consumió un cliente entre dos fechas, ambas inclusive y en la zona horaria del negocio
(`comportamiento.consumo-periodo.zona-horaria`, por defecto `America/La_Paz`). No incluye compras
anuladas; una devolución parcial aporta lo que le queda vigente. Un periodo sin compras responde 200
con cero, `sinDatos: true` y un mensaje.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/clientes/{clienteId}/consumo-periodo` | `INDICADORES_CONSULTAR` | Valor consumido en el periodo. `desde` y `hasta` (obligatorias, `AAAA-MM-DD`, máx. 5 años; 400 si faltan, están al revés o tienen otro formato). Devuelve `total`, `promedio`, `compras`, `sinDatos` y `mensaje`. Usa el parámetro repetible `identificador` |

## Clientes inactivos (SCRUM-31)

Diseño y criterio en `docs/compra/clientes-inactivos.md`. Un cliente es inactivo cuando su última
compra vigente tiene más de `comportamiento.inactividad.umbral-dias` días (por defecto 90). Se
recalcula con una tarea programada (`comportamiento.inactividad.cron`, por defecto una vez al día)
y se corrige de inmediato cuando el cliente vuelve a comprar.

| Método | Ruta                                           | Permiso (en el Gateway) | Descripción                                                                                                                                                                                                                                                                        |
| ------ | ---------------------------------------------- | ----------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| GET    | `/api/comportamiento/clientes?estado=inactivo` | `INDICADORES_CONSULTAR` | Clientes sin compras vigentes desde hace más del umbral configurado, del que lleva más tiempo sin comprar al que lleva menos. Devuelve el identificador del cliente (`idClienteOrigen`), la última compra y los días transcurridos. `pagina`, `tamanio` (por defecto 20, máx. 100) |

## Retención por cohortes (SCRUM-36)

Reglas, cálculo manual de referencia y limitaciones en `docs/compra/retencion-cohortes.md`; diagramas
en `docs/compra/retencion-cohortes-diagramas.md`. Cada cliente queda en la cohorte del mes de su
primera compra vigente (en hora de Bolivia, `COHORTES_ZONA`), y se mide qué porcentaje vuelve a
comprar en cada mes posterior. Las compras anuladas no cuentan ni como primera compra ni como retención.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/comportamiento/cohortes` | `INDICADORES_CONSULTAR` | Matriz de retención: por cohorte (`AAAA-MM`), sus clientes y, por periodo hasta el mes en curso, los clientes que compraron y su porcentaje. `desde` y `hasta` (`AAAA-MM`, por defecto los últimos 12 meses, máx. 36) |

## Métricas para la segmentación (SCRUM-26)

Después de cada compra o anulación procesada, y solo si su transacción se confirma, publica las
métricas vigentes del cliente (frecuencia, valor acumulado y categorías de todos sus identificadores
vinculados) en el exchange `crm.comportamiento.eventos` con la routing key `metricas.actualizadas`.
`servicio-segmentacion` las consume para asignar el segmento. Un evento descartado por duplicado o una
compra sin perfil vinculado no publican nada. Formato y reglas en
`docs/segmentacion/asignacion-automatica.md`.

## Perfil de preferencias (SCRUM-41)

Construcción explícita y persistencia por cliente CRM, usando todo su historial vigente y el ranking
de categorías de SCRUM-18 (monto, compras y nombre). Modelo, validaciones y alcance en
`docs/preferencias/perfil-preferencias.md`; comandos y resultados esperados en `docs/preferencias/prueba-manual.md`.

| Método | Ruta | Acceso en Gateway |
|---|---|---|
| POST | `/api/comportamiento/clientes/{clienteId}/preferencias/construir` | Administrador y `CLIENTE_EDITAR` |
| GET | `/api/comportamiento/clientes/{clienteId}/preferencias` | Administrador o Agente, y `FICHA_INTEGRAL_CONSULTAR` |

La construcción verifica los identificadores con el servicio de Perfil y persiste la instantánea
atómicamente mediante V11. GET no recalcula.

**SCRUM-42:** el perfil también se recalcula solo con cada compra confirmada o anulación, al recibir
las métricas que publica el servicio (cola `crm.comportamiento.preferencias`). Un evento reentregado
no lo actualiza dos veces y cada actualización queda registrada con su fecha y su evento. Detalle en
`docs/preferencias/actualizacion-por-eventos.md`.

| Método | Ruta | Acceso en Gateway |
|---|---|---|
| GET | `/api/comportamiento/clientes/{clienteId}/preferencias/actualizaciones` | Igual que la consulta del perfil |

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

## Mensajería con Google Cloud Pub/Sub

Por defecto usa RabbitMQ. Con `SPRING_PROFILES_ACTIVE=pubsub` consume y publica sus eventos en Google
Cloud Pub/Sub, con los mismos nombres de tópicos y suscripciones. Ver `docs/mensajeria/pubsub.md`.

# servicio-perfil — Perfil del Cliente

Mantiene la ficha única de cada cliente.

- **Guarda:** datos personales, contacto, direcciones, consentimiento, identificadores de origen (Marketplace y Ventas) e histórico de cambios.
- **Consume de RabbitMQ:** altas y cambios de datos del cliente.
- **Esquema en la base:** `perfil`

Historias del Sprint 1: crear y actualizar perfil, actualización ante cambios, identificadores de origen, perfiles incompletos, unificación de duplicados, consentimiento, histórico de cambios y ficha integral.

## Sincronización del perfil (SCRUM-9)

Consume el evento **RIO-CRM-01** (`docs/contratos-eventos/RIO-CRM-01-datos-cliente.md`) de la cola `crm.perfil.clientes`. El modelo de datos está en `docs/perfil/modelo-datos-perfil.md`.

1. Cada mensaje se anota en la **bitácora de sincronización** (`perfil.evento_cliente`) con su contenido original.
2. Se resuelve el cliente por su identificador de origen (`perfil.cliente_origen`): si ya está vinculado se actualiza ese perfil; si no, se crea uno nuevo.
3. Los datos se validan: lo inválido queda vacío y el perfil queda `INCOMPLETO` con sus motivos.
4. Cada creación o cambio queda en `perfil.cambio_perfil` con fecha, origen, responsable y campos cambiados.
5. El mensaje queda `PROCESADO`, `INCOMPLETO`, `DESCARTADO` (evento obsoleto o ya aplicado) o `FALLIDO`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/perfil/clientes/{clienteId}` | `CLIENTE_CONSULTAR` | Perfil con sus identificadores de origen y direcciones activas |
| GET | `/api/perfil/clientes` | `CLIENTE_CONSULTAR` | Búsqueda por `origen` + `idClienteOrigen`, por `tipoDocumento` / `numeroDocumento` o por `estado`, paginada (`pagina`, `tamanio`, máx. 100) |

## Actualización ante cambios (SCRUM-11)

Una actualización (`CLIENTE_ACTUALIZADO`) cambia solo los campos que trae. Los datos se normalizan antes de compararse; si dos sistemas informan valores distintos, se aplica la regla de prioridad y queda la traza; los errores técnicos se reintentan. Mapeo y políticas en `docs/perfil/mapeo-datos-perfil.md`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/perfil/clientes/{clienteId}/sincronizaciones` | `CLIENTE_CONSULTAR` | Últimas 100 notificaciones aplicadas al perfil, con estado, causa e intentos |
| GET | `/api/perfil/clientes/{clienteId}/conflictos` | `CLIENTE_CONSULTAR` | Conflictos entre sistemas y la decisión tomada |

Configuración (`application.yml`): `crm.perfil.conflictos.prioridad-identificacion` y `crm.perfil.reintentos.*`.

## Histórico de cambios (SCRUM-22)

Cada creación o cambio del perfil deja un registro de solo lectura con el campo, el valor anterior y el nuevo, la fecha, el origen (`VENTAS`, `MARKETPLACE` o `CRM`) y el responsable. Modelo en `docs/perfil/historico-cambios.md`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/perfil/clientes/{clienteId}/historial-cambios` | `CLIENTE_CONSULTAR` | Cambios del más reciente al más antiguo. Filtros: `campo` (o prefijo), `origen`, `desde`, `hasta`, `pagina`, `tamanio` (máx. 100) |

## Identificadores de origen (SCRUM-526)

Un mismo cliente puede tener identificadores en Marketplace y en Ventas; todos apuntan a un solo perfil. Un identificador desconocido cuyo documento coincide con un perfil existente queda **pendiente de vinculación** en lugar de crear un duplicado. Detalle en `docs/perfil/identificadores-origen.md`.

| Método | Ruta | Permiso (en el Gateway) | Descripción |
|---|---|---|---|
| GET | `/api/perfil/vinculaciones` | `CLIENTE_CONSULTAR` | Vinculaciones pendientes (`?estado=PENDIENTE`, `VINCULADO`, `NUEVO_PERFIL`) |
| POST | `/api/perfil/clientes/{clienteId}/identificadores` | `CLIENTE_EDITAR` | Vincula `{"origen", "idCliente"}` al perfil y aplica sus eventos pendientes |
| POST | `/api/perfil/vinculaciones/nuevo-perfil` | `CLIENTE_EDITAR` | Declara que `{"origen", "idCliente"}` es otra persona: sus eventos pendientes crean un perfil nuevo |

## Ejecutar en local

Requiere PostgreSQL y RabbitMQ (ver los comandos de Docker en `backend/servicio-comportamiento/README.md`). Con las variables de `.env.example`:

```bash
mvn spring-boot:run
```

El servicio escucha en el puerto **8081**. La aplicación web llega a él a través del API Gateway (`/api/perfil/**`).

## Pruebas

```bash
mvn test
```

Las pruebas de integración levantan PostgreSQL y RabbitMQ con **Testcontainers**; si Docker no está disponible, se omiten.

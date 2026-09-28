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

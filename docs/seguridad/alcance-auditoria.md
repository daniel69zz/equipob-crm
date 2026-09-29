# Alcance de la auditoría de accesos y consultas

Qué operaciones sobre datos de clientes quedan registradas, con qué campos y quién puede consultarlas. Todos los registros se guardan en la tabla `auditoria.evento`, que es de **solo inserción**.

## Qué se registra

El API Gateway registra **toda petición que trae o modifica datos de un cliente**, tanto si se permite como si se deniega. Una ruta refiere a datos de un cliente cuando sigue esta convención:

```
/api/{servicio}/clientes[/{clienteId}[/...]]
```

donde `{servicio}` es `perfil`, `comportamiento`, `segmentacion`, `fidelizacion` o `interacciones`. Los microservicios deben exponer bajo `/clientes/{clienteId}` toda consulta o cambio que corresponda a un cliente concreto. La forma exacta de las rutas, con ejemplos por servicio, está en `convencion-rutas-clientes.md`.

| Operación | Cuándo se registra |
|---|---|
| `CLIENTE_CONSULTADO` | Petición `GET` a una ruta de cliente que pasa el control de acceso |
| `CLIENTE_MODIFICADO` | Petición `POST`, `PUT`, `PATCH` o `DELETE` a una ruta de cliente que pasa el control de acceso |
| `ACCESO_DENEGADO` | Petición a una ruta de cliente rechazada con **401** (sin sesión válida) o **403** (rol sin permiso) |

Estas operaciones se suman a las que ya registra la administración de seguridad (SCRUM-508): `ROL_CREADO`, `ROL_ACTUALIZADO`, `ROL_DESACTIVADO`, `USUARIO_CREADO`, `ROL_ASIGNADO` y `USUARIO_DESACTIVADO`.

La ficha integral del cliente (SCRUM-10) se expone en `GET /api/perfil/clientes/{clienteId}/ficha-integral`, que sigue la misma convención de ruta que el perfil básico (`/api/perfil/clientes/{clienteId}`). Por eso queda cubierta automáticamente por este filtro, sin ningún cambio: una consulta exitosa se registra como `CLIENTE_CONSULTADO` y un intento sin el permiso `FICHA_INTEGRAL_CONSULTAR` (SCRUM-153) queda como `ACCESO_DENEGADO`, igual que con el resto de rutas de cliente (SCRUM-156, verificado en `TrazabilidadAuditoriaTest`).

### Qué no se registra

- Rutas que no son de un cliente (configuración de segmentación o fidelización, reproceso de eventos): su control es por permiso, no por cliente.
- Peticiones `OPTIONS`.
- El **cuerpo** de la petición o de la respuesta, ni los **parámetros de búsqueda** (`?nombre=...`, `?documento=...`): podrían contener datos personales y la auditoría no debe duplicarlos.

## Campos de cada registro

| Campo | Contenido | Ejemplo |
|---|---|---|
| `ocurrido_en` | Fecha y hora en que terminó la operación | `2026-09-27 19:42:10-04` |
| `usuario` | Usuario autenticado; `anonimo` si la petición no traía una sesión válida | `ana` |
| `operacion` | Código de la tabla anterior | `CLIENTE_CONSULTADO` |
| `entidad` | Siempre `CLIENTE` para los accesos a datos de clientes | `CLIENTE` |
| `entidad_id` | Identificador del cliente afectado; vacío en listados y búsquedas | `42` |
| `detalle` | Método, ruta sin parámetros, código HTTP de la respuesta e IP de origen | `GET /api/perfil/clientes/42 · HTTP 200 · IP 10.0.0.5` |

El registro se hace **al terminar** la operación, con el código HTTP real de la respuesta. Si el registro falla, el error queda en el log del Gateway y la respuesta al usuario no cambia.

## Protección de los registros

- La tabla `auditoria.evento` rechaza `UPDATE`, `DELETE` y `TRUNCATE` mediante triggers (`V3__auditoria.sql`, `V4__proteccion_auditoria.sql`).
- La entidad JPA `EventoAuditoria` está marcada como `@Immutable`.
- El API solo expone **consultas** (`GET`); no existe ninguna ruta para modificar o borrar registros.

## Consulta

Solo los roles con el permiso `AUDITORIA_CONSULTAR` (por defecto, el **Administrador de CRM**) pueden consultar la auditoría, en `GET /api/admin/auditoria` o en la pantalla **Auditoría** de la aplicación web.

| Filtro | Parámetro | Ejemplo |
|---|---|---|
| Usuario | `usuario` | `ana` |
| Operación | `operacion` | `ACCESO_DENEGADO` |
| Cliente afectado | `clienteId` | `42` |
| Desde (fecha, incluida) | `desde` | `2026-09-01` |
| Hasta (fecha, incluida) | `hasta` | `2026-09-30` |
| Página y tamaño | `pagina`, `tamanio` | `0`, `50` (máximo 100) |

Los resultados se ordenan del más reciente al más antiguo.

# Histórico de cambios del perfil (RF-14)

Registro de todo lo que cambia en el perfil de un cliente: qué dato, su valor anterior y el nuevo, cuándo, desde qué sistema y quién. Sirve para auditar y explicar cualquier diferencia en la información del cliente.

## Modelo

```
cambio_perfil 1 ──── N cambio_perfil_detalle
(una operación)        (un campo modificado)
```

### `perfil.cambio_perfil` — una fila por operación

Cada creación o modificación del perfil genera **exactamente una** fila, aunque cambien varios campos a la vez.

| Columna | Contenido |
|---|---|
| `id` | Identificador del registro |
| `id_cliente` | Perfil afectado |
| `fecha` | Momento en que el CRM guardó el cambio |
| `tipo` | `CREACION`, `ACTUALIZACION`, `VINCULACION`, `UNIFICACION` o `DETECCION` (ver abajo) |
| `origen` | `MARKETPLACE_VENTAS` (el módulo Marketplace y Ventas) o `CRM` |
| `responsable` | Usuario de Marketplace y Ventas (`vendedor.jperez`), usuario del CRM (`admin`), `sincronizacion-automatica` o `deteccion-automatica` |
| `id_evento` | Mensaje de la bitácora de sincronización que produjo el cambio; vacío en las acciones hechas en el CRM y en detecciones |
| `cambios` | Resumen en JSON de los campos modificados (el detalle consultable está en `cambio_perfil_detalle`) |

### `perfil.cambio_perfil_detalle` — una fila por campo

| Columna | Contenido |
|---|---|
| `id_cambio` | Operación a la que pertenece |
| `orden` | Orden del campo dentro de la operación |
| `campo` | `nombres`, `apellidos`, `tipoDocumento`, `numeroDocumento`, `email`, `telefono`, `estado`, `motivosIncompleto`, `motivosInconsistencia`, `motivosIncidencia` (resumen de ambos grupos), `direcciones[<id>].<dato>`, `identificadoresOrigen` o `idClienteConsolidado` |
| `valor_anterior` | Valor antes del cambio (vacío si el campo no tenía valor) |
| `valor_nuevo` | Valor después del cambio (vacío si el dato se borró) |

## Origen de cada cambio

| Origen | Tipo | Cuándo | Responsable |
|---|---|---|---|
| `MARKETPLACE_VENTAS` | `CREACION` | Un alta crea el perfil | El usuario que informa Marketplace y Ventas, o `sincronizacion-automatica` |
| `MARKETPLACE_VENTAS` | `ACTUALIZACION` | Una notificación modifica el perfil | Igual |
| `CRM` | `DETECCION` | Una detección modifica el estado o los motivos del perfil (SCRUM-170) | `deteccion-automatica` |
| `CRM` | `VINCULACION` | Un administrador vincula un identificador de origen al perfil | El usuario del CRM |
| `CRM` | `UNIFICACION` | Un administrador unifica dos perfiles duplicados | El usuario del CRM |

Las ediciones manuales del perfil que se agreguen en el CRM deben registrarse igual, con origen `CRM` y el usuario que las hizo.

Un evento que no cambia nada (por ejemplo, una notificación con los mismos datos) **no** genera registro.

## Solo lectura

Las dos tablas son de **solo inserción**: triggers de la base rechazan `UPDATE`, `DELETE` y `TRUNCATE`, y las entidades JPA son `@Immutable`. El API solo expone consultas.

## Consulta

`GET /api/perfil/clientes/{clienteId}/historial-cambios`, con el permiso `CLIENTE_CONSULTAR` (la consulta queda en la auditoría de accesos). Devuelve las operaciones **de la más reciente a la más antigua**, cada una con sus campos.

| Filtro | Parámetro | Ejemplo |
|---|---|---|
| Campo modificado | `campo` | `email` (también un prefijo: `direcciones`) |
| Origen | `origen` | `MARKETPLACE_VENTAS`, `CRM` |
| Desde / hasta (fechas incluidas) | `desde`, `hasta` | `2026-09-01`, `2026-09-30` |
| Página y tamaño | `pagina`, `tamanio` | `0`, `50` (máximo 100) |

Con el filtro `campo`, cada operación muestra solo los campos que coinciden.

La aplicación web lo muestra en **Clientes → Historial de cambios**.

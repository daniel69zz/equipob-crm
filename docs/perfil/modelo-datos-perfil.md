# Modelo de datos del perfil del cliente

Esquema `perfil` de la base `crm_maxiconecta`, dueño: `servicio-perfil`. Sigue el diseño del equipo (`crm_bd_mio_v1`) con los cambios de `ACTUALIZACION_RF_CRM_V3` (RF-05 y RF-62: tabla `cliente_origen`).

```
cliente 1 ──── N cliente_origen      (identificadores de Marketplace y Ventas)
cliente 1 ──── N direccion
cliente 1 ──── N cambio_perfil       (registro de cambios, solo inserción)
evento_cliente                       (bitácora de sincronización)
```

## `cliente` — el perfil único

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | bigint, PK | Identificador del cliente en el CRM. Es el `{clienteId}` de las rutas `/api/perfil/clientes/{clienteId}` |
| `nombres`, `apellidos` | varchar(100) | Nombre del cliente |
| `tipo_documento` | varchar(15) | `CI`, `NIT`, `PASAPORTE` o `CE` |
| `numero_documento` | varchar(20) | Número de documento |
| `email` | varchar(150) | Contacto |
| `telefono` | varchar(30) | Contacto |
| `estado` | varchar(15) | `COMPLETO`, `INCOMPLETO` o `INCONSISTENTE` (ver `catalogo-reglas-validacion.md`) |
| `motivos_incompleto` | varchar(1000) | Qué falta o está mal formado, por ejemplo `numeroDocumento: vacío; email: formato inválido` |
| `motivos_inconsistencia` | text | Reglas de coherencia incumplidas, aunque el perfil también esté incompleto |
| `creado_en`, `actualizado_en` | timestamptz | Alta y último cambio |
| `actualizado_por_origen` | varchar(20) | Origen del último cambio: `MARKETPLACE_VENTAS` (el módulo) o `CRM` cuando lo origina una acción interna, incluida la detección de SCRUM-170 |
| `actualizado_por` | varchar(100) | Responsable del último cambio |
| `id_cliente_consolidado` | FK → `cliente` | Si el perfil fue absorbido en una unificación, el perfil que se conserva |

Un perfil es **completo** cuando tiene nombres, apellidos, tipo y número de documento válidos, al menos un medio de contacto válido (correo o teléfono) y todas las direcciones informadas tienen código, calle y ciudad. Una dirección inválida no se guarda. La detección y el seguimiento de los incompletos es de SCRUM-12.

La migración V5 incorpora el estado `INCONSISTENTE` y el tipo de auditoría `DETECCION`; V6 recupera el nombre `motivos_incompleto` y agrega `motivos_inconsistencia`, conservando los motivos existentes. Si concurren ambos tipos de incidencia, prevalece `INCOMPLETO` y se conservan ambos grupos. La consulta también expone `motivosIncidencia`, que combina los dos grupos. Los cambios de estado y motivos quedan en el histórico; repetir una detección sin cambios no genera otra entrada.

## `cliente_origen` — identificadores de origen (RF-62)

| Columna | Tipo | Descripción |
|---|---|---|
| `id_cliente_origen` | PK | Identificador del cliente en Marketplace y Ventas |
| `id_cliente` | FK → `cliente` | Perfil al que apunta |
| `fecha_vinculacion` | timestamptz | Cuándo se vinculó |
| `motivo_vinculacion` | varchar(30) | `ALTA_AUTOMATICA`, `VINCULACION_MANUAL` o `UNIFICACION` |
| `vinculado_por` | varchar(100) | Quién lo vinculó |
| `ultima_actualizacion_origen` | timestamptz | `fechaActualizacion` del último evento aplicado; ordena los eventos de ese cliente |

Varios identificadores pueden apuntar al mismo perfil. Cómo se resuelve el perfil de cada evento, las vinculaciones pendientes y la unificación están en `identificadores-origen.md`.

## `direccion`

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | bigint, PK | |
| `id_cliente` | FK → `cliente` | |
| `id_direccion_origen` | varchar | Identificador de la dirección en Marketplace y Ventas. Único por cliente |
| `tipo` | varchar(15) | `ENTREGA`, `FACTURACION` u `OTRA` |
| `calle`, `numero`, `zona`, `ciudad`, `referencia` | varchar | Datos de la dirección, tal como llegan |
| `principal` | boolean | Dirección principal |
| `activa` | boolean | `false` cuando Marketplace y Ventas deja de informarla; no se borra |
| `actualizada_en` | timestamptz | |

La zona y la ciudad se guardan como texto: el catálogo de zonas y ciudades del diseño original queda para cuando el CRM capture direcciones por su cuenta.

## `cambio_perfil` — registro de cambios

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | bigint, PK | |
| `id_cliente` | FK → `cliente` | |
| `fecha` | timestamptz | Momento del cambio |
| `tipo` | varchar(15) | `CREACION`, `ACTUALIZACION`, `VINCULACION`, `UNIFICACION` o `DETECCION` |
| `origen` | varchar(20) | Quién originó el cambio: `MARKETPLACE_VENTAS` (el módulo) o `CRM` (acciones de un administrador y detecciones) |
| `responsable` | varchar(100) | Usuario de Marketplace y Ventas, `sincronizacion-automatica` o `deteccion-automatica` |
| `cambios` | text (JSON) | Lista de `{campo, anterior, nuevo}` |
| `id_evento` | FK → `evento_cliente`, opcional | Evento que produjo el cambio; vacío en detecciones y acciones del CRM |

Es de **solo inserción**: un trigger rechaza `UPDATE`, `DELETE` y `TRUNCATE`. El detalle de cada campo está en `cambio_perfil_detalle`; el modelo completo y la consulta del histórico, en `historico-cambios.md`.

## `evento_cliente` — bitácora de sincronización

Cada mensaje recibido, con su contenido original: `id_evento_origen`, `tipo_evento`, `id_cliente_origen`, `id_cliente` (perfil afectado), `estado` (`RECIBIDO`, `PROCESADO`, `INCOMPLETO`, `PENDIENTE`, `DESCARTADO`, `FALLIDO`), `causa`, `recibido_en`, `procesado_en`.

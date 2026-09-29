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
| `estado` | varchar(12) | `COMPLETO` o `INCOMPLETO` |
| `motivos_incompleto` | varchar(1000) | Qué falta o está mal formado, por ejemplo `numeroDocumento: vacío; email: formato inválido` |
| `creado_en`, `actualizado_en` | timestamptz | Alta y último cambio |
| `actualizado_por_origen` | varchar(15) | Sistema del último cambio (`MARKETPLACE`, `VENTAS`) |
| `actualizado_por` | varchar(100) | Responsable del último cambio |
| `id_cliente_consolidado` | FK → `cliente` | Si el perfil fue absorbido en una unificación, el perfil que se conserva |

Un perfil es **completo** cuando tiene nombres, apellidos, tipo y número de documento válidos, al menos un medio de contacto válido (correo o teléfono) y todas las direcciones informadas tienen código, calle y ciudad. Una dirección inválida no se guarda. La detección y el seguimiento de los incompletos es de SCRUM-12.

## `cliente_origen` — identificadores de origen (RF-62)

| Columna | Tipo | Descripción |
|---|---|---|
| `origen`, `id_cliente_origen` | PK | Canal e identificador del cliente en ese canal |
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
| `origen`, `id_direccion_origen` | varchar | Identificador de la dirección en el sistema de origen. Único por cliente |
| `tipo` | varchar(15) | `ENTREGA`, `FACTURACION` u `OTRA` |
| `calle`, `numero`, `zona`, `ciudad`, `referencia` | varchar | Datos de la dirección, tal como llegan |
| `principal` | boolean | Dirección principal |
| `activa` | boolean | `false` cuando el sistema de origen deja de informarla; no se borra |
| `actualizada_en` | timestamptz | |

La zona y la ciudad se guardan como texto: el catálogo de zonas y ciudades del diseño original queda para cuando el CRM capture direcciones por su cuenta.

## `cambio_perfil` — registro de cambios

| Columna | Tipo | Descripción |
|---|---|---|
| `id` | bigint, PK | |
| `id_cliente` | FK → `cliente` | |
| `fecha` | timestamptz | Momento del cambio |
| `tipo` | varchar(15) | `CREACION`, `ACTUALIZACION`, `VINCULACION` o `UNIFICACION` |
| `origen` | varchar(15) | Sistema que originó el cambio (`MARKETPLACE`, `VENTAS` o `CRM` para las acciones de un administrador) |
| `responsable` | varchar(100) | Usuario del sistema de origen, o `sincronizacion-automatica` |
| `cambios` | text (JSON) | Lista de `{campo, anterior, nuevo}` |
| `id_evento` | FK → `evento_cliente` | Evento que produjo el cambio |

Es de **solo inserción**: un trigger rechaza `UPDATE`, `DELETE` y `TRUNCATE`. El detalle de cada campo está en `cambio_perfil_detalle`; el modelo completo y la consulta del histórico, en `historico-cambios.md`.

## `evento_cliente` — bitácora de sincronización

Cada mensaje recibido, con su contenido original: `id_evento_origen`, `tipo_evento`, `origen`, `id_cliente_origen`, `id_cliente` (perfil afectado), `estado` (`RECIBIDO`, `PROCESADO`, `INCOMPLETO`, `PENDIENTE`, `DESCARTADO`, `FALLIDO`), `causa`, `recibido_en`, `procesado_en`.

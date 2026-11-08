# Registro de interacciones con el cliente (SCRUM-52)

El Agente de Atención al Cliente registra las consultas, los reclamos y las notas de atención de un cliente, para
mantener un historial unificado de su relación con la empresa (RF-44, EPIC-09). Lo atiende `servicio-interacciones`
(puerto 8085, esquema `interacciones`), detrás del API Gateway. Todo entra desde la Aplicación Web: el servicio no
consume eventos. Diagramas en `docs/interacciones/registro-interacciones-diagramas.md`.

## Modelo de datos (SCRUM-433)

### `interacciones.interaccion`

| Columna | Tipo | Regla |
|---|---|---|
| `id` | `BIGSERIAL` | Número de la interacción |
| `cliente_id` | `BIGINT` | Obligatorio. Cliente del CRM (`servicio-perfil`); el servicio comprueba que exista |
| `tipo` | `VARCHAR(20)` | Obligatorio: `CONSULTA`, `RECLAMO` o `NOTA` |
| `canal` | `VARCHAR(20)` | Opcional: `TELEFONO`, `CORREO`, `PRESENCIAL`, `CHAT`, `WHATSAPP` u `OTRO` |
| `asunto` | `VARCHAR(120)` | Opcional: resumen de una línea para el historial |
| `descripcion` | `TEXT` | Obligatoria, de 1 a 4.000 caracteres sin contar los espacios de los extremos |
| `referencia_compra` | `VARCHAR(64)` | Opcional: la compra de Marketplace y Ventas relacionada (por ejemplo `V-100234`) |
| `registrado_por`, `registrado_rol` | `VARCHAR` | Usuario y rol que la registraron, tomados de la sesión validada por el Gateway |
| `registrado_en` | `TIMESTAMPTZ` | Fecha y hora del registro, puesta por la base |
| `modificado_por`, `modificado_en` | | Último cambio; nulos mientras no se modifique |
| `version` | `BIGINT` | Empieza en 1 y sube con cada cambio, para detectar ediciones simultáneas |

Índices: `(cliente_id, registrado_en DESC)` para el historial del cliente y `(tipo)` para el filtro.

- **Tipos de interacción:**
  - `CONSULTA`: el cliente pide información.
  - `RECLAMO`: el cliente expresa una queja o un problema.
  - `NOTA`: una observación interna del agente sobre la atención.
- **Fuera de este alcance:** el estado de seguimiento, la asignación a un responsable y la clasificación quedan
  para las historias siguientes de EPIC-09. La tabla las admitirá como columnas nuevas.
- **Sin borrado:** una interacción no se borra. El historial es unificado, y una corrección se hace modificándola,
  con su auditoría.

### `interacciones.interaccion_auditoria`

Es de **solo inserción**: un trigger impide modificarla o borrarla, como en la auditoría compartida.

| Columna | Contenido |
|---|---|
| `id` | Número del registro |
| `interaccion_id` | Interacción afectada |
| `operacion` | `CREADA` o `MODIFICADA` |
| `usuario`, `rol` | Quién la creó o la cambió |
| `ocurrido_en` | Cuándo |
| `version` | Versión de la interacción después del cambio |
| `cambios` | JSON con cada campo cambiado, su valor anterior y el nuevo (en `CREADA`, los valores iniciales) |

Además, cada registro y cada cambio quedan en la auditoría compartida `auditoria.evento`
(`docs/seguridad/alcance-auditoria.md`) como `INTERACCION_REGISTRADA` o `INTERACCION_MODIFICADA`, con la entidad
`CLIENTE` y el número del cliente. El detalle lleva la interacción, su tipo y los campos cambiados, pero no el
texto de la descripción. Así el Administrador ve en la pantalla de auditoría quién atendió a cada cliente.

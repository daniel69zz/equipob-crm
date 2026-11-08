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
| `cliente_id` | `BIGINT` | Obligatorio. Cliente del CRM (`servicio-perfil`), tomado de la ruta; el servicio comprueba que exista |
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

Los dos registros van en la misma transacción que la interacción (SCRUM-436): si uno falla, la interacción no se
guarda ni cambia. Un cambio rechazado (sin permiso, con una versión anterior o sin cambios) no deja registro.
`GET /api/interacciones/clientes/{clienteId}/interacciones/{id}/auditoria` devuelve los registros de una interacción, de la creación al último
cambio:

```json
[
  {"id": 1, "operacion": "CREADA", "usuario": "agente.ana", "rol": "AGENTE_ATENCION",
   "ocurridoEn": "2026-11-08T14:02:10Z", "version": 1,
   "cambios": {"tipo": {"anterior": null, "nuevo": "RECLAMO"},
               "descripcion": {"anterior": null, "nuevo": "Llegaron 2 de los 3 productos."}}},
  {"id": 2, "operacion": "MODIFICADA", "usuario": "admin", "rol": "ADMINISTRADOR_CRM",
   "ocurridoEn": "2026-11-08T15:20:11Z", "version": 2,
   "cambios": {"canal": {"anterior": null, "nuevo": "TELEFONO"}}}
]
```

## Permisos de acceso (SCRUM-435)

Los permisos ya existen en el Gateway (`docs/seguridad/matriz-permisos.md`): `INTERACCIONES_CONSULTAR` lo tienen
los tres roles y `INTERACCIONES_REGISTRAR`, el Administrador de CRM y el Agente de Atención.

| Acción | Administrador de CRM | Agente de Atención | Gerente Comercial |
|---|:---:|:---:|:---:|
| Registrar una interacción | ✅ | ✅ | ❌ `403` en el Gateway |
| Modificar una interacción | ✅ cualquiera | ✅ solo las que registró él | ❌ `403` en el Gateway |
| Consultar el historial de un cliente y el detalle | ✅ | ✅ | ✅ |
| Consultar la auditoría de una interacción | ✅ | ❌ `403` | ❌ `403` |

El control va en dos capas, como en los demás servicios:

1. **API Gateway** (`SecurityConfig`):
   - `GET /api/interacciones/**` exige `INTERACCIONES_CONSULTAR`.
   - `POST` y `PUT` exigen `INTERACCIONES_REGISTRAR`.
   - Sin sesión válida responde `401`; sin el permiso, `403`, y el intento sobre la ruta de un cliente queda como
     `ACCESO_DENEGADO`.
2. **servicio-interacciones** exige la identidad que reenvía el Gateway (`X-Usuario`, `X-Usuario-Rol`,
   `X-Usuario-Permisos`). Sin ella responde `401`. Además:
   - **Registrar o modificar** exige `INTERACCIONES_REGISTRAR` y el rol `ADMINISTRADOR_CRM` o `AGENTE_ATENCION`,
     aunque otro rol reciba el permiso por error.
   - **Modificar una interacción ajena:** un Agente solo modifica las que él registró. Si la registró otro agente,
     responde `403` "Solo puede modificar las interacciones que usted registró". El Administrador puede corregir
     cualquiera.
   - **Auditoría de una interacción:** exige el rol `ADMINISTRADOR_CRM` y `AUDITORIA_CONSULTAR`, como la pantalla
     de auditoría.

El permiso se comprueba antes de validar el cuerpo, así que un usuario sin permiso recibe `403` aunque su solicitud
además sea inválida.

## API (SCRUM-432)

Todas las rutas van bajo el cliente, como pide `docs/seguridad/convencion-rutas-clientes.md`: el Gateway audita
cada consulta y cada cambio con el cliente afectado.

| Método y ruta | Quién | Respuesta |
|---|---|---|
| `POST /api/interacciones/clientes/{clienteId}/interacciones` | Agente de Atención o Administrador de CRM | `201` con la interacción |
| `PUT /api/interacciones/clientes/{clienteId}/interacciones/{id}` | Quien la registró o el Administrador de CRM | `200` con la interacción y su versión nueva |
| `GET /api/interacciones/clientes/{clienteId}/interacciones` | Los tres roles | `200` con una página del historial |
| `GET /api/interacciones/clientes/{clienteId}/interacciones/{id}` | Los tres roles | `200` con la interacción |
| `GET /api/interacciones/clientes/{clienteId}/interacciones/{id}/auditoria` | Administrador de CRM con `AUDITORIA_CONSULTAR` | `200` con su auditoría |

Registro:

```json
{
  "tipo": "RECLAMO",
  "canal": "TELEFONO",
  "asunto": "Pedido incompleto",
  "descripcion": "Llegaron 2 de los 3 productos de la compra.",
  "referenciaCompra": "V-100234"
}
```

| Campo | Regla |
|---|---|
| `tipo` | Obligatorio: `CONSULTA`, `RECLAMO` o `NOTA` |
| `canal` | Opcional; vacío equivale a no indicarlo |
| `asunto` | Opcional, hasta 120 caracteres |
| `descripcion` | Obligatoria, de 1 a 4.000 caracteres sin contar los espacios de los extremos |
| `referenciaCompra` | Opcional, hasta 64 caracteres |

- **Errores de validación:** responden `400` con cada campo en `campos`. Sin descripción, la interacción no se
  guarda.
- **Cliente que no existe** en el CRM: `404`.
- **Perfil no responde:** `503`, y no se guarda nada.
- **Responsable y fecha:** los pone el servidor.
- **Espacios:** los textos se guardan sin los espacios de los extremos, y los opcionales vacíos quedan nulos.

Una corrección (`PUT`) envía los mismos campos y la `version` que el usuario tenía en pantalla:

- Si otra persona la cambió antes, responde `409` con `campos.version`.
- Si no cambia nada, responde `400`.
- Una interacción de otro cliente responde `404`.
- El cliente no se cambia.

El historial va de la más reciente a la más antigua, con `pagina` (desde 0) y `tamanio` (de 1 a 100, por defecto
20). Se filtra con (SCRUM-431):

- `tipo`: un tipo de interacción.
- `texto`: hasta 100 caracteres, sin distinguir mayúsculas. Se busca en el asunto, la descripción y la referencia,
  y `%` y `_` se buscan literalmente.
- `desde` y `hasta`: días `aaaa-mm-dd` en la hora de Bolivia, incluidos. `desde` posterior a `hasta` responde
  `400`.

```json
{ "interacciones": [ { "id": 7, "clienteId": 42, "tipo": "RECLAMO", "...": "..." } ], "total": 1, "pagina": 0, "tamanio": 20 }
```

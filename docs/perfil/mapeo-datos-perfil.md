# Mapeo de datos y políticas de actualización del perfil

Cómo se traducen los datos de Marketplace y Ventas (evento RIO-CRM-01) al perfil del CRM, y qué hace el CRM ante notificaciones parciales, conflictos entre sistemas y errores técnicos. Complementa `modelo-datos-perfil.md` e `identificadores-origen.md`.

## Alta y actualización

| Tipo de evento | Qué trae | Cómo se aplica |
|---|---|---|
| `CLIENTE_REGISTRADO` | La **foto completa** del cliente | Todos los campos: uno ausente o vacío significa que el cliente no tiene ese dato |
| `CLIENTE_ACTUALIZADO` | Solo los campos que **cambiaron** | Se actualizan únicamente los campos presentes en el mensaje; el resto del perfil se conserva |

En una actualización:

- Un campo **ausente** no cambia.
- Un campo **presente con `null` o vacío** significa que el sistema de origen borró ese dato.
- `contacto` se evalúa campo por campo: `{"contacto": {"email": "nuevo@correo.com"}}` cambia el correo y conserva el teléfono.
- `direcciones`, si viene, es la **lista completa** de direcciones de ese sistema (las que falten se desactivan). Si no viene, las direcciones no cambian.

El perfil se valida **después** de combinar lo recibido con lo que ya tenía: una actualización que solo trae el correo no deja incompleto a un perfil que ya tenía su documento.

## Mapeo de campos

| Evento | Perfil (`perfil.*`) | Normalización antes de guardar |
|---|---|---|
| `cliente.nombres` | `cliente.nombres` | Espacios de más eliminados; cada palabra con mayúscula inicial, salvo `de`, `del`, `la`, `las`, `los`, `y` en medio del nombre |
| `cliente.apellidos` | `cliente.apellidos` | Igual que los nombres |
| `cliente.tipoDocumento` | `cliente.tipo_documento` | Mayúsculas |
| `cliente.numeroDocumento` | `cliente.numero_documento` | Mayúsculas, sin espacios ni puntos |
| `cliente.contacto.email` | `cliente.email` | Minúsculas, sin espacios |
| `cliente.contacto.telefono` | `cliente.telefono` | Solo dígitos y `+`. Un número boliviano de 8 dígitos sin código de país queda como `+591XXXXXXXX` |
| `cliente.direcciones[].idDireccion` | `direccion.id_direccion_origen` | Sin espacios alrededor |
| `cliente.direcciones[].tipo` | `direccion.tipo` | `ENTREGA`, `FACTURACION` u `OTRA` |
| `cliente.direcciones[].calle`, `numero`, `zona`, `referencia` | `direccion.*` | Espacios de más eliminados |
| `cliente.direcciones[].ciudad` | `direccion.ciudad` | Espacios de más eliminados; mayúscula inicial en cada palabra |
| `cliente.direcciones[].principal` | `direccion.principal` | Solo una dirección principal por cliente |
| `origen` | `cliente_origen.origen`, `direccion.origen` | — |
| `cliente.idCliente` | `cliente_origen.id_cliente_origen` | — |
| `responsable` | `cambio_perfil.responsable`, `cliente.actualizado_por` | Si no viene: `sincronizacion-automatica` |

La normalización se aplica **antes** de comparar: `ANA@Correo.com` y `ana@correo.com` son el mismo correo y no generan un cambio.

## Conflictos entre sistemas

Hay **conflicto** cuando una notificación trae para un campo un valor distinto del actual, y el valor actual lo puso **otro** sistema. El CRM guarda qué sistema puso cada campo y cuándo (`perfil.campo_origen`).

| Campos | Regla | Por qué |
|---|---|---|
| Identificación: `nombres`, `apellidos`, `tipoDocumento`, `numeroDocumento` | **Prioridad por sistema**: Ventas antes que Marketplace | Ventas registra al cliente con el documento a la vista (caja, factura); en Marketplace lo escribe el propio cliente |
| Contacto: `email`, `telefono` | **Gana el cambio más reciente** (`fechaActualizacion`) | El cliente actualiza su contacto en el sistema que esté usando |
| Direcciones | Sin conflicto | Cada sistema mantiene sus propias direcciones |

La prioridad se configura en `crm.perfil.conflictos.prioridad-identificacion` (por defecto `VENTAS,MARKETPLACE`).

Cada conflicto queda en `perfil.conflicto_perfil` con el campo, el valor y el sistema actuales, el valor y el sistema recibidos, la decisión (`APLICADO` o `CONSERVADO`), la regla aplicada y el evento. Se consulta en `GET /api/perfil/clientes/{clienteId}/conflictos`. La bitácora de sincronización indica cuántos conflictos resolvió cada evento.

## Reintentos ante errores técnicos

| Tipo de error | Ejemplos | Qué se hace |
|---|---|---|
| **Técnico transitorio** | La base no responde, se cortó la conexión, bloqueo o interbloqueo, tiempo de espera agotado | Se reintenta hasta **3 veces en total**, esperando 0,5 s y luego 1 s (configurable en `crm.perfil.reintentos`) |
| **Del mensaje** | JSON ilegible, falta `cliente.idCliente`, origen desconocido | No se reintenta: queda `FALLIDO` de inmediato |
| **Sin bitácora** | Ni siquiera se puede anotar el mensaje | RabbitMQ lo reintenta 3 veces y después lo deja en `crm.perfil.clientes.respaldo` |

El resultado final queda en la bitácora con la cantidad de intentos (`evento_cliente.intentos`): si un reintento funcionó, el evento queda `PROCESADO`; si se agotaron, queda `FALLIDO` con la causa `Error técnico tras 3 intentos: ...`, disponible para reproceso.

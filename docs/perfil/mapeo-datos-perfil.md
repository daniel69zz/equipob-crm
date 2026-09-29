# Mapeo de datos y políticas de actualización del perfil

Cómo se traducen los datos del módulo Marketplace y Ventas (evento RIO-CRM-01) al perfil del CRM, y qué hace el CRM ante notificaciones parciales, notificaciones obsoletas y errores técnicos. Complementa `modelo-datos-perfil.md` e `identificadores-origen.md`.

## Alta y actualización

| Tipo de evento | Qué trae | Cómo se aplica |
|---|---|---|
| `CLIENTE_REGISTRADO` | La **foto completa** del cliente | Todos los campos: uno ausente o vacío significa que el cliente no tiene ese dato |
| `CLIENTE_ACTUALIZADO` | Solo los campos que **cambiaron** | Se actualizan únicamente los campos presentes en el mensaje; el resto del perfil se conserva |

En una actualización:

- Un campo **ausente** no cambia.
- Un campo **presente con `null` o vacío** significa que Marketplace y Ventas borró ese dato.
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
| `cliente.idCliente` | `cliente_origen.id_cliente_origen` | — |
| `responsable` | `cambio_perfil.responsable`, `cliente.actualizado_por` | Si no viene: `sincronizacion-automatica` |

La normalización se aplica **antes** de comparar: `ANA@Correo.com` y `ana@correo.com` son el mismo correo y no generan un cambio.

## Cambios de un mismo cliente

Todos los datos llegan del mismo módulo, así que no hay dos fuentes que compitan por un campo: cada notificación aplica los campos que trae. Las notificaciones de un identificador se ordenan por `fechaActualizacion`: una igual o más antigua que la última aplicada se descarta (`DESCARTADO`).

## Reintentos ante errores técnicos

| Tipo de error | Ejemplos | Qué se hace |
|---|---|---|
| **Técnico transitorio** | La base no responde, se cortó la conexión, bloqueo o interbloqueo, tiempo de espera agotado | Se reintenta hasta **3 veces en total**, esperando 0,5 s y luego 1 s (configurable en `crm.perfil.reintentos`) |
| **Del mensaje** | JSON ilegible, falta `cliente.idCliente`, `tipoEvento` desconocido | No se reintenta: queda `FALLIDO` de inmediato |
| **Sin bitácora** | Ni siquiera se puede anotar el mensaje | RabbitMQ lo reintenta 3 veces y después lo deja en `crm.perfil.clientes.respaldo` |

El resultado final queda en la bitácora con la cantidad de intentos (`evento_cliente.intentos`): si un reintento funcionó, el evento queda `PROCESADO`; si se agotaron, queda `FALLIDO` con la causa `Error técnico tras 3 intentos: ...`, disponible para reproceso.
